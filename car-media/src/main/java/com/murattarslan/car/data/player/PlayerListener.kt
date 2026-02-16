package com.murattarslan.car.data.player

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.murattarslan.car.core.MediaService

internal class PlayerListener(
    val player: Player,
    val updateState: (errorMessage: String?) -> Unit
) : Player.Listener {

    companion object {
        private const val TAG = "MediaService_DefaultPlayerControllerPlayerListener"
    }

    init {
        player.addListener(this)
    }

    fun remove() {
        player.removeListener(this)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "onMediaItemTransition: reason=$reason, hasNext=${player.hasNextMediaItem()}")
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            if (MediaService.isDebugEnable)
                Log.i(TAG, "Queue finished. Resetting to first track and pausing.")
            if (!player.hasNextMediaItem()){
                player.pause()
                player.seekTo(0, 0)
            }
            updateState(null)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (MediaService.isDebugEnable)
            Log.e(TAG, "onPlayerError: ${error.message}", error)
        updateState(error.message)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        updateState(null)
    }
}