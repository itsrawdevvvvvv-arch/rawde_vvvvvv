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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

data class ChatUploadedFile(
    val fileName: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val textPreview: String = ""
)

class CacheManager(val context: Context) {

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

    val customFontsDir: File
        get() = File(context.filesDir, "custom_fonts").apply { mkdirs() }

    val customStyleDir: File
        get() = File(context.filesDir, "custom_style").apply { mkdirs() }

    val chatUploadsDir: File
        get() = File(context.filesDir, "chat_uploads").apply { mkdirs() }

    val chatHistoryDir: File
        get() = File(context.filesDir, "chat_history").apply { mkdirs() }


    fun sheetTempDir(projectId: Long): File =
        File(context.cacheDir, "sheet_temp/project_${projectId}").apply { mkdirs() }

    fun resetSheetTempDir(projectId: Long): File {
        val dir = File(context.cacheDir, "sheet_temp/project_${projectId}")
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    fun persistHtmlCode(projectId: Long, html: String): String {
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

    /**
     * Builds the active Style Pack ZIP, merging any Chatbot/user customizations from `customStyleDir`,
     * and user-imported custom fonts from `customFontsDir`.
     */
    suspend fun buildCustomizedStylePackFile(packName: String = STYLE_PACK_ASSET_NAME): File = withContext(Dispatchers.IO) {
        val outFile = File(sharedPacksDir, packName)
        val overrides = collectStylePackOverrides()
        val customFonts = listCustomFonts()

        if (overrides.isEmpty() && customFonts.isEmpty() && ghSkills.isEmpty()) {
            context.assets.open(STYLE_PACK_ASSET_NAME).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext outFile
        }

        val writtenEntries = HashSet<String>()
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            context.assets.open(STYLE_PACK_ASSET_NAME).use { assetStream ->
                ZipInputStream(assetStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory) {
                            val overrideFile = overrides[name]
                            zos.putNextEntry(ZipEntry(name))
                            if (overrideFile != null && overrideFile.exists()) {
                                overrideFile.inputStream().use { it.copyTo(zos) }
                            } else {
                                zis.copyTo(zos)
                            }
                            zos.closeEntry()
                            writtenEntries.add(name)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Write any new files added to customStyleDir that weren't in original zip
            for ((entryPath, file) in overrides) {
                if (entryPath !in writtenEntries && file.exists() && file.isFile) {
                    zos.putNextEntry(ZipEntry(entryPath))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    writtenEntries.add(entryPath)
                }
            }

            // Bundle user custom fonts inside kinetic-explainer-style/fonts/
            for (font in customFonts) {
                val entryName = "kinetic-explainer-style/fonts/${font.name}"
                if (entryName !in writtenEntries) {
                    zos.putNextEntry(ZipEntry(entryName))
                    font.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    writtenEntries.add(entryName)
                }
            }


        }
        outFile
    }

    private fun collectStylePackOverrides(): Map<String, File> {
        val result = LinkedHashMap<String, File>()
        fun walk(dir: File, prefix: String) {
            dir.listFiles()?.forEach { f ->
                val rel = if (prefix.isEmpty()) f.name else "$prefix/${f.name}"
                if (f.isDirectory) {
                    walk(f, rel)
                } else if (f.isFile) {
                    val zipPath = if (rel.startsWith("kinetic-explainer-style/")) rel else "kinetic-explainer-style/$rel"
                    result[zipPath] = f
                }
            }
        }
        if (customStyleDir.exists()) {
            walk(customStyleDir, "")
        }
        return result
    }

    fun saveStylePackOverride(entryPath: String, content: String): File {
        val normalized = entryPath.trim()
            .removePrefix("/")
            .removePrefix("kinetic-explainer-style/")
            .replace("..", "_")
            .ifBlank { "docs/CUSTOM_RULES.md" }
        val target = File(customStyleDir, normalized)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
        return target
    }

    fun readStylePackEntryText(entryPath: String): String {
        val normalized = entryPath.trim().removePrefix("/")
        val fullPath = if (normalized.startsWith("kinetic-explainer-style/")) normalized else "kinetic-explainer-style/$normalized"
        val relPath = fullPath.removePrefix("kinetic-explainer-style/")
        val overrideFile = File(customStyleDir, relPath)
        if (overrideFile.exists() && overrideFile.isFile) {
            return runCatching { overrideFile.readText(Charsets.UTF_8) }.getOrDefault("")
        }
        return runCatching {
            context.assets.open(STYLE_PACK_ASSET_NAME).use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.equals(fullPath, ignoreCase = true) || entry.name.endsWith("/$relPath", ignoreCase = true)) {
                            return@use zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    ""
                }
            }
        }.getOrDefault("")
    }

    fun listStylePackEntries(): List<String> {
        val entries = LinkedHashSet<String>()
        runCatching {
            context.assets.open(STYLE_PACK_ASSET_NAME).use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) entries.add(entry.name)
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        }
        collectStylePackOverrides().keys.forEach { entries.add(it) }
        return entries.toList()
    }

