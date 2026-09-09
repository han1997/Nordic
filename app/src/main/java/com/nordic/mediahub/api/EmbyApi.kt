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
    val user: EmbyUserDto? = null,
    @SerializedName("AccessToken")
    val accessToken: String? = null
)

data class EmbyUserDto(
    @SerializedName("Id")
    val id: String? = null,
    @SerializedName("Name")
    val name: String? = null
)

data class EmbyItemsResponse(
    @SerializedName("Items")
    val items: List<EmbyItemDto>? = null,
    @SerializedName("TotalRecordCount")
    val totalRecordCount: Int? = null
)

data class EmbyItemDto(
    @SerializedName("Id")
    val id: String? = null,
    @SerializedName("Name")
    val name: String? = null,
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
    @SerializedName("BackdropImageTags")
    val backdropImageTags: List<String>? = null,
    @SerializedName("ParentBackdropItemId")
    val parentBackdropItemId: String? = null,
    @SerializedName("ParentBackdropImageTags")
    val parentBackdropImageTags: List<String>? = null,
    @SerializedName("UserData")
    val userData: EmbyUserDataDto? = null,
    @SerializedName("MediaStreams")
    val mediaStreams: List<EmbyMediaStreamDto>? = null,
    @SerializedName("Chapters")
    val chapters: List<EmbyChapterDto>? = null
)

data class EmbyChapterDto(
    @SerializedName("Name")
    val name: String? = null,
    @SerializedName("StartPositionTicks")
    val startPositionTicks: Long? = null,
    @SerializedName("MarkerType")
    val markerType: String? = null
)

data class EmbyMediaStreamDto(
    @SerializedName("Index")
    val index: Int? = null,
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
    @SerializedName("IsExternal")
    val isExternal: Boolean? = null,
    @SerializedName("IsDefault")
    val isDefault: Boolean? = null
)

data class EmbyUserDataDto(
    @SerializedName("Played")
    val played: Boolean? = null,
    @SerializedName("PlaybackPositionTicks")
    val playbackPositionTicks: Long? = null,
    @SerializedName("LastPlayedDate")
    val lastPlayedDate: String? = null
)

data class EmbyPlaybackProgressRequest(
    @SerializedName("ItemId")
    val itemId: String,
    @SerializedName("PositionTicks")
    val positionTicks: Long,
    @SerializedName("IsPaused")
    val isPaused: Boolean,
    /** Present for transcoded sessions; null keeps legacy direct-play reports unchanged. */
    @SerializedName("PlaySessionId")
    val playSessionId: String? = null
)

/** Minimal device profile: only the fields the server needs to accept the handshake. */
data class EmbyDeviceProfileDto(
    @SerializedName("MaxStreamingBitrate")
    val maxStreamingBitrate: Long,
    @SerializedName("DirectPlayProfiles")
    val directPlayProfiles: List<EmbyProfileContainerDto>,
    @SerializedName("TranscodingProfiles")
    val transcodingProfiles: List<EmbyTranscodeProfileDto>
)

data class EmbyProfileContainerDto(
    @SerializedName("Container")
    val container: String,
    @SerializedName("Type")
    val type: String
)

data class EmbyTranscodeProfileDto(
    @SerializedName("Container")
    val container: String,
    @SerializedName("Type")
    val type: String,
    @SerializedName("Protocol")
    val protocol: String,
    @SerializedName("VideoCodec")
    val videoCodec: String,
    @SerializedName("AudioCodec")
    val audioCodec: String
)

data class EmbyPlaybackInfoRequest(
    @SerializedName("DeviceProfile")
    val deviceProfile: EmbyDeviceProfileDto,
    @SerializedName("UserId")
    val userId: String,
    @SerializedName("AutoOpenLiveStream")
    val autoOpenLiveStream: Boolean = false,
    @SerializedName("MaxStreamingBitrate")
    val maxStreamingBitrate: Long
)

data class EmbyMediaSourceDto(
    @SerializedName("Id")
    val id: String? = null,
    @SerializedName("Protocol")
    val protocol: String? = null,
    @SerializedName("Container")
    val container: String? = null,
    @SerializedName("SupportsDirectPlay")
    val supportsDirectPlay: Boolean? = null,
    @SerializedName("SupportsDirectStream")
    val supportsDirectStream: Boolean? = null,
    @SerializedName("SupportsTranscoding")
    val supportsTranscoding: Boolean? = null
)

data class EmbyPlaybackInfoResponse(
    @SerializedName("PlaySessionId")
    val playSessionId: String? = null,
    @SerializedName("MediaSources")
    val mediaSources: List<EmbyMediaSourceDto>? = null
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
        @Query("Fields") fields: String = "Overview,ProductionYear,SeriesId,SeriesName,ParentIndexNumber,IndexNumber,RunTimeTicks,ChildCount,ImageTags,BackdropImageTags,ParentBackdropImageTags,CommunityRating,UserData,MediaStreams,Chapters",
        @Query("SortBy") sortBy: String = "DateCreated",
        @Query("SortOrder") sortOrder: String = "Descending",
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 50
    ): Response<EmbyItemsResponse>

    @GET("Users/{userId}/Items/Resume")
    suspend fun getResumeItems(
        @Path("userId") userId: String,
        @Header("X-Emby-Token") token: String,
        @Query("MediaTypes") mediaTypes: String = "Video",
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "Overview,ProductionYear,SeriesId,SeriesName,ParentIndexNumber,IndexNumber,RunTimeTicks,ChildCount,ImageTags,BackdropImageTags,ParentBackdropImageTags,CommunityRating,UserData,MediaStreams,Chapters",
        @Query("Limit") limit: Int = 12
    ): Response<EmbyItemsResponse>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Header("X-Emby-Token") token: String,
        @Body request: EmbyPlaybackProgressRequest
    ): Response<Unit>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Header("X-Emby-Token") token: String,
        @Body request: EmbyPlaybackProgressRequest
    ): Response<Unit>

    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Header("X-Emby-Token") token: String,
        @Query("UserId") userId: String,
        @Body request: EmbyPlaybackInfoRequest
    ): Response<EmbyPlaybackInfoResponse>
}

private const val EMBY_CLIENT_AUTHORIZATION =
    "MediaBrowser Client=\"Nordic\", Device=\"Android\", DeviceId=\"nordic-android\", Version=\"1.0.0\""
