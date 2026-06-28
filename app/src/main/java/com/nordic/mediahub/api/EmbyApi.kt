package com.nordic.mediahub.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class EmbyAuthenticateRequest(
    @SerializedName("Username")
    val username: String,
    @SerializedName("Pw")
    val password: String
)

data class EmbyAuthenticateResponse(
    @SerializedName("User")
    val user: EmbyUserDto,
    @SerializedName("AccessToken")
    val accessToken: String
)

data class EmbyUserDto(
    @SerializedName("Id")
    val id: String,
    @SerializedName("Name")
    val name: String? = null
)

data class EmbyItemsResponse(
    @SerializedName("Items")
    val items: List<EmbyItemDto> = emptyList(),
    @SerializedName("TotalRecordCount")
    val totalRecordCount: Int = 0
)

data class EmbyItemDto(
    @SerializedName("Id")
    val id: String,
    @SerializedName("Name")
    val name: String,
    @SerializedName("Type")
    val type: String? = null,
    @SerializedName("CollectionType")
    val collectionType: String? = null,
    @SerializedName("Overview")
    val overview: String? = null,
    @SerializedName("ProductionYear")
    val productionYear: Int? = null,
    @SerializedName("SeriesId")
    val seriesId: String? = null,
    @SerializedName("SeriesName")
    val seriesName: String? = null,
    @SerializedName("ParentIndexNumber")
    val parentIndexNumber: Int? = null,
    @SerializedName("IndexNumber")
    val indexNumber: Int? = null,
    @SerializedName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerializedName("CommunityRating")
    val communityRating: Float? = null,
    @SerializedName("ChildCount")
    val childCount: Int? = null,
    @SerializedName("ImageTags")
    val imageTags: Map<String, String>? = emptyMap(),
    @SerializedName("UserData")
    val userData: EmbyUserDataDto? = null
)

data class EmbyUserDataDto(
    @SerializedName("Played")
    val played: Boolean? = null,
    @SerializedName("PlaybackPositionTicks")
    val playbackPositionTicks: Long? = null,
    @SerializedName("LastPlayedDate")
    val lastPlayedDate: String? = null
)

interface EmbyApi {
    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Body request: EmbyAuthenticateRequest,
        @Header("X-Emby-Authorization") authorization: String = EMBY_CLIENT_AUTHORIZATION
    ): Response<EmbyAuthenticateResponse>

    @GET("Users")
    suspend fun getUsers(
        @Header("X-Emby-Token") token: String
    ): Response<List<EmbyUserDto>>

    @GET("Users/{userId}/Views")
    suspend fun getUserViews(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String
    ): Response<EmbyItemsResponse>

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String,
        @Query("ParentId") parentId: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series,Episode,Video",
        @Query("Fields") fields: String = "Overview,ProductionYear,SeriesId,SeriesName,ParentIndexNumber,IndexNumber,RunTimeTicks,ChildCount,ImageTags,CommunityRating,UserData",
        @Query("SortBy") sortBy: String = "DateCreated",
        @Query("SortOrder") sortOrder: String = "Descending",
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 50
    ): Response<EmbyItemsResponse>
}

private const val EMBY_CLIENT_AUTHORIZATION =
    "MediaBrowser Client=\"Nordic\", Device=\"Android\", DeviceId=\"nordic-android\", Version=\"1.0.0\""
