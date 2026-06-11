package com.nordic.mediahub

data class Video(
    val id: String,
    val title: String,
    val genre: String,
    val duration: String,
    val posterUrl: String? = null
)
