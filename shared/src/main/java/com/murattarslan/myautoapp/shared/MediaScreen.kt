package com.murattarslan.myautoapp.shared

import android.R
import android.content.ComponentName
import android.graphics.Bitmap
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.TimeUnit

class MediaScreen(carContext: CarContext) : Screen(carContext) {

    private var mediaBrowser: MediaBrowserCompat? = null
    private var mediaController: MediaControllerCompat? = null
    private var currentMetadata: MediaMetadataCompat? = null
    private var playbackState: PlaybackStateCompat? = null
    private val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
    private var albumArtBitmap: Bitmap? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressUpdateJob: Job? = null

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaBrowser?.sessionToken?.let { token ->
                mediaController = MediaControllerCompat(carContext, token)
                mediaController?.registerCallback(controllerCallback)

                currentMetadata = mediaController?.metadata
                playbackState = mediaController?.playbackState

                loadMediaItems()
                loadAlbumArt()
                startProgressUpdates()
            }
        }

        override fun onConnectionSuspended() {
            mediaController?.unregisterCallback(controllerCallback)
            stopProgressUpdates()
        }

        override fun onConnectionFailed() {
            stopProgressUpdates()
        }
    }

    private val subscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(
            parentId: String,
            children: MutableList<MediaBrowserCompat.MediaItem>
        ) {
            mediaItems.clear()
            mediaItems.addAll(children)
            invalidate()
        }

        override fun onError(parentId: String) {
            // Hata durumu
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            currentMetadata = metadata
            loadAlbumArt()
            invalidate()
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            playbackState = state

            if (state?.state == PlaybackStateCompat.STATE_PLAYING) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }

            invalidate()
        }
    }

    init {
        connectToMediaBrowser()

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                cleanup()
            }
        })
    }

    private fun connectToMediaBrowser() {
        mediaBrowser = MediaBrowserCompat(
            carContext,
            ComponentName(carContext, MyMusicService::class.java),
            connectionCallbacks,
            null
        )
        mediaBrowser?.connect()
    }

    private fun loadMediaItems() {
        mediaBrowser?.subscribe("root", subscriptionCallback)
    }

    private fun loadAlbumArt() {
        val artUri = currentMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)

        if (artUri != null) {
            serviceScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        Glide.with(carContext)
                            .asBitmap()
                            .load(artUri)
                            .submit(512, 512)
                            .get()
                    }

                    albumArtBitmap = bitmap
                    invalidate()
                } catch (e: Exception) {
                    albumArtBitmap = null
                    invalidate()
                }
            }
        } else {
            albumArtBitmap = null
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()

        progressUpdateJob = serviceScope.launch {
            val basePosition = playbackState?.position ?: 0
            currentTime = basePosition
            while (isActive) {
                delay(1000) // Her saniye güncelle
                currentTime += 1000L
                invalidate()
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun cleanup() {
        stopProgressUpdates()
        serviceScope.cancel()
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser?.unsubscribe("root")
        mediaBrowser?.disconnect()
        albumArtBitmap?.recycle()
        albumArtBitmap = null
    }

    private var currentTime = 0L

    override fun onGetTemplate(): Template {
        val isPlaying = playbackState?.state == PlaybackStateCompat.STATE_PLAYING


        val position = currentTime

        val duration = currentMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: -1

        val title = currentMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "No track"
        val album = currentMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: "No track"
        val artist = currentMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "Unknown"

        // Progress bilgisi
        val progressPercent = if (duration > 0) {
            ((position.toFloat() / duration.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        // Progress bar karakterleri
        val totalBars = 20
        val filledBars = (progressPercent * totalBars / 100).coerceIn(0, totalBars)
        val progressBar = "━".repeat(filledBars) + "○" + "─".repeat((totalBars - filledBars).coerceAtLeast(0))

        // Albüm kapağı veya varsayılan ikon
        val albumArtIcon = if (albumArtBitmap != null) {
            CarIcon.Builder(IconCompat.createWithBitmap(albumArtBitmap!!)).build()
        } else {
            CarIcon.Builder(
                IconCompat.createWithResource(
                    carContext,
                    R.drawable.ic_menu_gallery
                )
            ).build()
        }

        // Now Playing Pane
        val nowPlayingPane = Pane.Builder()
            .setImage(albumArtIcon)
            .addRow(
                Row.Builder()
                    .setTitle(title)
                    .addText(album)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(artist)
                    .addText("$progressBar  ${formatTime(position)} / ${formatTime(duration)}")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("⏮️")
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener {
                        mediaController?.transportControls?.skipToPrevious()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("⏭️")
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener {
                        mediaController?.transportControls?.skipToNext()
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(nowPlayingPane)
            .setHeader(
                Header.Builder()
                    .setTitle("Now Playing")
                    .setStartHeaderAction(Action.APP_ICON)
                    .addEndHeaderAction(
                        Action.Builder()
                            .setIcon(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(
                                        carContext,
                                        if (isPlaying) R.drawable.ic_media_pause
                                        else R.drawable.ic_media_play
                                    )
                                ).build()
                            )
                            .setOnClickListener {
                                if (isPlaying) {
                                    mediaController?.transportControls?.pause()
                                } else {
                                    mediaController?.transportControls?.play()
                                }
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun formatTime(milliseconds: Long): String {
        if (milliseconds < 0) return "--:--"

        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}