package com.nordic.mediahub.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.compareNaturalNames

internal fun VideoItem.metaText(): String {
    return buildList {
        type.takeIf { it.isNotBlank() }?.let { add(it) }
        year?.let { add(it.toString()) }
        if (durationSeconds > 0) add(formatLongDuration(durationSeconds))
        if (playbackPositionSeconds > 0 && !isPlayed) add("看到 ${formatLongDuration(playbackPositionSeconds)}")
        if (isPlayed) add("已播放")
    }.joinToString("  /  ")
}

/**
 * Spotlight shelf poster-card width. Same fontScale growth policy as the
 * music home shelf (`musicShelfArtworkSize`): base size × clamped scale,
 * capped so large fonts never push a 2:3 poster past the viewport.
 */
internal fun videoShelfCardSize(fontScale: Float): Dp {
    val scale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(1f) ?: 1f
    return (132.dp * scale).coerceAtMost(176.dp)
}

internal data class VideoDetailPlayAction(
    val primaryLabel: String,
    val primaryResumeSeconds: Int,
    val secondaryLabel: String?
)

internal fun resolveVideoDetailPlayAction(video: VideoItem): VideoDetailPlayAction {
    val canResume = video.playbackPositionSeconds > 0 &&
        !video.isPlayed &&
        (video.durationSeconds == 0 || video.playbackPositionSeconds < video.durationSeconds)

    return if (canResume) {
        VideoDetailPlayAction(
            primaryLabel = "继续从 ${formatLongDuration(video.playbackPositionSeconds)} 播放",
            primaryResumeSeconds = video.playbackPositionSeconds,
            secondaryLabel = "从头播放"
        )
    } else {
        VideoDetailPlayAction(
            primaryLabel = "播放",
            primaryResumeSeconds = 0,
            secondaryLabel = null
        )
    }
}

/**
 * Resolves what the detail page's primary play button should do.
 *
 * Playable items (Movie/Episode/Video with a stream URL) play themselves.
 * A Series has no stream of its own: the button plays the next unwatched
 * episode, falling back to the first episode — matching mainstream clients.
 * Returns null when there is nothing playable (button stays disabled).
 */
internal fun resolveVideoDetailPlayTarget(
    video: VideoItem,
    relatedEpisodes: List<VideoItem>
): VideoItem? {
    if (!video.streamUrl.isNullOrBlank()) return video
    if (!video.type.equals("Series", ignoreCase = true)) return null
    val playable = relatedEpisodes.filter { !it.streamUrl.isNullOrBlank() }
    if (playable.isEmpty()) return null
    val ordered = playable.sortedWith(
        compareBy<VideoItem> { it.seasonNumber ?: Int.MAX_VALUE }
            .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
            .thenBy { it.title }
    )
    return ordered.firstOrNull { !it.isPlayed && it.playbackPositionSeconds > 0 }
        ?: ordered.firstOrNull { !it.isPlayed }
        ?: ordered.first()
}

internal enum class VideoEpisodeFilter(val label: String) {
    All("全部"),
    Unwatched("未看")
}

internal fun VideoItem.detailChips(): List<String> {
    return buildList {
        type.takeIf { it.isNotBlank() }?.let { add(it) }
        year?.let { add(it.toString()) }
        if (durationSeconds > 0) add(formatLongDuration(durationSeconds))
        if (playbackPositionSeconds > 0 && !isPlayed) add("续看 ${formatLongDuration(playbackPositionSeconds)}")
        if (isPlayed) add("已播放")
        communityRating?.takeIf { it > 0f }?.let { add("评分 ${"%.1f".format(it)}") }
    }.ifEmpty { listOf("视频") }
}

/**
 * Aligns the server Resume list with the loaded catalog: server rows win
 * (authoritative progress/order), but any field the Resume response omitted
 * (stream URL, artwork, chapters, owning library id) falls back to the
 * catalog row with the same id. Catalog rows absent from the server list are
 * dropped — the server list is the source of truth for what "continue
 * watching" contains.
 */
