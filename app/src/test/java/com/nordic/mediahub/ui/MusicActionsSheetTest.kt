package com.nordic.mediahub.ui

import com.nordic.mediahub.data.DownloadState
import com.nordic.mediahub.data.DownloadStateEntry
import com.nordic.mediahub.data.NavidromeSong
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class MusicActionsSheetTest {
    private val song = NavidromeSong(id = "track", title = "歌曲", streamUrl = "https://music.example/stream")

    @Test fun missingSongsAndMissingAddressesNeverExposeAnExecutableDownload() {
        assertFalse(musicDownloadActionEnabled(null, null))
        for (url in listOf(null, "", "  ")) assertFalse(musicDownloadActionEnabled(song.copy(streamUrl = url), null))
    }

    @Test fun alreadyLocalOrActiveDownloadsCannotBeStartedAgain() {
        for (url in listOf("file:///music/song.mp3", "FILE:///music/song.mp3")) {
            assertFalse(musicDownloadActionEnabled(song.copy(streamUrl = url), null))
        }
        for (state in listOf(DownloadState.DOWNLOADING, DownloadState.DOWNLOADED)) {
            assertFalse(musicDownloadActionEnabled(song, DownloadStateEntry(state = state)))
        }
    }

    @Test fun remoteSongsAndFailedDownloadsCanUseTheOriginalRetryCallback() {
        assertTrue(musicDownloadActionEnabled(song, null))
        assertTrue(musicDownloadActionEnabled(song, DownloadStateEntry(errorMessage = "下载失败")))
    }

    @Test fun equalizerFrequenciesKeepUnitsAndDoNotTruncateFractionalKilohertz() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("60 Hz", equalizerFrequencyLabel(60_000))
            assertEquals("910 Hz", equalizerFrequencyLabel(910_000))
            assertEquals("3.6 kHz", equalizerFrequencyLabel(3_600_000))
            assertEquals("14.0 kHz", equalizerFrequencyLabel(14_000_000))
        } finally { Locale.setDefault(before) }
    }

    @Test fun unavailableFrequencyDoesNotRenderANegativeLabel() {
        assertEquals("0 Hz", equalizerFrequencyLabel(-1))
        assertEquals("0 Hz", equalizerFrequencyLabel(0))
    }
}
