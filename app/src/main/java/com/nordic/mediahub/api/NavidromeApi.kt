package com.nordic.mediahub.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

data class SubsonicResponse(
    @SerializedName("subsonic-response")
    val response: SubsonicData
)

data class SubsonicData(
    val status: String,
    val version: String,
    val albumList2: AlbumList? = null,
    val album: NavidromeAlbumDetail? = null,
    val artists: ArtistsIndex? = null,
    @SerializedName("artist")
    val artistDetail: NavidromeArtistDetail? = null,
    val playlists: NavidromePlaylistList? = null,
    val playlist: NavidromePlaylistDetail? = null,
    val randomSongs: SongList? = null,
    val searchResult3: SearchResult3? = null,
    val lyrics: NavidromePlainLyrics? = null,
    val lyricsList: NavidromeLyricsList? = null,
    val error: SubsonicError? = null,
)

data class NavidromeArtistDetail(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val album: List<NavidromeAlbum>? = null
)

data class SearchResult3(
    val artist: List<NavidromeArtist> = emptyList(),
    val album: List<NavidromeAlbum> = emptyList(),
    val song: List<NavidromeSong> = emptyList()
)

data class SubsonicError(
    val code: Int,
    val message: String
)

data class AlbumList(
    val album: List<NavidromeAlbum> = emptyList()
)

data class SongList(
    val song: List<NavidromeSong> = emptyList()
)

data class NavidromePlaylistList(
    val playlist: List<NavidromePlaylist>? = null
)

data class ArtistsIndex(
    val index: List<ArtistIndex> = emptyList()
)

data class ArtistIndex(
    val name: String,
    val artist: List<NavidromeArtist> = emptyList()
)

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

data class NavidromeSong(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int = 0,
    val coverArt: String? = null,
    val streamUrl: String? = null,
    val created: String? = null
)

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
    val structuredLyrics: List<NavidromeStructuredLyrics> = emptyList()
)

data class NavidromeStructuredLyrics(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String? = null,
    val offset: Double? = null,
    val synced: Boolean = false,
    val line: List<NavidromeStructuredLyricLine> = emptyList()
)

data class NavidromeStructuredLyricLine(
    val start: Double? = null,
    val value: String = ""
)

interface NavidromeApi {
    @GET("rest/getAlbumList2.view")
    suspend fun getAlbumList2(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("type") type: String = "newest",
        @Query("size") size: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("fromYear") fromYear: Int? = null,
        @Query("toYear") toYear: Int? = null
    ): Response<SubsonicResponse>

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("id") albumId: String
    ): Response<SubsonicResponse>

    @GET("rest/getArtists.view")
    suspend fun getArtists(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json"
    ): Response<SubsonicResponse>

    @GET("rest/getArtist.view")
    suspend fun getArtist(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("id") artistId: String
    ): Response<SubsonicResponse>

    @GET("rest/getRandomSongs.view")
    suspend fun getRandomSongs(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("size") size: Int = 20
    ): Response<SubsonicResponse>

    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json"
    ): Response<SubsonicResponse>

    @GET("rest/getPlaylist.view")
    suspend fun getPlaylist(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("id") playlistId: String
    ): Response<SubsonicResponse>

    @GET("rest/getLyricsBySongId.view")
    suspend fun getLyricsBySongId(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("id") songId: String
    ): Response<SubsonicResponse>

    @GET("rest/getLyrics.view")
    suspend fun getLyrics(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("artist") artist: String,
        @Query("title") title: String
    ): Response<SubsonicResponse>

    @GET("rest/search3.view")
    suspend fun search3(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 10,
        @Query("albumCount") albumCount: Int = 10,
        @Query("songCount") songCount: Int = 20
    ): Response<SubsonicResponse>
}
