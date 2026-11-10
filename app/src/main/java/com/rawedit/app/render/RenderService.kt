package com.rawedit.app.render

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rawedit.app.MainActivity
import com.rawedit.app.R
import com.rawedit.app.RawEditApplication
import com.rawedit.app.data.local.ProjectEntity
import com.rawedit.app.data.local.VideoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RenderService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var renderJob: Job? = null
    private val cancelled = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelled.set(true)
                renderJob?.cancel()
                RenderStateHolder.update {
                    it.copy(
                        stage = RenderStage.CANCELLED,
                        statusText = "Cancelled"
                    )
                }
                stopSelfSafely()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
                if (projectId <= 0L) {
                    stopSelfSafely()
                    return START_NOT_STICKY
                }
                if (renderJob?.isActive == true) {
                    return START_NOT_STICKY
                }
                cancelled.set(false)
                RenderStateHolder.resetForNewRender(projectId)
                promoteToForeground("Preparing stage", 0)
                acquireWakeLock()
                startRenderJob(projectId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelled.set(true)
        renderJob?.cancel()
        RenderStateHolder.update {
            it.copy(
                stage = RenderStage.ERROR,
                errorMessage = "Rendering stopped due to Android background processing time limit. Please try again."
            )
        }
        stopSelfSafely()
    }

    private fun startRenderJob(projectId: Long) {
        val app = application as RawEditApplication
        val repo = app.container.repository
        val cache = app.container.cacheManager

        val tempVideo = File(cache.tempDir, "video_${projectId}_${System.currentTimeMillis()}.mp4")
        val tempAudio = File(cache.tempDir, "audio_${projectId}_${System.currentTimeMillis()}.m4a")

        renderJob = serviceScope.launch {
            val startMs = System.currentTimeMillis()
            try {
                val project = repo.getProject(projectId)
                    ?: throw IllegalStateException("Project not found.")

                val resolvedHtml = cache.resolveHtmlCode(project.code)
                if (project.audioUri.isBlank() || resolvedHtml.isBlank()) {
                    throw IllegalStateException("Audio file or HTML code is missing.")
                }
                val audioFile = File(project.audioUri)
                if (!audioFile.exists()) {
                    throw IllegalStateException("Voiceover audio file could not be read.")
                }

                val durationSec = maxOf(project.audioDurationSec, 1.0)
                val targetDurationUs = (durationSec * 1_000_000.0).toLong()

                val renderer = FrameRenderer(this@RenderService, cache)

                RenderStateHolder.update {
                    it.copy(stage = RenderStage.DRAWING_FRAMES, statusText = "Drawing frames 0%")
                }

                var lastNotifMs = 0L
                renderer.renderVideoOnly(
                    rawHtml = resolvedHtml,
                    durationSec = durationSec,
                    outputVideoOnlyMp4 = tempVideo,
                    onProgress = { framePct, _, _ ->
                        // Drawing frames accounts for 0..85% of total progress
                        val overallPct = (framePct * 85) / 100
                        val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(0L)
                        val etaSec = if (overallPct >= 5) {
                            val totalEst = (elapsedSec * 100L) / overallPct
                            (totalEst - elapsedSec).coerceAtLeast(1L)
                        } else {
                            null
                        }
                        val status = "Drawing frames $framePct%"
                        RenderStateHolder.update {
                            it.copy(
                                stage = RenderStage.DRAWING_FRAMES,
                                progressPercent = overallPct,
                                statusText = status,
                                elapsedSeconds = elapsedSec,
                                etaSeconds = etaSec
                            )
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastNotifMs > 600L) {
                            lastNotifMs = now
                            updateNotification(status, overallPct)
                        }
                    },
                    onPreviewFrame = { bmp ->
                        RenderStateHolder.update { it.copy(previewBitmap = bmp) }
                    },
                    cancelled = { cancelled.get() }
                )

                if (cancelled.get()) throw FrameRenderer.RenderCancelledException()

                // Step 2: Audio transcode (85..95%)
                val elapsedAfterFrames = (System.currentTimeMillis() - startMs) / 1000L
                RenderStateHolder.update {
                    it.copy(
                        stage = RenderStage.ADDING_VOICE,
                        progressPercent = 88,
                        statusText = "Adding your voice",
                        elapsedSeconds = elapsedAfterFrames
                    )
                }
                updateNotification("Adding your voice", 88)

                AudioPipeline.transcodeToAac(
                    context = this@RenderService,
                    sourceAudioFile = audioFile,
                    destAacMp4 = tempAudio,
                    cancelled = { cancelled.get() }
                )

                if (cancelled.get()) throw FrameRenderer.RenderCancelledException()

                // Step 3: Final mux + thumbnail (95..100%)
                RenderStateHolder.update {
                    it.copy(
                        stage = RenderStage.MUXING,
                        progressPercent = 95,
                        statusText = "Finishing MP4",
                        elapsedSeconds = (System.currentTimeMillis() - startMs) / 1000L
                    )
                }
                updateNotification("Finishing MP4", 95)

                val outId = UUID.randomUUID().toString().take(10)
                val finalFile = File(cache.rendersDir, "reel_${projectId}_$outId.mp4")
                val thumbFile = File(cache.rendersDir, "thumb_${projectId}_$outId.png")

                AudioPipeline.muxVideoAndAudio(
                    videoOnlyMp4 = tempVideo,
                    audioAacMp4 = tempAudio,
                    finalMp4 = finalFile,
                    targetDurationUs = targetDurationUs
                )
                AudioPipeline.extractThumbnail(finalFile, thumbFile, durationSec)

                val cleanTitle = project.title.ifBlank {
                    project.audioName.substringBeforeLast('.').ifBlank { "Typography Reel" }
                }
                val videoId = repo.insertRenderedVideo(
                    VideoEntity(
                        projectId = project.id,
                        title = cleanTitle,
                        durationSec = durationSec,
                        sizeBytes = finalFile.length(),
                        fileUri = finalFile.absolutePath,
                        thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else ""
                    )
                )

                repo.saveProject(project.copy(state = ProjectEntity.STATE_FINISHED))

                val totalElapsed = (System.currentTimeMillis() - startMs) / 1000L
                RenderStateHolder.update {
                    it.copy(
                        stage = RenderStage.DONE,
                        progressPercent = 100,
                        statusText = "Done",
                        elapsedSeconds = totalElapsed,
                        etaSeconds = 0L,
                        completedVideoId = videoId
                    )
                }
            } catch (_: FrameRenderer.RenderCancelledException) {
                RenderStateHolder.update {
                    it.copy(stage = RenderStage.CANCELLED, statusText = "Cancelled")
                }
            } catch (t: Throwable) {
                if (cancelled.get()) {
                    RenderStateHolder.update {
                        it.copy(stage = RenderStage.CANCELLED, statusText = "Cancelled")
                    }
                } else {
                    RenderStateHolder.update {
                        it.copy(
                            stage = RenderStage.ERROR,
                            statusText = "Render failed",
                            errorMessage = t.message ?: "Rendering failed unexpectedly."
                        )
                    }
                }
            } finally {
                runCatching { tempVideo.delete() }
                runCatching { tempAudio.delete() }
                releaseWakeLock()
                stopSelfSafely()
            }
        }
    }

    private fun promoteToForeground(status: String, progress: Int) {
        val notification = buildNotification(status, progress)
        val fgsType = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, fgsType)
    }

    private fun updateNotification(status: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(status, progress))
    }

    private fun buildNotification(status: String, progress: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, RenderService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, RawEditApplication.RENDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_raw)
            .setContentTitle("Rendering typography reel")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .setContentIntent(contentPending)
            .addAction(0, "Cancel", cancelPending)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "raw_edit:RenderWakeLock").apply {
            acquire(60L * 60L * 1000L) // up to 1 hour safety cap
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.rawedit.app.action.START_RENDER"
        const val ACTION_CANCEL = "com.rawedit.app.action.CANCEL_RENDER"
        const val EXTRA_PROJECT_ID = "extra_project_id"
        private const val NOTIF_ID = 4101

        fun start(context: Context, projectId: Long) {
            val intent = Intent(context, RenderService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, RenderService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
