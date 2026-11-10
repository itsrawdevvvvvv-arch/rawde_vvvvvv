package com.rawedit.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class AudioImportResult(
    val localPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationSec: Double
)

data class ImageAssetResult(
    val assetId: String,
    val fileName: String,
    val width: Int,
    val height: Int
)

class CacheManager(private val context: Context) {

    val projectAssetsDir: File
        get() = File(context.filesDir, "project_assets").apply { mkdirs() }

    val projectAudioDir: File
        get() = File(context.filesDir, "project_audio").apply { mkdirs() }

    val profileDir: File
        get() = File(context.filesDir, "profile").apply { mkdirs() }

    val rendersDir: File
        get() = File(context.filesDir, "renders").apply { mkdirs() }

    val tempDir: File
        get() = File(context.cacheDir, "render_temp").apply { mkdirs() }

    fun createCameraTempFile(): File {
        val dir = File(context.cacheDir, "camera_temp").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun getAssetFile(fileName: String): File? {
        if (!ASSET_FILENAME_REGEX.matches(fileName)) return null
        val file = File(projectAssetsDir, fileName)
        return file.takeIf { it.exists() && it.isFile }
    }

    fun countStoredAssets(): Int {
        return projectAssetsDir.listFiles()?.count { ASSET_FILENAME_REGEX.matches(it.name) } ?: 0
    }

    suspend fun importAudio(uri: Uri): Result<AudioImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val meta = queryDisplayNameAndSize(uri)
            val name = meta.first.ifBlank { "voiceover.mp3" }
            val ext = name.substringAfterLast('.', "mp3").lowercase()
                .takeIf { it in ALLOWED_AUDIO_EXT } ?: "mp3"

            val reportedSize = meta.second
            if (reportedSize > MAX_AUDIO_BYTES) {
                throw IllegalArgumentException("Audio file is over 100 MB. Pick a smaller file.")
            }

            val destFile = File(projectAudioDir, "audio_${UUID.randomUUID().toString().take(12)}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_AUDIO_BYTES) {
                            destFile.delete()
                            throw IllegalArgumentException("Audio file is over 100 MB. Pick a smaller file.")
                        }
                        output.write(buf, 0, read)
                    }
                }
            } ?: throw IllegalArgumentException("Could not open this audio file.")

            val actualSize = destFile.length()
            if (actualSize <= 0L) {
                destFile.delete()
                throw IllegalArgumentException("This audio file appears to be empty or unreadable.")
            }

            val retriever = MediaMetadataRetriever()
            val durationMs = try {
                retriever.setDataSource(destFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durStr?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            } finally {
                runCatching { retriever.release() }
            }

            val durationSec = durationMs / 1000.0
            if (durationSec < 1.0) {
                destFile.delete()
                throw IllegalArgumentException("Audio must be at least 1 second long.")
            }
            if (durationSec > MAX_AUDIO_SEC) {
                destFile.delete()
                throw IllegalArgumentException("Audio is over 20 minutes long. Please trim it first.")
            }

            AudioImportResult(
                localPath = destFile.absolutePath,
                displayName = name,
                sizeBytes = actualSize,
                durationSec = durationSec
            )
        }
    }

    suspend fun importEditorImage(uri: Uri): Result<ImageAssetResult> = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            val meta = queryDisplayNameAndSize(uri)
            if (meta.second > MAX_IMAGE_BYTES) {
                throw IllegalArgumentException("Image is over 10 MB.")
            }

            val isPng = mime.contains("png") || meta.first.lowercase().endsWith(".png")
            val isWebp = mime.contains("webp") || meta.first.lowercase().endsWith(".webp")
            val ext = when {
                isPng -> "png"
                isWebp -> "webp"
                else -> "jpg"
            }

            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Could not read image.")
            if (rawBytes.size > MAX_IMAGE_BYTES) {
                throw IllegalArgumentException("Image is over 10 MB.")
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalArgumentException("Unreadable image file.")
            }

            val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIM)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                ?: throw IllegalArgumentException("Could not decode image.")

            val maxDim = maxOf(bitmap.width, bitmap.height)
            if (maxDim > MAX_IMAGE_DIM) {
                val scale = MAX_IMAGE_DIM.toFloat() / maxDim.toFloat()
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }

            val assetId = generateAssetId()
            val fileName = "$assetId.$ext"
            val outFile = File(projectAssetsDir, fileName)
            FileOutputStream(outFile).use { fos ->
                val format = when (ext) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    else -> Bitmap.CompressFormat.JPEG
                }
                bitmap.compress(format, 92, fos)
            }
            val w = bitmap.width
            val h = bitmap.height
            bitmap.recycle()

            ImageAssetResult(assetId = assetId, fileName = fileName, width = w, height = h)
        }
    }

    suspend fun saveProfileAvatar(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val scaled = if (bitmap.width != 512 || bitmap.height != 512) {
            Bitmap.createScaledBitmap(bitmap, 512, 512, true)
        } else {
            bitmap
        }
        profileDir.listFiles()?.forEach { it.delete() }
        val file = File(profileDir, "avatar_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { fos ->
            scaled.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
        if (scaled !== bitmap) scaled.recycle()
        file.absolutePath
    }

    suspend fun loadBitmapWithExif(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, 1024)
        val bmp = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return@withContext null

        val orientation = runCatching {
            bytes.inputStream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return@withContext bmp
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated !== bmp) bmp.recycle()
        rotated
    }

    suspend fun saveVideoToPhoneGallery(mp4File: File, title: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = title.replace(Regex("[^A-Za-z0-9_\\- ]"), "_").trim().ifBlank { "raw_edit_reel" }
            val displayName = "${safeName}_${System.currentTimeMillis() / 1000}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/raw_edit")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create gallery entry.")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    mp4File.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Could not write video to gallery.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, done, null, null)
                }
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            uri
        }
    }

    suspend fun cleanExpiredTempFiles(maxAgeDays: Int = 7) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeDays * 24L * 3600L * 1000L
        listOf(tempDir, File(context.cacheDir, "camera_temp")).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.deleteRecursively()
                }
            }
        }
    }

    suspend fun clearAllTempCache(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        listOf(tempDir, File(context.cacheDir, "camera_temp"), context.codeCacheDir).forEach { dir ->
            if (dir.exists()) {
                freed += folderSize(dir)
                dir.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
        freed
    }

    suspend fun getTempCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        folderSize(tempDir) + folderSize(File(context.cacheDir, "camera_temp"))
    }

    private fun folderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { folderSize(it) } ?: 0L
    }

    private fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        var name = ""
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx).orEmpty()
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        while (width / sample > maxDim * 2 || height / sample > maxDim * 2) {
            sample *= 2
        }
        return sample
    }

    private fun generateAssetId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val raw = UUID.randomUUID().toString().replace("-", "")
        val sb = StringBuilder(8)
        for (i in 0 until 8) {
            val idx = raw.substring(i * 2, i * 2 + 2).toInt(16) % chars.length
            sb.append(chars[idx])
        }
        return sb.toString()
    }

    companion object {
        val ASSET_FILENAME_REGEX = Regex("^[A-Za-z0-9]{8}\\.(png|jpg|jpeg|webp)$")
        private val ALLOWED_AUDIO_EXT = setOf("mp3", "m4a", "wav", "aac", "ogg")
        private const val MAX_AUDIO_BYTES = 100L * 1024L * 1024L
        private const val MAX_AUDIO_SEC = 20.0 * 60.0
        private const val MAX_IMAGE_BYTES = 10L * 1024L * 1024L
        private const val MAX_IMAGE_DIM = 2560
    }
}
