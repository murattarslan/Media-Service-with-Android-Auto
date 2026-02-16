package com.murattarslan.car.domain.models

import android.graphics.Bitmap
import android.support.v4.media.session.MediaSessionCompat

data class SessionState(
    val session: MediaSessionCompat? = null,
    val artwork: Bitmap? = null,
    val isFavoriteChanged: Boolean = false
)