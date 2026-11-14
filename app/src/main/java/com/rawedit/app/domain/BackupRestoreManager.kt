package com.rawedit.app.domain

import android.content.Context
import android.net.Uri
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.local.UserPreferences
import com.rawedit.app.data.repository.RawEditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupStats(
    val projectCount: Int,
    val assetCount: Int,
    val fontCount: Int,
    val backupFile: File
)

data class RestoreStats(
    val projectCount: Int,
    val assetCount: Int,
    val fontCount: Int
)

object BackupRestoreManager {

    private const val MANIFEST_FILE = "backup_manifest.json"

    suspend fun exportBackup(
        context: Context,
        repository: RawEditRepository,
        userPreferences: UserPreferences,
        cacheManager: CacheManager
    ): Result<BackupStats> = withContext(Dispatchers.IO) {
        runCatching {
            val projects = repository.allProjects.first()
            val assets = repository.allAssets.first()
            val profile = userPreferences.userProfile.first()
            val customFonts = cacheManager.listCustomFonts()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val backupZip = File(backupsDir, "raw_edit_backup_$timeStamp.zip")

            val manifest = JSONObject().apply {
                put("version", 2)
                put("exportedAtMs", System.currentTimeMillis())
                put("userProfile", JSONObject().apply {
                    put("userName", profile.userName)
                    put("profilePhotoPath", profile.profilePhotoPath.orEmpty())
                    put("paletteJson", profile.paletteJson)
                    put("paletteCounter", profile.paletteCounter)
                    put("geminiApiKey", profile.geminiApiKey)
                    put("githubAccessToken", profile.githubAccessToken)
                    put("aiProviderRoute", profile.aiProviderRoute)
                    put("geminiTaskRoute", profile.geminiTaskRoute)
                })

                val projArr = JSONArray()
                projects.forEach { p ->
                    projArr.put(JSONObject().apply {
                        put("id", p.id)
                        put("title", p.title)
                        put("audioUri", p.audioUri)
                        put("audioName", p.audioName)
                        put("audioDurationSec", p.audioDurationSec)
                        put("audioSizeBytes", p.audioSizeBytes)
                        put("srtText", p.srtText)
                        put("srtCueCount", p.srtCueCount)
                        put("topic", p.topic)
                        put("code", p.code)
                        put("state", p.state)
                        put("createdAt", p.createdAt)
                        put("updatedAt", p.updatedAt)
                    })
                }
                put("projects", projArr)

                val assetArr = JSONArray()
                assets.forEach { a ->
                    assetArr.put(JSONObject().apply {
                        put("id", a.id)
                        put("fileName", a.fileName)
                        put("relativePath", a.relativePath)
                        put("kind", a.kind)
                        put("mimeType", a.mimeType)
                        put("width", a.width)
                        put("height", a.height)
                        put("durationSec", a.durationSec)
                        put("sizeBytes", a.sizeBytes)
                        put("description", a.description)
                        put("safeZone", a.safeZone)
                        put("createdAt", a.createdAt)
                    })
                }
                put("assets", assetArr)
            }

            ZipOutputStream(FileOutputStream(backupZip)).use { zos ->
                // 1. Write Manifest
                zos.putNextEntry(ZipEntry(MANIFEST_FILE))
                zos.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. Write Custom Fonts
                customFonts.forEach { fontFile ->
                    if (fontFile.exists() && fontFile.isFile) {
                        zos.putNextEntry(ZipEntry("custom_fonts/${fontFile.name}"))
                        fontFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                // 3. Write Project Assets
                assets.forEach { asset ->
                    val assetFile = cacheManager.getAssetFile(asset.relativePath)
                    if (assetFile != null && assetFile.exists() && assetFile.isFile) {
                        zos.putNextEntry(ZipEntry("assets/${asset.relativePath}"))
                        assetFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            BackupStats(
                projectCount = projects.size,
                assetCount = assets.size,
                fontCount = customFonts.size,
                backupFile = backupZip
            )
        }
    }

    suspend fun importBackup(
        context: Context,
        backupUri: Uri,
        repository: RawEditRepository,
        userPreferences: UserPreferences,
        cacheManager: CacheManager
    ): Result<RestoreStats> = withContext(Dispatchers.IO) {
        runCatching {
            val tempDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                // 1. Extract zip
                context.contentResolver.openInputStream(backupUri)?.use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val dest = File(tempDir, entry.name)
                            if (entry.isDirectory) {
                                dest.mkdirs()
                            } else {
                                dest.parentFile?.mkdirs()
                                FileOutputStream(dest).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } ?: throw IllegalStateException("Could not read backup file.")

                // 2. Read manifest
                val manifestFile = File(tempDir, MANIFEST_FILE)
                if (!manifestFile.exists()) {
                    throw IllegalArgumentException("Invalid backup file: missing $MANIFEST_FILE.")
                }
                val manifestObj = JSONObject(manifestFile.readText(Charsets.UTF_8))

                // 3. Restore User Preferences
                val profObj = manifestObj.optJSONObject("userProfile")
                if (profObj != null) {
                    val name = profObj.optString("userName")
                    if (name.isNotBlank()) userPreferences.updateUserName(name)
                    val geminiKey = profObj.optString("geminiApiKey")
                    if (geminiKey.isNotBlank()) userPreferences.saveGeminiApiKey(geminiKey)
                    val ghToken = profObj.optString("githubAccessToken")
                    if (ghToken.isNotBlank()) userPreferences.saveGithubAccessToken(ghToken)
                    val paletteJson = profObj.optString("paletteJson")
                    val paletteCounter = profObj.optInt("paletteCounter", 1)
                    if (paletteJson.isNotBlank()) userPreferences.savePaletteState(paletteCounter, paletteJson)
                }

                // 4. Restore Custom Fonts
                val fontsDir = File(tempDir, "custom_fonts")
                var restoredFonts = 0
                if (fontsDir.exists() && fontsDir.isDirectory) {
                    fontsDir.listFiles()?.forEach { f ->
                        if (f.isFile) {
                            val target = File(cacheManager.customFontsDir, f.name)
                            f.copyTo(target, overwrite = true)
                            restoredFonts++
                        }
                    }
                }

                // 5. Restore Assets
                val assetsDir = File(tempDir, "assets")
                var restoredAssets = 0
                val assetsArr = manifestObj.optJSONArray("assets") ?: JSONArray()
                for (i in 0 until assetsArr.length()) {
                    val aObj = assetsArr.optJSONObject(i) ?: continue
                    val relPath = aObj.optString("relativePath")
                    if (relPath.isNotBlank()) {
                        val sourceFile = File(assetsDir, relPath)
                        if (sourceFile.exists()) {
                            val targetFile = cacheManager.getAssetFile(relPath) ?: File(cacheManager.assetsDir, relPath)
                            targetFile.parentFile?.mkdirs()
                            sourceFile.copyTo(targetFile, overwrite = true)
                        }
                        val fileName = aObj.optString("fileName", relPath.substringAfterLast('/'))
                        val mime = aObj.optString("mimeType", if (relPath.endsWith(".mp4", ignoreCase = true)) "video/mp4" else "image/*")
                        repository.insertAsset(
                            LocalAssetEntity(
                                fileName = fileName,
                                relativePath = relPath,
                                kind = aObj.optString("kind", LocalAssetEntity.KIND_IMAGE),
                                mimeType = mime,
                                width = aObj.optInt("width", 1080),
                                height = aObj.optInt("height", 1920),
                                durationSec = aObj.optDouble("durationSec", 0.0),
                                sizeBytes = aObj.optLong("sizeBytes", 0L),
                                description = aObj.optString("description", ""),
                                safeZone = aObj.optString("safeZone", ""),
                                createdAt = aObj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                        restoredAssets++
                    }
                }

                // 6. Restore Projects
                var restoredProjects = 0
                val projArr = manifestObj.optJSONArray("projects") ?: JSONArray()
                for (i in 0 until projArr.length()) {
                    val pObj = projArr.optJSONObject(i) ?: continue
                    val title = pObj.optString("title", "Imported Reel")
                    val codeContent = pObj.optString("code", pObj.optString("htmlCode", ""))
                    val stateStr = pObj.optString("state", pObj.optString("status", ProjectEntity.STATE_IN_PROGRESS))
                    repository.insertProject(
                        ProjectEntity(
                            title = title,
                            audioUri = pObj.optString("audioUri", ""),
                            audioName = pObj.optString("audioName", ""),
                            audioDurationSec = pObj.optDouble("audioDurationSec", 0.0),
                            audioSizeBytes = pObj.optLong("audioSizeBytes", 0L),
                            srtText = pObj.optString("srtText", ""),
                            srtCueCount = pObj.optInt("srtCueCount", 0),
                            topic = pObj.optString("topic", ""),
                            code = codeContent,
                            state = stateStr,
                            createdAt = pObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = pObj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                    restoredProjects++
                }

                RestoreStats(
                    projectCount = restoredProjects,
                    assetCount = restoredAssets,
                    fontCount = restoredFonts
                )
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }
}
