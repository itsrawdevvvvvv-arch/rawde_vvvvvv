package com.rawedit.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val audioUri: String = "",
    val audioName: String = "",
    val audioSizeBytes: Long = 0L,
    val audioDurationSec: Double = 0.0,
    val srtText: String = "",
    val srtCueCount: Int = 0,
    val topic: String = "",
    val code: String = "",
    val layersJson: String = "",
    val codeDurationSec: Double = 0.0,
    val hasEditableLayers: Boolean = false,
    val state: String = STATE_IN_PROGRESS,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATE_IN_PROGRESS = "IN_PROGRESS"
        const val STATE_FINISHED = "FINISHED"
    }
}

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val title: String,
    val durationSec: Double,
    val sizeBytes: Long,
    val fileUri: String,
    val thumbnailPath: String = "",
    val savedToGalleryUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
