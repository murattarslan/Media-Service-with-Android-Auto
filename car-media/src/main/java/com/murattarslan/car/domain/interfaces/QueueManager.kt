package com.murattarslan.car.domain.interfaces

import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat
import com.murattarslan.car.domain.models.MediaItemModel
import com.murattarslan.car.domain.models.QueueState
import kotlinx.coroutines.flow.StateFlow

interface QueueManager {

    // Queue control
    fun createQueue(parentId: String, startIndex: Int)
    fun createQueue(trackId: String)
    fun markFavorite(id: String)

    // BrowserService
    fun onGetRoot(): MediaBrowserServiceCompat.BrowserRoot
    fun onLoadChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem>

    // Queue check
    fun getTrack(id: String? = null): MediaItemModel?
    val queueState: StateFlow<QueueState>
}