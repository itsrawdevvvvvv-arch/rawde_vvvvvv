package com.rawedit.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.webkit.WebView
import com.rawedit.app.data.CacheManager
import com.rawedit.app.data.local.RawEditDatabase
import com.rawedit.app.data.local.UserPreferences
import com.rawedit.app.data.repository.RawEditRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RawEditApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Required before creating any WebView so full 1920x1080 off-screen draw captures reliably.
        WebView.enableSlowWholeDocumentDraw()
        createNotificationChannels()
        container = AppContainer(this)
        appScope.launch {
            container.cacheManager.cleanExpiredTempFiles()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                RENDER_CHANNEL_ID,
                getString(R.string.render_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.render_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val RENDER_CHANNEL_ID = "raw_edit_render"
    }
}

class AppContainer(application: Application) {
    val database = RawEditDatabase.getInstance(application)
    val userPreferences = UserPreferences(application)
    val cacheManager = CacheManager(application)
    val repository = RawEditRepository(
        projectDao = database.projectDao(),
        videoDao = database.videoDao(),
        cacheManager = cacheManager
    )
}
