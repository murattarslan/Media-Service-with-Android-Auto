package com.murattarslan.car.domain.models

import android.graphics.Bitmap
import androidx.media3.common.Player

data class PlayerState(
    val track: MediaItemModel? = null,
    val artwork: Bitmap? = null,

    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,

    val error: String? = null,
    val updateAt: Long = System.currentTimeMillis()
)