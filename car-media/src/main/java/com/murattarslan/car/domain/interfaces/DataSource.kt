package com.murattarslan.car.domain.interfaces

import com.murattarslan.car.domain.models.DataState
import kotlinx.coroutines.flow.StateFlow

interface DataSource {

    val mediaState: StateFlow<DataState>
    fun fetchMediaItems()
    fun favorite(mediaId: String, isFavorite: Boolean)
}