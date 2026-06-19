package com.nordic.mediahub.data

internal fun normalizeAudiobookShelfBaseUrl(serverUrl: String): String {
    val trimmed = serverUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return ""

    return if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

internal fun AudiobookShelfConfig.normalizedBaseUrl(): String {
    return normalizeAudiobookShelfBaseUrl(serverUrl)
}

fun AudiobookShelfConfig.isReadyForAudiobookSync(): Boolean {
    return serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}
