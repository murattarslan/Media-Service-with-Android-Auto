package com.murattarslan.car.domain.listeners

import com.murattarslan.car.domain.models.MediaItemModel

interface OnMediaStateListener {

    fun onPlayTrack(position: Long, lastPositionUpdateTime: Long, playbackSpeed: Float)
    fun onPauseTrack()
    fun onChangeTrack(track: MediaItemModel)
    fun onFavoriteTrack(track: MediaItemModel)

}