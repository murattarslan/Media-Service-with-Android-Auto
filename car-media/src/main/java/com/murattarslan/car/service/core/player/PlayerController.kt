package com.murattarslan.car.service.core.player

import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat.BrowserRoot
import com.murattarslan.car.service.core.queue.RepeatMode
import com.murattarslan.car.service.data.MediaItemModel
import kotlinx.coroutines.flow.StateFlow

interface PlayerController {
    fun prepare(queue: List<MediaItemModel>, index: Int = 0)
    fun play()
    fun pause()
    fun isPlaying(): Boolean

    fun seekTo(position: Long)
    fun fastForward()
    fun rewind()
    fun hasNext(): Boolean
    fun skipToNext()
    fun hasPrevious(): Boolean
    fun skipToPrevious()

    fun playFromMediaId(mediaId: String)
    fun playFromAlbum(albumId: String, index: Int = 0)
    fun currentTrack(): MediaItemModel?

    fun markFavorite(id: String)
    fun repeatMode(repeatMode: RepeatMode)
    fun shuffleMode(shuffleMode: Boolean)

    fun stop()
    fun release()

    fun onGetRoot(): BrowserRoot
    fun onLoadChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem>

    val playerState: StateFlow<PlayerState>
}
