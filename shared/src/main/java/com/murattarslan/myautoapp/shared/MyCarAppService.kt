package com.murattarslan.myautoapp.shared

import android.content.ComponentName
import android.content.Intent
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.OptIn
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.media.MediaPlaybackManager
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MyCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return MyCarSession()
    }

    @OptIn(ExperimentalCarApi::class)
    inner class MyCarSession : Session() {
        lateinit var mediaBrowser: MediaBrowserCompat

        override fun onCreateScreen(intent: Intent): Screen {
            val serviceIntent = Intent(carContext, MyMusicService::class.java)
            carContext.startForegroundService(serviceIntent)
            return MediaScreen(carContext)
        }

        init {
            lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_CREATE) {
                        mediaBrowser = MediaBrowserCompat(
                            carContext,
                            ComponentName(carContext, MyMusicService::class.java),
                            connectionCallbacks,
                            null
                        )
                    }
                }
            )
        }

        private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                val token = mediaBrowser.sessionToken
                (carContext.getCarService(CarContext.MEDIA_PLAYBACK_SERVICE) as MediaPlaybackManager)
                    .registerMediaPlaybackToken(token)
            }

            override fun onConnectionSuspended() {}

            override fun onConnectionFailed() {}
        }
    }
}
