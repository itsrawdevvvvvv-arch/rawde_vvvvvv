package com.rawedit.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.rawedit.app.data.local.LocalAssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class AudioImportResult(
    val localPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationSec: Double
)

data class LocalAssetImportResult(
    val fileName: String,
    val relativePath: String,
    val kind: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val durationSec: Double,
    val sizeBytes: Long,
    val thumbPath: String,
    val defaultDescription: String,
    val defaultSafeZone: String
)

class CacheManager(private val context: Context) {

    val projectAssetsDir: File
        get() = File(context.filesDir, "project_assets").apply { mkdirs() }

    val assetThumbsDir: File
        get() = File(projectAssetsDir, "thumbs").apply { mkdirs() }

    val projectAudioDir: File
        get() = File(context.filesDir, "project_audio").apply { mkdirs() }

    val profileDir: File
        get() = File(context.filesDir, "profile").apply { mkdirs() }

    val rendersDir: File
        get() = File(context.filesDir, "renders").apply { mkdirs() }

    val tempDir: File
        get() = File(context.cacheDir, "render_temp").apply { mkdirs() }

    val codeStoreDir: File
        get() = File(context.filesDir, "raw_code").apply { mkdirs() }

    val sharedPacksDir: File
        get() = File(context.filesDir, "shared_packs").apply { mkdirs() }

    fun sheetTempDir(projectId: Long): File =
        File(context.cacheDir, "sheet_temp/project_${projectId}").apply { mkdirs() }

    fun resetSheetTempDir(projectId: Long): File {
        val dir = File(context.cacheDir, "sheet_temp/project_${projectId}")
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    fun persistHtmlCode(projectId: Long, html: String): String {
        // Keep Room rows safely under Android 9 CursorWindow limits even for multi-MB unlimited HTML
        if (html.length <= 120_000) return html
        val outFile = File(codeStoreDir, "project_${projectId}.html")
        outFile.writeText(html, Charsets.UTF_8)
        return "$CODE_FILE_PREFIX${outFile.name}"
    }

    fun resolveHtmlCode(stored: String): String {
        if (!stored.startsWith(CODE_FILE_PREFIX)) return stored
        val fileName = stored.removePrefix(CODE_FILE_PREFIX)
        val file = File(codeStoreDir, fileName)
        return if (file.exists()) runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("") else ""
    }

    fun persistTopicText(projectId: Long, topic: String): String {
        val trimmed = topic.trim()
        if (trimmed.length <= 60_000) return trimmed
        val outFile = File(codeStoreDir, "topic_${projectId}.txt")
        outFile.writeText(trimmed, Charsets.UTF_8)
        return "$TOPIC_FILE_PREFIX${outFile.name}"
    }

    fun resolveTopicText(stored: String): String {
        if (!stored.startsWith(TOPIC_FILE_PREFIX)) return stored
        val fileName = stored.removePrefix(TOPIC_FILE_PREFIX)
        val file = File(codeStoreDir, fileName)
        return if (file.exists()) runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("") else ""
    }

    fun persistSrtText(projectId: Long, srt: String): String {
        if (srt.length <= 120_000) return srt
        val outFile = File(codeStoreDir, "srt_${projectId}.srt")
        outFile.writeText(srt, Charsets.UTF_8)
        return "$SRT_FILE_PREFIX${outFile.name}"
    }

    fun resolveSrtText(stored: String): String {
        if (!stored.startsWith(SRT_FILE_PREFIX)) return stored
        val fileName = stored.removePrefix(SRT_FILE_PREFIX)
        val file = File(codeStoreDir, fileName)
        return if (file.exists()) runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("") else ""
    }

    fun deleteOverflowForProject(projectId: Long) {
        runCatching { File(codeStoreDir, "project_${projectId}.html").delete() }
        runCatching { File(codeStoreDir, "topic_${projectId}.txt").delete() }
        runCatching { File(codeStoreDir, "srt_${projectId}.srt").delete() }
        runCatching { File(context.cacheDir, "sheet_temp/project_${projectId}").deleteRecursively() }
    }

    suspend fun saveFileToUri(sourceFile: File, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open destination URI.")
            Unit
        }
    }

