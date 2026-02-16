package com.murattarslan.car.domain.interfaces

import android.graphics.Bitmap
import android.support.v4.media.session.MediaSessionCompat

interface NotificationEngine {

    fun updateNotification(session: MediaSessionCompat, currentArtworkBitmap: Bitmap?)
}