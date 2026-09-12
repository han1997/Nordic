package com.nordic.mediahub.ui

import androidx.compose.ui.unit.dp
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.resolveNextVideoEpisode
import com.nordic.mediahub.data.resolveNextWebDavVideo
import com.nordic.mediahub.data.resolveVideoPlayerEpisodes
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

    private fun playableVideo(
        id: String,
        title: String,
        type: String = "Video",
        libraryId: String = "/dir/",
        isPlayed: Boolean = false,
        playbackPositionSeconds: Int = 0
    ): VideoItem {
        return video(
            id = id, title = title, type = type, libraryId = libraryId,
            isPlayed = isPlayed, playbackPositionSeconds = playbackPositionSeconds
        ).copy(streamUrl = "https://example.test/$id")
    }

    @Test
    fun resolveVideoDetailPlayTarget_playableItemPlaysItself() {
        val movie = playableVideo(id = "movie-1", title = "Movie", type = "Movie")
        assertEquals("movie-1", resolveVideoDetailPlayTarget(movie, emptyList())?.id)
    }

    @Test
    fun resolveVideoDetailPlayTarget_seriesPlaysNextUnwatchedEpisode() {
        val series = video(id = "series-1", title = "Show", type = "Series")
        val watched = playableVideo(id = "ep-1", title = "E1", type = "Episode", isPlayed = true)
        val resumed = playableVideo(id = "ep-2", title = "E2", type = "Episode", playbackPositionSeconds = 60)
        val fresh = playableVideo(id = "ep-3", title = "E3", type = "Episode")

        assertEquals("ep-2", resolveVideoDetailPlayTarget(series, listOf(watched, fresh, resumed))?.id)
    }

    @Test
    fun resolveVideoDetailPlayTarget_seriesFallsBackToFirstEpisode() {
        val series = video(id = "series-1", title = "Show", type = "Series")
        val ep1 = playableVideo(id = "ep-1", title = "E1", type = "Episode")
        val ep2 = playableVideo(id = "ep-2", title = "E2", type = "Episode")

        assertEquals("ep-1", resolveVideoDetailPlayTarget(series, listOf(ep2, ep1))?.id)
    }

    @Test
    fun resolveVideoDetailPlayTarget_seriesWithoutPlayableEpisodesIsNull() {
        val series = video(id = "series-1", title = "Show", type = "Series")
        val unplayable = video(id = "ep-1", title = "E1", type = "Episode")

        assertNull(resolveVideoDetailPlayTarget(series, listOf(unplayable)))
        assertNull(resolveVideoDetailPlayTarget(series, emptyList()))
    }

    @Test
    fun resolveNextWebDavVideo_returnsNextSiblingInNaturalTitleOrder() {
        val current = playableVideo(id = "/dir/b.mp4", title = "b.mp4")
        val catalog = listOf(
            playableVideo(id = "/dir/a.mp4", title = "a.mp4"),
            current,
            playableVideo(id = "/dir/c10.mp4", title = "c10.mp4"),
            playableVideo(id = "/dir/c2.mp4", title = "c2.mp4"),
            playableVideo(id = "/other/d.mp4", title = "d.mp4", libraryId = "/other/")
        )

        assertEquals("/dir/c2.mp4", resolveNextWebDavVideo(current, catalog)?.id)
    }

    @Test
    fun resolveNextWebDavVideo_returnsNullForLastSibling() {
        val current = playableVideo(id = "/dir/c.mp4", title = "c.mp4")
        val catalog = listOf(
            playableVideo(id = "/dir/a.mp4", title = "a.mp4"),
            current
        )

        assertNull(resolveNextWebDavVideo(current, catalog))
    }

    @Test
    fun resolveVideoPlayerEpisodes_includesWebDavSameDirectoryVideos() {
        val current = playableVideo(id = "/dir/b.mp4", title = "b.mp4")
        val catalog = listOf(
            playableVideo(id = "/dir/a.mp4", title = "a.mp4"),
            current,
            playableVideo(id = "/dir/c.mp4", title = "c.mp4"),
            playableVideo(id = "/other/d.mp4", title = "d.mp4", libraryId = "/other/")
        )

        val episodes = resolveVideoPlayerEpisodes(current, catalog)
        assertEquals(listOf("/dir/a.mp4", "/dir/b.mp4", "/dir/c.mp4"), episodes.map { it.id })
    }

    @Test
    fun shouldPlaySelectedVideoEpisode_acceptsWebDavVideoType() {
        val current = playableVideo(id = "/dir/a.mp4", title = "a.mp4")
        val next = playableVideo(id = "/dir/b.mp4", title = "b.mp4")

        assertTrue(shouldPlaySelectedVideoEpisode(current, next))
        assertFalse(shouldPlaySelectedVideoEpisode(current, current))
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
    fun mergeResumeItemsWithCatalog_prefersServerRowsAndFillsMissingFields() {
        val catalogRow = video(
            id = "ep-1", title = "Episode 1", type = "Episode"
        ).copy(
            libraryId = "lib-1",
            streamUrl = "http://emby.example/Videos/ep-1/stream",
            imageUrl = "http://emby.example/ep-1.jpg",
            chapters = listOf(com.nordic.mediahub.data.VideoChapterInfo("C1", 0))
        )
        val serverRow = video(
            id = "ep-1", title = "Episode 1", type = "Episode"
        ).copy(
            playbackPositionSeconds = 300,
            streamUrl = null,
            imageUrl = null,
            chapters = emptyList()
        )
        val serverOnlyRow = video(id = "ep-2", title = "Episode 2", type = "Episode")
            .copy(playbackPositionSeconds = 120)

        val merged = mergeResumeItemsWithCatalog(
            resumeItems = listOf(serverRow, serverOnlyRow),
            catalog = listOf(catalogRow)
        )

        assertEquals(2, merged.size)
        // Server row wins for progress; catalog fills playback-critical gaps.
        assertEquals(300, merged[0].playbackPositionSeconds)
        assertEquals(catalogRow.streamUrl, merged[0].streamUrl)
        assertEquals(catalogRow.imageUrl, merged[0].imageUrl)
        assertEquals(catalogRow.chapters, merged[0].chapters)
        // Owning library id falls back to the catalog row so lazy-list keys and
        // episode-context matching stay consistent with the browse grid.
        assertEquals("library-1", merged[0].libraryId)
        // Server-only rows pass through untouched.
        assertEquals(serverOnlyRow, merged[1])
    }

    @Test
    fun mergeResumeItemsWithCatalog_emptyResumeStaysEmpty() {
        val catalog = listOf(video(id = "ep-1", title = "Episode 1", type = "Episode"))

        assertTrue(mergeResumeItemsWithCatalog(emptyList(), catalog).isEmpty())
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
