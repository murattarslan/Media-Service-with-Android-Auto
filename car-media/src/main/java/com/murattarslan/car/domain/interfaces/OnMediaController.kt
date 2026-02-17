package com.murattarslan.car.domain.interfaces

import com.murattarslan.car.domain.models.MediaItemModel

interface OnMediaController {

    // controller functions
    fun onPlay()
    fun onPause()
    fun onSeek(position: Long)
    fun onFastForward()
    fun onRewind()
    fun onNext()
    fun onPrev()
    fun onChange(trackId: String, fromFavorite: Boolean = false)
    fun onChange(albumId: String, index: Int)
    fun onFavorite(track: MediaItemModel)

    // session functions
    fun isPlaying(): Boolean
    fun hasNext(): Boolean
    fun hasPrevious(): Boolean
    fun onStop()
}