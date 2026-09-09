package com.nordic.mediahub.data

import android.util.Log
import com.nordic.mediahub.api.EmbyApi
import com.nordic.mediahub.api.EmbyChapterDto
import com.nordic.mediahub.api.EmbyMediaStreamDto
import com.nordic.mediahub.api.EmbyAuthenticateRequest
import com.nordic.mediahub.api.EmbyItemDto
import com.nordic.mediahub.api.EmbyPlaybackProgressRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.EOFException

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
    val backdropImageUrl: String? = null,
    val streamUrl: String? = null,
    val mediaStreams: List<VideoStreamInfo> = emptyList(),
    val chapters: List<VideoChapterInfo> = emptyList(),
    val introRange: VideoIntroRange? = null
)

/** A single chapter marker exposed by the server for in-player navigation. */
data class VideoChapterInfo(
    val name: String,
    val startSeconds: Int,
    /** Derived from the next chapter start; null for the last chapter. */
    val endSeconds: Int? = null
)

/**
 * The detected intro range of a video (Emby 4.9 IntroStart/IntroEnd chapter
 * markers). Null start/end means the server has no usable intro data.
 */
data class VideoIntroRange(
    val startSeconds: Int,
    val endSeconds: Int
)

internal const val EMBY_MARKER_INTRO_START = "IntroStart"
internal const val EMBY_MARKER_INTRO_END = "IntroEnd"

/** A single audio/subtitle stream exposed by the server for track selection. */
data class VideoStreamInfo(
    val index: Int,
    val kind: VideoStreamKind,
    val codec: String?,
    val language: String?,
    val displayTitle: String?,
    val isExternal: Boolean
)

enum class VideoStreamKind { Audio, Subtitle }

data class VideoCatalog(
    val libraries: List<VideoLibrary>,
    val selectedLibraryId: String?,
    val items: List<VideoItem>
)

/**
 * Keeps the first occurrence of each [VideoItem.id] in encounter order.
 * Emby can return the same item twice for libraries containing nested
 * collections (Recursive=true), and the browse grid keys rows by
 * "libraryId:id", so duplicates would crash LazyVerticalGrid.
 */
internal fun List<VideoItem>.deduplicatedById(): List<VideoItem> {
    if (size < 2) return this
    val seen = HashSet<String>(size)
    val result = ArrayList<VideoItem>(size)
    for (item in this) {
        if (seen.add(item.id)) {
            result += item
        }
    }
    return result
}

