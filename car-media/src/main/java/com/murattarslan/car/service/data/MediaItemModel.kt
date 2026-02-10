package com.murattarslan.car.service.data

import java.io.Serializable

data class MediaItemModel(
    val id: String,
    var parentId: String?,
    val title: String,
    val artist: String?,
    val description: String?,
    val imageUri: String,
    val mediaUri: String,
    var isFavorite: Boolean,
    var duration: Long = 0,
) : Serializable