package com.nordic.mediahub.api

import retrofit2.Response
import retrofit2.http.*

data class NavidromeAuthResponse(
    val id: String,
    val username: String,
    val token: String
)

data class NavidromeAlbum(
    val id: String,
    val name: String,
    val artist: String?,
    val coverArt: String?,
    val songCount: Int,
    val year: Int?
)

data class NavidromeSong(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Int,
    val coverArt: String?
)

data class NavidromeArtist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val coverArt: String?
)

interface NavidromeApi {
    @POST("auth/login")
    suspend fun login(
        @Body body: Map<String, String>
    ): Response<NavidromeAuthResponse>

    @GET("api/album")
    suspend fun getAlbums(
        @Query("_start") start: Int = 0,
        @Query("_end") end: Int = 20,
        @Query("_sort") sort: String = "recently_added",
        @Query("_order") order: String = "DESC"
    ): Response<List<NavidromeAlbum>>

    @GET("api/song")
    suspend fun getSongs(
        @Query("_start") start: Int = 0,
        @Query("_end") end: Int = 20
    ): Response<List<NavidromeSong>>

    @GET("api/artist")
    suspend fun getArtists(
        @Query("_start") start: Int = 0,
        @Query("_end") end: Int = 20
    ): Response<List<NavidromeArtist>>
}
