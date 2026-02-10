package com.murattarslan.car.service.core.queue

sealed class SortType {
    object TITLE : SortType()
    object ARTIST : SortType()
    object DURATION : SortType()
    object NONE : SortType()
}