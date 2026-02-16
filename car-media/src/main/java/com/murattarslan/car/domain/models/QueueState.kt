package com.murattarslan.car.domain.models

data class QueueState(
    val queue: List<MediaItemModel> = emptyList(),
    val currentIndex: Int = -1
)