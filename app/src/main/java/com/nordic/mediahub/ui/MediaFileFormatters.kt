package com.nordic.mediahub.ui

import java.util.Locale

internal fun formatMediaBytes(bytes: Long?): String {
    if (bytes == null) return "未知大小"
    if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
    var value = bytes.toDouble()
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}