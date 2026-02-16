package com.murattarslan.car.domain.interfaces

import android.content.Intent
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import com.murattarslan.car.domain.models.SessionState
import kotlinx.coroutines.flow.Flow

interface SessionManager {

    fun onBind(intent: Intent?)
    fun onGetRoot(): MediaBrowserServiceCompat.BrowserRoot
    fun onLoadChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem>
    fun onDestroy()

    val token: MediaSessionCompat.Token
    val sessionState: Flow<SessionState>
}