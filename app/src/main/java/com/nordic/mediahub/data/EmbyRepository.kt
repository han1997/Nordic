package com.nordic.mediahub.data

import androidx.compose.runtime.Stable

import android.util.Log
import com.nordic.mediahub.api.EmbyApi
import com.nordic.mediahub.api.EmbyAuthenticateRequest
import com.nordic.mediahub.api.EmbyItemDto
import com.nordic.mediahub.api.EmbyPlaybackStartRequest
import com.nordic.mediahub.api.EmbyPlaybackProgressRequest
import com.nordic.mediahub.api.EmbyPlaybackStopRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EmbyApiException(message: String, val kind: Kind) : Exception(message) {
    enum class Kind { HTTP, AUTH, API }
}

@Stable
data class VideoLibrary(
    val id: String,
    val name: String,
    val collectionType: String,
    val itemCount: Int = 0
)

@Stable
data class VideoItem(
    val id: String,
    val libraryId: String,
    val title: String,
    val type: String,
    val overview: String = "",
    val year: Int? = null,
    val durationSeconds: Int = 0,
    val imageUrl: String? = null
)

@Stable
data class VideoPlaybackInfo(
    val itemId: String,
    val title: String,
    val streamUrl: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val overview: String = "",
    val durationSeconds: Int = 0,
    val imageUrl: String? = null
)

@Stable
data class VideoCatalog(
    val libraries: List<VideoLibrary>,
    val selectedLibraryId: String?,
    val items: List<VideoItem>
)

@Stable
data class VideoSeason(
    val id: String,
    val name: String,
    val indexNumber: Int,
    val episodeCount: Int,
    val imageUrl: String? = null
)

