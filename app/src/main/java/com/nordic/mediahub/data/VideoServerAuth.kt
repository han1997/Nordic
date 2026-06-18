package com.nordic.mediahub.data

internal fun normalizeVideoServerBaseUrl(serverUrl: String): String {
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

internal fun VideoServerConfig.normalizedBaseUrl(): String {
    return normalizeVideoServerBaseUrl(serverUrl)
}
