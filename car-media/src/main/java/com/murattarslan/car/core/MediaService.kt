package com.murattarslan.car.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.murattarslan.car.R
import com.murattarslan.car.domain.interfaces.DataSource
import com.murattarslan.car.domain.interfaces.OnMediaController
import com.murattarslan.car.domain.listeners.OnMediaStateListener
import com.murattarslan.car.domain.listeners.OnQueueInformation
import com.murattarslan.car.domain.interfaces.NotificationEngine
import com.murattarslan.car.domain.interfaces.PlayerController
import com.murattarslan.car.domain.interfaces.QueueManager
import com.murattarslan.car.domain.interfaces.SessionManager
import com.murattarslan.car.domain.models.MediaItemModel
import com.murattarslan.car.ui.constants.MediaConstants
import com.murattarslan.car.util.ResourceUtils

/**
 * MediaService Class
 *
 * This class is used to create a media player service that can be managed
 * via notifications, WearOS, and Android Auto.
 *
 * @author murattarslan.dev
 */
class MediaService private constructor() {

    companion object {
        private const val TAG = "MediaService_Global"

        /**
         * Initializes the Builder to configure MediaService.
         *
         * @param context The application or activity context.
         * @return A [Builder] instance to chain configuration methods.
         */
        fun builder(context: Context): Builder {
            if (isDebugEnable) Log.d(
                TAG,
                "Builder: Initializing for context: ${context.packageName}"
            )
            return Builder(context)
        }

        internal var instance: MediaService? = null
        internal var isDebugEnable = true

        /**
         * Provides the singleton instance of the MediaService.
         *
         * @return The active [MediaService] instance.
         * @throws Exception if the service is not yet initialized via [Builder.build].
         */
        fun getInstance(): MediaService {
            return instance ?: run {
                if (isDebugEnable) Log.e(
                    TAG,
                    "getInstance: MediaService not initialized! Throwing Exception."
                )
                throw Exception("MediaService not initialized")
            }
        }
    }

    internal var allMediaTitle: String? = null
    internal var favoriteTitle: String? = null
    internal var allMediaIcon: Uri? = null
    internal var favoriteIcon: Uri? = null

    internal var service: Intent? = null
    internal var sessionActivity: Intent? = null

    internal var mediaStateListener: MutableList<OnMediaStateListener>? = null

    internal var queueInformation: OnQueueInformation? = null
    internal var mediaController: OnMediaController? = null

    internal var customSessionManager: SessionManager? = null
    internal var customPlayerController: PlayerController? = null
    internal var customQueueManager: QueueManager? = null
    internal var customDataSource: DataSource? = null
    internal var customNotificationEngine: NotificationEngine? = null

    /**
     * Sets the initial media state listener.
     * @param mediaStateListener The listener to observe playback changes.
     */
    private fun setMediaStateListener(mediaStateListener: OnMediaStateListener) {
        if (isDebugEnable) Log.d(TAG, "setMediaStateListener: Initial listener set")
        this.mediaStateListener = mutableListOf(mediaStateListener)
    }

    /**
     * Adds a new listener to the media state observers list.
     *
     * @param mediaStateListener The listener to add.
     * @example mediaService.addMediaStateListener(myListener)
     */
    fun addMediaStateListener(mediaStateListener: OnMediaStateListener) {
        if (this.mediaStateListener == null)
            this.mediaStateListener = mutableListOf()
        this.mediaStateListener?.add(mediaStateListener)
        if (isDebugEnable)
            Log.d(TAG, "addMediaStateListener: New listener added. Total: ${this.mediaStateListener?.size}")
    }

    /**
     * Removes a specific listener from the media state observers list.
     *
     * @param mediaStateListener The listener to remove.
     */
    fun removeMediaStateListener(mediaStateListener: OnMediaStateListener) {
        val removed = this.mediaStateListener?.remove(mediaStateListener)
        if (isDebugEnable)
            Log.d(TAG, "removeMediaStateListener: Success=$removed, Remaining: ${this.mediaStateListener?.size}")
    }

