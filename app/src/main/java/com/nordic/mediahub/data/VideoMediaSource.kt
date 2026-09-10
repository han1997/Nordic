package com.nordic.mediahub.data

/** Credential-free descriptor. WebDAV paths are prepared for this source at playback time. */
data class ExternalVideoSubtitle(
    val url: String,
    val mimeType: String,
    val label: String,
    val language: String? = null
)