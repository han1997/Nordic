package com.nordic.mediahub.ui

import com.nordic.mediahub.data.NavidromeAlbumSort
import com.nordic.mediahub.data.NavidromeSong

internal enum class MusicSongSort {
    Default,
    Added,
    Title,
    Artist,
    Album,
    Duration
}

private const val HOME_SONG_PREVIEW_LIMIT = 12

internal fun musicHomePreviewSongs(songs: List<NavidromeSong>): List<NavidromeSong> {
    return songs.take(HOME_SONG_PREVIEW_LIMIT)
}

internal fun musicHomePlaybackQueue(songs: List<NavidromeSong>): List<NavidromeSong> {
    return songs
}

internal fun resolveMusicLibraryPageAfterConfigChange(currentPage: MusicLibraryPage): MusicLibraryPage {
    return when (currentPage) {
        MusicLibraryPage.Home,
        MusicLibraryPage.Albums,
        MusicLibraryPage.Songs,
        MusicLibraryPage.Artists,
        MusicLibraryPage.ArtistDetail,
        MusicLibraryPage.AlbumDetail,
        MusicLibraryPage.Search,
        MusicLibraryPage.Playlists,
        MusicLibraryPage.PlaylistDetail -> MusicLibraryPage.Home
    }
}

internal fun firstPlayableSongIndex(songs: List<NavidromeSong>): Int? {
    return songs.indexOfFirst { song -> !song.streamUrl.isNullOrBlank() }
        .takeIf { index -> index >= 0 }
}

internal fun musicAlbumDetailLoadErrorMessage(error: Throwable): String {
    return "获取专辑曲目失败: ${error.message ?: "未知错误"}"
}

internal fun musicArtistDetailLoadErrorMessage(error: Throwable): String {
    return "获取歌手专辑失败: ${error.message ?: "未知错误"}"
}

internal fun sortMusicSongs(
    songs: List<NavidromeSong>,
    sort: MusicSongSort
): List<NavidromeSong> {
    return when (sort) {
        MusicSongSort.Default -> songs
        MusicSongSort.Added -> songs.sortedWith(
            compareByDescending<NavidromeSong> { it.created.orEmpty() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        MusicSongSort.Title -> songs.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        MusicSongSort.Artist -> songs.sortedWith(
            compareBy<NavidromeSong, String>(String.CASE_INSENSITIVE_ORDER) { it.artist.orEmpty() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        MusicSongSort.Album -> songs.sortedWith(
            compareBy<NavidromeSong, String>(String.CASE_INSENSITIVE_ORDER) { it.album.orEmpty() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        MusicSongSort.Duration -> songs.sortedWith(
            compareBy<NavidromeSong> { it.duration }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
    }
}

internal fun NavidromeAlbumSort.displayLabel(): String {
    return when (this) {
        NavidromeAlbumSort.RecentlyAdded -> "最近添加"
        NavidromeAlbumSort.ReleaseYear -> "发行年份"
        NavidromeAlbumSort.Name -> "名称"
    }
}

internal fun MusicSongSort.displayLabel(): String {
    return when (this) {
        MusicSongSort.Default -> "默认"
        MusicSongSort.Added -> "最近添加"
        MusicSongSort.Title -> "标题"
        MusicSongSort.Artist -> "歌手"
        MusicSongSort.Album -> "专辑"
        MusicSongSort.Duration -> "时长"
    }
}

internal fun formatCacheAge(updatedAtMillis: Long?): String? {
    if (updatedAtMillis == null || updatedAtMillis <= 0L) return null

    val elapsedMillis = (System.currentTimeMillis() - updatedAtMillis).coerceAtLeast(0L)
    val elapsedMinutes = elapsedMillis / 60_000L
    val elapsedHours = elapsedMillis / 3_600_000L
    val elapsedDays = elapsedMillis / 86_400_000L

    return when {
        elapsedMinutes < 1L -> "刚刚更新"
        elapsedMinutes < 60L -> "${elapsedMinutes} 分钟前更新"
        elapsedHours < 24L -> "${elapsedHours} 小时前更新"
        else -> "${elapsedDays} 天前更新"
    }
}