internal fun mergeResumeItemsWithCatalog(
    resumeItems: List<VideoItem>,
    catalog: List<VideoItem>
): List<VideoItem> {
    if (resumeItems.isEmpty()) return emptyList()
    val catalogById = catalog.associateBy { it.id }
    return resumeItems.map { resume ->
        val local = catalogById[resume.id] ?: return@map resume
        resume.copy(
            libraryId = resume.libraryId.ifBlank { local.libraryId },
            streamUrl = resume.streamUrl ?: local.streamUrl,
            imageUrl = resume.imageUrl ?: local.imageUrl,
            backdropImageUrl = resume.backdropImageUrl ?: local.backdropImageUrl,
            chapters = resume.chapters.ifEmpty { local.chapters },
            introRange = resume.introRange ?: local.introRange,
            mediaStreams = resume.mediaStreams.ifEmpty { local.mediaStreams }
        )
    }
}

internal fun browseCatalogVideos(videos: List<VideoItem>): List<VideoItem> {
    return videos.filterNot { video -> video.isEpisode() }
}

internal fun visibleBrowseVideos(
    videos: List<VideoItem>,
    searchQuery: String,
    selectedTypeFilter: VideoTypeFilter
): List<VideoItem> {
    return browseCatalogVideos(videos).filter { video ->
        selectedTypeFilter.matches(video) && video.matchesSearch(searchQuery)
    }
}

internal fun visibleVideoTypeFilters(videos: List<VideoItem>): List<VideoTypeFilter> {
    val browseVideos = browseCatalogVideos(videos)
    return VideoTypeFilter.values().filter { filter ->
        filter.isBrowseVisible && (filter == VideoTypeFilter.All || browseVideos.any(filter::matches))
    }
}

internal fun topRatedVideoShelf(videos: List<VideoItem>, limit: Int = 12): List<VideoItem> {
    return browseCatalogVideos(videos)
        .filter { video -> (video.communityRating ?: 0f) > 0f }
        .sortedByDescending { video -> video.communityRating ?: 0f }
        .take(limit)
}

internal fun unplayedVideoShelf(videos: List<VideoItem>, limit: Int = 12): List<VideoItem> {
    return browseCatalogVideos(videos)
        .filter { video -> !video.isPlayed && video.playbackPositionSeconds <= 0 }
        .take(limit)
}

internal fun resolveVideoSelectionAfterCatalogRefresh(
    selectedVideo: VideoItem?,
    selectedLibraryId: String?,
    videos: List<VideoItem>
): VideoItem? {
    val currentSelection = selectedVideo ?: return null
    val currentLibraryId = selectedLibraryId ?: return null
    if (currentSelection.libraryId != currentLibraryId) return null

    return videos.firstOrNull { video ->
        video.id == currentSelection.id && video.libraryId == currentLibraryId
    }
}

internal fun shouldShowVideoDetailInvalidationNotice(
    previousSelectedVideo: VideoItem?,
    refreshedSelectedVideo: VideoItem?
): Boolean {
    return previousSelectedVideo != null && refreshedSelectedVideo == null
}

internal fun resolveVideoTypeFilterAfterCatalogRefresh(
    selectedTypeFilter: VideoTypeFilter,
    videos: List<VideoItem>
): VideoTypeFilter {
    return selectedTypeFilter.takeIf { filter ->
        visibleVideoTypeFilters(videos).contains(filter)
    } ?: VideoTypeFilter.All
}

internal fun resolveVideoSelectionAfterConfigChange(selectedVideo: VideoItem?): VideoItem? {
    return when (selectedVideo) {
        null -> null
        else -> null
    }
}

internal fun resolveVideoTypeFilterAfterConfigChange(
    selectedTypeFilter: VideoTypeFilter
): VideoTypeFilter {
    return when (selectedTypeFilter) {
        VideoTypeFilter.All,
        VideoTypeFilter.Movies,
        VideoTypeFilter.Series,
        VideoTypeFilter.Episodes,
        VideoTypeFilter.Videos -> VideoTypeFilter.All
    }
}

internal fun shouldShowVideoConfigResetNotice(
    previousConfigChanged: Boolean,
    selectedVideo: VideoItem?,
    searchQuery: String,
    searchExpanded: Boolean,
    selectedTypeFilter: VideoTypeFilter
): Boolean {
    return previousConfigChanged && (
        selectedVideo != null ||
            searchQuery.isNotBlank() ||
            searchExpanded ||
            selectedTypeFilter != VideoTypeFilter.All
        )
}

internal fun shouldHandleVideoBrowserBack(
    searchExpanded: Boolean,
    searchQuery: String,
    selectedTypeFilter: VideoTypeFilter
): Boolean {
    return searchExpanded || searchQuery.isNotBlank() || selectedTypeFilter != VideoTypeFilter.All
}

