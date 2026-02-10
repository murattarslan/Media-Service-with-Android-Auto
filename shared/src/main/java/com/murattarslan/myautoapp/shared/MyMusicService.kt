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
import android.media.AudioAttributes as FrameworkAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes as AudioAttributes3

class MyMusicService : MediaBrowserServiceCompat() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private val podcastList = mutableListOf<PodcastItem>()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var currentArtworkBitmap: Bitmap? = null

    // Sınıf içinde (alanlar)
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLossTransient: Boolean = false

    // Audio focus listener
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Fokus geri geldi: volume normale çek, gerekiyorsa resume et
                try {
                    player.volume = 1.0f
                    if (wasPlayingBeforeFocusLossTransient) {
                        player.play()
                        wasPlayingBeforeFocusLossTransient = false
                    }
                } catch (t: Throwable) { /* safe-guard */ }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Geçici kayıp: pause et ama resume edebilmek için durumu sakla
                try {
                    wasPlayingBeforeFocusLossTransient = player.isPlaying
                    if (player.isPlaying) player.pause()
                } catch (t: Throwable) { /* safe-guard */ }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck: düşük sesle devam et
                try {
                    player.volume = 0.15f
                } catch (t: Throwable) { /* safe-guard */ }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Kalıcı kayıp: dur ve focus'u bırak
                try {
                    wasPlayingBeforeFocusLossTransient = false
                    if (player.isPlaying) player.pause()
                } catch (t: Throwable) { /* safe-guard */ }
                abandonAudioFocus()
            }
        }
    }

    // audio focus isteme (return true => focus alındı)
    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(AudioManager::class.java)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusAttr = FrameworkAudioAttributes.Builder()
                .setUsage(FrameworkAudioAttributes.USAGE_MEDIA)
                .setContentType(FrameworkAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(focusAttr)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(afChangeListener, Handler(Looper.getMainLooper()))
                .build()

            val res = audioManager.requestAudioFocus(audioFocusRequest!!)
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            // Legacy
            val res = audioManager.requestAudioFocus(
                afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                audioManager.abandonAudioFocus(afChangeListener)
            }
        } catch (t: Throwable) { /* ignore */ }
    }

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
            if (!requestAudioFocus()) {
                // focus alınamadıysa çalmayı deneme
                Log.d(TAG, "Audio focus not granted, abort play")
                return
            }
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
            abandonAudioFocus()
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

        override fun onFastForward() {
            // Standart fast-forward (ör. 15s)
            Log.d(TAG, "onFastForward")
            if (!this@MyMusicService::player.isInitialized) return
            val pos = player.currentPosition + 15_000
            val duration = player.duration
            val max = if (duration > 0) duration else Long.MAX_VALUE
            when {
                pos < 0L -> player.seekToPreviousMediaItem()
                pos > max -> player.seekToNextMediaItem()
                else -> player.seekTo(0)
            }
        }

        override fun onRewind() {
            // Standart rewind (ör. 15s)
            Log.d(TAG, "onRewind")
            if (!this@MyMusicService::player.isInitialized) return
            val pos = player.currentPosition - 15_000
            val duration = player.duration
            val max = if (duration > 0) duration else Long.MAX_VALUE
            when {
                pos < 0L -> player.seekToPreviousMediaItem()
                pos > max -> player.seekToNextMediaItem()
                else -> player.seekTo(0)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        createNotificationChannel()
        loadPodcasts()

        player = ExoPlayer.Builder(this).build()
        player = ExoPlayer.Builder(this).build()
        val audioAttrs3 = AudioAttributes3.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
// Eğer kendi focus yönetimini kullanıyorsan handleAudioFocus = false
        player.setAudioAttributes(audioAttrs3, /* handleAudioFocus = */ false)
        player.addListener(playerListener)
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
                        PlaybackStateCompat.ACTION_FAST_FORWARD or
                        PlaybackStateCompat.ACTION_REWIND or
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
            // Bildirimi sürekli yap (foreground service)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2, 3, 4)
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
                    R.drawable.ic_media_rew,
                    "Rewind",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_REWIND
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
                    R.drawable.ic_media_ff,
                    "Forward",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_FAST_FORWARD
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
                // Kullanıcının bildirimi kapatmasını engelleme kararını proje gereksinimine göre ayarla
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }// --- SERVICE: Playlist uygulayan yardımcı fonksiyon ---
    private fun setPlaylistFromIds(ids: ArrayList<String>?, startIndex: Int = 0, playImmediately: Boolean = true) {
        if (ids.isNullOrEmpty()) return

        // podcastList'de eşleşen öğeleri sırayla al
        val matched = ids.mapNotNull { id -> podcastList.find { it.id == id } }

        if (matched.isEmpty()) return

        // player için MediaItem listesi oluştur
        val mediaItems = matched.map { podcast ->
            MediaItem.Builder()
                .setUri(podcast.url)
                .setMediaId(podcast.id)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(podcast.title)
                        .setArtist(podcast.artist)
                        .setArtworkUri(Uri.parse(podcast.imageUrl))
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, /* resetPosition= */ true)
        // queue'yu MediaSession'e set et ki Android Auto queue'yu göstersin
        val queue = matched.mapIndexed { index, podcast ->
            val desc = MediaDescriptionCompat.Builder()
                .setMediaId(podcast.id)
                .setTitle(podcast.title)
                .setSubtitle(podcast.artist)
                .setIconUri(Uri.parse(podcast.imageUrl))
                .setMediaUri(Uri.parse(podcast.url))
                .build()
            MediaSessionCompat.QueueItem(desc, index.toLong())
        }
        session.setQueue(queue)
        session.setQueueTitle("Özel Çalma Listesi")

        // Başlangıç konumuna atla
        val safeIndex = startIndex.coerceIn(0, mediaItems.size - 1)
        player.seekTo(safeIndex, 0L)
        player.prepare()

        if (playImmediately) {
            player.play()
            session.isActive = true
        } else {
            // sadece metadata & queue güncellensin
            updateSessionMetadata()
            updatePlaybackState()
            updateNotification()
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
        stopForeground(STOP_FOREGROUND_DETACH)
        abandonAudioFocus()
        super.onDestroy()
    }
}