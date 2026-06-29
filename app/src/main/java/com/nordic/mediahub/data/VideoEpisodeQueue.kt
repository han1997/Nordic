package com.nordic.mediahub.data

import androidx.compose.runtime.Stable

@Stable
data class VideoEpisodeQueue(
    val libraryId: String,
    val episodes: List<VideoEpisode>,
    val currentIndex: Int
) {
    val nextIndex: Int?
        get() = resolveNextVideoEpisodeIndex(currentIndex, episodes.size)

    val hasNext: Boolean
        get() = nextIndex != null

    val previousIndex: Int?
        get() = resolvePreviousVideoEpisodeIndex(currentIndex, episodes.size)

    val hasPrevious: Boolean
        get() = previousIndex != null

    fun nextEpisode(): VideoEpisode? = nextIndex?.let { index -> episodes.getOrNull(index) }

    fun previousEpisode(): VideoEpisode? = previousIndex?.let { index -> episodes.getOrNull(index) }

    fun advanceToNext(): VideoEpisodeQueue? = nextIndex?.let { index -> copy(currentIndex = index) }

    fun goToPrevious(): VideoEpisodeQueue? = previousIndex?.let { index -> copy(currentIndex = index) }
}

fun resolveNextVideoEpisodeIndex(currentIndex: Int, itemCount: Int): Int? {
    return if (currentIndex >= 0 && currentIndex < itemCount - 1) currentIndex + 1 else null
}

fun resolvePreviousVideoEpisodeIndex(currentIndex: Int, itemCount: Int): Int? {
    return if (currentIndex > 0 && currentIndex < itemCount) currentIndex - 1 else null
}

fun VideoEpisode.toPlaybackVideoItem(libraryId: String): VideoItem {
    return VideoItem(
        id = id,
        libraryId = libraryId,
        title = "S${seasonNumber}E${episodeNumber} $name",
        type = "Episode",
        overview = overview,
        durationSeconds = durationSeconds,
        imageUrl = imageUrl,
        progress = progress
    )
}