private fun VideoItem.isEpisode(): Boolean {
    return type.equals("Episode", ignoreCase = true)
}

internal fun List<VideoItem>.relatedEpisodesFor(series: VideoItem): List<VideoItem> {
    if (!series.type.equals("Series", ignoreCase = true)) return emptyList()

    return filter { item ->
        item.type.equals("Episode", ignoreCase = true) &&
            (
                item.seriesId == series.id ||
                    (
                        item.seriesId.isNullOrBlank() &&
                            !item.seriesName.isNullOrBlank() &&
                            item.seriesName.equals(series.title, ignoreCase = true)
                    )
            )
    }.sortedWith(
        compareBy<VideoItem> { it.seasonNumber ?: Int.MAX_VALUE }
            .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
            .thenBy { it.title }
    )
}

internal fun VideoItem.episodeLabel(): String {
    val season = seasonNumber
    val episode = episodeNumber
    return when {
        season != null && episode != null -> "S$season E$episode"
        episode != null -> "第 $episode 集"
        season != null -> "第 $season 季"
        else -> type.ifBlank { "Episode" }
    }
}

/**
 * Resolves the next episode to play after [current], from a catalog of videos.
 * Episodes of the same series (matched by [VideoItem.seriesId], falling back to
 * [VideoItem.seriesName]) are ordered by (seasonNumber, episodeNumber, title);
 * the result is the item immediately following [current] in that order.
 * WebDAV items (type "Video") fall back to same-directory natural ordering.
 * Returns null when [current] has no successor.
 */
internal fun resolveNextVideoEpisode(
    current: VideoItem,
    videos: List<VideoItem>
): VideoItem? {
    if (current.type.equals("Video", ignoreCase = true)) {
        return resolveNextWebDavVideo(current, videos)
    }
    if (!current.type.equals("Episode", ignoreCase = true)) return null
    val sameSeries = resolveVideoPlayerEpisodes(current, videos).filter { it.id != current.id }
    val currentIndex = sameSeries.indexOfFirst { candidate ->
        (candidate.seasonNumber ?: Int.MAX_VALUE) == (current.seasonNumber ?: Int.MAX_VALUE) &&
            (candidate.episodeNumber ?: Int.MAX_VALUE) == (current.episodeNumber ?: Int.MAX_VALUE) &&
            candidate.title == current.title
    }
    if (currentIndex >= 0) return sameSeries.getOrNull(currentIndex + 1)
    // Current not found in the sorted list (e.g. missing episode numbers):
    // return the first episode strictly after the current one.
    val currentSeason = current.seasonNumber ?: Int.MAX_VALUE
    val currentEpisode = current.episodeNumber ?: Int.MAX_VALUE
    return sameSeries.firstOrNull { candidate ->
        val candidateSeason = candidate.seasonNumber ?: Int.MAX_VALUE
        val candidateEpisode = candidate.episodeNumber ?: Int.MAX_VALUE
        when {
            candidateSeason != currentSeason -> candidateSeason > currentSeason
            candidateEpisode != currentEpisode -> candidateEpisode > currentEpisode
            else -> candidate.title > current.title
        }
    }
}

/**
 * WebDAV continuation: videos in the same directory (libraryId holds the
 * parent path) ordered by natural filename order — matching the browse
 * screen's `compareNaturalNames` so "next" follows what the user sees.
 */
