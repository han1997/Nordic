package com.nordic.mediahub.ui

import com.nordic.mediahub.data.AppPreferences
import com.nordic.mediahub.data.AppPreferencesCodec
import com.nordic.mediahub.data.MusicDefaultView
import com.nordic.mediahub.data.ThemeMode
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
        assertEquals(SettingsPage.SERVERS, searchSettings("webdav").single().page)
        assertTrue(searchSettings("a-secret-password").isEmpty())
        assertTrue(searchSettings("   ").isEmpty())
    }
    @Test fun searchCombinesKeywordsAndAllSettingsHaveUniqueIds() {
        assertTrue(searchSettings("视频 倍速").any { it.id == "video_speed" })
        assertEquals(SETTINGS_SEARCH_ENTRIES.size, SETTINGS_SEARCH_ENTRIES.map { it.id }.distinct().size)
        assertEquals(8, SETTINGS_HOME_PAGES.size)
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
    @Test fun fileSizesAreHonestForUnknownAndZeroValues() {
        assertEquals("未知大小", formatMediaBytes(null))
        assertEquals("0 B", formatMediaBytes(0))
        assertTrue(formatMediaBytes(1024).contains("KB"))
    }
}