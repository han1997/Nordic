package com.nordic.mediahub.data

import androidx.compose.runtime.Stable
import com.google.gson.annotations.SerializedName

@Stable
data class NavidromeArtistDetail(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val album: List<NavidromeAlbum>? = null
)

data class NavidromePlaylistList(
    val playlist: List<NavidromePlaylist>? = null
)

@Stable
data class NavidromeAlbum(
    val id: String,
    val name: String,
    val artist: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val year: Int? = null
)

data class NavidromeAlbumDetail(
    val id: String,
    val name: String,
    val artist: String? = null,
    val coverArt: String? = null,
    val song: List<NavidromeSong>? = null
)

@Stable
data class NavidromePlaylist(
    val id: String,
    val name: String,
    val comment: String? = null,
    val owner: String? = null,
    @SerializedName("public")
    val isPublic: Boolean = false,
    val songCount: Int = 0,
    val duration: Int = 0,
    val created: String? = null,
    val changed: String? = null,
    val coverArt: String? = null
)

data class NavidromePlaylistDetail(
    val id: String,
    val name: String,
    val comment: String? = null,
    val owner: String? = null,
    @SerializedName("public")
    val isPublic: Boolean = false,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val entry: List<NavidromeSong>? = null
)

@Stable
data class NavidromeSong(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int = 0,
    val coverArt: String? = null,
    val streamUrl: String? = null,
    val created: String? = null,
    val starred: String? = null
)

@Stable
data class NavidromeArtist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    @Transient val initials: String = ""
)

data class NavidromePlainLyrics(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null
)

data class NavidromeLyricsList(
    val structuredLyrics: List<NavidromeStructuredLyrics>? = null
)

data class NavidromeStructuredLyrics(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String? = null,
    val offset: Double? = null,
    val synced: Boolean = false,
    val line: List<NavidromeStructuredLyricLine>? = null
)

data class NavidromeStructuredLyricLine(
    val start: Double? = null,
    val value: String? = null
)
