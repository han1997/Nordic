package com.nordic.mediahub.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nordic.mediahub.data.DownloadState
import com.nordic.mediahub.data.DownloadStateEntry
import com.nordic.mediahub.data.NavidromeSong

internal fun musicDownloadActionEnabled(song: NavidromeSong?, download: DownloadStateEntry?): Boolean =
    !song?.streamUrl.isNullOrBlank() && song?.streamUrl?.startsWith("file:", ignoreCase = true) != true &&
        download?.state != DownloadState.DOWNLOADED && download?.state != DownloadState.DOWNLOADING

/** The caller owns source-scoped download state and actions; this layer never downloads. */
@Composable
internal fun MusicActionsSheet(
    song: NavidromeSong?,
    download: DownloadStateEntry?,
    colorScheme: ColorScheme,
    onDownloadSong: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val canDownload = musicDownloadActionEnabled(song, download)
    MediaPlayerSheet("音乐操作", colorScheme, onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SettingsRow("下载当前歌曲", subtitle = when {
                song == null -> "没有正在播放的歌曲"
                download?.state == DownloadState.DOWNLOADED -> "已下载，可在设置中的存储与下载页面管理"
                download?.state == DownloadState.DOWNLOADING -> "正在下载 ${(download.progress.coerceIn(0f, 1f) * 100).toInt()}%"
                song.streamUrl.isNullOrBlank() -> "当前歌曲没有可用下载地址"
                song.streamUrl.startsWith("file:", ignoreCase = true) -> "当前歌曲已在本机"
                else -> download?.errorMessage ?: "保存在此来源的本机下载目录"
            }, enabled = canDownload, onClick = {
                if (canDownload) { onDownloadSong(); onDismiss() }
            })
            if (song != null && download?.state == DownloadState.DOWNLOADING) {
                SettingsRow("取消下载", onClick = { onCancelDownload(); onDismiss() })
            }
        }
    }
}