private const val EMBY_TICKS_PER_SECOND = 10_000_000L
private const val EMBY_ITEMS_PAGE_SIZE = 100
// TODO: confirm with `curl -H "X-Emby-Token: <key>" <emby-stream-url>` that the
// stream/image endpoints accept header auth. If a server rejects it, set this to
// false to fall back to api_key query params (B1 clean cache key still protects disk).
internal const val EMBY_HEADER_AUTH_ENABLED = true
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

    suspend fun testConnection(): Int = try {
        getLibraries(session()).size
    } catch (e: EmbyApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("测试 Emby 连接失败: ${e.message}")
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
            val users = requireResponseBody("获取 Emby 用户失败") {
                api.getUsers(config.apiKey.trim())
            }
            val usableUsers = users.filter { !it.id.isNullOrBlank() }
            val user = usableUsers.firstOrNull { it.name.equals(config.username, ignoreCase = true) }
                ?: usableUsers.firstOrNull()
                ?: throw EmbyApiException("获取 Emby 用户失败: 没有可用用户", EmbyApiException.Kind.AUTH)
            val userId = user.id?.takeIf { it.isNotBlank() }
                ?: throw EmbyApiException("获取 Emby 用户失败: 没有可用用户", EmbyApiException.Kind.AUTH)
            EmbySession(userId = userId, token = config.apiKey.trim())
        } else {
            val response = requireResponseBody("登录 Emby 失败") {
                api.authenticateByName(
                    EmbyAuthenticateRequest(
                        username = config.username.trim(),
                        password = config.password
                    )
                )
            }

            val accessToken = response.accessToken?.takeIf { it.isNotBlank() }
            if (accessToken == null) {
                throw EmbyApiException("登录 Emby 失败: 未返回访问令牌", EmbyApiException.Kind.AUTH)
            }
            val userId = response.user?.id?.takeIf { it.isNotBlank() }
                ?: throw EmbyApiException("登录 Emby 失败: 未返回用户标识", EmbyApiException.Kind.AUTH)
            EmbySession(userId = userId, token = accessToken)
        }

        cachedSession = session
        if (EMBY_HEADER_AUTH_ENABLED) {
            runCatching { baseUrl.toHttpUrl().originKey() }
                .getOrNull()
                ?.let { origin -> MediaAuthHeaderRegistry.register(origin, "X-Emby-Token", session.token) }
        }
        return session
    }

    private suspend fun getLibraries(session: EmbySession): List<VideoLibrary> {
        return requireResponseBody("获取 Emby 媒体库失败") {
            api.getUserViews(session.userId, session.token)
        }
            .items
            .orEmpty()
            .filter { item -> item.isVideoLibrary() }
            .mapNotNull { item ->
                val id = item.id?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = item.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                VideoLibrary(
                    id = id,
                    name = name,
                    collectionType = item.collectionType.orEmpty(),
                    itemCount = item.childCount ?: 0
                )
            }
    }

    private suspend fun getLibraryItems(session: EmbySession, libraryId: String): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        var startIndex = 0
        var previousPageFirstItemId: String? = null

        while (true) {
            val response = requireResponseBody("获取 Emby 视频失败") {
                api.getItems(
                    userId = session.userId,
                    token = session.token,
                    parentId = libraryId,
                    startIndex = startIndex,
                    limit = EMBY_ITEMS_PAGE_SIZE
                )
            }
            val pageItems = response.items.orEmpty()
            // Stall guard: a server that ignores StartIndex keeps returning the
            // same first row, which would otherwise paginate forever.
            val pageFirstItemId = pageItems.firstOrNull()?.id?.trim()
            if (pageItems.isNotEmpty() && pageFirstItemId != null && pageFirstItemId == previousPageFirstItemId) {
                break
            }
            previousPageFirstItemId = pageFirstItemId
            items += pageItems.mapNotNull { item -> item.toVideoItem(libraryId, session.token) }
            startIndex += pageItems.size

            val total = response.totalRecordCount
            if (total != null) {
                if (pageItems.isEmpty() || startIndex >= total) break
            } else {
                if (pageItems.size < EMBY_ITEMS_PAGE_SIZE) break
            }
        }

        return items.deduplicatedById()
    }

    private fun EmbyItemDto.toVideoItem(libraryId: String, token: String): VideoItem? {
        val itemId = id?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val title = name?.trim()?.takeIf { it.isNotBlank() } ?: return null

        val ownBackdropTag = backdropImageTags.orEmpty().firstOrNull()?.takeIf { it.isNotBlank() }
        val parentBackdropTag = parentBackdropImageTags.orEmpty().firstOrNull()?.takeIf { it.isNotBlank() }
        val parentBackdropId = parentBackdropItemId?.trim()?.takeIf { it.isNotBlank() }
        val safeSeriesId = seriesId?.trim()?.takeIf { it.isNotBlank() }
        val resolvedBackdrop: String? = when {
            ownBackdropTag != null -> backdropImageUrl(itemId, token, ownBackdropTag)
            parentBackdropTag != null && parentBackdropId != null ->
                backdropImageUrl(parentBackdropId, token, parentBackdropTag)
            safeSeriesId != null -> backdropImageUrl(safeSeriesId, token, null)
            else -> null
        }

        return VideoItem(
            id = itemId,
            libraryId = libraryId,
            title = title,
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
            imageUrl = primaryImageUrl(itemId, token, imageTags.orEmpty()["Primary"]),
            backdropImageUrl = resolvedBackdrop,
            streamUrl = if (isDirectlyPlayableVideoType(type)) streamUrl(itemId, token) else null,
            mediaStreams = mediaStreams.orEmpty().mapNotNull { it.toVideoStreamInfo() },
            chapters = chapters.toVideoChapterInfos(),
            introRange = chapters.toVideoIntroRange()
        )
    }

    private fun EmbyMediaStreamDto.toVideoStreamInfo(): VideoStreamInfo? {
        val streamIndex = index ?: return null
        val kind = when (type?.uppercase()) {
            "AUDIO" -> VideoStreamKind.Audio
            "SUBTITLE" -> VideoStreamKind.Subtitle
            else -> return null
        }
        return VideoStreamInfo(
            index = streamIndex,
            kind = kind,
            codec = codec?.takeIf { it.isNotBlank() },
            language = language?.takeIf { it.isNotBlank() },
            displayTitle = (displayTitle ?: title)?.takeIf { it.isNotBlank() },
            isExternal = isExternal == true
        )
    }

    private fun List<EmbyChapterDto>?.toVideoChapterInfos(): List<VideoChapterInfo> {
        val raw = orEmpty()
            .asSequence()
            .filter { it.isRegularChapter() }
            .mapNotNull { dto ->
                val name = dto.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // A chapter without a usable start position cannot be navigated to.
                val ticks = dto.startPositionTicks?.takeIf { it >= 0 } ?: return@mapNotNull null
                VideoChapterInfo(name = name, startSeconds = ticks.toDurationSeconds())
            }
            .sortedBy { it.startSeconds }
            .toList()
        return raw.mapIndexed { index, chapter ->
            val nextStart = raw.getOrNull(index + 1)?.startSeconds
            val endSeconds = nextStart?.takeIf { it > chapter.startSeconds }
            chapter.copy(endSeconds = endSeconds)
        }
    }

    private fun List<EmbyChapterDto>?.toVideoIntroRange(): VideoIntroRange? {
        val chapters = orEmpty()
        val startIndex = chapters.indexOfFirst { it.markerType == EMBY_MARKER_INTRO_START }
        if (startIndex < 0) return null
        val end = chapters.asSequence()
            .drop(startIndex + 1)
            .firstOrNull { it.markerType == EMBY_MARKER_INTRO_END }
            ?: return null
        val startSeconds = chapters[startIndex].startPositionTicks.toDurationSeconds()
        val endSeconds = end.startPositionTicks.toDurationSeconds()
        if (endSeconds <= startSeconds) return null
        return VideoIntroRange(startSeconds = startSeconds, endSeconds = endSeconds)
    }

    private fun EmbyChapterDto.isRegularChapter(): Boolean =
        markerType == null || markerType.equals("Chapter", ignoreCase = true)

    private fun EmbyItemDto.isVideoLibrary(): Boolean {
        return videoCollectionTypes.any { collectionType ->
            collectionType.equals(this.collectionType, ignoreCase = true)
        } || (collectionType.isNullOrBlank() && type.orEmpty().equals("CollectionFolder", ignoreCase = true))
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
            .apply {
                if (!EMBY_HEADER_AUTH_ENABLED) addQueryParameter("api_key", token)
            }
            .build()
            .toString()
    }

    private fun backdropImageUrl(itemId: String, token: String, backdropTag: String?): String {
        return baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("Items")
            .addPathSegment(itemId)
            .addPathSegment("Images")
            .addPathSegment("Backdrop")
            .addQueryParameter("maxWidth", "1280")
            .addQueryParameter("quality", "90")
            .apply {
                if (!backdropTag.isNullOrBlank()) addQueryParameter("tag", backdropTag)
                if (!EMBY_HEADER_AUTH_ENABLED) addQueryParameter("api_key", token)
            }
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
            .apply {
                if (!EMBY_HEADER_AUTH_ENABLED) addQueryParameter("api_key", token)
            }
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

    private suspend fun <T> requireResponseBody(
        action: String,
        request: suspend () -> Response<T>
    ): T {
        val response = try {
            request()
        } catch (error: EOFException) {
            throw EmbyApiException("$action: 响应为空", EmbyApiException.Kind.API)
        }
        if (!response.isSuccessful) {
            throw EmbyApiException("$action: HTTP ${response.code()}", EmbyApiException.Kind.HTTP)
        }

        return response.body() ?: throw EmbyApiException("$action: 响应为空", EmbyApiException.Kind.API)
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
