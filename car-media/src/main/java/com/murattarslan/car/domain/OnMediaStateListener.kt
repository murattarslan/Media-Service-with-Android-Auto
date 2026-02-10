package com.murattarslan.car.domain

import com.murattarslan.car.service.data.MediaItemModel

interface OnMediaStateListener {

    fun onPlayTrack(){}
    fun onPauseTrack(){}
    fun onChangeTrack(track: MediaItemModel){}
    fun onSeek(position: Long){}
    fun onFavoriteTrack(trackId: String){}

}