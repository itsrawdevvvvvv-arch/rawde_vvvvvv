package com.rawedit.app.data.repository

import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.LocalAssetDao
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectDao
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.VideoDao
import com.rawedit.app.data.local.VideoEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class RawEditRepository(
    private val projectDao: ProjectDao,
    private val videoDao: VideoDao,
    private val assetDao: LocalAssetDao,
    private val cacheManager: CacheManager
) {
    val allProjects: Flow<List<ProjectEntity>> = projectDao.observeAll()
    val allVideos: Flow<List<VideoEntity>> = videoDao.observeAll()
    val allAssets: Flow<List<LocalAssetEntity>> = assetDao.observeAll()

    fun observeProject(id: Long): Flow<ProjectEntity?> = projectDao.observeById(id)
    fun observeVideo(id: Long): Flow<VideoEntity?> = videoDao.observeById(id)

    suspend fun getProject(id: Long): ProjectEntity? = projectDao.getById(id)
    suspend fun getVideo(id: Long): VideoEntity? = videoDao.getById(id)

    suspend fun createEmptyProject(title: String = "Untitled Reel"): Long {
        val now = System.currentTimeMillis()
        return projectDao.insert(
            ProjectEntity(
                title = title,
                state = ProjectEntity.STATE_IN_PROGRESS,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun saveProject(project: ProjectEntity) {
        projectDao.update(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(project: ProjectEntity) {
        if (project.audioUri.isNotBlank()) {
            runCatching { File(project.audioUri).delete() }
        }
        cacheManager.deleteOverflowForProject(project.id)
        projectDao.delete(project)
    }

    suspend fun insertAsset(asset: LocalAssetEntity): Long {
        return assetDao.insert(asset)
    }

    suspend fun updateAssetDescription(id: Long, description: String, safeZone: String) {
        assetDao.updateDescription(id, description.trim(), safeZone.trim())
    }

    suspend fun deleteAsset(asset: LocalAssetEntity) {
        cacheManager.deleteLockerAssetFiles(asset.fileName, asset.thumbPath)
        assetDao.delete(asset)
    }

    suspend fun attachAssetToProject(projectId: Long, assetId: Long) {
        val proj = projectDao.getById(projectId) ?: return
        val ids = parseAttachedAssetIds(proj.attachedAssetIds).toMutableList()
        if (!ids.contains(assetId)) {
            ids.add(assetId)
            projectDao.update(
                proj.copy(
                    attachedAssetIds = ids.joinToString(","),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun toggleAssetForProject(projectId: Long, assetId: Long) {
        val proj = projectDao.getById(projectId) ?: return
        val ids = parseAttachedAssetIds(proj.attachedAssetIds).toMutableList()
        if (ids.contains(assetId)) {
            ids.remove(assetId)
        } else {
            ids.add(assetId)
        }
        projectDao.update(
            proj.copy(
                attachedAssetIds = ids.joinToString(","),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun insertRenderedVideo(video: VideoEntity): Long {
        return videoDao.insert(video)
    }

    suspend fun renameVideo(id: Long, newTitle: String) {
        val cleaned = newTitle.trim()
        if (cleaned.isNotEmpty()) {
            videoDao.rename(id, cleaned)
        }
    }

    suspend fun markVideoSavedToGallery(id: Long, galleryUri: String) {
        videoDao.updateGalleryUri(id, galleryUri)
    }

    suspend fun deleteVideo(video: VideoEntity) {
        runCatching { File(video.fileUri).delete() }
        if (video.thumbnailPath.isNotBlank()) {
            runCatching { File(video.thumbnailPath).delete() }
        }
        videoDao.delete(video)
    }

    companion object {
        fun parseAttachedAssetIds(raw: String): List<Long> {
            return raw.split(',')
                .mapNotNull { it.trim().toLongOrNull() }
                .distinct()
        }
    }
}
