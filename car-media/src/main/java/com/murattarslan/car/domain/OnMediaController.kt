package com.murattarslan.car.domain

import com.murattarslan.car.service.data.MediaItemModel

interface OnMediaController {

    fun isPlaying(): Boolean { return false }
    fun onPlay(){}
    fun onPause(){}
    fun hasNext(): Boolean{ return false}
    fun onNext(){}
    fun hasPrevious(): Boolean{ return false}
    fun onPrev(){}
    fun currentItem(): MediaItemModel? { return null }
    fun onChange(track: MediaItemModel){}
    fun onSeek(position: Long){}
    fun onFavorite(track: MediaItemModel){}

}