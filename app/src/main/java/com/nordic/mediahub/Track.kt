package com.nordic.mediahub

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val albumArt: String? = null
)
