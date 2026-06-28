package com.nordic.mediahub.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AudiobookShelfLoginRequest(
    val username: String,
    val password: String
)

data class AudiobookShelfLoginResponse(
    val user: AudiobookShelfUserDto? = null,
    val userDefaultLibraryId: String? = null
)

data class AudiobookShelfUserDto(
    val id: String,
    val username: String,
    val token: String? = null,
    val accessToken: String? = null
)

data class AudiobookShelfLibrariesResponse(
    val libraries: List<AudiobookShelfLibraryDto> = emptyList()
)

data class AudiobookShelfLibraryDto(
    val id: String,
    val name: String,
    val mediaType: String,
    val icon: String? = null
)

data class AudiobookShelfLibraryItemsResponse(
    val results: List<AudiobookShelfLibraryItemMinifiedDto> = emptyList(),
    val total: Int? = null,
    val limit: Int = 0,
    val page: Int = 0,
    val mediaType: String? = null,
    val minified: Boolean = false
)

data class AudiobookShelfLibraryItemMinifiedDto(
    val id: String,
    val libraryId: String,
    val mediaType: String,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val media: AudiobookShelfBookMinifiedDto
)

data class AudiobookShelfLibraryItemExpandedDto(
    val id: String,
    val libraryId: String,
    val mediaType: String,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val media: AudiobookShelfBookExpandedDto,
    @SerializedName("userMediaProgress")
    val userMediaProgress: AudiobookShelfMediaProgressDto? = null
)

data class AudiobookShelfBookMinifiedDto(
    val id: String,
    val metadata: AudiobookShelfBookMinifiedMetadataDto,
    val coverPath: String? = null,
    val duration: Double = 0.0,
    val numTracks: Int = 0,
    val numAudioFiles: Int = 0,
    val numChapters: Int = 0
)

data class AudiobookShelfBookMinifiedMetadataDto(
    val title: String,
    val titleIgnorePrefix: String? = null,
    val authorName: String? = null,
    val authorNameLF: String? = null,
    val narratorName: String? = null,
    val seriesName: String? = null,
    val publishedYear: String? = null,
    val description: String? = null
)

data class AudiobookShelfBookExpandedDto(
    val id: String,
    val metadata: AudiobookShelfBookExpandedMetadataDto,
    val coverPath: String? = null,
    val duration: Double = 0.0,
    val chapters: List<AudiobookShelfChapterDto>? = null,
    val tracks: List<AudiobookShelfAudioTrackDto> = emptyList(),
    val audioFiles: List<AudiobookShelfAudioTrackDto> = emptyList()
)

data class AudiobookShelfBookExpandedMetadataDto(
    val title: String,
    val titleIgnorePrefix: String? = null,
    val subtitle: String? = null,
    val authorName: String? = null,
    val authorNameLF: String? = null,
    val narratorName: String? = null,
    val seriesName: String? = null,
    val description: String? = null,
    val descriptionPlain: String? = null,
    val publishedYear: String? = null,
    val authors: List<AudiobookShelfNamedRefDto>? = null,
    val narrators: List<String>? = null,
    val series: List<AudiobookShelfSeriesRefDto>? = null
)

data class AudiobookShelfNamedRefDto(
    val id: String,
    val name: String
)

data class AudiobookShelfSeriesRefDto(
    val id: String,
    val name: String,
    val sequence: String? = null
)

data class AudiobookShelfChapterDto(
    val id: Int = 0,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val title: String
)

data class AudiobookShelfAudioTrackDto(
    val index: Int = 0,
    val ino: String? = null,
    val duration: Double = 0.0,
    val startOffset: Double = 0.0,
    val title: String? = null,
    val contentUrl: String? = null,
    val metadata: AudiobookShelfFileMetadataDto? = null
)

data class AudiobookShelfFileMetadataDto(
    val filename: String? = null,
    val path: String? = null,
    val relPath: String? = null
)

