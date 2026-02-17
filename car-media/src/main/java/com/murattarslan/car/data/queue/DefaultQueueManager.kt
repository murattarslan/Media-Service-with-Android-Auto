package com.murattarslan.car.data.queue

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat.BrowserRoot
import com.murattarslan.car.core.MediaService
import com.murattarslan.car.domain.interfaces.DataSource
import com.murattarslan.car.domain.interfaces.QueueManager
import com.murattarslan.car.domain.listeners.OnQueueInformation
import com.murattarslan.car.domain.models.MediaItemModel
import com.murattarslan.car.domain.models.QueueState
import com.murattarslan.car.ui.constants.MediaConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultQueueManager(private val dataSource: DataSource, scope: CoroutineScope) :
    QueueManager {

    companion object {
        private const val TAG = "MediaService_DefaultQueueManager"
    }

    private val _queueState = MutableStateFlow(QueueState())
    override val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    private val allItems = mutableListOf<MediaItemModel>()
    private val favItems = mutableListOf<MediaItemModel>()
    private var currentTrackId = ""

    init {
        if (MediaService.isDebugEnable) Log.d(TAG, "init: Initializing DefaultQueueManager")
        MediaService.instance?.queueInformation = object : OnQueueInformation {
            override fun findTrack(mediaId: String): MediaItemModel? {
                return allItems.find { it.id == mediaId }
            }

            override fun getCurrentTrack(): MediaItemModel? {
                return getTrack(currentTrackId)
            }

            override fun getMediaList(albumId: String): List<MediaItemModel> {
                return allItems.filter { it.parentId == albumId }
            }

            override fun getAlbumList(): List<MediaItemModel> {
                return allItems.filter { it.parentId == null }
            }

            override fun getFavMediaList(): List<MediaItemModel> {
                return allItems.filter { it.isFavorite }
            }
        }
        scope.launch {
            dataSource.mediaState.collect { state ->
                allItems.clear()
                allItems.addAll(state.data)
                favItems.clear()
                favItems.addAll(
                    state.data.filter { track -> track.isFavorite }
                        .map { truck -> truck.copy(id = "${MediaConstants.FAV_PREFIX_KEY}${truck.id}") }
                )
            }
        }
        dataSource.fetchMediaItems()
    }

    // interface functions

    override fun createQueue(parentId: String, startIndex: Int) {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "createQueue: parentId=$parentId, startIndex=$startIndex")

        val baseList = allItems.filter { it.id == parentId }.toMutableList()

        if (MediaService.isDebugEnable)
            Log.i(TAG, "createQueue: Queue created. Size=${baseList.size}")
        _queueState.update { it.copy(queue = baseList, currentIndex = startIndex) }
    }

    override fun createQueue(trackId: String) {
        if (MediaService.isDebugEnable) Log.d(TAG, "createQueue: trackId=$trackId")
        if (trackId.startsWith(MediaConstants.FAV_PREFIX_KEY)) {
            val index =
                favItems.indexOfFirst { it.id == trackId }
            if (MediaService.isDebugEnable)
                Log.d(TAG, "createQueue: Favorite index detected as $index")
            if (index == -1) {
                if (MediaService.isDebugEnable)
                    Log.w(TAG, "createQueue: trackId not found in map")
            } else {
                _queueState.update { it.copy(queue = favItems, currentIndex = index) }
            }
        } else {
            val index = allItems.indexOfFirst { it.id == trackId }
            if (index == -1) {
                if (MediaService.isDebugEnable)
                    Log.w(TAG, "createQueue: trackId not found in map")
            } else {
                val track = allItems[index]
                var indexInCategory = allItems.filter { it.parentId == track.parentId }.indexOfFirst { it.id == trackId }
                if (indexInCategory == -1) indexInCategory = 0
                if (MediaService.isDebugEnable)
                    Log.d(TAG, "createQueue: Found in album=${track.parentId} at index=$index")
                _queueState.update { state ->
                    state.copy(
                        queue = allItems.filter { it.parentId == track.parentId },
                        currentIndex = indexInCategory
                    )
                }
            }
        }
    }

    override fun getTrack(id: String?): MediaItemModel? {
        if (id != null) currentTrackId = id
        val track = allItems.find { it.id == currentTrackId.removePrefix(MediaConstants.FAV_PREFIX_KEY) }
        if (MediaService.isDebugEnable)
            Log.d(TAG, "getTrack id=$id, found=${track != null}")
        return track
    }

    override fun markFavorite(id: String) {
        if (MediaService.isDebugEnable) Log.d(TAG, "markFavorite: toggling favorite for id=$id")
        getTrack(id.removePrefix(MediaConstants.FAV_PREFIX_KEY))?.let {
            dataSource.favorite(it.id, !it.isFavorite)
        }
    }

    override fun onGetRoot(): BrowserRoot {
        if (MediaService.isDebugEnable)
            Log.d(TAG, "onGetRoot: returning root=${MediaConstants.MEDIA_ID_ROOT}")
        return BrowserRoot(MediaConstants.MEDIA_ID_ROOT, null)
    }

    override fun onLoadChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem> {
        if (MediaService.isDebugEnable)
            Log.i(TAG, "onLoadChildren: Browsing parentId=$parentId")
        return when (parentId) {
            MediaConstants.MEDIA_ID_ROOT -> {
                mutableListOf<MediaBrowserCompat.MediaItem>()
                    .addFavoriteCategory()
                    .addAllMediaCategory()
            }

            MediaConstants.CATEGORY_FAVORITE -> {
                favItems.map { createMediaItem(it) }.toMutableList()
            }

            MediaConstants.CATEGORY_ALL -> {
                allItems.filter { it.parentId == null }
                    .map {
                        if (it.mediaUri.isEmpty())
                            createBrowsableItem(it)
                        else
                            createMediaItem(it)
                    }
                    .toMutableList()
            }

            else -> allItems.filter { it.parentId == parentId }
                .map {
                    if (it.mediaUri.isEmpty())
                        createBrowsableItem(it)
                    else
                        createMediaItem(it)
                }
                .toMutableList()
        }
    }

    // custom functions

    private fun MutableList<MediaBrowserCompat.MediaItem>.addFavoriteCategory()
            : MutableList<MediaBrowserCompat.MediaItem> {
        add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(MediaConstants.CATEGORY_FAVORITE)
                    .setTitle(MediaService.instance?.favoriteTitle ?: "Favoriler")
                    .setIconUri(MediaService.instance?.favoriteIcon)
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
        return this
    }

    private fun MutableList<MediaBrowserCompat.MediaItem>.addAllMediaCategory()
            : MutableList<MediaBrowserCompat.MediaItem> {
        add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(MediaConstants.CATEGORY_ALL)
                    .setTitle(MediaService.instance?.allMediaTitle ?: "Tümü")
                    .setIconUri(MediaService.instance?.allMediaIcon)
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
        return this
    }

    private fun createBrowsableItem(album: MediaItemModel): MediaBrowserCompat.MediaItem {
        val extras = Bundle()
        extras.putInt(
            androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(album.id)
            .setTitle(album.title)
            .setIconUri(album.imageUri.toUri())
            .setExtras(extras)
            .build()

        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun createMediaItem(track: MediaItemModel)
            : MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(track.id)
            .setTitle(track.title)
            .setSubtitle(track.artist)
            .setDescription(track.artist)
            .setIconUri(track.imageUri.toUri())
            .setMediaUri(track.mediaUri.toUri())
            .build()

        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

}
