package com.murattarslan.car.service.core.queue

import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat.BrowserRoot
import com.murattarslan.car.service.data.MediaItemModel
import kotlinx.coroutines.flow.StateFlow

interface QueueManager {

    fun setMediaItems(items: List<MediaItemModel>)

    // MediaBrowserService
    fun getChildren(parentId: String): List<MediaItemModel>

    // Queue control
    fun createQueue(parentId: String, startIndex: Int = 0)
    fun createQueueFromMediaId(trackId: String)
    fun getQueue(): List<MediaItemModel>
    fun getCurrent(id: String? = null): MediaItemModel?

    fun skipToNext(): MediaItemModel?
    fun skipToPrevious(): MediaItemModel?

    fun setSortType(sortType: SortType)
    fun setShuffle(enabled: Boolean)
    fun setRepeatMode(mode: RepeatMode)

    fun markFavorite(id: String)

    fun clear()
    fun onGetRoot(): BrowserRoot
    fun onLoadChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem>
    val queueState: StateFlow<QueueState>
}
