package com.murattarslan.car.service.core.player

import android.graphics.Bitmap
import com.murattarslan.car.service.core.queue.RepeatMode
import com.murattarslan.car.service.data.MediaItemModel

data class PlayerState(
    val playbackState: PlaybackState = PlaybackState.IDLE,

    val track: MediaItemModel? = null,
    val artwork: Bitmap? = null,

    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val bufferedPosition: Long = 0L,

    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,

    val error: String? = null
)


