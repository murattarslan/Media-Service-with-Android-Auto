package com.murattarslan.car.data.session

import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.murattarslan.car.core.MediaService
import com.murattarslan.car.domain.interfaces.OnMediaController
import com.murattarslan.car.domain.interfaces.PlayerController
import com.murattarslan.car.domain.models.MediaItemModel

class SessionListener(val session: MediaSessionCompat, val player: PlayerController) : OnMediaController{

    companion object {
        private const val TAG = "MediaService_SessionManagerListener"
    }

    // controller functions

    override fun onPlay() {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onPlay")
        player.play()
        session.isActive = true
    }

    override fun onPause() {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onPause")
        player.pause()
    }

    override fun onSeek(position: Long) {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onSeek to $position")
        player.seekTo(position)
    }

    override fun onFastForward() {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onFastForward")
        player.fastForward()
    }

    override fun onRewind() {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onRewind")
        player.rewind()
    }

    override fun onNext() {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onNext")
        player.skipToNext()
    }

    override fun onPrev() {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onPrev")
        player.skipToPrevious()
    }

    override fun onChange(trackId: String) {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onChange trackId=${trackId}")
        player.playFromMediaId(trackId)
    }

    override fun onChange(albumId: String, index: Int) {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onChange trackId=${albumId}, index=$index")
        player.playFromAlbum(albumId, index)
    }

    override fun onFavorite(track: MediaItemModel) {
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onFavorite for ${track.id}")
        player.markFavorite(track.id)
    }

    // session functions

    override fun isPlaying(): Boolean {
        val playing = player.isPlaying()
        if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: isPlaying check: $playing")
        return playing
    }

    override fun hasNext(): Boolean {
        return player.hasNext()
    }

    override fun hasPrevious(): Boolean {
        return player.hasPrevious()
    }

    override fun onStop() {
        player.stop()
        session.isActive = false
    }
}