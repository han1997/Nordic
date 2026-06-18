package com.nordic.mediahub.ui

fun formatTrackDuration(durationSeconds: Int): String {
    val safeDuration = durationSeconds.coerceAtLeast(0)
    val minutes = safeDuration / 60
    val seconds = (safeDuration % 60).toString().padStart(2, '0')
    return "$minutes:$seconds"
}