    /**
     * Handles favorite button clicks for a track.
     *
     * @param track The [MediaItemModel] to be favorited or unfavorited.
     * @param isFavorite Boolean state of the favorite action.
     */
    fun onFav(track: MediaItemModel) {
        if (isDebugEnable) Log.i(TAG, "onFav: User clicked favorite for track: ${track.title}")
        mediaController?.onFavorite(track)
    }

    /**
     * Checks if the player is currently in a playing state.
     *
     * @return True if media is playing, false otherwise.
     */
    fun isPlaying(): Boolean {
        val playing = mediaController?.isPlaying() ?: false
        if (isDebugEnable) Log.v(TAG, "isPlaying check: $playing")
        return playing
    }

    /**
     * Commands the controller to start or resume playback.
     */
    fun onPlay() {
        if (isDebugEnable) Log.d(TAG, "onPlay: Forwarding to controller")
        mediaController?.onPlay()
    }

    /**
     * Commands the controller to pause current playback.
     */
    fun onPause() {
        if (isDebugEnable) Log.d(TAG, "onPause: Forwarding to controller")
        mediaController?.onPause()
    }

    /**
     * Commands the controller to skip to the next track.
     */
    fun onNext() {
        if (isDebugEnable) Log.d(TAG, "onNext: Forwarding to controller")
        mediaController?.onNext()
    }

    /**
     * Commands the controller to skip to the previous track.
     */
    fun onPrev() {
        if (isDebugEnable) Log.d(TAG, "onPrev: Forwarding to controller")
        mediaController?.onPrev()
    }

    /**
     * Commands the controller to seek to a specific time position.
     *
     * @param position The target time in milliseconds.
     */
    fun onSeek(position: Long) {
        if (isDebugEnable) Log.d(TAG, "onSeek: Forwarding to controller, position: $position")
        mediaController?.onSeek(position)
    }

    /**
     * Switches playback to a specific media item.
     *
     * @param track The [MediaItemModel] to play.
     */
    fun onChange(trackId: String, fromFavorite: Boolean = false) {
        if (isDebugEnable) Log.i(TAG, "onChange: Switching to track: ${trackId}, fromFavorite=$fromFavorite")
        mediaController?.onChange(trackId, fromFavorite)
    }

    /**
     * Checks if there is a next track available in the queue.
     * @return True if next track exists.
     */
    fun hasNext(): Boolean {
        val hasNext = mediaController?.hasNext() ?: false
        if (isDebugEnable) Log.v(TAG, "hasNext check: $hasNext")
        return hasNext
    }

    /**
     * Checks if there is a previous track available in the queue.
     * @return True if previous track exists.
     */
    fun hasPrevious(): Boolean {
        val hasPrev = mediaController?.hasPrevious() ?: false
        if (isDebugEnable) Log.v(TAG, "hasPrevious check: $hasPrev")
        return hasPrev
    }

    /**
     * Retrieves a media item model by its unique ID.
     *
     * @param mediaId The ID of the media.
     * @return Found [MediaItemModel] or null.
     */
    fun getMediaItemModel(mediaId: String): MediaItemModel? {
        return queueInformation?.findTrack(mediaId)
    }

    /**
     * Gets the currently active track in the player.
     * @return The current [MediaItemModel] or null.
     */
    fun getCurrentTrack(): MediaItemModel? {
        return queueInformation?.getCurrentTrack()
    }

    /**
     * Retrieves a list of media items for a specific album.
     *
     * @param albumId The ID of the album.
     * @return A list of [MediaItemModel].
     */
    fun getMediaList(albumId: String): List<MediaItemModel> {
        return queueInformation?.getMediaList(albumId) ?: listOf()
    }

    /**
     * Retrieves all available albums.
     * @return A list of album [MediaItemModel]s.
     */
    fun getAlbumList(): List<MediaItemModel> {
        return queueInformation?.getAlbumList() ?: listOf()
    }

    /**
     * Retrieves the list of media items marked as favorites.
     * @return A list of favorite [MediaItemModel]s.
     */
    fun getFavMediaList(): List<MediaItemModel> {
        return queueInformation?.getFavMediaList() ?: listOf()
    }

