package com.nordic.mediahub.api

import androidx.compose.runtime.Stable
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

@Stable
data class SubsonicResponse(
    @SerializedName("subsonic-response")
    val response: SubsonicData
)

@Stable
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
    val similarSongs: SongList? = null,
    val searchResult3: SearchResult3? = null,
    val lyrics: NavidromePlainLyrics? = null,
    val lyricsList: NavidromeLyricsList? = null,
    val starred2: Starred2? = null,
    val error: SubsonicError? = null,
)

@Stable
data class NavidromeArtistDetail(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val album: List<NavidromeAlbum>? = null
)

@Stable
data class SearchResult3(
    val artist: List<NavidromeArtist>? = null,
    val album: List<NavidromeAlbum>? = null,
    val song: List<NavidromeSong>? = null
)

@Stable
data class SubsonicError(
    val code: Int,
    val message: String
)

@Stable
data class AlbumList(
    val album: List<NavidromeAlbum>? = null
)

@Stable
data class SongList(
    val song: List<NavidromeSong>? = null
)

@Stable
data class NavidromePlaylistList(
    val playlist: List<NavidromePlaylist>? = null
)

@Stable
data class ArtistsIndex(
    val index: List<ArtistIndex>? = null
)

@Stable
data class ArtistIndex(
    val name: String,
    val artist: List<NavidromeArtist>? = null
)

@Stable
data class NavidromeAlbum(
    val id: String,
    val name: String,
    val artist: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val year: Int? = null,
    val starred: String? = null
)

@Stable
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

@Stable
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
    val starred: String? = null,
    @Transient val initials: String = ""
)

@Stable
data class NavidromePlainLyrics(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null
)

@Stable
data class NavidromeLyricsList(
    val structuredLyrics: List<NavidromeStructuredLyrics>? = null
)

@Stable
data class NavidromeStructuredLyrics(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String? = null,
    val offset: Double? = null,
    val synced: Boolean = false,
    val line: List<NavidromeStructuredLyricLine>? = null
)

@Stable
data class NavidromeStructuredLyricLine(
    val start: Double? = null,
    val value: String? = null
)

@Stable
data class Starred2(
    val artist: List<NavidromeArtist> = emptyList(),
    val album: List<NavidromeAlbum> = emptyList(),
    val song: List<NavidromeSong> = emptyList()
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

    @GET("rest/getSimilarSongs.view")
    suspend fun getSimilarSongs(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("id") id: String,
        @Query("count") count: Int = 50
    ): Response<SubsonicResponse>

    @GET("rest/scrobble.view")
    suspend fun scrobble(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("id") id: String,
        @Query("submission") submission: Boolean
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

    @GET("rest/star2.view")
    suspend fun star2(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): Response<SubsonicResponse>

    @GET("rest/unstar2.view")
    suspend fun unstar2(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null
    ): Response<SubsonicResponse>

    @GET("rest/getStarred2.view")
    suspend fun getStarred2(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json"
    ): Response<SubsonicResponse>

    @GET("rest/createPlaylist.view")
    suspend fun createPlaylist(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("name") name: String,
        @Query("songId") songId: List<String>? = null
    ): Response<SubsonicResponse>

    @GET("rest/updatePlaylist.view")
    suspend fun updatePlaylist(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("playlistId") playlistId: String,
        @Query("name") name: String? = null,
        @Query("songIdToAdd") songIdToAdd: List<String>? = null,
        @Query("songIndexToRemove") songIndexToRemove: List<Int>? = null
    ): Response<SubsonicResponse>

    @Streaming
    @GET("rest/download.view")
    suspend fun download(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("id") songId: String
    ): Response<ResponseBody>

    @GET("rest/deletePlaylist.view")
    suspend fun deletePlaylist(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "Nordic",
        @Query("f") format: String = "json",
        @Query("id") playlistId: String
    ): Response<SubsonicResponse>
}
