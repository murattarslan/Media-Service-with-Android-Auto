package com.murattarslan.car.data.session

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.media3.common.Player
import com.murattarslan.car.core.MediaService
import com.murattarslan.car.domain.interfaces.PlayerController
import com.murattarslan.car.ui.constants.MediaConstants

internal class SessionCallback(val session: MediaSessionCompat, val player: PlayerController) : MediaSessionCompat.Callback() {

    companion object {
        private const val TAG = "MediaService_SessionManagerCallback"
    }

    override fun onPlay() {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onPlay called")
        player.play()
        session.isActive = true
    }

    override fun onPause() {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onPause called")
        player.pause()
    }

    override fun onSeekTo(position: Long) {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onSeekTo called with pos=$position")
        player.seekTo(position)
    }

    override fun onFastForward() {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onFastForward called")
        player.fastForward()
    }

    override fun onRewind() {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onRewind called")
        player.rewind()
    }

    override fun onSkipToNext() {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onSkipToNext called")
        player.skipToNext()
    }

    override fun onSkipToPrevious() {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onSkipToPrevious called")
        player.skipToPrevious()
    }

    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onPlayFromMediaId called with id=$mediaId")
        mediaId?.let { id ->
            player.playFromMediaId(id)
        }
    }

    override fun onStop() {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onStop called")
        player.stop()
        session.isActive = false
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onCustomAction called action=$action")
        when (action) {
            MediaConstants.ACTION_TOGGLE_FAVORITE -> {
                val mediaId = session.controller.metadata
                    ?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                if (MediaService.isDebugEnable) Log.d(TAG, "onCustomAction: Toggle Favorite for id=$mediaId")
                mediaId?.let { player.markFavorite(it) }
            }
            MediaConstants.ACTION_TOGGLE_SHUFFLE -> {
                val newShuffleState = player.playerState.value.isShuffleEnabled.not()
                if (MediaService.isDebugEnable) Log.d(TAG, "onCustomAction: Toggle Shuffle to $newShuffleState")
                player.shuffleMode(newShuffleState)
            }
            MediaConstants.ACTION_TOGGLE_REPEAT -> {
                val currentMode = player.playerState.value.repeatMode
                if (MediaService.isDebugEnable) Log.d(TAG, "onCustomAction: Toggle Repeat from $currentMode")
                when (currentMode) {
                    Player.REPEAT_MODE_ALL -> player.repeatMode(Player.REPEAT_MODE_OFF)
                    Player.REPEAT_MODE_OFF -> player.repeatMode(Player.REPEAT_MODE_ONE)
                    else -> player.repeatMode(Player.REPEAT_MODE_ALL)
                }
            }
        }
    }
}