    /**
     * Starts the MediaService background operation.
     * @param context Context to trigger the service start.
     */
    fun startService(context: Context) {
        if (isDebugEnable) Log.d(TAG, "startService: Starting MediaService")
        context.startService(service)
    }

    /**
     * Builder class for configuring and creating a [MediaService] instance.
     */
    class Builder(val context: Context) {
        private var sessionActivity: Class<*>? = null

        /**
         * Sets the target activity to open when user interacts with media UI.
         * @param sessionActivity The Activity class.
         */
        fun setSessionActivity(sessionActivity: Class<*>): Builder {
            this.sessionActivity = sessionActivity
            return this
        }

        private var customSessionManager: SessionManager? = null
        fun setSessionManager(sessionManager: SessionManager): Builder {
            this.customSessionManager = sessionManager
            return this
        }

        private var customPlayerController: PlayerController? = null
        fun setPlayerController(playerController: PlayerController): Builder {
            this.customPlayerController = playerController
            return this
        }

        private var customQueueManager: QueueManager? = null
        fun setQueueManager(queueManager: QueueManager): Builder {
            this.customQueueManager = queueManager
            return this
        }

        private var customDataSource: DataSource? = null
        fun setDataSource(dataSource: DataSource): Builder {
            this.customDataSource = dataSource
            return this
        }


        private var customNotificationEngine: NotificationEngine? = null
        fun setNotificationEngine(notificationEngine: NotificationEngine): Builder {
            this.customNotificationEngine = notificationEngine
            return this
        }

        private var allMediaTitle: Int? = null
        fun setAllMediaTitle(@StringRes title: Int): Builder {
            allMediaTitle = title
            return this
        }

        private var favoriteTitle: Int? = null
        fun setFavoriteTitle(@StringRes title: Int): Builder {
            favoriteTitle = title
            return this
        }

        private var allMediaIcon: Int? = null
        fun setAllMediaIcon(@DrawableRes icon: Int): Builder {
            allMediaIcon = icon
            return this
        }

        private var favoriteIcon: Int? = null
        fun setFavoriteIcon(@DrawableRes icon: Int): Builder {
            favoriteIcon = icon
            return this
        }

        /**
         * Builds the [MediaService] with the provided configurations.
         * It stops any existing service instance before starting a new one.
         * @return Initialized [MediaService] instance.
         */
        fun build(): MediaService {
            if (isDebugEnable) Log.i(TAG, "build: Starting MediaService build process")
            val mediaService = MediaService()

            customSessionManager?.let { mediaService.customSessionManager = it }
            customPlayerController?.let { mediaService.customPlayerController = it }
            customQueueManager?.let { mediaService.customQueueManager = it }
            customDataSource?.let { mediaService.customDataSource = it }
            customNotificationEngine?.let { mediaService.customNotificationEngine = it }
            mediaService.favoriteTitle = context.getString( favoriteTitle ?: R.string.favorite)
            mediaService.allMediaTitle = context.getString( allMediaTitle ?: R.string.all_media)
            mediaService.favoriteIcon = ResourceUtils.getUriToResource(context, favoriteIcon ?: R.drawable.ic_recent)
            mediaService.allMediaIcon = ResourceUtils.getUriToResource(context, allMediaIcon ?: R.drawable.ic_recent)

            mediaService.service =
                Intent(context.applicationContext, MediaPlayerService::class.java)

            sessionActivity?.let {
                if (isDebugEnable) Log.d(TAG, "build: Setting SessionActivity to ${it.simpleName}")
                mediaService.sessionActivity = Intent(context.applicationContext, it).apply {
                    putExtra(
                        MediaConstants.SESSION_ACTIVITY_EXTRA_KEY,
                        MediaConstants.SESSION_ACTIVITY_EXTRA_VALUE
                    )
                }
            }

            instance?.service?.let {
                if (isDebugEnable) Log.w(
                    TAG,
                    "build: An existing instance was found. Stopping old service."
                )
                try {
                    context.stopService(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            instance = mediaService
            if (isDebugEnable) Log.i(TAG, "build: MediaService build completed successfully.")
            return mediaService
        }
    }
}