package com.murattarslan.car.core

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import com.murattarslan.car.domain.interfaces.SessionManager
import com.murattarslan.car.ui.constants.MediaConstants
import com.murattarslan.car.ui.notification.DefaultNotificationEngine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class MediaPlayerService : MediaBrowserServiceCompat() {

    private lateinit var sessionManager: SessionManager
    private val coroutineErrorHandler = CoroutineExceptionHandler { scope, throwable ->
        scope.job.start()
        Log.e(TAG, "Coroutine error: $throwable")
    }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + coroutineErrorHandler)

    companion object {
        const val TAG = "MyMusicService"
    }

    override fun onCreate() {
        super.onCreate()
        if (MediaService.isDebugEnable) Log.d(TAG, "onCreate: Service is starting...")

        sessionManager = MediaPlayerServiceFactory.createSessionManager(this, serviceScope)
        if (MediaService.isDebugEnable) Log.d(TAG, "onCreate: Initializing managers and controllers")

        val notificationEngine = MediaService.instance?.customNotificationEngine ?: DefaultNotificationEngine(this)

        serviceScope.launch {
            if (MediaService.isDebugEnable) Log.d(TAG, "onCreate: SessionState collection coroutine started")
            sessionManager.sessionState.collect { state ->
                if (MediaService.isDebugEnable) {
                    Log.d(TAG, "onCreate: SessionState collected. HasSession: ${state.session != null}, HasArtwork: ${state.artwork != null}")
                }

                state.session?.let {
                    if (MediaService.isDebugEnable) Log.d(TAG, "onCreate: Updating notification")
                    notificationEngine.updateNotification(it, state.artwork)
                }

                if (MediaService.isDebugEnable) {
                    Log.d(TAG, "onCreate: Notifying children changed for FAVORITE category")
                }
                notifyChildrenChanged(MediaConstants.CATEGORY_FAVORITE)
            }
        }

        sessionToken = sessionManager.token
        if (MediaService.isDebugEnable) Log.d(TAG, "onCreate: SessionToken set: $sessionToken")
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (MediaService.isDebugEnable) Log.d(TAG, "onBind: Intent action = ${intent?.action}")
        sessionManager.onBind(intent)
        return super.onBind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (MediaService.isDebugEnable) Log.d(TAG, "onStartCommand: Intent action = ${intent?.action}, startId = $startId")
        sessionManager.onBind(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        if (MediaService.isDebugEnable) Log.d(TAG, "onGetRoot: clientPackageName=$clientPackageName, clientUid=$clientUid")
        if (clientPackageName == packageName) {
            if (MediaService.isDebugEnable) Log.w(TAG, "onGetRoot: Access denied (Own package)")
            return null
        }
        val root = sessionManager.onGetRoot()
        if (MediaService.isDebugEnable) Log.d(TAG, "onGetRoot: Returning root with ID = ${root.rootId}")
        return root
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (MediaService.isDebugEnable) Log.d(TAG, "onLoadChildren: parentId=$parentId")
        val children = sessionManager.onLoadChildren(parentId)
        if (MediaService.isDebugEnable) Log.d(TAG, "onLoadChildren: Sending result for $parentId, size=${children.size}")
        result.sendResult(children)
    }

    override fun onDestroy() {
        if (MediaService.isDebugEnable) Log.d(TAG, "onDestroy: Service is being destroyed")
        sessionManager.onDestroy()
        if (MediaService.isDebugEnable) Log.d(TAG, "onDestroy: Stopping foreground and clearing session")
        stopForeground(STOP_FOREGROUND_DETACH)
        super.onDestroy()
    }
}