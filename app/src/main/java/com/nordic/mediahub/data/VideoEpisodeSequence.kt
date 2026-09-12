package com.nordic.mediahub.data

/** Source-scoped identity: remote item IDs are only unique inside one connection. */
internal data class VideoPlaybackIdentity(
    val sourceType: VideoServerType,
    val sourceId: String,
    val itemId: String
)

internal fun VideoItem.playbackIdentity() = VideoPlaybackIdentity(sourceType, sourceId, id)

/** The picker and manual/automatic Next all use exactly the same loaded sequence. */
internal fun resolveVideoPlayerEpisodes(current: VideoItem?, videos: List<VideoItem>): List<VideoItem> {
    if (current == null) return emptyList()
    val isFile = current.type.equals("Video", ignoreCase = true)
    if (!isFile && !current.type.equals("Episode", ignoreCase = true)) return emptyList()
    val related = videos.filter { candidate ->
        candidate.sourceType == current.sourceType && candidate.sourceId == current.sourceId &&
            candidate.libraryId == current.libraryId &&
            if (isFile) {
                candidate.type.equals("Video", ignoreCase = true) && !candidate.streamUrl.isNullOrBlank()
            } else {
                candidate.type.equals("Episode", ignoreCase = true) &&
                    if (!current.seriesId.isNullOrBlank() && !candidate.seriesId.isNullOrBlank()) {
                        candidate.seriesId == current.seriesId
                    } else {
                        !current.seriesName.isNullOrBlank() &&
                            candidate.seriesName.equals(current.seriesName, ignoreCase = true)
                    }
            }
    }
    // The live item wins stale catalog data and is also the anchor when it is missing from the list.
    val sequence = (listOf(current) + related).distinctBy { it.playbackIdentity() }
    return if (isFile) {
        sequence.sortedWith(Comparator { a, b ->
            compareNaturalNames(a.title, b.title).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        })
    } else {
        sequence.sortedWith(compareBy<VideoItem> { it.seasonNumber ?: Int.MAX_VALUE }
            .thenBy { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.title }.thenBy { it.id })
    }
}

/** No fetching, wrapping, or fallback to an unrelated item when there is no successor. */
internal fun resolveNextVideoEpisode(current: VideoItem, videos: List<VideoItem>): VideoItem? {
    val sequence = resolveVideoPlayerEpisodes(current, videos)
    val index = sequence.indexOfFirst { it.playbackIdentity() == current.playbackIdentity() }
    return if (index >= 0) sequence.getOrNull(index + 1) else null
}

internal fun resolveNextWebDavVideo(current: VideoItem, videos: List<VideoItem>): VideoItem? =
    if (current.type.equals("Video", ignoreCase = true)) resolveNextVideoEpisode(current, videos) else null
