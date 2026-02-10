package com.murattarslan.car.domain

import com.murattarslan.car.service.data.MediaItemModel

interface OnQueueInformation {

    fun getMediaItemModel(mediaId: String): MediaItemModel?
    fun getCurrentTrack(): MediaItemModel?
    fun getMediaList(albumId: String): List<MediaItemModel>
    fun getAlbumList(): List<MediaItemModel>
    fun getFavMediaList(): List<MediaItemModel>

}