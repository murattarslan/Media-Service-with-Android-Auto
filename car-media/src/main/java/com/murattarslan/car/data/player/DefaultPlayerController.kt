package com.murattarslan.car.data.player

import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.murattarslan.car.core.MediaService
import com.murattarslan.car.domain.interfaces.PlayerController
import com.murattarslan.car.domain.interfaces.QueueManager
import com.murattarslan.car.domain.models.MediaItemModel
import com.murattarslan.car.domain.models.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DefaultPlayerController(
    private val context: Context,
    private val queueManager: QueueManager,
    private val scope: CoroutineScope
) : PlayerController {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var audioEngine: AudioEngine = AudioEngine(context, player)
    private lateinit var playerListener: PlayerListener

    companion object {
        private const val TAG = "MediaService_DefaultPlayerController"
    }

    init {
        scope.launch { collectState() }
        initializePlayer()
    }

    private suspend fun collectState() {
        queueManager.queueState.collect { state ->
            prepare(state.queue, state.currentIndex)
            if (MediaService.isDebugEnable)
                Log.d(TAG, "syncPlayerWithQueue: Index mismatch. Seeking to ${state.currentIndex}")
            player.seekTo(state.currentIndex, 0)
            scope.launch { loadArtworkAndUpdateNotification() }
            updateState()
        }
    }

    fun prepare(queue: List<MediaItemModel>, index: Int) {
        if (MediaService.isDebugEnable) Log.d(
            TAG,
            "prepare: queueSize=${queue.size}, startIndex=$index"
        )
        val mediaItems = queue.map { track ->
            MediaItem.Builder()
                .setUri(track.mediaUri)
                .setMediaId(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(track.imageUri.toUri())
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems)
        player.seekToDefaultPosition(index)
        player.prepare()
        player.playWhenReady = true
    }

    private fun initializePlayer() {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "init: Setting up ExoPlayer")

        val audioAttr = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttr, false)

        playerListener = PlayerListener(player) { errorMessage ->
            if (errorMessage != null)
                _playerState.update { it.copy(error = errorMessage) }
            else {
                scope.launch { loadArtworkAndUpdateNotification() }
                updateState()
            }
        }
    }

    private fun updateState(mediaId: String? = null) {
        val track = queueManager.getTrack(player.currentMediaItem?.mediaId)?.copy()
        mediaId?.let {
            if (track?.id == it)
                track.isFavorite = track.isFavorite.not()
        }
        track?.duration =
            if (player.isCurrentMediaItemLive) -1 else player.duration.coerceAtLeast(0)
        if (MediaService.isDebugEnable)
            Log.v(TAG, "updateState: pos=${currentPosition()}, duration=${track?.duration}")
        _playerState.update {
            it.copy(
                track = track,
                isShuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                updateAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun loadArtworkAndUpdateNotification() {
        val currentTrack = queueManager.getTrack(player.currentMediaItem?.mediaId)
        try {
            if (currentTrack?.imageUri == null) {
                _playerState.update { it.copy(artwork = null) }
                return
            }

            if (MediaService.isDebugEnable) Log.v(TAG, "Loading artwork for: ${currentTrack.title}")
            val bitmap = withContext(Dispatchers.IO) {
                Glide.with(context)
                    .asBitmap()
                    .load(currentTrack.imageUri)
                    .submit(1024, 1024)
                    .get()
            }
            _playerState.update { it.copy(artwork = bitmap) }
        } catch (e: Exception) {
            if (MediaService.isDebugEnable) Log.e(
                TAG,
                "Error loading artwork for ${currentTrack?.title}: ${e.message}"
            )
            _playerState.update { it.copy(artwork = null) }
        }
    }

    // interface functions

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    override fun play() {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "play() called")
        if (!audioEngine.requestAudioFocus()) {
            if (MediaService.isDebugEnable)
                Log.w(TAG, "Audio focus not granted, aborting play")
            return
        }
        if (player.currentMediaItem != null) {
            player.play()
            scope.launch { loadArtworkAndUpdateNotification() }
            updateState()
        } else {
            queueManager.queueState.value.queue.firstOrNull()?.id?.let { playFromMediaId(it) }
        }
    }

    override fun pause() {
        if (MediaService.isDebugEnable) Log.d(TAG, "pause() called")
        player.pause()
        updateState()
    }

    override fun isPlaying(): Boolean {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "isPlaying() called ${player.isPlaying}")
        return player.isPlaying
    }

    override fun currentPosition(): Long {
        val position = player.currentPosition
        if (MediaService.isDebugEnable)
            Log.d(TAG, "currentPosition() called: $position")
        return position
    }

    override fun seekTo(position: Long) {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "seekTo() called: $position")
        player.seekTo(position)
    }

    override fun fastForward() {
        val pos = player.currentPosition + 15_000
        val duration = player.duration
        val max = if (duration > 0) duration else Long.MAX_VALUE
        if (MediaService.isDebugEnable) Log.d(TAG, "fastForward() to $pos")
        when {
            pos < 0L -> skipToPrevious()
            pos > max -> skipToNext()
            else -> player.seekTo(pos)
        }
    }

    override fun rewind() {
        val pos = player.currentPosition - 15_000
        val duration = player.duration
        val max = if (duration > 0) duration else Long.MAX_VALUE
        if (MediaService.isDebugEnable) Log.d(TAG, "rewind() to $pos")
        when {
            pos < 0L -> skipToPrevious()
            pos > max -> skipToNext()
            else -> player.seekTo(pos)
        }
    }

    override fun hasNext(): Boolean {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "hasNext() called: ${player.hasNextMediaItem()}")
        return player.hasNextMediaItem()
    }

    override fun skipToNext() {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "skipToNext() triggered via queueManager")
        if (hasNext())
            player.seekToNextMediaItem()
        scope.launch { loadArtworkAndUpdateNotification() }
        updateState()
    }

    override fun hasPrevious(): Boolean {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "hasPrevious() called: ${player.hasPreviousMediaItem()}")
        return player.hasPreviousMediaItem()
    }

    override fun skipToPrevious() {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "skipToPrevious() triggered via queueManager")
        if (hasPrevious())
            player.seekToPreviousMediaItem()
        scope.launch { loadArtworkAndUpdateNotification() }
        updateState()
    }

    override fun currentTrack(id: String?): MediaItemModel? {
        val model = queueManager.getTrack(id ?: player.currentMediaItem?.mediaId)
        if (MediaService.isDebugEnable)
            Log.d(TAG, "currentTrack: ${model?.id}")
        return model
    }

    override fun currentQueue(): List<MediaItemModel> {
        val queue = queueManager.queueState.value.queue
        if (MediaService.isDebugEnable)
            Log.d(TAG, "currentQueue: ${queue.size}")
        return queue
    }

    override fun repeatMode(repeatMode: Int) {
        if (MediaService.isDebugEnable) Log.d(TAG, "setRepeatMode: $repeatMode")
        player.repeatMode = repeatMode
    }

    override fun shuffleMode(shuffleMode: Boolean) {
        if (MediaService.isDebugEnable) Log.d(TAG, "setShuffleMode: $shuffleMode")
        player.shuffleModeEnabled = shuffleMode
    }

    override fun stop() {
        if (MediaService.isDebugEnable) Log.d(TAG, "stop() called")
        player.stop()
        audioEngine.abandonAudioFocus()
    }

    override fun release() {
        if (MediaService.isDebugEnable) Log.d(TAG, "release() player")
        audioEngine.abandonAudioFocus()
        playerListener.remove()
        player.release()
    }

    // Queue control

    override fun playFromMediaId(mediaId: String) {
        if (MediaService.isDebugEnable)
            Log.i(TAG, "playFromMediaId: $mediaId")
        updateState()
        queueManager.createQueue(mediaId)
    }

    override fun playFromAlbum(albumId: String, index: Int) {
        if (MediaService.isDebugEnable)
            Log.i(TAG, "playFromAlbum: $albumId, index: $index")
        queueManager.createQueue(albumId, index)
    }

    override fun markFavorite(id: String) {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "markFavorite: $id")
        scope.launch {
            updateState(id)
            delay(500)
            queueManager.markFavorite(id)
        }
    }

    override fun onGetRoot(): MediaBrowserServiceCompat.BrowserRoot {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "onGetRoot")
        return queueManager.onGetRoot()
    }

    override fun onLoadChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem> {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "onLoadChildren parentId: $parentId")
        return queueManager.onLoadChildren(parentId)
    }
}
