package com.murattarslan.car.service.core.session

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.session.MediaButtonReceiver
import com.murattarslan.car.R
import com.murattarslan.car.domain.MediaService
import com.murattarslan.car.domain.OnMediaController
import com.murattarslan.car.service.core.player.PlayerController
import com.murattarslan.car.service.core.player.PlayerState
import com.murattarslan.car.service.core.queue.RepeatMode
import com.murattarslan.car.service.data.MediaItemModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DefaultSessionManager(context: Context, val player: PlayerController, scope: CoroutineScope) :
    SessionManager {

    companion object {
        private const val TAG = "MediaService_SessionManager"

        private const val ACTION_TOGGLE_FAVORITE = "ACTION_TOGGLE_FAVORITE"
        private const val ACTION_TOGGLE_SHUFFLE = "ACTION_TOGGLE_SHUFFLE"
        private const val ACTION_TOGGLE_REPEAT = "ACTION_TOGGLE_REPEAT"
    }

    private var session: MediaSessionCompat

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onPlay called")
            player.play()
            session.isActive = true
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onPlayFromMediaId called with id=$mediaId")
            mediaId?.let { id ->
                player.playFromMediaId(id)
            }
        }

        override fun onPause() {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onPause called")
            player.pause()
        }

        override fun onStop() {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onStop called")
            player.stop()
            session.isActive = false
        }

        override fun onSkipToNext() {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onSkipToNext called")
            player.skipToNext()
        }

        override fun onSkipToPrevious() {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onSkipToPrevious called")
            player.skipToPrevious()
        }

        override fun onSeekTo(position: Long) {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onSeekTo called with pos=$position")
            player.seekTo(position)
        }

        override fun onFastForward() {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onFastForward called")
            player.fastForward()
        }

        override fun onRewind() {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onRewind called")
            player.rewind()
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (MediaService.isDebugEnable) Log.d(TAG, "Callback: onCustomAction called action=$action")
            when (action) {
                ACTION_TOGGLE_FAVORITE -> {
                    val mediaId = session.controller.metadata
                        ?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                    if (MediaService.isDebugEnable) Log.d(TAG, "onCustomAction: Toggle Favorite for id=$mediaId")
                    mediaId?.let {
                        player.markFavorite(it)
                        MediaService.instance?.mediaStateListener?.forEach { listener -> listener.onFavoriteTrack(it) }
                    }
                }
                ACTION_TOGGLE_SHUFFLE -> {
                    val newShuffleState = player.playerState.value.isShuffleEnabled.not()
                    if (MediaService.isDebugEnable) Log.d(TAG, "onCustomAction: Toggle Shuffle to $newShuffleState")
                    player.shuffleMode(newShuffleState)
                }
                ACTION_TOGGLE_REPEAT -> {
                    val currentMode = player.playerState.value.repeatMode
                    if (MediaService.isDebugEnable) Log.d(TAG, "onCustomAction: Toggle Repeat from $currentMode")
                    when (currentMode) {
                        RepeatMode.ALL -> player.repeatMode(RepeatMode.NONE)
                        RepeatMode.NONE -> player.repeatMode(RepeatMode.ONE)
                        else -> player.repeatMode(RepeatMode.ALL)
                    }
                }
            }
        }
    }

    init {
        if (MediaService.isDebugEnable) Log.d(TAG, "init: Initializing DefaultSessionManager")
        scope.launch {
            player.playerState.collect { state ->
                if (MediaService.isDebugEnable) Log.d(TAG, "init: New PlayerState collected for track=${state.track?.title}")
                updateMediaSession(state)
            }
        }
        session = MediaSessionCompat(context, TAG)
        val intent = MediaService.instance?.sessionActivity ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
        session.setSessionActivity(
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        )

        session.setCallback(callback)
        session.isActive = true

        if (MediaService.isDebugEnable) Log.d(TAG, "init: Setting up MediaService.mediaController")
        MediaService.mediaController = object : OnMediaController {
            override fun onPlay() {
                if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onPlay")
                player.play()
            }

            override fun onPause() {
                if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onPause")
                player.pause()
            }

            override fun isPlaying(): Boolean {
                val playing = player.isPlaying()
                if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: isPlaying check: $playing")
                return playing
            }

            override fun onNext() {
                if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onNext")
                player.skipToNext()
            }

            override fun onPrev() {
                if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onPrev")
                player.skipToPrevious()
            }

            override fun onSeek(position: Long) {
                if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onSeek to $position")
                player.seekTo(position)
            }

            override fun onChange(track: MediaItemModel) {
                if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onChange trackId=${track.id}")
                player.playFromMediaId(track.id)
            }

            override fun currentItem(): MediaItemModel? {
                return player.currentTrack()
            }

            override fun hasNext(): Boolean {
                return player.hasNext()
            }

            override fun hasPrevious(): Boolean {
                return player.hasPrevious()
            }

            override fun onFavorite(track: MediaItemModel) {
                if (MediaService.isDebugEnable) Log.d(TAG, "OnMediaController: onFavorite for ${track.id}")
                player.markFavorite(track.id)
            }
        }
    }

    private fun updateMediaSession(state: PlayerState) {
        if (MediaService.isDebugEnable) Log.d(TAG, "updateMediaSession: Updating session UI. isPlaying=${state.isPlaying}, pos=${state.currentPosition}")

        val favoriteAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_FAVORITE,
            if (state.track?.isFavorite == true) "Remove Favorite" else "Add Favorite",
            if (state.track?.isFavorite == true) androidx.media3.session.R.drawable.media3_icon_heart_filled else androidx.media3.session.R.drawable.media3_icon_heart_unfilled
        ).build()

        val shuffleAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_SHUFFLE,
            "shuffle enable",
            if (state.isShuffleEnabled) androidx.media3.session.R.drawable.media3_icon_shuffle_on else androidx.media3.session.R.drawable.media3_icon_shuffle_off
        ).build()

        val repeatAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_REPEAT,
            "repeat mode",
            when (state.repeatMode) {
                RepeatMode.ALL -> androidx.media3.session.R.drawable.media3_icon_repeat_all
                RepeatMode.NONE -> androidx.media3.session.R.drawable.media3_icon_repeat_off
                else -> androidx.media3.session.R.drawable.media3_icon_repeat_one
            }
        ).build()

        val actions = buildPlaybackActions(
            isLive = state.duration < 0,
            hasNext = player.hasNext(),
            hasPrevious = player.hasPrevious()
        )

        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                //.addCustomAction(favoriteAction)
                .addCustomAction(shuffleAction)
                .addCustomAction(repeatAction)
                .setState(
                    if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    if (state.duration < 0) PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN else state.currentPosition,
                    1f
                )
                .build()
        )

        val metadata = updateSessionMetadata(state)
        session.setMetadata(metadata)

        MediaService.instance?.apply {
            if (MediaService.isDebugEnable) Log.d(TAG, "updateMediaSession: Notifying mediaStateListeners")
            state.track?.let {
                mediaStateListener?.forEach { listener -> listener.onChangeTrack(it) }
            }
            mediaStateListener?.forEach { listener -> listener.onSeek(state.currentPosition) }
            if (state.isPlaying)
                mediaStateListener?.forEach { listener -> listener.onPlayTrack() }
            else
                mediaStateListener?.forEach { listener -> listener.onPauseTrack() }
        }

        if (MediaService.isDebugEnable) Log.d(TAG, "updateMediaSession: Favorite state in metadata: ${state.track?.isFavorite}")

        _sessionState.value = _sessionState.value.copy(
            session = session,
            artwork = state.artwork,
            isFavoriteChanged = state.track?.isFavorite == true
        )
    }

    fun buildPlaybackActions(
        isLive: Boolean,
        hasNext: Boolean,
        hasPrevious: Boolean
    ): Long {
        var actions = 0L
        actions = actions or PlaybackStateCompat.ACTION_PLAY
        actions = actions or PlaybackStateCompat.ACTION_PAUSE
        actions = actions or PlaybackStateCompat.ACTION_PLAY_PAUSE
        actions = actions or PlaybackStateCompat.ACTION_STOP

        if (!isLive) {
            actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
            actions = actions or PlaybackStateCompat.ACTION_FAST_FORWARD
            actions = actions or PlaybackStateCompat.ACTION_REWIND
        }

        if (hasNext) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }

        if (hasPrevious) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }

        if (MediaService.isDebugEnable) Log.d(TAG, "buildPlaybackActions: isLive=$isLive, actions=$actions")
        return actions
    }

    private fun updateSessionMetadata(state: PlayerState): MediaMetadataCompat {
        val currentPodcast = state.track
        if (MediaService.isDebugEnable) Log.d(TAG, "updateSessionMetadata: Building metadata for ${currentPodcast?.title}")

        val metadataBuilder = MediaMetadataCompat.Builder()

        if (currentPodcast != null) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, currentPodcast.id)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentPodcast.title)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentPodcast.artist)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Tech Podcast Series")

            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentPodcast.title)
            currentPodcast.artist?.let{ metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, it) }
            currentPodcast.description?.let{ metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, it) }

            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, currentPodcast.imageUri)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, currentPodcast.imageUri)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, currentPodcast.imageUri)

            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentPodcast.duration)

            state.artwork?.let {
                if (MediaService.isDebugEnable) Log.d(TAG, "updateSessionMetadata: Adding artwork bitmap")
                metadataBuilder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
            }
        }

        return metadataBuilder.build()
    }

    override val token: MediaSessionCompat.Token
        get() = session.sessionToken

    private val _sessionState = MutableStateFlow(SessionState())
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    override fun onBind(intent: Intent?) {
        if (MediaService.isDebugEnable) Log.d(TAG, "onBind: Handling MediaButtonReceiver intent")
        MediaButtonReceiver.handleIntent(session, intent)
    }

    override fun onGetRoot() = player.onGetRoot()

    override fun onLoadChildren(parentId: String) = player.onLoadChildren(parentId)

    override fun onDestroy() {
        if (MediaService.isDebugEnable) Log.d(TAG, "onDestroy: Destroying SessionManager")
        player.release()
        MediaService.instance?.mediaStateListener?.forEach { listener -> listener.onPauseTrack() }
        session.isActive = false
        session.release()
    }
}