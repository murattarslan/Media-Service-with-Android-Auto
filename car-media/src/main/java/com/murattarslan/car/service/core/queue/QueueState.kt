package com.murattarslan.car.service.core.queue

import com.murattarslan.car.service.data.MediaItemModel


data class QueueState(
    val queue: List<MediaItemModel> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val version: Int = 0, // yapısal değişiklikte artar
    val updatedAt: Long = System.currentTimeMillis()
)

