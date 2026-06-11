package com.nordic.mediahub

data class Audiobook(
    val id: String,
    val title: String,
    val author: String,
    val progress: String,
    val coverUrl: String? = null
)
