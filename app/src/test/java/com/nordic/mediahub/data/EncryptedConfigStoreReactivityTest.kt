package com.nordic.mediahub.data

import android.content.SharedPreferences
import com.nordic.mediahub.ui.SettingsPage
import com.nordic.mediahub.ui.hiddenModulePages
import com.nordic.mediahub.ui.searchSettings
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class EncryptedConfigStoreReactivityTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(10)

    @Test
    fun separateWrappersSharePersistedDataButNotChangeListeners() {
        val data = mutableMapOf<String, Any?>()
        val reader = FakeSharedPreferences(data)
        val writer = FakeSharedPreferences(data)
        var notifications = 0
        reader.registerOnSharedPreferenceChangeListener(
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> notifications++ }
        )

        writer.edit().putBoolean("visible", false).commit()

        assertFalse(reader.getBoolean("visible", true))
        assertEquals(0, notifications)
    }

    @Test
    fun moduleTogglesReachExistingCollectorsAndSettingsWithoutRecreatingStores() = runBlocking {
        val fixture = Stores()
        val root = Observation(this, fixture.newStore().preferences)
        val settings = Observation(this, fixture.newStore().preferences)
        val writer = fixture.newStore()
        try {
            assertEquals(AppPreferences(), root.next())
            assertEquals(AppPreferences(), settings.next())
            val music = MediaDomain.MUSIC
            val audiobook = MediaDomain.AUDIOBOOK
            val video = MediaDomain.VIDEO
            val changes = listOf(
                Triple(music, false, listOf(audiobook, video)),
                Triple(audiobook, false, listOf(video)),
                Triple(music, true, listOf(music, video)),
                Triple(video, false, listOf(music)),
                Triple(audiobook, true, listOf(music, audiobook)),
                Triple(music, false, listOf(audiobook)),
                Triple(video, true, listOf(audiobook, video)),
                Triple(music, true, listOf(music, audiobook, video))
            )
            var expected = AppPreferences()
            for ((domain, visible, expectedDomains) in changes) {
                writer.updatePreferences { it.withMediaDomainVisible(domain, visible) }
                expected = expected.withMediaDomainVisible(domain, visible)
                val current = root.next()
                assertEquals(expected, current)
                assertEquals(expected, settings.next())
                assertEquals(expectedDomains, current.visibleMediaDomains())
                val expectedTabs = expectedDomains.map {
                    when (it) {
                        music -> 0
                        audiobook -> 1
                        else -> 2
                    }
                }
                for (requested in 0..2) {
                    val expectedTab = requested.takeIf { it in expectedTabs } ?: expectedTabs.first()
                    assertEquals(expectedTab, current.resolveVisibleMediaTab(requested))
                }
                val expectedHiddenPages = listOf(
                    music to SettingsPage.MUSIC,
                    audiobook to SettingsPage.AUDIOBOOK,
                    video to SettingsPage.VIDEO
                ).filter { it.first !in expectedDomains }.map { it.second }.toSet()
                assertEquals(expectedHiddenPages, hiddenModulePages(current))
                assertEquals(video !in expectedDomains, searchSettings("画中画", current).isEmpty())
                assertTrue(searchSettings("模块显示", current).any { it.id == "modules" })
            }
            assertEquals(1, fixture.opens.get())

            // Disposing one screen must not unregister another screen's listener.
            root.close()
            writer.updatePreferences { it.copy(showMusic = false) }
            val persisted = expected.copy(showMusic = false)
            assertEquals(persisted, settings.next())
            assertEquals(persisted, fixture.newStore(EncryptedPreferencesInstance()).preferences.first())
        } finally {
            root.close()
            settings.close()
        }
    }

    @Test
    fun preferenceChangesAndResetReachOtherStoresAndPlaybackProjections() = runBlocking {
        val fixture = Stores()
        val reader = fixture.newStore()
        val writer = fixture.newStore()
        val preferences = Observation(this, reader.preferences)
        val pip = Observation(this, reader.videoPipEnabled)
        val quality = Observation(this, reader.videoQualityMode)
        try {
            assertEquals(AppPreferences(), preferences.next())
            assertTrue(pip.next())
            assertEquals(VideoQualityMode.AUTO, quality.next())
            val changed = AppPreferences(
                theme = ThemeMode.LIGHT,
                showVideo = false,
                videoPip = false,
                videoQuality = VideoQualityMode.ORIGINAL
            )
            writer.updatePreferences { changed }
            assertEquals(changed, preferences.next())
            assertFalse(pip.next())
            assertEquals(VideoQualityMode.ORIGINAL, quality.next())

            writer.resetPreferences()
            assertEquals(AppPreferences(), preferences.next())
            assertTrue(pip.next())
            assertEquals(VideoQualityMode.AUTO, quality.next())
            assertEquals(1, fixture.opens.get())
        } finally {
            preferences.close()
            pip.close()
            quality.close()
        }
    }

    @Test
    fun rejectingLastVisibleModuleAndNoOpWritesPreservesTheLiveState() = runBlocking {
        val fixture = Stores()
        val reader = fixture.newStore()
        val writer = fixture.newStore()
        val preferences = Observation(this, reader.preferences)
        try {
            assertEquals(AppPreferences(), preferences.next())
            val musicOnly = AppPreferences(showAudiobook = false, showVideo = false)
            writer.updatePreferences { musicOnly }
            assertEquals(musicOnly, preferences.next())

            val failure = runCatching {
                writer.updatePreferences { it.copy(showMusic = false) }
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(musicOnly, fixture.newStore(EncryptedPreferencesInstance()).preferences.first())
            writer.updatePreferences { it }

            writer.updatePreferences { it.copy(showVideo = true) }
            assertEquals(musicOnly.copy(showVideo = true), preferences.next())
        } finally {
            preferences.close()
        }
    }

    @Test
    fun sourceChangesReachAnAlreadySubscribedStore() = runBlocking {
        val fixture = Stores()
        val reader = fixture.newStore()
        val writer = fixture.newStore()
        val sources = Observation(this, reader.sources)
        try {
            assertEquals(MediaSourceState(), sources.next())
            val music = MediaSource(id = "music", kind = MediaSourceKind.NAVIDROME, serverUrl = "https://music.example")
            val video = MediaSource(id = "video", kind = MediaSourceKind.EMBY, serverUrl = "https://video.example")
            val expected = MediaSourceState(
                sources = listOf(music, video), activeMusicId = music.id, activeVideoId = video.id
            )
            writer.updateSources { expected }
            assertEquals(expected, sources.next())
            writer.updateSources { it.select(MediaDomain.VIDEO, null) }
            assertEquals(expected.copy(activeVideoId = null), sources.next())
            assertEquals(1, fixture.opens.get())
        } finally {
            sources.close()
        }
    }

    private class Stores {
        private val data = mutableMapOf<String, Any?>(ENCRYPTED_PREFS_MIGRATED_KEY to true)
        private val instance = EncryptedPreferencesInstance()
        val opens = AtomicInteger()

        fun newStore(shared: EncryptedPreferencesInstance = instance) = EncryptedConfigStore(
            context = null,
            prefsProvider = {
                shared.getOrCreate {
                    opens.incrementAndGet()
                    // AndroidX wrappers share the file, not their listener registry.
                    FakeSharedPreferences(data)
                }
            },
            legacyDataStoreSnapshot = { emptyMap() },
            removeLegacyCredentialKeys = {}
        )
    }

    private class Observation<T>(scope: CoroutineScope, flow: Flow<T>) {
        private val values = Channel<T>(Channel.UNLIMITED)
        private val job = scope.launch { flow.collect { values.send(it) } }
        suspend fun next(): T = withTimeout(2_000) { values.receive() }
        suspend fun close() {
            job.cancelAndJoin()
            values.close()
        }
    }
}
