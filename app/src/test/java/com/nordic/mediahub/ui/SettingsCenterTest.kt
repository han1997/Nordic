package com.nordic.mediahub.ui

import com.nordic.mediahub.data.AppPreferences
import com.nordic.mediahub.data.AppPreferencesCodec
import com.nordic.mediahub.data.MediaDomain
import com.nordic.mediahub.data.MusicDefaultView
import com.nordic.mediahub.data.ThemeMode
import com.nordic.mediahub.data.resolveVisibleMediaTab
import com.nordic.mediahub.data.visibleMediaDomains
import org.junit.Assert.*
import org.junit.Test

class SettingsCenterTest {
    @Test fun failedWebDavRequestsAreNotPresentedAsEmptyDirectories() {
        assertFalse(showWebDavEmptyState(false, "认证失败", 0))
        assertFalse(showWebDavEmptyState(true, null, 0))
        assertFalse(showWebDavEmptyState(false, null, 2))
        assertTrue(showWebDavEmptyState(false, null, 0))
    }
    @Test fun searchFindsDirectPreferencesWithoutIndexingCredentials() {
        assertEquals("subtitle", searchSettings("默认字幕").single().id)
        assertTrue(searchSettings("webdav").any { it.page == SettingsPage.SERVERS })
        assertTrue(searchSettings("a-secret-password").isEmpty())
        assertTrue(searchSettings("   ").isEmpty())
    }
    @Test fun searchCombinesKeywordsAndAllSettingsHaveUniqueIds() {
        assertTrue(searchSettings("视频 倍速").any { it.id == "video_speed" })
        assertEquals(SETTINGS_SEARCH_ENTRIES.size, SETTINGS_SEARCH_ENTRIES.map { it.id }.distinct().size)
        assertEquals(10, SETTINGS_HOME_PAGES.size)
    }
    @Test fun navigationGuardCanCancelAndReleaseDeferredNavigation() {
        val guard = SettingsNavigationGuard()
        var navigations = 0
        var deferred: (() -> Unit)? = null
        guard.handler = { deferred = it }
        guard.navigate { navigations++ }
        assertEquals(0, navigations)
        deferred?.invoke()
        assertEquals(1, navigations)
        guard.handler = null
        guard.navigate { navigations++ }
        assertEquals(2, navigations)
    }
    @Test fun preferencesRoundTripAndResetLeaveNoHiddenPlaybackOverrides() {
        val changed = AppPreferences(theme = ThemeMode.DARK, musicDefaultView = MusicDefaultView.LYRICS,
            videoSkipBack = 60, videoPip = false, audiobookSpeed = 1.5f)
        assertEquals(changed, AppPreferencesCodec.decode(AppPreferencesCodec.encode(changed)))
        val defaults = AppPreferencesCodec.decode(null)
        assertTrue(defaults.videoPip)
        assertEquals(10, defaults.videoSkipBack)
        assertEquals(30, defaults.audiobookSkipBack)
        assertEquals(ThemeMode.SYSTEM, defaults.theme)
    }
    @Test fun autoPlayPreferenceDefaultsOffAndSearchTargetsTheSameSetting() {
        assertFalse(AppPreferencesCodec.decode(null).videoAutoPlayNext)
        val legacy = AppPreferencesCodec.decode("""{"videoPip":false,"videoSpeed":1.5}""")
        assertFalse(legacy.videoAutoPlayNext)
        assertFalse(legacy.videoPip)
        assertEquals(1.5f, legacy.videoSpeed)
        val enabled = legacy.copy(videoAutoPlayNext = true)
        assertEquals(enabled, AppPreferencesCodec.decode(AppPreferencesCodec.encode(enabled)))
        assertFalse(AppPreferencesCodec.decode(AppPreferencesCodec.encode(enabled.copy(videoAutoPlayNext = false))).videoAutoPlayNext)
        assertEquals("video_auto_play_next", searchSettings("自动连播").single().id)
        assertEquals("video_auto_play_next", searchSettings("下一集").single().id)
        assertEquals(SettingsPage.VIDEO, searchSettings("WebDAV 连播").single().page)
    }

    @Test fun fileSizesAreHonestForUnknownAndZeroValues() {
        assertEquals("未知大小", formatMediaBytes(null))
        assertEquals("0 B", formatMediaBytes(0))
        assertTrue(formatMediaBytes(1024).contains("KB"))
    }

    @Test fun moduleVisibilityDefaultsAllVisibleAndRoundTrips() {
        val defaults = AppPreferencesCodec.decode(null)
        assertTrue(defaults.showMusic)
        assertTrue(defaults.showAudiobook)
        assertTrue(defaults.showVideo)
        val hidden = defaults.copy(showVideo = false)
        assertEquals(hidden, AppPreferencesCodec.decode(AppPreferencesCodec.encode(hidden)))
        assertFalse(AppPreferencesCodec.decode(AppPreferencesCodec.encode(hidden)).showVideo)
    }

    @Test fun allOffVisibilityRestoresEveryModuleVisible() {
        val allOff = AppPreferences(showMusic = false, showAudiobook = false, showVideo = false)
        val restored = AppPreferencesCodec.decode(AppPreferencesCodec.encode(allOff))
        assertTrue(restored.showMusic)
        assertTrue(restored.showAudiobook)
        assertTrue(restored.showVideo)
    }

    @Test fun visibleMediaDomainsFollowsStableOrder() {
        assertEquals(
            listOf(MediaDomain.MUSIC, MediaDomain.AUDIOBOOK, MediaDomain.VIDEO),
            AppPreferences().visibleMediaDomains()
        )
        assertEquals(
            listOf(MediaDomain.MUSIC, MediaDomain.VIDEO),
            AppPreferences(showAudiobook = false).visibleMediaDomains()
        )
        assertEquals(
            listOf(MediaDomain.VIDEO),
            AppPreferences(showMusic = false, showAudiobook = false).visibleMediaDomains()
        )
    }

    @Test fun resolveVisibleMediaTabFallsBackInMusicAudiobookVideoOrder() {
        assertEquals(0, AppPreferences().resolveVisibleMediaTab(0))
        assertEquals(2, AppPreferences().resolveVisibleMediaTab(2))
        // Hidden music falls back to audiobook.
        assertEquals(1, AppPreferences(showMusic = false).resolveVisibleMediaTab(0))
        // Hidden music + audiobook falls back to video.
        assertEquals(2, AppPreferences(showMusic = false, showAudiobook = false).resolveVisibleMediaTab(1))
        // Requested visible tab is preserved.
        assertEquals(2, AppPreferences(showMusic = false).resolveVisibleMediaTab(2))
    }

    @Test fun hiddenModulePagesAndSearchFilterFollowVisibility() {
        val hiddenVideo = AppPreferences(showVideo = false)
        assertTrue(SettingsPage.VIDEO in hiddenModulePages(hiddenVideo))
        assertTrue(SettingsPage.MUSIC !in hiddenModulePages(hiddenVideo))
        assertTrue(searchSettings("画中画", hiddenVideo).isEmpty())
        assertTrue(searchSettings("画中画").any { it.id == "video_pip" })
        assertTrue(searchSettings("模块显示").any { it.id == "modules" })
    }
}