internal fun resolveNextWebDavVideo(current: VideoItem, videos: List<VideoItem>): VideoItem? {
    val siblings = videos.filter { candidate ->
        candidate.type.equals("Video", ignoreCase = true) &&
            candidate.libraryId == current.libraryId &&
            !candidate.streamUrl.isNullOrBlank()
    }.sortedWith(
        Comparator { a: VideoItem, b: VideoItem ->
            compareNaturalNames(a.title, b.title).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
    )
    val currentIndex = siblings.indexOfFirst { it.id == current.id }
    if (currentIndex >= 0) return siblings.getOrNull(currentIndex + 1)
    return siblings.lastOrNull { it.id != current.id }
}

/** Same-series context shared by the picker and Next episode; the active item wins stale catalog copies. */
internal fun resolveVideoPlayerEpisodes(current: VideoItem?, videos: List<VideoItem>): List<VideoItem> {
    if (current == null) return emptyList()
    if (current.type.equals("Video", ignoreCase = true)) {
        return videos.filter { candidate ->
            candidate.type.equals("Video", ignoreCase = true) &&
                candidate.libraryId == current.libraryId &&
                !candidate.streamUrl.isNullOrBlank()
        }.distinctBy { it.id }.sortedWith(
            Comparator { a: VideoItem, b: VideoItem ->
                compareNaturalNames(a.title, b.title).takeIf { it != 0 } ?: a.id.compareTo(b.id)
            }
        )
    }
    if (!current.type.equals("Episode", ignoreCase = true)) return emptyList()
    val related = videos.filter { candidate ->
        candidate.type.equals("Episode", ignoreCase = true) &&
            candidate.libraryId == current.libraryId &&
            if (!current.seriesId.isNullOrBlank() && !candidate.seriesId.isNullOrBlank()) {
                candidate.seriesId == current.seriesId
            } else {
                !current.seriesName.isNullOrBlank() &&
                    candidate.seriesName.equals(current.seriesName, ignoreCase = true)
            }
    }
    return (listOf(current) + related).distinctBy { it.id }.sortedWith(
        compareBy<VideoItem> { it.seasonNumber ?: Int.MAX_VALUE }
            .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
            .thenBy { it.title }
    )
}

internal fun videoPlayerSeasonLabel(season: Int?): String = when (season) {
    null -> "未分季"
    0 -> "特别篇"
    else -> "第 $season 季"
}

internal fun videoPlayerSeasons(episodes: List<VideoItem>): List<Int?> =
    episodes.map { it.seasonNumber }.distinct().sortedBy { it ?: Int.MAX_VALUE }

internal fun videoPlayerEpisodeStartIndex(episodes: List<VideoItem>, currentId: String): Int =
    episodes.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

internal fun shouldPlaySelectedVideoEpisode(current: VideoItem?, selected: VideoItem): Boolean =
    selected.id != current?.id && selected.streamUrl.isNullOrBlank().not() &&
        (selected.type.equals("Episode", ignoreCase = true) || selected.type.equals("Video", ignoreCase = true))

internal enum class VideoTypeFilter(val label: String) {
    All("全部"),
    Movies("电影"),
    Series("剧集"),
    Episodes("单集"),
    Videos("视频");

    fun matches(video: VideoItem): Boolean {
        return when (this) {
            All -> true
            Movies -> video.type.equals("Movie", ignoreCase = true)
            Series -> video.type.equals("Series", ignoreCase = true)
            Episodes -> video.type.equals("Episode", ignoreCase = true)
            Videos -> video.type.equals("Video", ignoreCase = true)
        }
    }

    val isBrowseVisible: Boolean
        get() = this != Episodes
}

internal fun videoMatchesSearch(video: VideoItem, query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return true

    val searchableTerms = buildList {
        add(video.title)
        add(video.overview)
        add(video.type)
        video.year?.let { year -> add(year.toString()) }
        video.seriesName?.takeIf { it.isNotBlank() }?.let { seriesName -> add(seriesName) }

        val seasonNumber = video.seasonNumber?.takeIf { it > 0 }
        val episodeNumber = video.episodeNumber?.takeIf { it > 0 }
        val seasonTokens = seasonNumber?.let { number -> videoNumberVariants(number) }.orEmpty()
        val episodeTokens = episodeNumber?.let { number -> videoNumberVariants(number) }.orEmpty()

        seasonTokens.forEach { season -> add("S$season") }
        episodeTokens.forEach { episode -> add("E$episode") }
        seasonNumber?.let { season -> add("Season $season") }
        episodeNumber?.let { episode -> add("Episode $episode") }

        seasonTokens.forEach { season ->
            episodeTokens.forEach { episode ->
                add("S${season}E${episode}")
                add("S$season E$episode")
            }
        }
        if (seasonNumber != null && episodeNumber != null) {
            add("Season $seasonNumber Episode $episodeNumber")
        }
    }

    return searchableTerms.any { term -> term.contains(normalizedQuery, ignoreCase = true) }
}

internal fun VideoItem.matchesSearch(query: String): Boolean {
    return videoMatchesSearch(this, query)
}

private fun videoNumberVariants(number: Int): List<String> {
    val raw = number.toString()
    val padded = raw.padStart(2, '0')
    return if (raw == padded) listOf(raw) else listOf(raw, padded)
}
