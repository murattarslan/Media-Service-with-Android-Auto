package com.murattarslan.car.service.core.queue

import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat.BrowserRoot
import androidx.media.utils.MediaConstants
import com.murattarslan.car.R
import com.murattarslan.car.domain.MediaService
import com.murattarslan.car.domain.OnQueueInformation
import com.murattarslan.car.service.data.Category
import com.murattarslan.car.service.data.MediaItemModel
import com.murattarslan.car.util.ResourceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultQueueManager(context: Context) : QueueManager {

    companion object {
        private const val TAG = "MediaService_DefaultQueueManager"
        const val CATEGORY_FAVORITE = "com.murattarslan.car.CATEGORY_FAVORITE"
        private const val CATEGORY_ALL = "com.murattarslan.car.CATEGORY_ALL"
        private const val ALBUM_ROOT = "com.murattarslan.car.ALBUM_ROOT"
        const val MEDIA_ID_ROOT = "com.murattarslan.car.MEDIA_ID_ROOT"
    }

    val categories = listOf(
        Category(
            CATEGORY_FAVORITE,
            context.getString(MediaService.favoriteTitle ?: R.string.favorite),
            ResourceUtils.getUriToResource(context, MediaService.favoriteIcon ?: R.drawable.ic_recent).toString()
        ),
        Category(
            CATEGORY_ALL,
            context.getString(MediaService.allMediaTitle ?: R.string.all_media),
            ResourceUtils.getUriToResource(context, MediaService.allMediaIcon ?: R.drawable.ic_audio).toString()
        )
    )

    private val _queueState = MutableStateFlow(QueueState())
    override val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    private val allItems = mutableListOf<MediaItemModel>()
    private val albumMap = mutableMapOf<String, MutableList<MediaItemModel>>()
    private val mediaIdToAlbumMap = mutableMapOf<String, Pair<String, Int>>()

    private var sortType: SortType = SortType.NONE

    init {
        if (MediaService.isDebugEnable) Log.d(TAG, "init: Initializing DefaultQueueManager")
        MediaService.queueInformation = object : OnQueueInformation {
            override fun getMediaItemModel(mediaId: String): MediaItemModel? {
                setMediaItems(MediaService.mediaList)
                return allItems.find { it.id == mediaId }
            }

            override fun getCurrentTrack(): MediaItemModel? {
                return getCurrent()
            }

            override fun getMediaList(albumId: String): List<MediaItemModel> {
                return albumMap[albumId]?.toList() ?: emptyList()
            }

            override fun getAlbumList(): List<MediaItemModel> {
                return albumMap[ALBUM_ROOT]?.toList() ?: emptyList()
            }

            override fun getFavMediaList(): List<MediaItemModel> {
                return allItems.filter { it.isFavorite }
            }
        }
    }

    override fun setMediaItems(items: List<MediaItemModel>) {
        if (MediaService.isDebugEnable) Log.d(TAG, "setMediaItems: Setting ${items.size} items")
        allItems.clear()
        allItems.addAll(items)
        buildAlbumMap()
    }

    private fun buildAlbumMap() {
        if (MediaService.isDebugEnable) Log.d(TAG, "buildAlbumMap: Processing album structures")
        albumMap.clear()
        mediaIdToAlbumMap.clear()
        allItems.groupBy { it.parentId }.forEach { (albumId, list) ->
            if (albumId != null) {
                if (MediaService.isDebugEnable) Log.v(TAG, "buildAlbumMap: Album found: $albumId with ${list.size} tracks")
                albumMap[albumId] = list.toMutableList()
                list.forEachIndexed { index, item ->
                    mediaIdToAlbumMap[item.id] = albumId to index
                }
            } else {
                if (MediaService.isDebugEnable) Log.v(TAG, "buildAlbumMap: Adding ${list.size} items to ALBUM_ROOT")
                albumMap[ALBUM_ROOT] = list.toMutableList()
            }
        }
    }

    override fun getChildren(parentId: String): List<MediaItemModel> {
        val children = albumMap[parentId]?.toList() ?: emptyList()
        if (MediaService.isDebugEnable) Log.d(TAG, "getChildren: parentId=$parentId, count=${children.size}")
        return children
    }

    override fun createQueue(parentId: String, startIndex: Int) {
        if (MediaService.isDebugEnable) Log.d(TAG, "createQueue: parentId=$parentId, startIndex=$startIndex")
        val baseList = if (parentId.startsWith("fav_")) {
            allItems.filter { it.isFavorite }.toMutableList()
        } else {
            albumMap[parentId]?.toMutableList() ?: run {
                if (MediaService.isDebugEnable) Log.e(TAG, "createQueue: parentId not found in albumMap")
                return
            }
        }

        val sorted = applySort(baseList)
        if (MediaService.isDebugEnable) Log.i(TAG, "createQueue: Queue created. Size=${sorted.size}, New Version=${_queueState.value.version + 1}")

        _queueState.value = _queueState.value.copy(
            queue = sorted,
            currentIndex = startIndex,
            version = _queueState.value.version + 1
        )
    }

    override fun createQueueFromMediaId(trackId: String) {
        if (MediaService.isDebugEnable) Log.d(TAG, "createQueueFromMediaId: trackId=$trackId")
        if (trackId.startsWith("fav_")){
            val index = allItems.filter { it.isFavorite }.indexOfFirst { it.id == trackId.removePrefix("fav_") }
            if (MediaService.isDebugEnable) Log.d(TAG, "createQueueFromMediaId: Favorite index detected as $index")
            createQueue(trackId, index)
        }
        else {
            val albumAndIndex = mediaIdToAlbumMap[trackId]
            if (albumAndIndex != null) {
                val albumId = albumAndIndex.first
                val startIndex = albumAndIndex.second
                if (MediaService.isDebugEnable) Log.d(TAG, "createQueueFromMediaId: Found in album=$albumId at index=$startIndex")
                createQueue(albumId, startIndex)
            } else {
                if (MediaService.isDebugEnable) Log.w(TAG, "createQueueFromMediaId: trackId not found in map")
            }
        }
    }

    override fun getQueue(): List<MediaItemModel> = _queueState.value.queue

    override fun getCurrent(id: String?): MediaItemModel? {
        return if (id != null) {
            val index = _queueState.value.queue.indexOfFirst { it.id == id }
            if (MediaService.isDebugEnable) Log.v(TAG, "getCurrent: id=$id found at index=$index")
            _queueState.value = _queueState.value.copy(currentIndex = index)
            _queueState.value.queue.getOrNull(index)
        } else {
            val current = _queueState.value.queue.getOrNull(_queueState.value.currentIndex)
            if (MediaService.isDebugEnable) Log.v(TAG, "getCurrent: returning current index=${_queueState.value.currentIndex}, title=${current?.title}")
            current
        }
    }

    override fun skipToNext(): MediaItemModel? {
        val state = _queueState.value
        val newIndex = when {
            state.repeatMode == RepeatMode.ONE -> state.currentIndex
            state.currentIndex + 1 < state.queue.size -> state.currentIndex + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> {
                if (MediaService.isDebugEnable) Log.d(TAG, "skipToNext: End of queue reached")
                return null
            }
        }
        if (MediaService.isDebugEnable) Log.d(TAG, "skipToNext: newIndex=$newIndex")
        _queueState.value = state.copy(currentIndex = newIndex)
        return getCurrent()
    }

    override fun skipToPrevious(): MediaItemModel? {
        val state = _queueState.value
        val newIndex = when {
            state.currentIndex - 1 >= 0 -> state.currentIndex - 1
            state.repeatMode == RepeatMode.ALL -> state.queue.lastIndex
            else -> {
                if (MediaService.isDebugEnable) Log.d(TAG, "skipToPrevious: Beginning of queue reached")
                return null
            }
        }
        if (MediaService.isDebugEnable) Log.d(TAG, "skipToPrevious: newIndex=$newIndex")
        _queueState.value = state.copy(currentIndex = newIndex)
        return getCurrent()
    }

    override fun setSortType(sortType: SortType) {
        if (MediaService.isDebugEnable) Log.d(TAG, "setSortType: $sortType")
        this.sortType = sortType
    }

    private fun applySort(list: MutableList<MediaItemModel>): List<MediaItemModel> {
        if (MediaService.isDebugEnable && sortType != SortType.NONE) Log.v(TAG, "applySort: Sorting by $sortType")
        return when (sortType) {
            SortType.TITLE -> list.sortedBy { it.title }
            SortType.ARTIST -> list.sortedBy { it.artist }
            SortType.DURATION -> list.sortedBy { it.duration }
            SortType.NONE -> list
        }
    }

    override fun setShuffle(enabled: Boolean) {
        if (MediaService.isDebugEnable) Log.d(TAG, "setShuffle: $enabled")
        val state = _queueState.value
        val newQueue = if (enabled) state.queue.shuffled() else state.queue

        _queueState.value = state.copy(
            queue = newQueue,
            isShuffleEnabled = enabled,
            version = _queueState.value.version + 1
        )
    }

    override fun setRepeatMode(mode: RepeatMode) {
        if (MediaService.isDebugEnable) Log.d(TAG, "setRepeatMode: $mode")
        _queueState.value = _queueState.value.copy(repeatMode = mode)
    }

    override fun markFavorite(id: String) {
        if (MediaService.isDebugEnable) Log.d(TAG, "markFavorite: toggling favorite for id=$id")
        val favorite = allItems.find { it.id == id }?.isFavorite == true
        _queueState.value.queue.find { it.id == id }?.isFavorite = favorite.not()
        _queueState.value = _queueState.value.copy(updatedAt = System.currentTimeMillis())
    }

    override fun onGetRoot(): BrowserRoot {
        if (MediaService.isDebugEnable) Log.d(TAG, "onGetRoot: returning root=$MEDIA_ID_ROOT")
        return BrowserRoot(MEDIA_ID_ROOT, null)
    }

    override fun onLoadChildren(parentId: String): MutableList<MediaBrowserCompat.MediaItem> {
        if (MediaService.isDebugEnable) Log.i(TAG, "onLoadChildren: Browsing parentId=$parentId")
        return when (parentId) {
            MEDIA_ID_ROOT -> getCategoryItems()
            CATEGORY_FAVORITE -> getAlbumItems(true)
            CATEGORY_ALL -> getAlbumItems(false)
            else -> getTrackItems(parentId)
        }
    }

    private fun getCategoryItems(): MutableList<MediaBrowserCompat.MediaItem> {
        if (MediaService.isDebugEnable) Log.d(TAG, "getCategoryItems: Creating root categories")
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
        categories.forEach { category ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(category.id)
                .setTitle(category.title)
                .setIconUri(category.iconUri.toUri())
                .build()
            mediaItems.add(
                MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
            )
        }
        return mediaItems
    }

    private fun getAlbumItems(isFavorite: Boolean): MutableList<MediaBrowserCompat.MediaItem> {
        if (MediaService.isDebugEnable) Log.d(TAG, "getAlbumItems: isFavorite=$isFavorite")
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
        val albums = if (isFavorite) {
            allItems.filter { it.isFavorite }
        } else {
            albumMap[ALBUM_ROOT]
        }

        if (MediaService.isDebugEnable) Log.v(TAG, "getAlbumItems: Found ${albums?.size ?: 0} items")
        albums?.forEach {
            if (isFavorite)
                mediaItems.add(createMediaItem(it, true))
            else
                mediaItems.add(createBrowsableItem(it))
        }
        return mediaItems
    }

    private fun getTrackItems(parentId: String): MutableList<MediaBrowserCompat.MediaItem> {
        if (MediaService.isDebugEnable) Log.d(TAG, "getTrackItems: Fetching tracks for parent=$parentId")
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
        val tracks = albumMap[parentId] ?: return mediaItems.also {
            if (MediaService.isDebugEnable) Log.w(TAG, "getTrackItems: No tracks found for $parentId")
        }
        tracks.forEach {
            mediaItems.add(createMediaItem(it))
        }
        return mediaItems
    }

    private fun createBrowsableItem(album: MediaItemModel): MediaBrowserCompat.MediaItem {
        val extras = Bundle()
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(album.id)
            .setTitle(album.title)
            .setIconUri(album.imageUri.toUri())
            .setExtras(extras)
            .build()

        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createMediaItem(track: MediaItemModel, isFavorite: Boolean = false): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(if (isFavorite) "fav_${track.id}" else track.id)
            .setTitle(track.title)
            .setSubtitle(track.artist)
            .setDescription(track.artist)
            .setIconUri(track.imageUri.toUri())
            .setMediaUri(track.mediaUri.toUri())
            .build()

        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    override fun clear() {
        if (MediaService.isDebugEnable) Log.d(TAG, "clear: Clearing all items and queue")
        allItems.clear()
        albumMap.clear()
        _queueState.value = QueueState()
    }
}
