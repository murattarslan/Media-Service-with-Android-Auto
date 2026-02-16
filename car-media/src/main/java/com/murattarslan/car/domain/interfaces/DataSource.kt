package com.murattarslan.car.domain.interfaces

import com.murattarslan.car.domain.models.MediaItemModel
import kotlinx.coroutines.flow.StateFlow

interface DataSource {

    val mediaState: StateFlow<List<MediaItemModel>>
    fun fetchMediaItems()
    fun favorite(mediaId: String, isFavorite: Boolean)
}