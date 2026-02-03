package com.murattarslan.myautoapp.shared

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyMusicService : MediaBrowserServiceCompat() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private val podcastList = mutableListOf<PodcastItem>()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var currentArtworkBitmap: Bitmap? = null

    companion object {
        const val TAG = "MyMusicService"
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001

        private const val MEDIA_ID_ROOT = "root"
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
            updateNotification()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateSessionMetadata()
            loadArtworkAndUpdateNotification()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updateSessionMetadata()
            loadArtworkAndUpdateNotification()
        }
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d(TAG, "onPlay")
            player.play()
            session.isActive = true
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.d(TAG, "onPlayFromMediaId: $mediaId")
            mediaId?.let { id ->
                val index = podcastList.indexOfFirst { it.id == id }
                if (index >= 0) {
                    player.seekToDefaultPosition(index)
                    player.prepare()
                    player.play()
                    session.isActive = true
                }
            }
        }

        override fun onPause() {
            Log.d(TAG, "onPause")
            player.pause()
        }

        override fun onStop() {
            Log.d(TAG, "onStop")
            player.stop()
            session.isActive = false
        }

        override fun onSkipToNext() {
            Log.d(TAG, "onSkipToNext")
            player.seekToNextMediaItem()
        }

        override fun onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious")
            player.seekToPreviousMediaItem()
        }

        override fun onSeekTo(position: Long) {
            player.seekTo(position)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        createNotificationChannel()
        loadPodcasts()

        player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)

        session = MediaSessionCompat(this, TAG)
        sessionToken = session.sessionToken
        session.setCallback(callback)
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            )

        updatePlaybackState()
        session.isActive = true
    }

    private fun loadPodcasts() {
        podcastList.clear()
        podcastList.addAll(
            listOf(
                PodcastItem(
                    id = "1",
                    title = "Teknoloji Gündemi",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    imageUrl = "https://images.unsplash.com/photo-1519389950473-47ba0277781c?w=500",
                    artist = "Tech Podcast"
                ),
                PodcastItem(
                    id = "2",
                    title = "Klasik Rock Mix",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                    imageUrl = "https://upload.wikimedia.org/wikipedia/en/4/49/Hotelcalifornia.jpg",
                    artist = "Retro Band"
                ),
                PodcastItem(
                    id = "3",
                    title = "Yazılım Dünyası",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                    imageUrl = "https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=500",
                    artist = "Code Master"
                ),
                PodcastItem(
                    id = "4",
                    title = "Caz Esintileri",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                    imageUrl = "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=500",
                    artist = "Jazz Quartet"
                ),
                PodcastItem(
                    id = "5",
                    title = "Girişimcilik Hikayeleri",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                    imageUrl = "https://images.unsplash.com/photo-1556761175-b413da4baf72?w=500",
                    artist = "Startup Soul"
                ),
                PodcastItem(
                    id = "6",
                    title = "Hard Rock Cafe",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
                    imageUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500",
                    artist = "The Guitarists"
                ),
                PodcastItem(
                    id = "8",
                    title = "Akustik Akşamlar",
                    url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3",
                    imageUrl = "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=500",
                    artist = "Solo Artist"
                )
            )
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        MediaButtonReceiver.handleIntent(session, intent)

        if (player.mediaItemCount == 0) {
            val mediaItems = podcastList.map { podcast ->
                MediaItem.Builder()
                    .setUri(podcast.url)
                    .setMediaId(podcast.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(podcast.title)
                            .setArtist(podcast.artist)
                            .setArtworkUri(Uri.parse(podcast.imageUrl))
                            .build()
                    )
                    .build()
            }

            player.setMediaItems(mediaItems)
            player.prepare()
        }

        updateNotification()
        return super.onBind(intent)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Log.d(TAG, "onGetRoot: clientPackageName=$clientPackageName")
        return BrowserRoot(MEDIA_ID_ROOT, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d(TAG, "onLoadChildren: parentId=$parentId")

        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()

        if (parentId == MEDIA_ID_ROOT) {
            podcastList.forEach { podcast ->
                val description = MediaDescriptionCompat.Builder()
                    .setMediaId(podcast.id)
                    .setTitle(podcast.title)
                    .setSubtitle(podcast.artist)
                    .setDescription(podcast.artist)
                    .setIconUri(Uri.parse(podcast.imageUrl))
                    .setMediaUri(Uri.parse(podcast.url))
                    .build()

                mediaItems.add(
                    MediaBrowserCompat.MediaItem(
                        description,
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
                )
            }
        }

        result.sendResult(mediaItems)
    }

    private fun updatePlaybackState() {
        val state = when {
            player.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            player.playbackState == Player.STATE_READY -> PlaybackStateCompat.STATE_PAUSED
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            player.playbackState == Player.STATE_ENDED -> {
                player.seekToNextMediaItem()
                PlaybackStateCompat.STATE_STOPPED
            }

            else -> PlaybackStateCompat.STATE_NONE
        }

        stateBuilder.setState(
            state,
            player.currentPosition,
            1.0f
        )

        session.setPlaybackState(stateBuilder.build())
    }

    private fun updateSessionMetadata() {
        val currentIndex = player.currentMediaItemIndex

        if (currentIndex >= 0 && currentIndex < podcastList.size) {
            val currentPodcast = podcastList[currentIndex]

            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(
                    MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                    currentPodcast.id
                )
                .putString(
                    MediaMetadataCompat.METADATA_KEY_TITLE,
                    currentPodcast.title
                )
                .putString(
                    MediaMetadataCompat.METADATA_KEY_ARTIST,
                    currentPodcast.artist
                )
                .putString(
                    MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                    currentPodcast.imageUrl
                )
                .putString(
                    MediaMetadataCompat.METADATA_KEY_ART_URI,
                    currentPodcast.imageUrl
                )
                .putLong(
                    MediaMetadataCompat.METADATA_KEY_DURATION,
                    if (player.duration > 0) player.duration else -1
                )

            currentArtworkBitmap?.let {
                metadataBuilder.putBitmap(
                    MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                    it
                )
            }

            session.setMetadata(metadataBuilder.build())
        }
    }

    private fun loadArtworkAndUpdateNotification() {
        val currentIndex = player.currentMediaItemIndex

        if (currentIndex >= 0 && currentIndex < podcastList.size) {
            val currentPodcast = podcastList[currentIndex]

            serviceScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        Glide.with(this@MyMusicService)
                            .asBitmap()
                            .load(currentPodcast.imageUrl)
                            .submit(512, 512)
                            .get()
                    }

                    currentArtworkBitmap = bitmap
                    updateSessionMetadata()
                    updateNotification()

                } catch (e: Exception) {
                    Log.e(TAG, "Error loading artwork", e)
                    currentArtworkBitmap = null
                    updateNotification()
                }
            }
        }
    }

    private fun updateNotification() {
        val controller = session.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata?.description

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(description?.title ?: "Podcast Player")
            .setContentText(description?.subtitle ?: "")
            .setSubText(description?.description)
            .setSmallIcon(R.drawable.ic_media_play)
            .setLargeIcon(currentArtworkBitmap)
            .setContentIntent(controller.sessionActivity)
            // BİLDİRİMİ TAMAMEN KAPATILABİLİR YAPMA
            .setOngoing(true)  // Her zaman açık kalır
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_media_previous,
                    "Previous",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                )
            )
            .addAction(
                NotificationCompat.Action(
                    if (player.isPlaying) R.drawable.ic_media_pause
                    else R.drawable.ic_media_play,
                    if (player.isPlaying) "Pause" else "Play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_media_next,
                    "Next",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    )
                )
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
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
                // Kullanıcının bildirimi kapatmasını engelle
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        player.removeListener(playerListener)
        player.release()
        session.isActive = false
        session.release()
        currentArtworkBitmap?.recycle()
        currentArtworkBitmap = null
        stopForeground(true)
        super.onDestroy()
    }
}