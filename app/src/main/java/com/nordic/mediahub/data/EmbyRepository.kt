package com.nordic.mediahub.data

import android.util.Log
import com.nordic.mediahub.api.EmbyApi
import com.nordic.mediahub.api.EmbyAuthenticateRequest
import com.nordic.mediahub.api.EmbyItemDto
import com.nordic.mediahub.api.EmbyPlaybackProgressRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EmbyApiException(message: String, val kind: Kind) : Exception(message) {
    enum class Kind { HTTP, AUTH, API }
}

data class VideoLibrary(
    val id: String,
    val name: String,
    val collectionType: String,
    val itemCount: Int = 0
)

data class VideoItem(
    val id: String,
    val libraryId: String,
    val title: String,
    val type: String,
    val overview: String = "",
    val year: Int? = null,
    val durationSeconds: Int = 0,
    val playbackPositionSeconds: Int = 0,
    val lastPlayedDate: String? = null,
    val isPlayed: Boolean = false,
    val communityRating: Float? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val imageUrl: String? = null,
    val streamUrl: String? = null
)

data class VideoCatalog(
    val libraries: List<VideoLibrary>,
    val selectedLibraryId: String?,
    val items: List<VideoItem>
)

private const val EMBY_TICKS_PER_SECOND = 10_000_000L
private const val EMBY_ITEMS_PAGE_SIZE = 100
private val videoCollectionTypes = setOf("movies", "tvshows", "homevideos", "mixed")

internal fun resolveEmbyPlaybackPositionTicks(positionSeconds: Int, durationSeconds: Int): Long {
    val safePositionSeconds = if (durationSeconds > 0) {
        positionSeconds.coerceIn(0, durationSeconds)
    } else {
        positionSeconds.coerceAtLeast(0)
    }
    return safePositionSeconds.toLong() * EMBY_TICKS_PER_SECOND
}