data class AudiobookShelfMediaProgressDto(
    val id: String,
    val libraryItemId: String? = null,
    val episodeId: String? = null,
    val duration: Double = 0.0,
    val progress: Double = 0.0,
    val currentTime: Double = 0.0,
    val isFinished: Boolean = false,
    val lastUpdate: Long = 0L
)

data class AudiobookShelfPlaybackSessionDto(
    val id: String,
    val libraryId: String,
    val libraryItemId: String,
    val mediaType: String,
    val displayTitle: String,
    val displayAuthor: String? = null,
    val coverPath: String? = null,
    val duration: Double = 0.0,
    val playMethod: Int = 0,
    val startTime: Double = 0.0,
    val currentTime: Double = 0.0,
    val chapters: List<AudiobookShelfChapterDto>? = null,
    val audioTracks: List<AudiobookShelfAudioTrackDto>? = null,
    val libraryItem: AudiobookShelfLibraryItemExpandedDto? = null
)

data class AudiobookShelfPlayRequest(
    val deviceInfo: AudiobookShelfDeviceInfoRequest,
    val forceDirectPlay: Boolean = true,
    val forceTranscode: Boolean = false,
    val mediaPlayer: String = "nordic-android",
    val supportedMimeTypes: List<String> = emptyList()
)

data class AudiobookShelfDeviceInfoRequest(
    val deviceId: String = "nordic-android",
    val clientName: String = "Nordic",
    val clientVersion: String = "1.0.0",
    val manufacturer: String = "Android",
    val model: String = "Android"
)

data class AudiobookShelfProgressUpdateRequest(
    val duration: Double,
    val currentTime: Double,
    val progress: Double,
    val lastUpdate: Long
)

data class AudiobookShelfSessionSyncRequest(
    val currentTime: Double,
    @SerializedName("timeListened")
    val timeListened: Double,
    val duration: Double
)

interface AudiobookShelfApi {
    @POST("login")
    suspend fun login(
        @Body request: AudiobookShelfLoginRequest,
        @Header("x-return-tokens") returnTokens: String = "true"
    ): Response<AudiobookShelfLoginResponse>

    @GET("api/libraries")
    suspend fun getLibraries(
        @Header("Authorization") bearerToken: String
    ): Response<AudiobookShelfLibrariesResponse>

    @GET("api/libraries/{id}/items")
    suspend fun getLibraryItems(
        @Header("Authorization") bearerToken: String,
        @Path("id") libraryId: String,
        @Query("minified") minified: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 0
    ): Response<AudiobookShelfLibraryItemsResponse>

    @GET("api/items/{id}")
    suspend fun getLibraryItem(
        @Header("Authorization") bearerToken: String,
        @Path("id") itemId: String,
        @Query("expanded") expanded: Int = 1,
        @Query("include") include: String = "progress"
    ): Response<AudiobookShelfLibraryItemExpandedDto>

    @POST("api/items/{id}/play")
    suspend fun startPlayback(
        @Header("Authorization") bearerToken: String,
        @Path("id") itemId: String,
        @Body request: AudiobookShelfPlayRequest
    ): Response<AudiobookShelfPlaybackSessionDto>

    @GET("api/me/progress/{id}")
    suspend fun getProgress(
        @Header("Authorization") bearerToken: String,
        @Path("id") itemId: String
    ): Response<AudiobookShelfMediaProgressDto>

    @PATCH("api/me/progress/{id}")
    suspend fun updateProgress(
        @Header("Authorization") bearerToken: String,
        @Path("id") itemId: String,
        @Body request: AudiobookShelfProgressUpdateRequest
    ): Response<Unit>

    @POST("api/session/{id}/sync")
    suspend fun syncSession(
        @Header("Authorization") bearerToken: String,
        @Path("id") sessionId: String,
        @Body request: AudiobookShelfSessionSyncRequest
    ): Response<Unit>

    @POST("api/session/{id}/close")
    suspend fun closeSession(
        @Header("Authorization") bearerToken: String,
        @Path("id") sessionId: String,
        @Body request: AudiobookShelfSessionSyncRequest? = null
    ): Response<Unit>
}
