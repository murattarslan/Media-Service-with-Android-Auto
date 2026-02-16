package com.murattarslan.car.data.session

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.net.toUri
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.Player
import com.murattarslan.car.core.MediaService
import com.murattarslan.car.domain.interfaces.PlayerController
import com.murattarslan.car.domain.interfaces.SessionManager
import com.murattarslan.car.domain.models.PlayerState
import com.murattarslan.car.domain.models.SessionState
import com.murattarslan.car.ui.constants.MediaConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultSessionManager(
    context: Context,
    val player: PlayerController,
    scope: CoroutineScope
) : SessionManager {

    private lateinit var session: MediaSessionCompat

    companion object {
        private const val TAG = "MediaService_SessionManager"
    }

    init {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "init: Initializing DefaultSessionManager")
        initSession(context)
        if (MediaService.isDebugEnable)
            Log.d(TAG, "init: Setting up MediaService.mediaController")
        session.setCallback(SessionCallback(session, player))
        MediaService.instance?.mediaController = SessionListener(session, player)
        scope.launch { collectState() }
    }

    // interface fonksiyonları

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
        MediaService.instance?.mediaStateListener?.forEach { listener -> listener.onPauseTrack(player.playerState.value.track?.id) }
        session.isActive = false
        session.release()
    }

    // initialize fonksiyonları

    private fun initSession(context: Context) {
        session = MediaSessionCompat(context, TAG)
        val intent = MediaService.instance?.sessionActivity
            ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
        session.setSessionActivity(
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private suspend fun collectState() {
        player.playerState.collect { state ->
            if (MediaService.isDebugEnable) Log.d(
                TAG,
                "init: New PlayerState collected for track=${state.track?.title}"
            )
            updateMediaSession(state)
        }
    }

    // sınıf içi fonksiyonlar

    private fun updateMediaSession(state: PlayerState) {
        if (MediaService.isDebugEnable)
            Log.d(
                TAG,
                "updateMediaSession: Updating session UI. " +
                        "isPlaying=${player.isPlaying()}, " +
                        "pos=${player.currentPosition()}"
            )

        session.setPlaybackState(createPlaybackState(state))

        val metadata = updateSessionMetadata(state)
        session.setMetadata(metadata)
        session.setQueue(createQueueList())
        session.setQueueTitle(player.currentTrack(state.track?.parentId)?.title)
        fallbackService(state)
        updateState(state)
    }

    private fun updateState(state: PlayerState) {
        _sessionState.update {
            it.copy(
                session = session,
                artwork = state.artwork,
                isFavoriteChanged = state.track?.isFavorite == true
            )
        }
    }

    fun fallbackService(state: PlayerState) {
        if (MediaService.isDebugEnable) Log.d(
            TAG,
            "updateMediaSession: Notifying mediaStateListeners"
        )
        MediaService.instance?.apply {
            state.track?.let {
                mediaStateListener?.forEach { listener -> listener.onChangeTrack(it) }
            }
            if (player.isPlaying())
                mediaStateListener?.forEach { listener ->
                    listener.onPlayTrack(
                        state.track?.id,
                        session.controller.playbackState.position,
                        session.controller.playbackState.lastPositionUpdateTime,
                        session.controller.playbackState.playbackSpeed
                    )
                }
            else
                mediaStateListener?.forEach { listener -> listener.onPauseTrack(state.track?.id) }
        }
    }

    private fun updateSessionMetadata(state: PlayerState): MediaMetadataCompat {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "updateSessionMetadata: Building metadata for ${state.track?.title}")

        val builder = MediaMetadataCompat.Builder()

        state.track?.let {
            builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, it.id)
            builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.title)
            builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it.artist)
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Tech Podcast Series")

            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, it.title)
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, it.artist)
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, it.description)

            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it.imageUri)
            builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, it.imageUri)
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, it.imageUri)

            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it.duration)

            state.artwork?.let { artwork ->
                if (MediaService.isDebugEnable)
                    Log.d(TAG, "updateSessionMetadata: Adding artwork bitmap")
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artwork)
            }
        }

        return builder.build()
    }

    // playback creator

    private fun createQueueList(): List<MediaSessionCompat.QueueItem> {
        return player.currentQueue().map {
            MediaSessionCompat.QueueItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(it.id)
                    .setTitle(it.title)
                    .setSubtitle(it.artist)
                    .setDescription(it.description)
                    .setMediaUri(it.mediaUri.toUri())
                    .setIconUri(it.imageUri.toUri())
                    .build(),
                player.currentQueue().indexOf(it).toLong()
            )
        }
    }

    private fun createPlaybackState(state: PlayerState): PlaybackStateCompat {
        return PlaybackStateCompat.Builder()
            .addActions(state)
            .addShuffleAction(state)
            .addFavoriteAction(state)
            .addRepeatAction(state)
            .setState(
                if (player.isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                if (state.track?.duration == null) PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN else player.currentPosition(),
                1f,
                SystemClock.elapsedRealtime()
            )
            .build()
    }

    private fun PlaybackStateCompat.Builder.addActions(state: PlayerState): PlaybackStateCompat.Builder {
        var actions = 0L
        actions = actions or PlaybackStateCompat.ACTION_PLAY
        actions = actions or PlaybackStateCompat.ACTION_PAUSE
        actions = actions or PlaybackStateCompat.ACTION_PLAY_PAUSE
        actions = actions or PlaybackStateCompat.ACTION_STOP

        if (state.track?.duration == null) {
            actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
            actions = actions or PlaybackStateCompat.ACTION_FAST_FORWARD
            actions = actions or PlaybackStateCompat.ACTION_REWIND
        }

        if (player.hasNext()) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }

        if (player.hasPrevious()) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }

        if (MediaService.isDebugEnable) Log.d(
            TAG,
            "buildPlaybackActions: isLive=${state.track?.duration == null}, actions=$actions"
        )
        setActions(actions)
        return this
    }

    private fun PlaybackStateCompat.Builder.addFavoriteAction(state: PlayerState): PlaybackStateCompat.Builder {
        val action = PlaybackStateCompat.CustomAction.Builder(
            MediaConstants.ACTION_TOGGLE_FAVORITE,
            if (state.track?.isFavorite == true) "Remove Favorite" else "Add Favorite",
            if (state.track?.isFavorite == true) androidx.media3.session.R.drawable.media3_icon_heart_filled else androidx.media3.session.R.drawable.media3_icon_heart_unfilled
        ).build()
        addCustomAction(action)
        return this
    }

    private fun PlaybackStateCompat.Builder.addShuffleAction(state: PlayerState): PlaybackStateCompat.Builder {
        val action = PlaybackStateCompat.CustomAction.Builder(
            MediaConstants.ACTION_TOGGLE_SHUFFLE,
            "shuffle enable",
            if (state.isShuffleEnabled) androidx.media3.session.R.drawable.media3_icon_shuffle_on else androidx.media3.session.R.drawable.media3_icon_shuffle_off
        ).build()
        addCustomAction(action)
        return this
    }

    private fun PlaybackStateCompat.Builder.addRepeatAction(state: PlayerState): PlaybackStateCompat.Builder {
        val action = PlaybackStateCompat.CustomAction.Builder(
            MediaConstants.ACTION_TOGGLE_REPEAT,
            "repeat mode",
            when (state.repeatMode) {
                Player.REPEAT_MODE_ALL -> androidx.media3.session.R.drawable.media3_icon_repeat_all
                Player.REPEAT_MODE_OFF -> androidx.media3.session.R.drawable.media3_icon_repeat_off
                else -> androidx.media3.session.R.drawable.media3_icon_repeat_one
            }
        ).build()
        addCustomAction(action)
        return this
    }
}