package com.murattarslan.car.util

import java.util.Locale
import java.util.concurrent.TimeUnit

object StringFormatter {

    fun formatTime(milliseconds: Long): String {
        if (milliseconds < 0) return "--:--"

        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}