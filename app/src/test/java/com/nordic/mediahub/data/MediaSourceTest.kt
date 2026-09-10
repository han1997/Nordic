package com.nordic.mediahub.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MediaSourceTest {
    private fun source(name: String = "家中", kind: MediaSourceKind = MediaSourceKind.EMBY) =
        MediaSource(name = name, kind = kind, serverUrl = "https://example.com/media", username = "user", password = "secret")

    @Test fun firstSourceActivatesButAdditionalSourceDoesNot() {
        val first = saveMediaSource(MediaSourceState(), source())
        val second = saveMediaSource(first.state, source("外出"))
        assertEquals(first.source.id, second.state.activeVideoId)
        assertEquals(2, second.state.sources.size)
    }
    @Test fun domainsSelectIndependently() {
        val video = saveMediaSource(MediaSourceState(), source())
        val music = saveMediaSource(video.state, source(kind = MediaSourceKind.NAVIDROME))
        assertEquals(video.source.id, music.state.activeVideoId)
        assertEquals(music.source.id, music.state.activeMusicId)
        assertNull(music.state.activeAudiobookId)
    }
    @Test fun passwordAndNameKeepIdentityButAccountChangesDoNot() {
        val first = saveMediaSource(MediaSourceState(), source())
        val renamed = saveMediaSource(first.state, first.source.copy(name = "改名", password = "new"))
        assertEquals(first.source.id, renamed.source.id)
        val changed = saveMediaSource(renamed.state, renamed.source.copy(username = "other"))
        assertNotEquals(first.source.id, changed.source.id)
        assertEquals(changed.source.id, changed.state.activeVideoId)
    }
    @Test fun removeActiveSourceDoesNotSilentlySelectAnotherAccount() {
        val first = saveMediaSource(MediaSourceState(), source())
        val second = saveMediaSource(first.state, source())
        assertNull(second.state.remove(first.source.id).activeVideoId)
        assertEquals(1, second.state.remove(first.source.id).sources.size)
    }
    @Test fun codecRoundTripsAndRejectsCorruptStorage() {
        val saved = saveMediaSource(MediaSourceState(), source()).state
        assertEquals(saved, MediaSourceCodec.decode(MediaSourceCodec.encode(saved)))
        assertThrows(IOException::class.java) { MediaSourceCodec.decode("not-json") }
        assertFalse(saved.sources.first().toString().contains("secret"))
    }
    @Test fun unsafeAddressesRequireExplicitConsent() {
        assertThrows(IllegalArgumentException::class.java) {
            saveMediaSource(MediaSourceState(), source().copy(serverUrl = "http://example.com"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            saveMediaSource(MediaSourceState(), source().copy(serverUrl = "https://user:pass@example.com"))
        }
        assertThrows(IllegalArgumentException::class.java) { normalizeWebDavDirectory("/video/../private") }
    }
    @Test fun legacyMigrationIsIdempotentAndPreservesHttpAndCredentials() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(EncryptedConfigKeys.NAVIDROME_URL, "http://music.local")
            .putString(EncryptedConfigKeys.NAVIDROME_USER, "me")
            .putString(EncryptedConfigKeys.NAVIDROME_PASS, "secret").commit()
        val first = migrateMediaSources(prefs)
        assertEquals(first, migrateMediaSources(prefs))
        val migrated = first.active(MediaDomain.MUSIC)!!
        assertTrue(migrated.allowInsecureHttp)
        assertEquals("secret", migrated.password)
        assertEquals(migrated.id, migrated.navidromeConfig().sourceId)
    }
    @Test fun storeRestoresSourcesAndSharedVideoPreferences() = runBlocking {
        val prefs = FakeSharedPreferences()
        val store = EncryptedConfigStore(null, { prefs }, { emptyMap() }, {})
        store.updateSources { saveMediaSource(it, source()).state }
        store.updatePreferences { it.copy(theme = ThemeMode.DARK, videoSpeed = 1.5f) }
        val restored = EncryptedConfigStore(null, { prefs }, { emptyMap() }, {})
        assertEquals(1, restored.sources.first().sources.size)
        assertEquals(ThemeMode.DARK, restored.preferences.first().theme)
        assertEquals(1.5f, restored.videoPlaybackSpeed.first())
        restored.saveVideoPipEnabled(false)
        assertFalse(restored.preferences.first().videoPip)
    }
    @Test fun preferencesAddDefaultsForMissingFieldsAndValidateRanges() {
        val prefs = AppPreferencesCodec.decode("{\"theme\":\"DARK\",\"videoSkipBack\":999}")
        assertEquals(ThemeMode.DARK, prefs.theme)
        assertEquals(30, prefs.audiobookSkipBack)
        assertEquals(10, prefs.videoSkipBack)
        assertEquals(TrackLanguage.OFF, prefs.subtitleLanguage)
        assertEquals(prefs, AppPreferencesCodec.decode(AppPreferencesCodec.encode(prefs)))
    }
}