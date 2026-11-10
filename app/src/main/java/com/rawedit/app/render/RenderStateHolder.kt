package com.rawedit.app.render

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RenderStage {
    IDLE,
    PREPARING,
    DRAWING_FRAMES,
    ADDING_VOICE,
    MUXING,
    DONE,
    CANCELLED,
    ERROR
}

data class RenderUiState(
    val projectId: Long = -1L,
    val stage: RenderStage = RenderStage.IDLE,
    val progressPercent: Int = 0,
    val statusText: String = "Preparing stage",
    val elapsedSeconds: Long = 0L,
    val etaSeconds: Long? = null,
    val previewBitmap: Bitmap? = null,
    val errorMessage: String? = null,
    val completedVideoId: Long? = null,
    val renderSpeedNote: String? = null
)

object RenderStateHolder {
    private val _state = MutableStateFlow(RenderUiState())
    val state: StateFlow<RenderUiState> = _state.asStateFlow()

    fun resetForNewRender(projectId: Long) {
        _state.value = RenderUiState(
            projectId = projectId,
            stage = RenderStage.PREPARING,
            progressPercent = 0,
            statusText = "Preparing fonts and stage",
            elapsedSeconds = 0L,
            etaSeconds = null,
            previewBitmap = null,
            errorMessage = null,
            completedVideoId = null
        )
    }

    fun update(transform: (RenderUiState) -> RenderUiState) {
        _state.update(transform)
    }

    fun clearPreview() {
        _state.update { it.copy(previewBitmap = null) }
    }
}
