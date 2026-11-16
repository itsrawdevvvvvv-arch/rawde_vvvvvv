package com.rawedit.app.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.ChatUploadedFile
import com.rawedit.app.data.local.LocalAssetEntity
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.UserPreferences
import com.rawedit.app.data.local.UserProfile
import com.rawedit.app.data.repository.RawEditRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ChatbotBackgroundManager {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSessions = MutableStateFlow<Set<String>>(emptySet())
    val activeSessions: StateFlow<Set<String>> = _activeSessions.asStateFlow()

    private val _updateTick = MutableStateFlow(0L)
    val updateTick: StateFlow<Long> = _updateTick.asStateFlow()

    private val _lastToast = MutableStateFlow<String?>(null)
    val lastToast: StateFlow<String?> = _lastToast.asStateFlow()

    fun clearToast() {
        _lastToast.value = null
    }

    fun launchChatJob(
        appContext: Context,
        sessionId: String,
        geminiRouter: GeminiWaterfallRouter,
        cacheManager: CacheManager,
        userPreferences: UserPreferences,
        repository: RawEditRepository,
        userProfile: UserProfile,
        allProjects: List<ProjectEntity>,
        activeProject: ProjectEntity?,
        allAssets: List<LocalAssetEntity>,
        activePalette: PaletteState,
        historySnapshot: List<ChatMessageItem>,
        userText: String,
        uploadedFile: ChatUploadedFile?,
        browserPageContext: String?,
        mode: ChatbotMode = ChatbotMode.RAW_ASSISTANT,
        designConfig: DesignRequestConfig = DesignRequestConfig()
    ) {
        _activeSessions.value = _activeSessions.value + sessionId
        runCatching {
            val intent = Intent(appContext, ChatbotBackgroundService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        }

        serviceScope.launch {
            try {
                val res = ChatbotEngine.sendAppChatMessage(
                    context = appContext,
                    geminiRouter = geminiRouter,
                    cacheManager = cacheManager,
                    userPreferences = userPreferences,
                    repository = repository,
                    userProfile = userProfile,
                    allProjects = allProjects,
                    activeProject = activeProject,
                    allAssets = allAssets,
                    activePalette = activePalette,
                    history = historySnapshot,
                    userText = userText,
                    uploadedFile = uploadedFile,
                    browserPageContext = browserPageContext,
                    mode = mode,
                    designConfig = designConfig
                )

                val currentList = ChatbotEngine.loadChatHistory(cacheManager, sessionId).ifEmpty { historySnapshot }
                res.onSuccess { outcome ->
                    val nextList = currentList + outcome.assistantMessage
                    ChatbotEngine.saveChatHistory(cacheManager, nextList, sessionId)
                    ChatbotEngine.upsertChatSessionMeta(cacheManager, sessionId, nextList)
                    if (outcome.assistantMessage.appliedActions.isNotEmpty()) {
                        _lastToast.value = outcome.assistantMessage.appliedActions.first()
                    }
                }.onFailure { err ->
                    val errMsg = ChatMessageItem(
                        role = "assistant",
                        text = "Error: ${err.message ?: "Could not reach Gemini AI. Check your API key in Settings."}"
                    )
                    val nextList = currentList + errMsg
                    ChatbotEngine.saveChatHistory(cacheManager, nextList, sessionId)
                    ChatbotEngine.upsertChatSessionMeta(cacheManager, sessionId, nextList)
                }
            } finally {
                _activeSessions.value = _activeSessions.value - sessionId
                _updateTick.value = System.currentTimeMillis()
                if (_activeSessions.value.isEmpty()) {
                    runCatching {
                        appContext.stopService(Intent(appContext, ChatbotBackgroundService::class.java))
                    }
                }
            }
        }
    }
}

class ChatbotBackgroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "rawedit_ai_bg"
        private const val NOTIF_ID = 4409
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("RAW EDIT AI")
            .setContentText("Working in background...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "RAW EDIT AI Background",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