@Stable
data class VideoEpisode(
    val id: String,
    val name: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val overview: String,
    val durationSeconds: Int,
    val imageUrl: String? = null
)

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

    suspend fun getPlaybackInfo(item: VideoItem): VideoPlaybackInfo = try {
        val session = session()
        val playbackInfo = api.getPlaybackInfo(
            itemId = item.id,
            token = session.token,
            userId = session.userId
        ).requireBody("获取 Emby 播放信息失败")
        val mediaSource = playbackInfo.mediaSources.firstOrNull { source ->
            source.id?.isNotBlank() == true && source.supportsDirectStream != false
        } ?: throw EmbyApiException("获取 Emby 播放信息失败: 没有可直接播放的媒体源", EmbyApiException.Kind.API)
        val mediaSourceId = mediaSource.id.orEmpty()
        val playSessionId = playbackInfo.playSessionId.orEmpty()
        if (playSessionId.isBlank()) {
            throw EmbyApiException("获取 Emby 播放信息失败: 缺少播放会话", EmbyApiException.Kind.API)
        }

        VideoPlaybackInfo(
            itemId = item.id,
            title = item.title,
            streamUrl = directStreamUrl(item.id, mediaSourceId, playSessionId, session.token),
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            overview = item.overview,
            durationSeconds = (mediaSource.runTimeTicks ?: item.durationSeconds.toLong() * EMBY_TICKS_PER_SECOND)
                .toDurationSeconds()
                .takeIf { it > 0 } ?: item.durationSeconds,
            imageUrl = item.imageUrl
        )
    } catch (e: EmbyApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("启动 Emby 播放失败: ${e.message}")
    }

    suspend fun reportPlaybackStart(
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        positionSeconds: Int = 0
    ) {
        try {
            val session = session()
            api.reportPlaybackStart(
                request = EmbyPlaybackStartRequest(
                    itemId = itemId,
                    sessionId = playSessionId,
                    mediaSourceId = mediaSourceId,
                    positionTicks = positionSeconds.toLong() * EMBY_TICKS_PER_SECOND
                ),
                token = session.token
            ).requireSuccess("上报播放开始失败")
        } catch (e: EmbyApiException) {
            throw e
        } catch (e: Exception) {
            throw Exception("上报播放开始失败: ${e.message}")
        }
    }

    suspend fun reportPlaybackProgress(
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        positionSeconds: Int
    ) {
        try {
            val session = session()
            api.reportPlaybackProgress(
                request = EmbyPlaybackProgressRequest(
                    itemId = itemId,
                    sessionId = playSessionId,
                    mediaSourceId = mediaSourceId,
                    positionTicks = positionSeconds.toLong() * EMBY_TICKS_PER_SECOND
                ),
                token = session.token
            ).requireSuccess("上报播放进度失败")
        } catch (e: EmbyApiException) {
            throw e
        } catch (e: Exception) {
            throw Exception("上报播放进度失败: ${e.message}")
        }
    }

    suspend fun reportPlaybackStopped(
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        positionSeconds: Int
    ) {
        try {
            val session = session()
            api.reportPlaybackStopped(
                request = EmbyPlaybackStopRequest(
                    itemId = itemId,
                    sessionId = playSessionId,
                    mediaSourceId = mediaSourceId,
                    positionTicks = positionSeconds.toLong() * EMBY_TICKS_PER_SECOND
                ),
                token = session.token
            ).requireSuccess("上报播放停止失败")
        } catch (e: EmbyApiException) {
            throw e
        } catch (e: Exception) {
            throw Exception("上报播放停止失败: ${e.message}")
        }
    }

    suspend fun getSeasons(seriesId: String): List<VideoSeason> = try {
        val session = session()
        api.getSeasons(
            userId = session.userId,
            token = session.token,
            parentId = seriesId
        ).requireBody("获取季列表失败")
            .items
            .map { dto -> dto.toVideoSeason(session.token) }
    } catch (e: EmbyApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取季列表失败: ${e.message}")
    }

    suspend fun getEpisodes(seasonId: String): List<VideoEpisode> = try {
        val session = session()
        api.getEpisodes(
            userId = session.userId,
            token = session.token,
            parentId = seasonId
        ).requireBody("获取集列表失败")
            .items
            .map { dto -> dto.toVideoEpisode(session.token) }
    } catch (e: EmbyApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取集列表失败: ${e.message}")
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
        return api.getItems(
            userId = session.userId,
            token = session.token,
            parentId = libraryId
        ).requireBody("获取 Emby 视频失败")
            .items
            .map { item -> item.toVideoItem(libraryId, session.token) }
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
            imageUrl = primaryImageUrl(id, token, imageTags?.get("Primary"))
        )
    }

    private fun EmbyItemDto.toVideoSeason(token: String): VideoSeason {
        return VideoSeason(
            id = id,
            name = name,
            indexNumber = indexNumber ?: 0,
            episodeCount = childCount ?: 0,
            imageUrl = primaryImageUrl(id, token, imageTags?.get("Primary"))
        )
    }

    private fun EmbyItemDto.toVideoEpisode(token: String): VideoEpisode {
        return VideoEpisode(
            id = id,
            name = name,
            seasonNumber = parentIndexNumber ?: 0,
            episodeNumber = indexNumber ?: 0,
            overview = overview.orEmpty(),
            durationSeconds = runTimeTicks.toDurationSeconds(),
            imageUrl = primaryImageUrl(id, token, imageTags?.get("Primary"))
        )
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

    private fun directStreamUrl(
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        token: String
    ): String {
        return baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("Videos")
            .addPathSegment(itemId)
            .addPathSegment("stream")
            .addQueryParameter("static", "true")
            .addQueryParameter("MediaSourceId", mediaSourceId)
            .addQueryParameter("PlaySessionId", playSessionId)
            .addQueryParameter("api_key", token)
            .build()
            .toString()
    }

    private fun Long?.toDurationSeconds(): Int {
        return ((this ?: 0L) / EMBY_TICKS_PER_SECOND).coerceAtLeast(0L).toInt()
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

    private companion object {
        const val EMBY_TICKS_PER_SECOND = 10_000_000L
        val videoCollectionTypes = setOf("movies", "tvshows", "homevideos", "mixed")
    }
}
