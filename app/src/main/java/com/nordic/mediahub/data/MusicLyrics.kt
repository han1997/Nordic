package com.nordic.mediahub.data

import androidx.compose.runtime.Stable

@Stable
data class MusicLyrics(
    val lines: List<MusicLyricsLine> = emptyList(),
    val synced: Boolean = false,
    val syncedLines: List<MusicLyricsLine> = if (synced) lines else emptyList(),
    val plainLines: List<MusicLyricsLine> = if (!synced) lines else emptyList()
) {
    val hasSynced: Boolean
        get() = syncedLines.isNotEmpty()

    val hasPlain: Boolean
        get() = plainLines.isNotEmpty()
}

@Stable
data class MusicLyricsLine(
    val startMillis: Int? = null,
    val text: String
)

/** Keep all readable text; only timed duplicates can be safely removed. */
internal fun normalizeMusicLyrics(lines: List<MusicLyricsLine>, synced: Boolean): MusicLyrics? {
    val readable = lines.mapNotNull { line ->
        line.text.trim().takeIf { it.isNotBlank() }?.let { text ->
            line.copy(text = text, startMillis = line.startMillis?.coerceAtLeast(0))
        }
    }
    if (readable.isEmpty()) return null
    if (!synced || readable.none { it.startMillis != null }) {
        return MusicLyrics(lines = readable.map { it.copy(startMillis = null) }, synced = false)
    }
    val timed = readable.filter { it.startMillis != null }
        .distinct()
        .sortedBy { it.startMillis }
    return MusicLyrics(lines = timed + readable.filter { it.startMillis == null }, synced = true)
}

/** Several texts at the same timestamp form one highlighted, measured list item. */
internal fun MusicLyrics.displayCues(): List<MusicLyricsLine> {
    val normalized = normalizeMusicLyrics(lines, synced) ?: return emptyList()
    if (!normalized.synced) return normalized.lines
    val timed = normalized.lines.filter { it.startMillis != null }
        .groupBy { it.startMillis }
        .map { (start, group) -> MusicLyricsLine(start, group.joinToString("\n") { it.text }) }
    return timed + normalized.lines.filter { it.startMillis == null }
}

/** [cues] must have the sorted timed prefix produced by [displayCues]. */
internal fun resolveActiveMusicLyricIndex(cues: List<MusicLyricsLine>, positionMillis: Long): Int? {
    val position = positionMillis.coerceAtLeast(0)
    var low = 0
    var high = cues.size
    while (low < high) {
        val middle = low + (high - low) / 2
        val start = cues[middle].startMillis
        if (start != null && start.toLong() <= position) low = middle + 1 else high = middle
    }
    return (low - 1).takeIf { it >= 0 }
}
