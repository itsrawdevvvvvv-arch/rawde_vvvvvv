package com.rawedit.app.data

import android.content.Context
import android.net.Uri
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserPreferences
import com.rawedit.app.data.local.VideoEntity
import com.rawedit.app.data.repository.RawEditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val projectsCount: Int,
    val assetsCount: Int,
    val fontsCount: Int,
    val skillsCount: Int,
    val zipFileName: String
)

object BackupManager {

    private val BACKUP_FOLDERS = listOf(
        "raw_code",
        "custom_fonts",
        "custom_style",
        "chat_history",
        "profile",
        "project_assets",
        "project_audio"
    )

    suspend fun createBackupFile(
        context: Context,
        cacheManager: CacheManager,
        userPreferences: UserPreferences,
        repository: RawEditRepository
    ): Result<Pair<File, BackupSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val timestamp = System.currentTimeMillis() / 1000L
            val fileName = "raw_edit_backup_$timestamp.zip"
            val outZip = File(cacheManager.sharedPacksDir, fileName)

            val projects = repository.getAllProjectsSync()
            val videos = repository.getAllVideosSync()
            val assets = repository.getAllAssetsSync()
            val prefsJson = userPreferences.exportToJson()

            val dbJson = JSONObject().apply {
                put("version", 2)
                put("exportedAtMs", System.currentTimeMillis())
                put("projects", JSONArray().apply {
                    projects.forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("title", p.title)
                            put("audioUri", toRelativeInternalPath(context, p.audioUri))
                            put("audioName", p.audioName)
                            put("audioSizeBytes", p.audioSizeBytes)
                            put("audioDurationSec", p.audioDurationSec)
                            put("srtText", p.srtText)
                            put("srtCueCount", p.srtCueCount)
                            put("topic", p.topic)
                            put("code", p.code)
                            put("layersJson", p.layersJson)
                            put("codeDurationSec", p.codeDurationSec)
                            put("hasEditableLayers", p.hasEditableLayers)
                            put("attachedAssetIds", p.attachedAssetIds)
                            put("state", p.state)
                            put("createdAt", p.createdAt)
                            put("updatedAt", p.updatedAt)
                        })
                    }
                })
                put("videos", JSONArray().apply {
                    videos.forEach { v ->
                        put(JSONObject().apply {
                            put("id", v.id)
                            put("projectId", v.projectId)
                            put("title", v.title)
                            put("durationSec", v.durationSec)
                            put("sizeBytes", v.sizeBytes)
                            put("fileUri", toRelativeInternalPath(context, v.fileUri))
                            put("thumbnailPath", toRelativeInternalPath(context, v.thumbnailPath))
                            put("savedToGalleryUri", v.savedToGalleryUri ?: "")
                            put("createdAt", v.createdAt)
                        })
                    }
                })
                put("assets", JSONArray().apply {
                    assets.forEach { a ->
                        put(JSONObject().apply {
                            put("id", a.id)
                            put("fileName", a.fileName)
                            put("relativePath", a.relativePath)
                            put("kind", a.kind)
                            put("mimeType", a.mimeType)
                            put("width", a.width)
                            put("height", a.height)
                            put("durationSec", a.durationSec)
                            put("sizeBytes", a.sizeBytes)
                            put("thumbPath", toRelativeInternalPath(context, a.thumbPath))
                            put("description", a.description)
                            put("safeZone", a.safeZone)
                            put("createdAt", a.createdAt)
                        })
                    }
                })
            }

            ZipOutputStream(FileOutputStream(outZip)).use { zos ->
                zos.putNextEntry(ZipEntry("user_preferences.json"))
                zos.write(prefsJson.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("database.json"))
                zos.write(dbJson.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val filesRoot = context.filesDir
                for (folderName in BACKUP_FOLDERS) {
                    val dir = File(filesRoot, folderName)
                    if (dir.exists() && dir.isDirectory) {
                        zipDirectoryRecursive(zos, dir, "files/$folderName")
                    }
                }
            }

            val summary = BackupSummary(
                projectsCount = projects.size,
                assetsCount = assets.size,
                fontsCount = cacheManager.listCustomFonts().size,
                skillsCount = 0,
                zipFileName = fileName
            )
            outZip to summary
        }
    }

    suspend fun restoreFromBackupUri(
        context: Context,
        cacheManager: CacheManager,
        userPreferences: UserPreferences,
        repository: RawEditRepository,
        sourceUri: Uri
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            var prefsJsonStr: String? = null
            var dbJsonStr: String? = null
            var restoredPhotoPath: String? = null

            val filesRoot = context.filesDir
            context.contentResolver.openInputStream(sourceUri)?.use { rawIn ->
                ZipInputStream(rawIn).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace('\\', '/').trimStart('/')
                        if (!entry.isDirectory && !name.contains("..")) {
                            when {
                                name == "user_preferences.json" -> {
                                    prefsJsonStr = readEntryString(zis)
                                }
                                name == "database.json" -> {
                                    dbJsonStr = readEntryString(zis)
                                }
                                name.startsWith("files/") -> {
                                    val rel = name.removePrefix("files/")
                                    val topFolder = rel.substringBefore('/')
                                    if (topFolder in BACKUP_FOLDERS) {
                                        val destFile = File(filesRoot, rel)
                                        destFile.parentFile?.mkdirs()
                                        FileOutputStream(destFile).use { out ->
                                            zis.copyTo(out)
                                        }
                                        if (topFolder == "profile" && destFile.name.startsWith("avatar_")) {
                                            restoredPhotoPath = destFile.absolutePath
                                        }
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: throw IllegalArgumentException("Could not open backup ZIP file.")

            if (prefsJsonStr == null && dbJsonStr == null) {
                throw IllegalArgumentException("Invalid backup archive: missing raw_edit backup manifest.")
            }

            prefsJsonStr?.let { str ->
                val obj = JSONObject(str)
                userPreferences.importFromJson(obj, restoredPhotoPath)
            }

            var restoredProjectsCount = 0
            var restoredAssetsCount = 0

            dbJsonStr?.let { str ->
                val root = JSONObject(str)
                val projArr = root.optJSONArray("projects") ?: JSONArray()
                val vidArr = root.optJSONArray("videos") ?: JSONArray()
                val assetArr = root.optJSONArray("assets") ?: JSONArray()

                val projects = mutableListOf<ProjectEntity>()
                for (i in 0 until projArr.length()) {
                    val o = projArr.optJSONObject(i) ?: continue
                    projects.add(
                        ProjectEntity(
                            id = o.optLong("id", 0L),
                            title = o.optString("title", "Untitled Reel"),
                            audioUri = fromRelativeInternalPath(context, o.optString("audioUri", "")),
                            audioName = o.optString("audioName", ""),
                            audioSizeBytes = o.optLong("audioSizeBytes", 0L),
                            audioDurationSec = o.optDouble("audioDurationSec", 0.0),
                            srtText = o.optString("srtText", ""),
                            srtCueCount = o.optInt("srtCueCount", 0),
                            topic = o.optString("topic", ""),
                            code = o.optString("code", ""),
                            layersJson = o.optString("layersJson", ""),
                            codeDurationSec = o.optDouble("codeDurationSec", 0.0),
                            hasEditableLayers = o.optBoolean("hasEditableLayers", false),
                            attachedAssetIds = o.optString("attachedAssetIds", ""),
                            state = o.optString("state", ProjectEntity.STATE_IN_PROGRESS),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }

                val videos = mutableListOf<VideoEntity>()
                for (i in 0 until vidArr.length()) {
                    val o = vidArr.optJSONObject(i) ?: continue
                    videos.add(
                        VideoEntity(
                            id = o.optLong("id", 0L),
                            projectId = o.optLong("projectId", 0L),
                            title = o.optString("title", "Reel"),
                            durationSec = o.optDouble("durationSec", 0.0),
                            sizeBytes = o.optLong("sizeBytes", 0L),
                            fileUri = fromRelativeInternalPath(context, o.optString("fileUri", "")),
                            thumbnailPath = fromRelativeInternalPath(context, o.optString("thumbnailPath", "")),
                            savedToGalleryUri = o.optString("savedToGalleryUri", "").takeIf { it.isNotBlank() },
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }

                val assets = mutableListOf<LocalAssetEntity>()
                for (i in 0 until assetArr.length()) {
                    val o = assetArr.optJSONObject(i) ?: continue
                    assets.add(
                        LocalAssetEntity(
                            id = o.optLong("id", 0L),
                            fileName = o.optString("fileName", ""),
                            relativePath = o.optString("relativePath", ""),
                            kind = o.optString("kind", LocalAssetEntity.KIND_IMAGE),
                            mimeType = o.optString("mimeType", "image/jpeg"),
                            width = o.optInt("width", 1080),
                            height = o.optInt("height", 1080),
                            durationSec = o.optDouble("durationSec", 0.0),
                            sizeBytes = o.optLong("sizeBytes", 0L),
                            thumbPath = fromRelativeInternalPath(context, o.optString("thumbPath", "")),
                            description = o.optString("description", ""),
                            safeZone = o.optString("safeZone", ""),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }

                repository.restoreDatabaseEntities(projects, videos, assets)
                restoredProjectsCount = projects.size
                restoredAssetsCount = assets.size
            }

            BackupSummary(
                projectsCount = restoredProjectsCount,
                assetsCount = restoredAssetsCount,
                fontsCount = cacheManager.listCustomFonts().size,
                skillsCount = 0,
                zipFileName = "restored"
            )
        }
    }

    private fun zipDirectoryRecursive(zos: ZipOutputStream, dir: File, basePrefix: String) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val entryPath = "$basePrefix/${child.name}"
            if (child.isDirectory) {
                zipDirectoryRecursive(zos, child, entryPath)
            } else if (child.isFile) {
                zos.putNextEntry(ZipEntry(entryPath))
                FileInputStream(child).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    private fun readEntryString(zis: ZipInputStream): String {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val r = zis.read(buf)
            if (r <= 0) break
            baos.write(buf, 0, r)
        }
        return baos.toString("UTF-8")
    }

    private fun toRelativeInternalPath(context: Context, absPath: String): String {
        if (absPath.isBlank()) return ""
        val rootPath = context.filesDir.absolutePath.trimEnd('/') + "/"
        return if (absPath.startsWith(rootPath)) {
            "@filesdir/" + absPath.removePrefix(rootPath)
        } else {
            absPath
        }
    }

    private fun fromRelativeInternalPath(context: Context, storedPath: String): String {
        if (storedPath.isBlank()) return ""
        return if (storedPath.startsWith("@filesdir/")) {
            File(context.filesDir, storedPath.removePrefix("@filesdir/")).absolutePath
        } else {
            storedPath
        }
    }
}
