package com.nordic.mediahub.ui

import androidx.compose.ui.unit.dp
import com.nordic.mediahub.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoScreenTest {
    @Test
    fun videoShelfCardSize_growsWithFontScaleAndCapsAt176dp() {
        assertEquals(132.dp, videoShelfCardSize(1f))
        assertEquals(198.dp.coerceAtMost(176.dp), videoShelfCardSize(1.5f))
        assertEquals(176.dp, videoShelfCardSize(2f))
        assertEquals(132.dp, videoShelfCardSize(0.5f))
        assertEquals(132.dp, videoShelfCardSize(Float.NaN))
    }

    @Test
    fun continueWatchingShelf_ordersByLastPlayedDateBeforeResumePositionFallback() {
        val oldNearFinished = video(
            id = "old-near-finished",
            title = "Old Near Finished",
            playbackPositionSeconds = 5_000,
            lastPlayedDate = "2026-06-20T10:00:00.0000000Z"
        )
        val recentStarted = video(
            id = "recent-started",
            title = "Recent Started",
            playbackPositionSeconds = 60,
            lastPlayedDate = "2026-06-28T10:00:00.0000000Z"
        )
        val undatedFarther = video(
            id = "undated-farther",
            title = "Undated Farther",
            playbackPositionSeconds = 4_000
        )
        val undatedEarlier = video(
            id = "undated-earlier",
            title = "Undated Earlier",
            playbackPositionSeconds = 600
        )
        val alreadyPlayed = video(
            id = "already-played",
            title = "Already Played",
            playbackPositionSeconds = 90,
            lastPlayedDate = "2026-06-29T10:00:00.0000000Z",
            isPlayed = true
        )
        val notStarted = video(
            id = "not-started",
            title = "Not Started",
            playbackPositionSeconds = 0,
            lastPlayedDate = "2026-06-29T10:00:00.0000000Z"
        )

        val shelf = continueWatchingShelf(
            videos = listOf(
                oldNearFinished,
                undatedEarlier,
                alreadyPlayed,
                recentStarted,
                undatedFarther,
                notStarted
            ),
            limit = 3
        )

        assertEquals(
            listOf("recent-started", "old-near-finished", "undated-farther"),
            shelf.map { it.id }
        )
    }

    @Test
    fun continueWatchingShelf_excludesResumePositionsAtOrBeyondKnownDuration() {
        val resumable = video(
            id = "resumable",
            title = "Resumable",
            playbackPositionSeconds = 60,
            durationSeconds = 120
        )
        val atDuration = video(
            id = "at-duration",
            title = "At Duration",
            playbackPositionSeconds = 120,
            durationSeconds = 120
        )
        val beyondDuration = video(
            id = "beyond-duration",
            title = "Beyond Duration",
            playbackPositionSeconds = 150,
            durationSeconds = 120
        )

        val shelf = continueWatchingShelf(
            videos = listOf(atDuration, beyondDuration, resumable)
        )

        assertEquals(listOf("resumable"), shelf.map { it.id })
    }

    @Test
    fun continueWatchingShelf_keepsResumePositionsWhenDurationUnknown() {
        val unknownDuration = video(
            id = "unknown-duration",
            title = "Unknown Duration",
            playbackPositionSeconds = 150,
            durationSeconds = 0
        )

        val shelf = continueWatchingShelf(listOf(unknownDuration))

        assertEquals(listOf("unknown-duration"), shelf.map { it.id })
    }

    @Test
    fun continueWatchingShelf_keepsEpisodesAsResumeEntries() {
        val episode = video(
            id = "episode-1",
            title = "Episode One",
            type = "Episode",
            playbackPositionSeconds = 60,
            durationSeconds = 1200
        )

        val shelf = continueWatchingShelf(listOf(episode))

        assertEquals(listOf("episode-1"), shelf.map { it.id })
    }

    @Test
    fun browseCatalogVideos_excludesEpisodes() {
        val movie = video(id = "movie-1", title = "Movie One", type = "Movie")
        val series = video(id = "series-1", title = "Series One", type = "Series")
        val episode = video(id = "episode-1", title = "Episode One", type = "Episode")
        val standaloneVideo = video(id = "video-1", title = "Video One", type = "Video")

        val catalog = browseCatalogVideos(listOf(movie, episode, series, standaloneVideo))

        assertEquals(listOf("movie-1", "series-1", "video-1"), catalog.map { it.id })
    }

    @Test
    fun visibleBrowseVideos_appliesSearchAndFilterWithoutEpisodes() {
        val series = video(id = "series-1", title = "Nordic Show", type = "Series")
        val matchingEpisode = video(
            id = "episode-1",
            title = "The Pilot",
            type = "Episode",
            seriesName = "Nordic Show"
        )
        val otherSeries = video(id = "series-2", title = "Other Show", type = "Series")

        val visible = visibleBrowseVideos(
            videos = listOf(series, matchingEpisode, otherSeries),
            searchQuery = "Nordic",
            selectedTypeFilter = VideoTypeFilter.All
        )

        assertEquals(listOf("series-1"), visible.map { it.id })
    }

    @Test
    fun visibleVideoTypeFilters_neverIncludesEpisodeFilter() {
        val filters = visibleVideoTypeFilters(
            listOf(
                video(id = "movie-1", title = "Movie One", type = "Movie"),
                video(id = "episode-1", title = "Episode One", type = "Episode")
            )
        )

        assertFalse(filters.contains(VideoTypeFilter.Episodes))
        assertTrue(filters.contains(VideoTypeFilter.All))
        assertTrue(filters.contains(VideoTypeFilter.Movies))
    }

    @Test
    fun topRatedVideoShelf_excludesEpisodes() {
        val movie = video(id = "movie-1", title = "Movie One", communityRating = 8.5f)
        val episode = video(
            id = "episode-1",
            title = "Episode One",
            type = "Episode",
            communityRating = 9.5f
        )

        val shelf = topRatedVideoShelf(listOf(episode, movie))

        assertEquals(listOf("movie-1"), shelf.map { it.id })
    }

    @Test
    fun unplayedVideoShelf_excludesEpisodes() {
        val series = video(id = "series-1", title = "Series One", type = "Series")
        val episode = video(id = "episode-1", title = "Episode One", type = "Episode")

        val shelf = unplayedVideoShelf(listOf(episode, series))

        assertEquals(listOf("series-1"), shelf.map { it.id })
    }

    @Test
    fun resolveVideoSelectionAfterCatalogRefresh_keepsRefreshedItemWhenStillPresent() {
        val selected = video(
            id = "movie-1",
            title = "Old Title",
            playbackPositionSeconds = 30
        )
        val refreshed = video(
            id = "movie-1",
            title = "Updated Title",
            playbackPositionSeconds = 90
        )

        val resolved = resolveVideoSelectionAfterCatalogRefresh(
            selectedVideo = selected,
            selectedLibraryId = "library-1",
            videos = listOf(refreshed)
        )

        assertEquals(refreshed, resolved)
    }

    @Test
    fun resolveVideoSelectionAfterCatalogRefresh_clearsSelectionWhenLibraryChanges() {
        val selected = video(
            id = "movie-1",
            title = "Movie One",
            playbackPositionSeconds = 30,
            libraryId = "library-1"
        )
        val refreshed = video(
            id = "movie-1",
            title = "Movie One",
            playbackPositionSeconds = 30,
            libraryId = "library-2"
        )

        val resolved = resolveVideoSelectionAfterCatalogRefresh(
            selectedVideo = selected,
            selectedLibraryId = "library-2",
            videos = listOf(refreshed)
        )

        assertNull(resolved)
    }

    @Test
    fun resolveVideoSelectionAfterCatalogRefresh_clearsSelectionWhenItemDisappears() {
        val selected = video(
            id = "movie-1",
            title = "Movie One",
            playbackPositionSeconds = 30
        )
        val refreshed = video(
            id = "movie-2",
            title = "Movie Two",
            playbackPositionSeconds = 0
        )

        val resolved = resolveVideoSelectionAfterCatalogRefresh(
            selectedVideo = selected,
            selectedLibraryId = "library-1",
            videos = listOf(refreshed)
        )

        assertNull(resolved)
    }

    @Test
    fun resolveVideoTypeFilterAfterCatalogRefresh_keepsFilterWhenTypeStillPresent() {
        val resolved = resolveVideoTypeFilterAfterCatalogRefresh(
            selectedTypeFilter = VideoTypeFilter.Series,
            videos = listOf(video(id = "series-1", title = "Series One", type = "Series"))
        )

        assertEquals(VideoTypeFilter.Series, resolved)
    }

    @Test
    fun shouldShowVideoDetailInvalidationNotice_onlyWhenSelectionDisappears() {
        val selected = video(id = "movie-1", title = "Movie One")

        assertTrue(shouldShowVideoDetailInvalidationNotice(selected, null))
        assertFalse(shouldShowVideoDetailInvalidationNotice(selected, selected))
        assertFalse(shouldShowVideoDetailInvalidationNotice(null, null))
    }

    @Test
    fun resolveVideoTypeFilterAfterCatalogRefresh_resetsEpisodeFilterEvenWhenEpisodesExist() {
        val resolved = resolveVideoTypeFilterAfterCatalogRefresh(
            selectedTypeFilter = VideoTypeFilter.Episodes,
            videos = listOf(video(id = "episode-1", title = "Episode One", type = "Episode"))
        )

        assertEquals(VideoTypeFilter.All, resolved)
    }

    @Test
    fun resolveVideoTypeFilterAfterCatalogRefresh_resetsFilterWhenTypeIsGone() {
        val resolved = resolveVideoTypeFilterAfterCatalogRefresh(
            selectedTypeFilter = VideoTypeFilter.Episodes,
            videos = listOf(video(id = "movie-1", title = "Movie One", type = "Movie"))
        )

        assertEquals(VideoTypeFilter.All, resolved)
    }

    @Test
    fun resolveVideoTypeFilterAfterCatalogRefresh_keepsAllForEmptyCatalog() {
        val resolved = resolveVideoTypeFilterAfterCatalogRefresh(
            selectedTypeFilter = VideoTypeFilter.All,
            videos = emptyList()
        )

        assertEquals(VideoTypeFilter.All, resolved)
    }

    @Test
    fun resolveVideoSelectionAfterConfigChange_clearsSelectedVideo() {
        val selected = video(id = "movie-1", title = "Movie One")

        assertNull(resolveVideoSelectionAfterConfigChange(selected))
        assertNull(resolveVideoSelectionAfterConfigChange(null))
    }

    @Test
    fun resolveVideoTypeFilterAfterConfigChange_resetsEveryFilterToAll() {
        VideoTypeFilter.values().forEach { filter ->
            assertEquals(VideoTypeFilter.All, resolveVideoTypeFilterAfterConfigChange(filter))
        }
    }

    @Test
    fun shouldShowVideoConfigResetNotice_whenDetailSearchOrFilterWouldClear() {
        assertTrue(
            shouldShowVideoConfigResetNotice(
                previousConfigChanged = true,
                selectedVideo = video(id = "movie-1", title = "Movie One"),
                searchQuery = "",
                searchExpanded = false,
                selectedTypeFilter = VideoTypeFilter.All
            )
        )
        assertTrue(
            shouldShowVideoConfigResetNotice(
                previousConfigChanged = true,
                selectedVideo = null,
                searchQuery = "movie",
                searchExpanded = false,
                selectedTypeFilter = VideoTypeFilter.All
            )
        )
        assertTrue(
            shouldShowVideoConfigResetNotice(
                previousConfigChanged = true,
                selectedVideo = null,
                searchQuery = "",
                searchExpanded = true,
                selectedTypeFilter = VideoTypeFilter.All
            )
        )
        assertTrue(
            shouldShowVideoConfigResetNotice(
                previousConfigChanged = true,
                selectedVideo = null,
                searchQuery = "",
                searchExpanded = false,
                selectedTypeFilter = VideoTypeFilter.Series
            )
        )
    }

    @Test
    fun shouldShowVideoConfigResetNotice_ignoresFirstLaunchAndDefaultBrowseState() {
        assertFalse(
            shouldShowVideoConfigResetNotice(
                previousConfigChanged = false,
                selectedVideo = video(id = "movie-1", title = "Movie One"),
                searchQuery = "movie",
                searchExpanded = true,
                selectedTypeFilter = VideoTypeFilter.Series
            )
        )
        assertFalse(
            shouldShowVideoConfigResetNotice(
                previousConfigChanged = true,
                selectedVideo = null,
                searchQuery = "   ",
                searchExpanded = false,
                selectedTypeFilter = VideoTypeFilter.All
            )
        )
    }

    @Test
    fun shouldHandleVideoBrowserBack_whenSearchOrFilterIsActive() {
        assertTrue(
            shouldHandleVideoBrowserBack(
                searchExpanded = true,
                searchQuery = "",
                selectedTypeFilter = VideoTypeFilter.All
            )
        )
        assertTrue(
            shouldHandleVideoBrowserBack(
                searchExpanded = false,
                searchQuery = "movie",
                selectedTypeFilter = VideoTypeFilter.All
            )
        )
        assertTrue(
            shouldHandleVideoBrowserBack(
                searchExpanded = false,
                searchQuery = "",
                selectedTypeFilter = VideoTypeFilter.Episodes
            )
        )
    }

    @Test
    fun shouldHandleVideoBrowserBack_ignoresDefaultBrowseState() {
        assertEquals(
            false,
            shouldHandleVideoBrowserBack(
                searchExpanded = false,
                searchQuery = "   ",
                selectedTypeFilter = VideoTypeFilter.All
            )
        )
    }

    @Test
    fun relatedEpisodesFor_usesSeriesNameFallbackOnlyWhenSeriesIdIsMissing() {
        val series = video(
            id = "series-1",
            title = "Nordic Show",
            type = "Series"
        )
        val matchingId = video(
            id = "matching-id",
            title = "Matching Id",
            type = "Episode",
            seriesId = "series-1",
            seriesName = "Different Name"
        )
        val missingIdFallback = video(
            id = "missing-id-fallback",
            title = "Missing Id Fallback",
            type = "Episode",
            seriesName = "Nordic Show"
        )
        val blankIdFallback = video(
            id = "blank-id-fallback",
            title = "Blank Id Fallback",
            type = "Episode",
            seriesId = "",
            seriesName = "Nordic Show"
        )
        val differentIdSameName = video(
            id = "different-id-same-name",
            title = "Different Id Same Name",
            type = "Episode",
            seriesId = "series-2",
            seriesName = "Nordic Show"
        )
        val nonEpisode = video(
            id = "movie-same-name",
            title = "Nordic Show",
            type = "Movie",
            seriesName = "Nordic Show"
        )

        val related = listOf(
            differentIdSameName,
            nonEpisode,
            missingIdFallback,
            matchingId,
            blankIdFallback
        ).relatedEpisodesFor(series)

        assertEquals(
            listOf("blank-id-fallback", "matching-id", "missing-id-fallback"),
            related.map { it.id }
        )
    }

    @Test
    fun relatedEpisodesFor_sortsBySeasonEpisodeAndTitle() {
        val series = video(
            id = "series-1",
            title = "Nordic Show",
            type = "Series"
        )
        val episodes = listOf(
            episode(id = "s2e1", title = "D", seasonNumber = 2, episodeNumber = 1),
            episode(id = "s1e2", title = "B", seasonNumber = 1, episodeNumber = 2),
            episode(id = "unknown", title = "Z"),
            episode(id = "s1e1-c", title = "C", seasonNumber = 1, episodeNumber = 1),
            episode(id = "s1e1-a", title = "A", seasonNumber = 1, episodeNumber = 1)
        )

        val related = episodes.relatedEpisodesFor(series)

        assertEquals(
            listOf("s1e1-a", "s1e1-c", "s1e2", "s2e1", "unknown"),
            related.map { it.id }
        )
    }

    @Test
    fun videoMatchesSearch_matchesEpisodeSeriesName() {
        val episode = video(
            id = "episode-1",
            title = "The Arrival",
            type = "Episode",
            seriesName = "Together"
        )

        assertTrue(videoMatchesSearch(episode, "together"))
    }

    @Test
    fun videoMatchesSearch_matchesCompactSeasonEpisodeCodes() {
        val episode = video(
            id = "episode-1",
            title = "The Arrival",
            type = "Episode",
            seasonNumber = 1,
            episodeNumber = 2
        )

        assertTrue(videoMatchesSearch(episode, "S1E2"))
        assertTrue(videoMatchesSearch(episode, "s01e02"))
    }

    @Test
    fun videoMatchesSearch_keepsBlankQueryMatchAll() {
        val video = video(
            id = "movie-1",
            title = "Movie One"
        )

        assertTrue(videoMatchesSearch(video, "   "))
    }

    @Test
    fun resolveVideoDetailPlayAction_returnsResumeActionWhenPositionPresent() {
        val video = video(
            id = "movie-1",
            title = "Resumable Movie",
            playbackPositionSeconds = 90,
            durationSeconds = 1200
        )
        val action = resolveVideoDetailPlayAction(video)

        assertTrue(action.primaryLabel.contains("继续从"))
        assertTrue(action.primaryLabel.contains("1m"))
        assertEquals("从头播放", action.secondaryLabel)
        assertEquals(90, action.primaryResumeSeconds)
    }

    @Test
    fun resolveVideoDetailPlayAction_returnsPlayOnlyWhenNoResume() {
        val video = video(
            id = "movie-1",
            title = "Fresh Movie",
            playbackPositionSeconds = 0,
            durationSeconds = 1200
        )
        val action = resolveVideoDetailPlayAction(video)

        assertEquals("播放", action.primaryLabel)
        assertNull(action.secondaryLabel)
        assertEquals(0, action.primaryResumeSeconds)
    }

    @Test
    fun resolveVideoDetailPlayAction_returnsPlayOnlyWhenPlayed() {
        val video = video(
            id = "movie-1",
            title = "Already Watched",
            playbackPositionSeconds = 90,
            durationSeconds = 1200,
            isPlayed = true
        )
        val action = resolveVideoDetailPlayAction(video)

        assertEquals("播放", action.primaryLabel)
        assertNull(action.secondaryLabel)
        assertEquals(0, action.primaryResumeSeconds)
    }

    @Test
    fun resolveVideoDetailPlayAction_returnsResumeWhenDurationUnknown() {
        val video = video(
            id = "movie-1",
            title = "Unknown Duration",
            playbackPositionSeconds = 150,
            durationSeconds = 0
        )
        val action = resolveVideoDetailPlayAction(video)

        assertTrue(action.primaryLabel.contains("继续从"))
        assertTrue(action.primaryLabel.contains("2m"))
        assertEquals("从头播放", action.secondaryLabel)
        assertEquals(150, action.primaryResumeSeconds)
    }

    private fun episode(
        id: String,
        title: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): VideoItem {
        return video(
            id = id,
            title = title,
            type = "Episode",
            seriesId = "series-1",
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    @Test
    fun resolveNextVideoEpisode_returnsNextInOrderWithinSameSeries() {
        val s1e1 = video(
            id = "s1e1", title = "Pilot", type = "Episode",
            seriesId = "series-1", seriesName = "Show", seasonNumber = 1, episodeNumber = 1
        )
        val s1e2 = video(
            id = "s1e2", title = "Second", type = "Episode",
            seriesId = "series-1", seriesName = "Show", seasonNumber = 1, episodeNumber = 2
        )
        val s2e1 = video(
            id = "s2e1", title = "New Season", type = "Episode",
            seriesId = "series-1", seriesName = "Show", seasonNumber = 2, episodeNumber = 1
        )

        assertEquals("s1e2", resolveNextVideoEpisode(s1e1, listOf(s2e1, s1e1, s1e2))?.id)
        assertEquals("s2e1", resolveNextVideoEpisode(s1e2, listOf(s1e1, s2e1, s1e2))?.id)
    }

    @Test
    fun resolveNextVideoEpisode_returnsNullForLastEpisodeAndMovies() {
        val s1e1 = video(
            id = "s1e1", title = "Pilot", type = "Episode",
            seriesId = "series-1", seriesName = "Show", seasonNumber = 1, episodeNumber = 1
        )
        val movie = video(id = "movie-1", title = "Movie One", type = "Movie")

        assertNull(resolveNextVideoEpisode(s1e1, listOf(s1e1)))
        assertNull(resolveNextVideoEpisode(movie, listOf(s1e1, movie)))
    }

    @Test
    fun resolveNextVideoEpisode_matchesBySeriesNameWhenSeriesIdMissing() {
        val current = video(
            id = "e1", title = "One", type = "Episode",
            seriesName = "Show", seasonNumber = 1, episodeNumber = 1
        )
        val otherSeries = video(
            id = "other", title = "Other", type = "Episode",
            seriesName = "Other Show", seasonNumber = 1, episodeNumber = 1
        )
        val next = video(
            id = "e2", title = "Two", type = "Episode",
            seriesName = "Show", seasonNumber = 1, episodeNumber = 2
        )

        assertEquals("e2", resolveNextVideoEpisode(current, listOf(otherSeries, next))?.id)
    }

    @Test
    fun resolveNextVideoEpisode_ignoresOtherSeriesEpisodes() {
        val current = video(
            id = "e1", title = "One", type = "Episode",
            seriesId = "series-1", seriesName = "Show", seasonNumber = 1, episodeNumber = 1
        )
        val otherSeriesEpisode = video(
            id = "other", title = "Other", type = "Episode",
            seriesId = "series-2", seriesName = "Other Show", seasonNumber = 1, episodeNumber = 2
        )

        assertNull(resolveNextVideoEpisode(current, listOf(otherSeriesEpisode)))
    }

    @Test
    fun videoChapterIndexForPosition_resolvesCoveringChapterAndNullBeforeFirst() {
        val chapters = listOf(
            com.nordic.mediahub.data.VideoChapterInfo(name = "C1", startSeconds = 0),
            com.nordic.mediahub.data.VideoChapterInfo(name = "C2", startSeconds = 300),
            com.nordic.mediahub.data.VideoChapterInfo(name = "C3", startSeconds = 600)
        )

        assertEquals(0, videoChapterIndexForPosition(chapters, 0) ?: -1)
        assertEquals(0, videoChapterIndexForPosition(chapters, 299) ?: -1)
        assertEquals(1, videoChapterIndexForPosition(chapters, 300) ?: -1)
        assertEquals(2, videoChapterIndexForPosition(chapters, 1200) ?: -1)
        assertNull(videoChapterIndexForPosition(emptyList(), 100))
    }

    @Test
    fun resolveVideoChaptersSummary_showsCurrentChapterOrCount() {
        val chapters = listOf(
            com.nordic.mediahub.data.VideoChapterInfo(name = "开场", startSeconds = 0),
            com.nordic.mediahub.data.VideoChapterInfo(name = "中段", startSeconds = 300)
        )
        val withChapters = video(id = "v1", title = "V").copy(chapters = chapters)

        assertEquals("1/2 · 开场", resolveVideoChaptersSummary(withChapters, 120))
        assertEquals("2/2 · 中段", resolveVideoChaptersSummary(withChapters, 900))
        assertEquals("2 章", resolveVideoChaptersSummary(withChapters, -5))
        assertEquals("无章节", resolveVideoChaptersSummary(video(id = "v2", title = "V"), 100))
    }

    @Test
    fun shouldShowVideoSkipIntroButton_showsOnlyInsideRangeWithoutBlockingStates() {
        val intro = com.nordic.mediahub.data.VideoIntroRange(startSeconds = 30, endSeconds = 90)

        assertTrue(shouldShowVideoSkipIntroButton(intro, 45, panelOpen = false, gesturesLocked = false, hasPlaybackStatus = false))
        assertFalse(shouldShowVideoSkipIntroButton(intro, 29, panelOpen = false, gesturesLocked = false, hasPlaybackStatus = false))
        assertFalse(shouldShowVideoSkipIntroButton(intro, 90, panelOpen = false, gesturesLocked = false, hasPlaybackStatus = false))
        assertFalse(shouldShowVideoSkipIntroButton(intro, 45, panelOpen = true, gesturesLocked = false, hasPlaybackStatus = false))
        assertFalse(shouldShowVideoSkipIntroButton(intro, 45, panelOpen = false, gesturesLocked = true, hasPlaybackStatus = false))
        assertFalse(shouldShowVideoSkipIntroButton(intro, 45, panelOpen = false, gesturesLocked = false, hasPlaybackStatus = true))
        assertFalse(shouldShowVideoSkipIntroButton(null, 45, panelOpen = false, gesturesLocked = false, hasPlaybackStatus = false))
    }

    private fun video(
        id: String,
        title: String,
        playbackPositionSeconds: Int = 0,
        lastPlayedDate: String? = null,
        isPlayed: Boolean = false,
        durationSeconds: Int = 0,
        libraryId: String = "library-1",
        type: String = "Movie",
        communityRating: Float? = null,
        seriesId: String? = null,
        seriesName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): VideoItem {
        return VideoItem(
            id = id,
            libraryId = libraryId,
            title = title,
            type = type,
            communityRating = communityRating,
            durationSeconds = durationSeconds,
            playbackPositionSeconds = playbackPositionSeconds,
            lastPlayedDate = lastPlayedDate,
            isPlayed = isPlayed,
            seriesId = seriesId,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }
}