    fun resetStylePackOverrides() {
        runCatching { customStyleDir.deleteRecursively() }
        customStyleDir.mkdirs()
    }

    fun hasStylePackOverrides(): Boolean =
        collectStylePackOverrides().isNotEmpty()

    suspend fun prepareStylePackShareFile(packName: String = STYLE_PACK_ASSET_NAME): File =
        buildCustomizedStylePackFile(packName)

    suspend fun saveStylePackToUri(destUri: Uri, packName: String = STYLE_PACK_ASSET_NAME): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val zipFile = buildCustomizedStylePackFile(packName)
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                zipFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open selected location.")
            Unit
        }
    }

    suspend fun saveStylePackToDownloads(packName: String = STYLE_PACK_ASSET_NAME): Result<String> = withContext(Dispatchers.IO) {
        val zipFile = buildCustomizedStylePackFile(packName)
        saveFileToDownloads(zipFile, packName, "application/zip")
    }



    /**
     * Imports a file into the Chatbot local storage with a 500 MB limit (250–500 MB range supported),
     * streaming directly to disk so large files never trigger OOM.
     */
    suspend fun importChatAttachment(uri: Uri): Result<ChatUploadedFile> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeRaw = resolver.getType(uri)?.lowercase(Locale.US).orEmpty().ifBlank { "application/octet-stream" }
            val (origName, reportedSize) = queryDisplayNameAndSize(uri)
            if (reportedSize > MAX_CHAT_ATTACHMENT_BYTES) {
                throw IllegalArgumentException("File exceeds the 500 MB local storage limit.")
            }
            val safeName = origName.ifBlank { "upload_${System.currentTimeMillis()}" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trim('_')
                .ifBlank { "upload_${System.currentTimeMillis()}" }
            var destFile = File(chatUploadsDir, safeName)
            var idx = 1
            while (destFile.exists()) {
                val base = safeName.substringBeforeLast('.', safeName)
                val ext = safeName.substringAfterLast('.', "")
                destFile = File(chatUploadsDir, if (ext.isNotEmpty()) "${base}_$idx.$ext" else "${base}_$idx")
                idx++
            }

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val r = input.read(buf)
                        if (r <= 0) break
                        total += r
                        if (total > MAX_CHAT_ATTACHMENT_BYTES) {
                            destFile.delete()
                            throw IllegalArgumentException("File exceeds the 500 MB local storage limit.")
                        }
                        output.write(buf, 0, r)
                    }
                }
            } ?: throw IllegalArgumentException("Could not open selected file.")

            val actualSize = destFile.length()
            val ext = destFile.extension.lowercase(Locale.US)

            // If user uploaded a .ttf or .otf font in Chatbot, also install it into customFontsDir!
            if (ext == "ttf" || ext == "otf") {
                runCatching {
                    destFile.copyTo(File(customFontsDir, destFile.name), overwrite = true)
                }
            }

            val isTextLike = ext in setOf("txt", "srt", "md", "html", "htm", "js", "css", "json", "xml", "csv", "py", "kt") ||
                mimeRaw.startsWith("text/") || mimeRaw.contains("json") || mimeRaw.contains("html")

            val preview = if (isTextLike && actualSize > 0L) {
                runCatching {
                    FileInputStream(destFile).use { fis ->
                        val cap = minOf(actualSize, 24_000L).toInt()
                        val bytes = ByteArray(cap)
                        val n = fis.read(bytes)
                        if (n > 0) String(bytes, 0, n, Charsets.UTF_8) else ""
                    }
                }.getOrDefault("")
            } else if (ext == "zip") {
                runCatching {
                    val entries = mutableListOf<String>()
                    ZipInputStream(FileInputStream(destFile)).use { zis ->
                        var e = zis.nextEntry
                        while (e != null && entries.size < 40) {
                            entries.add("${e.name} (${e.size.coerceAtLeast(0)}B)")
                            zis.closeEntry()
                            e = zis.nextEntry
                        }
                    }
                    "ZIP archive entries: ${entries.joinToString(", ")}"
                }.getOrDefault("ZIP archive (${actualSize} bytes)")
            } else {
                ""
            }

            ChatUploadedFile(
                fileName = destFile.name,
                localPath = destFile.absolutePath,
                mimeType = mimeRaw,
                sizeBytes = actualSize,
                textPreview = preview
            )
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
                    throw IllegalArgumentException("Video clip exceeds 500 MB limit.")
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
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            total += r
                            if (total > MAX_VIDEO_BYTES) {
                                destFile.delete()
                                throw IllegalArgumentException("Video clip exceeds 500 MB limit.")
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
                    defaultDescription = "Local video clip ($vidW x $vidH, ${String.format(Locale.US, "%.1f", durationSec)}s). Tap Auto-describe with Gemini AI or edit note.",
                    defaultSafeZone = "Place kinetic overlay text along opposite margin or clean dark band via RAW.syncVideo."
                )
            } else {
                if (reportedSize > MAX_IMAGE_BYTES) {
                    throw IllegalArgumentException("Image exceeds 50 MB limit.")
                }
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Could not read image file.")
                if (rawBytes.size > MAX_IMAGE_BYTES) {
                    throw IllegalArgumentException("Image exceeds 50 MB limit.")
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
                        "Transparent PNG cutout ($finalW x $finalH). Tap Auto-describe with Gemini AI or edit note."
                    } else {
                        "Local image ($finalW x $finalH). Tap Auto-describe with Gemini AI or edit note."
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
                "Transparent PNG cutout ($finalW x $finalH). Tap Auto-describe with Gemini AI or edit note."
            } else {
                "Captured PNG image ($finalW x $finalH). Tap Auto-describe with Gemini AI or edit note."
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
                throw IllegalArgumentException("Audio file is over 500 MB. Pick a smaller file.")
            }

            val destFile = File(projectAudioDir, "audio_${UUID.randomUUID().toString().take(12)}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buf = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_AUDIO_BYTES) {
                            destFile.delete()
                            throw IllegalArgumentException("Audio file is over 500 MB. Pick a smaller file.")
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
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx).orEmpty()
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
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

    /**
     * Supports importing `.ttf`, `.otf`, AND `.zip` font packs (e.g. Google Fonts / DaFont downloads)
     * as well as files from content providers that omit extensions.
     */
    fun importCustomFont(uri: Uri): Result<File> = runCatching {
        val imported = importCustomFonts(uri).getOrThrow()
        imported.firstOrNull() ?: throw IllegalArgumentException("No .ttf or .otf font found in selection.")
    }

    fun importCustomFonts(uri: Uri): Result<List<File>> = runCatching {
        val (displayName, _) = queryDisplayNameAndSize(uri)
        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()
        val rawName = displayName.ifBlank { "CustomFont_${System.currentTimeMillis()}.ttf" }
        val extCandidate = if (rawName.contains('.')) rawName.substringAfterLast('.').lowercase(Locale.US) else ""

        // If user selected a .zip font archive (like Google Fonts download), extract all .ttf / .otf inside!
        if (extCandidate == "zip" || mime.contains("zip")) {
            val extracted = mutableListOf<File>()
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.replace('\\', '/').substringAfterLast('/')
                        val eExt = entryName.substringAfterLast('.', "").lowercase(Locale.US)
                        if (!entry.isDirectory && !entryName.startsWith(".") && (eExt == "ttf" || eExt == "otf")) {
                            val base = entryName.substringBeforeLast('.')
                                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                                .trim('_')
                                .ifBlank { "CustomFont" }
                            var dest = File(customFontsDir, "$base.$eExt")
                            var counter = 1
                            while (dest.exists()) {
                                dest = File(customFontsDir, "${base}_$counter.$eExt")
                                counter++
                            }
                            dest.outputStream().use { out -> zis.copyTo(out) }
                            if (dest.length() > 0L) extracted.add(dest)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            if (extracted.isEmpty()) {
                throw IllegalArgumentException("No .ttf or .otf font files found inside the ZIP archive.")
            }
            return@runCatching extracted
        }

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read font stream.")
        if (bytes.size < 12) {
            throw IllegalArgumentException("Selected font file is empty or invalid.")
        }

        // Check magic bytes for TrueType (00 01 00 00 or 'true') or OpenType ('OTTO')
        val isOtoMagic = bytes[0] == 'O'.code.toByte() && bytes[1] == 'T'.code.toByte() &&
            bytes[2] == 'T'.code.toByte() && bytes[3] == 'O'.code.toByte()
        val isTtfMagic = (bytes[0] == 0.toByte() && bytes[1] == 1.toByte() && bytes[2] == 0.toByte() && bytes[3] == 0.toByte()) ||
            (bytes[0] == 't'.code.toByte() && bytes[1] == 'r'.code.toByte() && bytes[2] == 'u'.code.toByte() && bytes[3] == 'e'.code.toByte())

        val finalExt = when {
            extCandidate == "otf" || isOtoMagic || mime.contains("otf") || mime.contains("opentype") -> "otf"
            extCandidate == "ttf" || isTtfMagic || mime.contains("ttf") || mime.contains("truetype") || mime.contains("font") -> "ttf"
            else -> throw IllegalArgumentException("Only .ttf, .otf, or .zip font packs are supported.")
        }

        val base = rawName.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_')
            .ifBlank { "CustomFont_${System.currentTimeMillis()}" }
        var dest = File(customFontsDir, "$base.$finalExt")
        var counter = 1
        while (dest.exists()) {
            dest = File(customFontsDir, "${base}_$counter.$finalExt")
            counter++
        }
        dest.writeBytes(bytes)
        listOf(dest)
    }

    fun deleteCustomFont(fileName: String): Boolean {
        val clean = fileName.substringAfterLast('/')
        val f = File(customFontsDir, clean)
        return if (f.exists()) f.delete() else false
    }

    companion object {
        val ASSET_FILENAME_REGEX = Regex("^[A-Za-z0-9._-]{1,80}\\.(png|jpg|jpeg|webp|mp4|mov|webm)$")
        private val ALLOWED_AUDIO_EXT = setOf("mp3", "m4a", "wav", "aac", "ogg")
        const val MAX_AUDIO_BYTES = 500L * 1024L * 1024L
        private const val MAX_AUDIO_SEC = 20.0 * 60.0
        const val MAX_IMAGE_BYTES = 50L * 1024L * 1024L
        const val MAX_VIDEO_BYTES = 500L * 1024L * 1024L
        const val MAX_CHAT_ATTACHMENT_BYTES = 500L * 1024L * 1024L
        private const val MAX_LOCKER_IMAGE_DIM = 1920
        const val CODE_FILE_PREFIX = "@rawfile:"
        const val TOPIC_FILE_PREFIX = "@rawtxt:"
        const val SRT_FILE_PREFIX = "@rawsrt:"
        const val STYLE_PACK_KINETIC = "kinetic-explainer-style.zip"
        const val STYLE_PACK_ASSET_NAME = STYLE_PACK_KINETIC
    }
}