class EmbyRepository(private val config: VideoServerConfig) {
    private val baseUrl = config.normalizedBaseUrl()
    private var cachedSession: EmbySession? = null

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("EmbyApi", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val apiClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .client(apiClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(EmbyApi::class.java)

    suspend fun getCatalog(selectedLibraryId: String? = null): VideoCatalog = try {
        val session = session()
        val libraries = getLibraries(session)
        val libraryId = selectedLibraryId?.takeIf { selectedId ->
            libraries.any { it.id == selectedId }
        } ?: libraries.firstOrNull()?.id

        VideoCatalog(
            libraries = libraries,
            selectedLibraryId = libraryId,
            items = libraryId?.let { getLibraryItems(session, it) }.orEmpty()
        )
    } catch (e: EmbyApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("连接 Emby 失败: ${e.message}")
    }

    suspend fun getLibraryItems(libraryId: String): List<VideoItem> = try {
        getLibraryItems(session(), libraryId)
    } catch (e: EmbyApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("加载视频列表失败: ${e.message}")
    }

    suspend fun syncPlaybackProgress(video: VideoItem, positionSeconds: Int, isPaused: Boolean) = try {
        val session = session()
        api.reportPlaybackProgress(
            token = session.token,
            request = video.toPlaybackProgressRequest(positionSeconds, isPaused)
        ).requireSuccess("同步 Emby 视频进度失败")
    } catch (e: EmbyApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("同步 Emby 视频进度失败: ${e.message}")
    }

    suspend fun stopPlaybackProgress(video: VideoItem, positionSeconds: Int) = try {
        val session = session()
        api.reportPlaybackStopped(
            token = session.token,
            request = video.toPlaybackProgressRequest(positionSeconds, isPaused = true)
        ).requireSuccess("同步 Emby 视频停止进度失败")
    } catch (e: EmbyApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("同步 Emby 视频停止进度失败: ${e.message}")
    }

    private suspend fun session(): EmbySession {
        cachedSession?.let { return it }

        val session = if (config.apiKey.isNotBlank()) {
            val users = api.getUsers(config.apiKey.trim()).requireBody("获取 Emby 用户失败")
            val user = users.firstOrNull { it.name.equals(config.username, ignoreCase = true) }
                ?: users.firstOrNull()
                ?: throw EmbyApiException("获取 Emby 用户失败: 没有可用用户", EmbyApiException.Kind.AUTH)
            EmbySession(userId = user.id, token = config.apiKey.trim())
        } else {
            val response = api.authenticateByName(
                EmbyAuthenticateRequest(
                    username = config.username.trim(),
                    password = config.password
                )
            ).requireBody("登录 Emby 失败")

            if (response.accessToken.isBlank()) {
                throw EmbyApiException("登录 Emby 失败: 未返回访问令牌", EmbyApiException.Kind.AUTH)
            }
            EmbySession(userId = response.user.id, token = response.accessToken)
        }

        cachedSession = session
        return session
    }

    private suspend fun getLibraries(session: EmbySession): List<VideoLibrary> {
        return api.getUserViews(session.userId, session.token)
            .requireBody("获取 Emby 媒体库失败")
            .items
            .filter { item ->
                item.collectionType in videoCollectionTypes ||
                    (item.collectionType.isNullOrBlank() && item.type == "CollectionFolder")
            }
            .map { item ->
                VideoLibrary(
                    id = item.id,
                    name = item.name,
                    collectionType = item.collectionType.orEmpty(),
                    itemCount = item.childCount ?: 0
                )
            }
    }

    private suspend fun getLibraryItems(session: EmbySession, libraryId: String): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        var startIndex = 0

        do {
            val response = api.getItems(
                userId = session.userId,
                token = session.token,
                parentId = libraryId,
                startIndex = startIndex,
                limit = EMBY_ITEMS_PAGE_SIZE
            ).requireBody("获取 Emby 视频失败")
            val pageItems = response.items
            items += pageItems.map { item -> item.toVideoItem(libraryId, session.token) }
            startIndex += pageItems.size
        } while (pageItems.isNotEmpty() && startIndex < response.totalRecordCount)

        return items
    }

    private fun EmbyItemDto.toVideoItem(libraryId: String, token: String): VideoItem {
        return VideoItem(
            id = id,
            libraryId = libraryId,
            title = name,
            type = type.orEmpty(),
            overview = overview.orEmpty(),
            year = productionYear,
            durationSeconds = runTimeTicks.toDurationSeconds(),
            playbackPositionSeconds = userData?.playbackPositionTicks.toDurationSeconds(),
            lastPlayedDate = userData?.lastPlayedDate?.takeIf { it.isNotBlank() },
            isPlayed = userData?.played == true,
            communityRating = communityRating,
            seriesId = seriesId,
            seriesName = seriesName,
            seasonNumber = parentIndexNumber,
            episodeNumber = indexNumber,
            imageUrl = primaryImageUrl(id, token, imageTags.orEmpty()["Primary"]),
            streamUrl = if (isDirectlyPlayableVideoType(type)) streamUrl(id, token) else null
        )
    }

    private fun isDirectlyPlayableVideoType(type: String?): Boolean {
        return type.equals("Movie", ignoreCase = true) ||
            type.equals("Episode", ignoreCase = true) ||
            type.equals("Video", ignoreCase = true)
    }

    private fun primaryImageUrl(itemId: String, token: String, primaryTag: String?): String? {
        if (primaryTag.isNullOrBlank()) return null

        return baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("Items")
            .addPathSegment(itemId)
            .addPathSegment("Images")
            .addPathSegment("Primary")
            .addQueryParameter("maxWidth", "640")
            .addQueryParameter("quality", "90")
            .addQueryParameter("tag", primaryTag)
            .addQueryParameter("api_key", token)
            .build()
            .toString()
    }

    private fun streamUrl(itemId: String, token: String): String {
        return baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("Videos")
            .addPathSegment(itemId)
            .addPathSegment("stream")
            .addQueryParameter("Static", "true")
            .addQueryParameter("api_key", token)
            .build()
            .toString()
    }

    private fun Long?.toDurationSeconds(): Int {
        return ((this ?: 0L) / EMBY_TICKS_PER_SECOND).coerceAtLeast(0L).toInt()
    }

    private fun VideoItem.toPlaybackProgressRequest(positionSeconds: Int, isPaused: Boolean): EmbyPlaybackProgressRequest {
        return EmbyPlaybackProgressRequest(
            itemId = id,
            positionTicks = resolveEmbyPlaybackPositionTicks(positionSeconds, durationSeconds),
            isPaused = isPaused
        )
    }

    private fun <T> Response<T>.requireBody(action: String): T {
        if (!isSuccessful) {
            throw EmbyApiException("$action: HTTP ${code()}", EmbyApiException.Kind.HTTP)
        }

        return body() ?: throw EmbyApiException("$action: 响应为空", EmbyApiException.Kind.API)
    }

    private fun Response<Unit>.requireSuccess(action: String) {
        if (!isSuccessful) {
            throw EmbyApiException("$action: HTTP ${code()}", EmbyApiException.Kind.HTTP)
        }
    }

    private data class EmbySession(
        val userId: String,
        val token: String
    )

}
