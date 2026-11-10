package com.rawedit.app.data.repository

import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.ProjectDao
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.VideoDao
import com.rawedit.app.data.local.VideoEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class RawEditRepository(
    private val projectDao: ProjectDao,
    private val videoDao: VideoDao,
    private val cacheManager: CacheManager
) {
    val allProjects: Flow<List<ProjectEntity>> = projectDao.observeAll()
    val allVideos: Flow<List<VideoEntity>> = videoDao.observeAll()

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
        projectDao.delete(project)
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
}
