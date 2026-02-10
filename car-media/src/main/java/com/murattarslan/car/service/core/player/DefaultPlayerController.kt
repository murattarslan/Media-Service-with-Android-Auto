package com.murattarslan.car.service.core.player

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.murattarslan.car.domain.MediaService
import com.murattarslan.car.service.core.queue.QueueManager
import com.murattarslan.car.service.core.queue.QueueState
import com.murattarslan.car.service.core.queue.RepeatMode
import com.murattarslan.car.service.data.MediaItemModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DefaultPlayerController(
    private val context: Context,
    private val queueManager: QueueManager,
    private val scope: CoroutineScope
) :
    PlayerController {

    companion object {
        private const val TAG = "MediaService_DefaultPlayerController"
    }

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var audioEngine: AudioEngine = AudioEngine(context, player)

    private var lastQueueVersion = -1
    private var positionUpdateJob: Job? = null

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (MediaService.isDebugEnable) Log.d(TAG, "onIsPlayingChanged: $isPlaying")
            if (isPlaying) {
                positionUpdateJob = scope.launch {
                    if (MediaService.isDebugEnable) Log.d(TAG, "Starting position update job")
                    var position = _playerState.value.currentPosition
                    while (isActive){
                        position += 1000
                        if (position > _playerState.value.duration) {
                            if (MediaService.isDebugEnable) Log.v(TAG, "Position reached duration, breaking loop")
                            break
                        }
                        delay(1000)
                        updateState(position)
                    }
                }
                positionUpdateJob?.start()
            } else {
                if (MediaService.isDebugEnable) Log.d(TAG, "Cancelling position update job")
                positionUpdateJob?.cancel()
            }
            scope.launch { updateState() }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            if (MediaService.isDebugEnable) Log.d(TAG, "onMediaMetadataChanged: ${mediaMetadata.title}")
            positionUpdateJob?.cancel()
            scope.launch { updateState() }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (MediaService.isDebugEnable) {
                Log.d(TAG, "onMediaItemTransition: reason=$reason, hasNext=${player.hasNextMediaItem()}")
            }

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !player.hasNextMediaItem()) {

                if (MediaService.isDebugEnable) Log.i(TAG, "Queue finished. Resetting to first track and pausing.")

                player.pause()
                player.seekTo(0, 0)

                scope.launch { updateState(0) }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (MediaService.isDebugEnable) {
                val stateStr = when(state) {
                    Player.STATE_READY -> "READY"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "IDLE"
                }
                Log.d(TAG, "onPlaybackStateChanged: $stateStr")
            }
            scope.launch { updateState() }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (MediaService.isDebugEnable) Log.e(TAG, "onPlayerError: ${error.message}", error)
            _playerState.value = _playerState.value.copy(
                playbackState = PlaybackState.ERROR,
                error = error.message
            )
        }
    }

    init {
        if (MediaService.isDebugEnable) Log.d(TAG, "init: Setting up ExoPlayer")
        val audioAttrs3 = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttrs3, false)
        player.addListener(playerListener)

        scope.launch {
            queueManager.queueState.collect { state ->
                if (MediaService.isDebugEnable) Log.v(TAG, "QueueState collected: version=${state.version}")
                syncPlayerWithQueue(state)
            }
        }

        if (MediaService.isDebugEnable) Log.d(TAG, "init: Setting initial media items, count=${MediaService.mediaList.size}")
        queueManager.setMediaItems(MediaService.mediaList)
    }

    private fun syncPlayerWithQueue(state: QueueState) {
        if (state.version != lastQueueVersion) {
            if (MediaService.isDebugEnable) Log.i(TAG, "syncPlayerWithQueue: Version mismatch ($lastQueueVersion -> ${state.version}). Preparing new queue.")
            prepare(state.queue, state.currentIndex)
            lastQueueVersion = state.version
        } else {
            if (player.currentMediaItemIndex != state.currentIndex) {
                if (MediaService.isDebugEnable) Log.d(TAG, "syncPlayerWithQueue: Index mismatch. Seeking to ${state.currentIndex}")
                player.seekTo(state.currentIndex, 0)
            }
        }

        syncRepeatShuffle(state)
        scope.launch { updateState() }
    }

    override fun prepare(queue: List<MediaItemModel>, index: Int) {
        if (MediaService.isDebugEnable) Log.d(TAG, "prepare: queueSize=${queue.size}, startIndex=$index")
        val mediaItems = queue.map { podcast ->
            MediaItem.Builder()
                .setUri(podcast.mediaUri)
                .setMediaId(podcast.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(podcast.title)
                        .setArtist(podcast.artist)
                        .setArtworkUri(podcast.imageUri.toUri())
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems)
        player.seekToDefaultPosition(index)
        player.prepare()
        player.play()
    }

    private fun syncRepeatShuffle(state: QueueState) {
        if (MediaService.isDebugEnable) Log.v(TAG, "syncRepeatShuffle: RepeatMode=${state.repeatMode}, Shuffle=${state.isShuffleEnabled}")
        player.repeatMode = when (state.repeatMode) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.NONE -> Player.REPEAT_MODE_OFF
        }
        player.shuffleModeEnabled = state.isShuffleEnabled
    }

    private suspend fun updateState(position: Long = player.currentPosition) {
        val time = if (player.isCurrentMediaItemLive) -1 else player.duration.coerceAtLeast(0)

        if (MediaService.isDebugEnable && !player.isCurrentMediaItemLive) {
            // Sık log basmamak için verbose kullanıyoruz
            Log.v(TAG, "updateState: pos=$position, duration=$time")
        }

        _playerState.value = _playerState.value.copy(
            playbackState = player.toPlaybackState(),
            isPlaying = player.isPlaying,
            duration = time,
            currentPosition = if (player.isCurrentMediaItemLive) -1 else position,
            bufferedPosition = player.bufferedPosition,
            track = queueManager.getCurrent(player.currentMediaItem?.mediaId)?.apply { duration = time },
            artwork = loadArtworkAndUpdateNotification(),
            isShuffleEnabled = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.NONE
            }
        )
    }

    private fun ExoPlayer.toPlaybackState(): PlaybackState {
        return when (playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            Player.STATE_READY -> PlaybackState.READY
            Player.STATE_ENDED -> PlaybackState.END
            else -> PlaybackState.IDLE
        }
    }

    private suspend fun loadArtworkAndUpdateNotification(): Bitmap? {
        val currentTrack = queueManager.getCurrent()
        try {
            if (currentTrack?.imageUri == null) return null

            if (MediaService.isDebugEnable) Log.v(TAG, "Loading artwork for: ${currentTrack.title}")
            val bitmap = withContext(Dispatchers.IO) {
                Glide.with(context)
                    .asBitmap()
                    .load(currentTrack.imageUri)
                    .submit(1024, 1024)
                    .get()
            }
            return bitmap
        } catch (e: Exception) {
            if (MediaService.isDebugEnable) Log.e(TAG, "Error loading artwork for ${currentTrack?.title}: ${e.message}")
            return null
        }
    }

    override fun play() {
        if (MediaService.isDebugEnable) Log.d(TAG, "play() called")
        if (!audioEngine.requestAudioFocus()) {
            if (MediaService.isDebugEnable) Log.w(TAG, "Audio focus not granted, aborting play")
            return
        }
        if (player.currentMediaItem == null) {
            if (MediaService.isDebugEnable) Log.d(TAG, "No current item, playing first available")
            MediaService.mediaList.firstOrNull()?.id?.let { playFromMediaId(it) }
        }
        player.play()
    }

    override fun pause() {
        if (MediaService.isDebugEnable) Log.d(TAG, "pause() called")
        player.pause()
    }

    override fun isPlaying() = player.isPlaying

    override fun seekTo(position: Long) {
        if (MediaService.isDebugEnable) Log.d(TAG, "seekTo() called: $position")
        player.seekTo(position)
    }

    override fun fastForward() {
        val pos = player.currentPosition + 15_000
        val duration = player.duration
        val max = if (duration > 0) duration else Long.MAX_VALUE
        if (MediaService.isDebugEnable) Log.d(TAG, "fastForward() to $pos")
        when {
            pos < 0L -> player.seekToPreviousMediaItem()
            pos > max -> player.seekToNextMediaItem()
            else -> player.seekTo(pos) // DÜZELTME: Kendi kodunda 0'a çekiyordu, pos'a çektim.
        }
    }

    override fun rewind() {
        val pos = player.currentPosition - 15_000
        if (MediaService.isDebugEnable) Log.d(TAG, "rewind() to $pos")
        when {
            pos < 0L -> player.seekToPreviousMediaItem()
            else -> player.seekTo(pos) // DÜZELTME: Kendi kodunda 0'a çekiyordu, pos'a çektim.
        }
    }

    override fun hasNext() = player.hasNextMediaItem()
    override fun skipToNext() {
        if (MediaService.isDebugEnable) Log.d(TAG, "skipToNext() triggered via queueManager")
        queueManager.skipToNext()
    }

    override fun hasPrevious() = player.hasPreviousMediaItem()
    override fun skipToPrevious() {
        if (MediaService.isDebugEnable) Log.d(TAG, "skipToPrevious() triggered via queueManager")
        queueManager.skipToPrevious()
    }

    override fun playFromMediaId(mediaId: String) {
        if (MediaService.isDebugEnable) Log.i(TAG, "playFromMediaId: $mediaId")
        queueManager.setMediaItems(MediaService.mediaList)
        queueManager.createQueueFromMediaId(mediaId)
    }

    override fun playFromAlbum(albumId: String, index: Int) {
        if (MediaService.isDebugEnable) Log.i(TAG, "playFromAlbum: $albumId, index: $index")
        queueManager.setMediaItems(MediaService.mediaList)
        queueManager.createQueue(albumId, index)
    }

    override fun currentTrack(): MediaItemModel? {
        return queueManager.getCurrent()
    }

    override fun stop() {
        if (MediaService.isDebugEnable) Log.d(TAG, "stop() called")
        player.stop()
        audioEngine.abandonAudioFocus()
    }

    override fun release() {
        if (MediaService.isDebugEnable) Log.d(TAG, "release() player")
        audioEngine.abandonAudioFocus()
        player.removeListener(playerListener)
        player.release()
    }

    override fun markFavorite(id: String) {
        if (MediaService.isDebugEnable) Log.d(TAG, "markFavorite: $id")
        queueManager.markFavorite(id)
    }

    override fun repeatMode(repeatMode: RepeatMode) {
        if (MediaService.isDebugEnable) Log.d(TAG, "setRepeatMode: $repeatMode")
        queueManager.setRepeatMode(repeatMode)
    }

    override fun shuffleMode(shuffleMode: Boolean) {
        if (MediaService.isDebugEnable) Log.d(TAG, "setShuffleMode: $shuffleMode")
        queueManager.setShuffle(shuffleMode)
    }

    override fun onGetRoot() = queueManager.onGetRoot()

    override fun onLoadChildren(parentId: String) = queueManager.onLoadChildren(parentId)
}
