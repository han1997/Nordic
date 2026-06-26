package com.nordic.mediahub.ui

fun formatDuration(durationSeconds: Int): String {
    val safeSeconds = durationSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = (safeSeconds % 60).toString().padStart(2, '0')
    return "$minutes:$seconds"
}

fun formatVideoDuration(durationSeconds: Int): String {
    val safeSeconds = durationSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