    suspend fun saveFileToDownloads(
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/raw_edit")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create Downloads entry.")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IllegalStateException("Could not write file to Downloads.")
                    val done = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, done, null, null)
                } catch (t: Throwable) {
                    resolver.delete(uri, null, null)
                    throw t
                }
                "Saved to Downloads/raw_edit/$displayName"
            } else {
                throw UnsupportedOperationException("Picker required on Android 9")
            }
        }
    }

    suspend fun prepareStylePackShareFile(packName: String = STYLE_PACK_ASSET_NAME): File = withContext(Dispatchers.IO) {
        val outFile = File(sharedPacksDir, packName)
        context.assets.open(packName).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        outFile
    }

    suspend fun saveStylePackToUri(destUri: Uri, packName: String = STYLE_PACK_ASSET_NAME): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                context.assets.open(packName).use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open selected location.")
            Unit
        }
    }

    suspend fun saveStylePackToDownloads(packName: String = STYLE_PACK_ASSET_NAME): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, packName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/raw_edit")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create Downloads entry.")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        context.assets.open(packName).use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IllegalStateException("Could not write ZIP to Downloads.")
                    val done = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, done, null, null)
                } catch (t: Throwable) {
                    resolver.delete(uri, null, null)
                    throw t
                }
                "Saved to Downloads/raw_edit/$packName"
            } else {
                throw UnsupportedOperationException("Picker required on Android 9")
            }
        }
    }

    fun createCameraTempFile(): File {
        val dir = File(context.cacheDir, "camera_temp").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun getAssetFile(pathOrName: String): File? {
        val normalized = pathOrName
            .removePrefix("/")
            .removePrefix("assets/")
            .trim()
        if (normalized.contains("..") || normalized.startsWith(".")) return null
        if (!ASSET_FILENAME_REGEX.matches(normalized)) return null
        val file = File(projectAssetsDir, normalized)
        return file.takeIf { it.exists() && it.isFile }
    }

    fun deleteLockerAssetFiles(fileName: String, thumbPath: String) {
        if (ASSET_FILENAME_REGEX.matches(fileName)) {
            runCatching { File(projectAssetsDir, fileName).delete() }
        }
        if (thumbPath.isNotBlank()) {
            runCatching { File(thumbPath).delete() }
        }
    }

    suspend fun importLockerAsset(
        uri: Uri,
        isVideo: Boolean
    ): Result<LocalAssetImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeRaw = resolver.getType(uri)?.lowercase(Locale.US).orEmpty()
            val (origName, reportedSize) = queryDisplayNameAndSize(uri)
            val fallbackBase = if (isVideo) "clip" else "image"

            if (isVideo || mimeRaw.startsWith("video/")) {
                if (reportedSize > MAX_VIDEO_BYTES) {
                    throw IllegalArgumentException("Video clip exceeds 150 MB limit.")
                }
                val ext = origName.substringAfterLast('.', "mp4").lowercase(Locale.US)
                    .takeIf { it in setOf("mp4", "mov", "webm") } ?: "mp4"
                val uniqueName = allocateUniqueAssetName(
                    origName.substringBeforeLast('.', fallbackBase),
                    ext,
                    fallbackBase
                )
                val destFile = File(projectAssetsDir, uniqueName)
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buf = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            total += r
                            if (total > MAX_VIDEO_BYTES) {
                                destFile.delete()
                                throw IllegalArgumentException("Video clip exceeds 150 MB limit.")
                            }
                            output.write(buf, 0, r)
                        }
                    }
                } ?: throw IllegalArgumentException("Could not open video clip.")

                val actualSize = destFile.length()
                if (actualSize <= 0L) {
                    destFile.delete()
                    throw IllegalArgumentException("Selected clip is empty.")
                }

                var vidW = 1080
                var vidH = 1920
                var durationSec = 5.0
                val thumbFile = File(assetThumbsDir, "${uniqueName.substringBeforeLast('.')}_story.jpg")
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(destFile.absolutePath)
                    vidW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()?.coerceAtLeast(1) ?: 1080
                    vidH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()?.coerceAtLeast(1) ?: 1920
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 5000L
                    durationSec = (durMs / 1000.0).coerceAtLeast(0.5)

                    val storyBmp = Bitmap.createBitmap(720, 405, Bitmap.Config.ARGB_8888)
                    val storyCanvas = Canvas(storyBmp)
                    storyCanvas.drawColor(android.graphics.Color.parseColor("#17171A"))
                    val tileW = 240
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    val pts = doubleArrayOf(0.1, 0.5, 0.85)
                    for (i in 0 until 3) {
                        val us = ((durMs * pts[i]).toLong()).coerceAtLeast(0L) * 1000L
                        val frame = runCatching {
                            retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }.getOrNull()
                        if (frame != null) {
                            val srcRect = Rect(0, 0, frame.width, frame.height)
                            val dstRect = Rect(i * tileW, 0, (i + 1) * tileW, 405)
                            storyCanvas.drawBitmap(frame, srcRect, dstRect, paint)
                            frame.recycle()
                        }
                    }
                    FileOutputStream(thumbFile).use { fos ->
                        storyBmp.compress(Bitmap.CompressFormat.JPEG, 84, fos)
                    }
                    storyBmp.recycle()
                } finally {
                    runCatching { retriever.release() }
                }

                val clipMime = when (ext) {
                    "webm" -> "video/webm"
                    "mov" -> "video/quicktime"
                    else -> "video/mp4"
                }
                LocalAssetImportResult(
                    fileName = uniqueName,
                    relativePath = "assets/$uniqueName",
                    kind = LocalAssetEntity.KIND_CLIP,
                    mimeType = clipMime,
                    width = vidW,
                    height = vidH,
                    durationSec = durationSec,
                    sizeBytes = actualSize,
                    thumbPath = thumbFile.absolutePath,
                    defaultDescription = "Local video clip ($vidW x $vidH, ${String.format(Locale.US, "%.1f", durationSec)}s). Tap Auto-describe with Groq AI or edit note.",
                    defaultSafeZone = "Place kinetic overlay text along opposite margin or clean dark band via RAW.syncVideo."
                )
            } else {
                if (reportedSize > MAX_IMAGE_BYTES) {
                    throw IllegalArgumentException("Image exceeds 15 MB limit.")
                }
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Could not read image file.")
                if (rawBytes.size > MAX_IMAGE_BYTES) {
                    throw IllegalArgumentException("Image exceeds 15 MB limit.")
                }

                val isPng = mimeRaw.contains("png") || origName.lowercase(Locale.US).endsWith(".png")
                val isWebp = mimeRaw.contains("webp") || origName.lowercase(Locale.US).endsWith(".webp")
                val ext = when {
                    isPng -> "png"
                    isWebp -> "webp"
                    else -> "jpg"
                }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw IllegalArgumentException("Unreadable image file.")
                }

                val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_LOCKER_IMAGE_DIM)
                var bmp = BitmapFactory.decodeByteArray(
                    rawBytes,
                    0,
                    rawBytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: throw IllegalArgumentException("Could not decode image.")

                if (!isPng) {
                    val orientation = runCatching {
                        rawBytes.inputStream().use {
                            ExifInterface(it).getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                        }
                    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    }
                    if (!matrix.isIdentity) {
                        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                        if (rotated !== bmp) bmp.recycle()
                        bmp = rotated
                    }
                }

                val maxDim = maxOf(bmp.width, bmp.height)
                if (maxDim > MAX_LOCKER_IMAGE_DIM) {
                    val scale = MAX_LOCKER_IMAGE_DIM.toFloat() / maxDim.toFloat()
                    val scaled = Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * scale).toInt().coerceAtLeast(1),
                        (bmp.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    if (scaled !== bmp) bmp.recycle()
                    bmp = scaled
                }

                val hasAlpha = isPng && bmp.hasAlpha()
                val kind = if (hasAlpha || isPng) LocalAssetEntity.KIND_CUTOUT else LocalAssetEntity.KIND_IMAGE
                val uniqueName = allocateUniqueAssetName(
                    origName.substringBeforeLast('.', if (isPng) "cutout" else "photo"),
                    ext,
                    if (isPng) "cutout" else "photo"
                )
                val outFile = File(projectAssetsDir, uniqueName)
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
                    bmp.compress(format, 92, fos)
                }

                // Compact preview/Vision JPEG or PNG thumb (<= 512px)
                val thumbScale = minOf(1f, 512f / maxOf(bmp.width, bmp.height).toFloat())
                val thumbBmp = if (thumbScale < 1f) {
                    Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * thumbScale).toInt().coerceAtLeast(1),
                        (bmp.height * thumbScale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    bmp
                }
                val thumbExt = if (isPng) "png" else "jpg"
                val thumbFile = File(assetThumbsDir, "${uniqueName.substringBeforeLast('.')}_thumb.$thumbExt")
                FileOutputStream(thumbFile).use { fos ->
                    thumbBmp.compress(
                        if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        86,
                        fos
                    )
                }
                if (thumbBmp !== bmp) thumbBmp.recycle()
                val finalW = bmp.width
                val finalH = bmp.height
                bmp.recycle()

                val imgMime = when (ext) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                LocalAssetImportResult(
                    fileName = uniqueName,
                    relativePath = "assets/$uniqueName",
                    kind = kind,
                    mimeType = imgMime,
                    width = finalW,
                    height = finalH,
                    durationSec = 0.0,
                    sizeBytes = outFile.length(),
                    thumbPath = thumbFile.absolutePath,
                    defaultDescription = if (kind == LocalAssetEntity.KIND_CUTOUT) {
                        "Transparent PNG cutout ($finalW x $finalH). Tap Auto-describe with Groq AI or edit note."
                    } else {
                        "Local image ($finalW x $finalH). Tap Auto-describe with Groq AI or edit note."
                    },
                    defaultSafeZone = "Keep headlines outside the subject center; pair with dark scrim if needed."
                )
            }
        }
    }

    suspend fun importBitmapAsLockerAsset(
        bitmap: Bitmap,
        suggestedName: String,
        isCutout: Boolean,
        customDescription: String? = null,
        customSafeZone: String? = null
    ): Result<LocalAssetImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val maxDim = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
            val working = if (maxDim > MAX_LOCKER_IMAGE_DIM) {
                val scale = MAX_LOCKER_IMAGE_DIM.toFloat() / maxDim.toFloat()
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val fallback = if (isCutout) "cutout" else "webshot"
            val uniqueName = allocateUniqueAssetName(suggestedName, "png", fallback)
            val outFile = File(projectAssetsDir, uniqueName)
            FileOutputStream(outFile).use { fos ->
                working.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }

            val thumbScale = minOf(1f, 512f / maxOf(working.width, working.height).toFloat())
            val thumbBmp = if (thumbScale < 1f) {
                Bitmap.createScaledBitmap(
                    working,
                    (working.width * thumbScale).toInt().coerceAtLeast(1),
                    (working.height * thumbScale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                working
            }
            val thumbFile = File(assetThumbsDir, "${uniqueName.substringBeforeLast('.')}_thumb.png")
            FileOutputStream(thumbFile).use { fos ->
                thumbBmp.compress(Bitmap.CompressFormat.PNG, 90, fos)
            }
            if (thumbBmp !== working) thumbBmp.recycle()

            val finalW = working.width
            val finalH = working.height
            if (working !== bitmap) working.recycle()

            val kind = if (isCutout) LocalAssetEntity.KIND_CUTOUT else LocalAssetEntity.KIND_IMAGE
            val desc = customDescription?.takeIf { it.isNotBlank() } ?: if (isCutout) {
                "Transparent PNG cutout ($finalW x $finalH). Tap Auto-describe with Groq AI or edit note."
            } else {
                "Captured PNG image ($finalW x $finalH). Tap Auto-describe with Groq AI or edit note."
            }
            val safe = customSafeZone?.takeIf { it.isNotBlank() }
                ?: "Keep headlines outside the subject center; pair with dark scrim if needed."

            LocalAssetImportResult(
                fileName = uniqueName,
                relativePath = "assets/$uniqueName",
                kind = kind,
                mimeType = "image/png",
                width = finalW,
                height = finalH,
                durationSec = 0.0,
                sizeBytes = outFile.length(),
                thumbPath = thumbFile.absolutePath,
                defaultDescription = desc,
                defaultSafeZone = safe
            )
        }
    }

    suspend fun loadAssetBitmapForCutout(fileName: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val file = getAssetFile(fileName)
                ?: throw IllegalArgumentException("Asset file not found.")
            val bytes = file.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalArgumentException("Asset is not a decodable image.")
            }
            val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, 1440)
            val bmp = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: throw IllegalArgumentException("Could not decode asset image.")
            val maxSide = maxOf(bmp.width, bmp.height)
            if (maxSide > 1440) {
                val scale = 1440f / maxSide.toFloat()
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== bmp) bmp.recycle() }
            } else if (bmp.config == Bitmap.Config.ARGB_8888) {
                bmp
            } else {
                bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() }
            }
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
        listOf(tempDir, File(context.cacheDir, "camera_temp"), File(context.cacheDir, "sheet_temp")).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.deleteRecursively()
                }
            }
        }
    }

    suspend fun clearAllTempCache(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        listOf(tempDir, File(context.cacheDir, "camera_temp"), File(context.cacheDir, "sheet_temp"), context.codeCacheDir).forEach { dir ->
            if (dir.exists()) {
                freed += folderSize(dir)
                dir.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
        freed
    }

    suspend fun getTempCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        folderSize(tempDir) + folderSize(File(context.cacheDir, "camera_temp")) + folderSize(File(context.cacheDir, "sheet_temp"))
    }

    private fun allocateUniqueAssetName(rawBase: String, ext: String, fallback: String): String {
        val cleanBase = rawBase.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_', '-')
            .take(36)
            .ifBlank { fallback }
        var candidate = "$cleanBase.$ext"
        var index = 1
        while (File(projectAssetsDir, candidate).exists() && index < 500) {
            candidate = "${cleanBase}_$index.$ext"
            index++
        }
        if (File(projectAssetsDir, candidate).exists()) {
            candidate = "${cleanBase}_${UUID.randomUUID().toString().take(6)}.$ext"
        }
        return candidate
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

    val customFontsDir: File
        get() = File(context.filesDir, "custom_fonts").apply { mkdirs() }

    fun listCustomFonts(): List<File> {
        return customFontsDir.listFiles { f ->
            val ext = f.extension.lowercase(Locale.US)
            (ext == "ttf" || ext == "otf") && f.length() > 0L
        }?.sortedBy { it.name } ?: emptyList()
    }

    fun getCustomFontFile(name: String): File? {
        val clean = name.substringAfterLast('/')
        val f = File(customFontsDir, clean)
        return if (f.exists() && f.isFile) f else null
    }

    fun importCustomFont(uri: Uri): Result<File> = runCatching {
        val (displayName, _) = queryDisplayNameAndSize(uri)
        val rawName = displayName.ifBlank { "CustomFont_${System.currentTimeMillis()}.ttf" }
        val ext = rawName.substringAfterLast('.', "ttf").lowercase(Locale.US)
        if (ext != "ttf" && ext != "otf") {
            throw IllegalArgumentException("Only .ttf and .otf font files are supported.")
        }
        val base = rawName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_')
        val cleanName = "${base.ifBlank { "font" }}.$ext"
        var dest = File(customFontsDir, cleanName)
        var counter = 1
        while (dest.exists()) {
            dest = File(customFontsDir, "${base}_$counter.$ext")
            counter++
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not read font stream.")
        dest
    }

    fun deleteCustomFont(fileName: String): Boolean {
        val clean = fileName.substringAfterLast('/')
        val f = File(customFontsDir, clean)
        return if (f.exists()) f.delete() else false
    }

    companion object {
        val ASSET_FILENAME_REGEX = Regex("^[A-Za-z0-9._-]{1,80}\\.(png|jpg|jpeg|webp|mp4|mov|webm)$")
        private val ALLOWED_AUDIO_EXT = setOf("mp3", "m4a", "wav", "aac", "ogg")
        private const val MAX_AUDIO_BYTES = 100L * 1024L * 1024L
        private const val MAX_AUDIO_SEC = 20.0 * 60.0
        private const val MAX_IMAGE_BYTES = 15L * 1024L * 1024L
        private const val MAX_VIDEO_BYTES = 150L * 1024L * 1024L
        private const val MAX_LOCKER_IMAGE_DIM = 1920
        const val CODE_FILE_PREFIX = "@rawfile:"
        const val TOPIC_FILE_PREFIX = "@rawtxt:"
        const val SRT_FILE_PREFIX = "@rawsrt:"
        const val STYLE_PACK_KINETIC = "kinetic-explainer-style.zip"
        const val STYLE_PACK_SWISS = "swiss-grid-motion-style.zip"
        const val STYLE_PACK_ASSET_NAME = STYLE_PACK_KINETIC
    }
}
