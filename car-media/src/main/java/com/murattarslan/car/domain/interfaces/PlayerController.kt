package com.murattarslan.car.domain.interfaces

import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat
import com.murattarslan.car.domain.models.MediaItemModel
import com.murattarslan.car.domain.models.PlayerState
import kotlinx.coroutines.flow.StateFlow

interface PlayerController {
    // controller functions
    fun play()
    fun pause()
    fun seekTo(position: Long)
    fun fastForward()
    fun rewind()
    fun skipToNext()
    fun skipToPrevious()
    fun playFromMediaId(mediaId: String)
    fun playFromAlbum(albumId: String, index: Int = 0)
    fun markFavorite(id: String)

    // config functions
    fun repeatMode(repeatMode: Int)
    fun shuffleMode(shuffleMode: Boolean)

    // session functions
    fun isPlaying(): Boolean
    fun currentPosition(): Long
    fun hasNext(): Boolean
    fun hasPrevious(): Boolean
    fun currentTrack(id: String?): MediaItemModel?
    fun currentQueue(): List<MediaItemModel>
    fun stop()
    fun release()
    fun onGetRoot(): MediaBrowserServiceCompat.BrowserRoot
    fun onLoadChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem>

    val playerState: StateFlow<PlayerState>
}