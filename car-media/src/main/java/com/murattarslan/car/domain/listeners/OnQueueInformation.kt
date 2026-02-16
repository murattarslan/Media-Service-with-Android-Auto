package com.murattarslan.car.domain.listeners

import com.murattarslan.car.domain.models.MediaItemModel

interface OnQueueInformation {

    fun findTrack(mediaId: String): MediaItemModel?
    fun getCurrentTrack(): MediaItemModel?
    fun getMediaList(albumId: String): List<MediaItemModel>
    fun getAlbumList(): List<MediaItemModel>
    fun getFavMediaList(): List<MediaItemModel>

}