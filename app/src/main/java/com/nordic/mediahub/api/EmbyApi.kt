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
    @SerializedName("ParentId")
    val parentId: String? = null,
    @SerializedName("Type")
    val type: String? = null,
    @SerializedName("CollectionType")
    val collectionType: String? = null,
    @SerializedName("Overview")
    val overview: String? = null,
    @SerializedName("ProductionYear")
    val productionYear: Int? = null,
    @SerializedName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerializedName("ChildCount")
    val childCount: Int? = null,
    @SerializedName("IndexNumber")
    val indexNumber: Int? = null,
    @SerializedName("ParentIndexNumber")
    val parentIndexNumber: Int? = null,
    @SerializedName("ImageTags")
    val imageTags: Map<String, String>? = emptyMap(),
    @SerializedName("UserData")
    val userData: EmbyUserDataDto? = null
)

data class EmbyUserDataDto(
    @SerializedName("PlayedPercentage")
    val playedPercentage: Double? = null,
    @SerializedName("PlaybackPositionTicks")
    val playbackPositionTicks: Long? = null,
    @SerializedName("Played")
    val played: Boolean = false,
    @SerializedName("LastPlayedDate")
    val lastPlayedDate: String? = null
)

data class EmbyPlaybackInfoResponse(
    @SerializedName("MediaSources")
    val mediaSources: List<EmbyMediaSourceDto> = emptyList(),
    @SerializedName("PlaySessionId")
    val playSessionId: String? = null
)

data class EmbyMediaSourceDto(
    @SerializedName("Id")
    val id: String? = null,
    @SerializedName("Name")
    val name: String? = null,
    @SerializedName("Container")
    val container: String? = null,
    @SerializedName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerializedName("SupportsDirectPlay")
    val supportsDirectPlay: Boolean? = null,
    @SerializedName("SupportsDirectStream")
    val supportsDirectStream: Boolean? = null,
    @SerializedName("MediaStreams")
    val mediaStreams: List<EmbyMediaStreamDto> = emptyList()
)

data class EmbyMediaStreamDto(
    @SerializedName("Index")
    val index: Int = -1,
    @SerializedName("Type")
    val type: String? = null,
    @SerializedName("Codec")
    val codec: String? = null,
    @SerializedName("Language")
    val language: String? = null,
    @SerializedName("DisplayTitle")
    val displayTitle: String? = null,
    @SerializedName("Title")
    val title: String? = null,
    @SerializedName("IsDefault")
    val isDefault: Boolean = false,
    @SerializedName("IsForced")
    val isForced: Boolean = false,
    @SerializedName("IsExternal")
    val isExternal: Boolean = false,
    @SerializedName("DeliveryUrl")
    val deliveryUrl: String? = null
)

data class EmbyPlaybackStartRequest(
    @SerializedName("ItemId")
    val itemId: String,
    @SerializedName("SessionId")
    val sessionId: String,
    @SerializedName("MediaSourceId")
    val mediaSourceId: String,
    @SerializedName("IsPaused")
    val isPaused: Boolean = false,
    @SerializedName("IsMuted")
    val isMuted: Boolean = false,
    @SerializedName("PositionTicks")
    val positionTicks: Long = 0L
)

data class EmbyPlaybackProgressRequest(
    @SerializedName("ItemId")
    val itemId: String,
    @SerializedName("SessionId")
    val sessionId: String,
    @SerializedName("MediaSourceId")
    val mediaSourceId: String,
    @SerializedName("IsPaused")
    val isPaused: Boolean = false,
    @SerializedName("PositionTicks")
    val positionTicks: Long = 0L
)

data class EmbyPlaybackStopRequest(
    @SerializedName("ItemId")
    val itemId: String,
    @SerializedName("SessionId")
    val sessionId: String,
    @SerializedName("MediaSourceId")
    val mediaSourceId: String,
    @SerializedName("PositionTicks")
    val positionTicks: Long = 0L
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
        @Query("Fields") fields: String = "Overview,ProductionYear,RunTimeTicks,ChildCount,ImageTags",
        @Query("SortBy") sortBy: String = "DateCreated",
        @Query("SortOrder") sortOrder: String = "Descending",
        @Query("Limit") limit: Int = 50
    ): Response<EmbyItemsResponse>

    @GET("Users/{userId}/Items/Resume")
    suspend fun getResumeItems(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String,
        @Query("MediaTypes") mediaTypes: String = "Video",
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Episode,Video",
        @Query("Fields") fields: String = "Overview,ProductionYear,RunTimeTicks,ChildCount,ImageTags",
        @Query("Limit") limit: Int = 12
    ): Response<EmbyItemsResponse>

    @GET("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Header("X-Emby-Token") token: String,
        @Query("UserId") userId: String
    ): Response<EmbyPlaybackInfoResponse>

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Body request: EmbyPlaybackStartRequest,
        @Header("X-Emby-Token") token: String
    ): Response<Unit>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Body request: EmbyPlaybackProgressRequest,
        @Header("X-Emby-Token") token: String
    ): Response<Unit>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Body request: EmbyPlaybackStopRequest,
        @Header("X-Emby-Token") token: String
    ): Response<Unit>

    @GET("Users/{userId}/Items")
    suspend fun getSeasons(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String,
        @Query("ParentId") parentId: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String = "Season",
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending"
    ): Response<EmbyItemsResponse>

    @GET("Users/{userId}/Items")
    suspend fun getEpisodes(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String,
        @Query("ParentId") parentId: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String = "Episode",
        @Query("Fields") fields: String = "Overview,ProductionYear,RunTimeTicks,ChildCount,ImageTags,UserData",
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending"
    ): Response<EmbyItemsResponse>
}

private const val EMBY_CLIENT_AUTHORIZATION =
    "MediaBrowser Client=\"Nordic\", Device=\"Android\", DeviceId=\"nordic-android\", Version=\"1.0.0\""
