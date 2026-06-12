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
    val randomSongs: SongList? = null,
    val lyrics: NavidromePlainLyrics? = null,
    val lyricsList: NavidromeLyricsList? = null,
    val error: SubsonicError? = null,
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
    val song: List<NavidromeSong> = emptyList()
)

data class NavidromeSong(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int = 0,
    val coverArt: String? = null,
    val streamUrl: String? = null
)

data class NavidromeArtist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArt: String? = null
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
        @Query("size") size: Int = 20
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
}
