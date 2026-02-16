package com.murattarslan.car.ui.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.murattarslan.car.R
import com.murattarslan.car.domain.interfaces.NotificationEngine

internal class DefaultNotificationEngine(private val context: Service): NotificationEngine {

    companion object {
        const val TAG = "MediaService_DefaultNotificationEngine"
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    override fun updateNotification(
        session: MediaSessionCompat,
        currentArtworkBitmap: Bitmap?
    ) {
        val controller = session.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata?.description
        if (description?.title == null) {
            context.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(description.title ?: "Podcast Player")
                .setContentText(description.subtitle ?: "")
                .setSubText(description.description)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(currentArtworkBitmap)
                .setContentIntent(controller.sessionActivity)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(session.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .build()

            context.startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}