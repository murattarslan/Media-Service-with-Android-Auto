package com.murattarslan.car.domain.models

data class DataState(
    val data: List<MediaItemModel> = listOf(),
    val isLoading: Boolean = false,
)
