package com.nordic.mediahub.data

import android.util.Log
import com.nordic.mediahub.api.AudiobookShelfApi
import com.nordic.mediahub.api.AudiobookShelfDeviceInfoRequest
import com.nordic.mediahub.api.AudiobookShelfLibraryItemExpandedDto
import com.nordic.mediahub.api.AudiobookShelfLibraryItemMinifiedDto
import com.nordic.mediahub.api.AudiobookShelfMediaProgressDto
import com.nordic.mediahub.api.AudiobookShelfPlayRequest
import com.nordic.mediahub.api.AudiobookShelfProgressUpdateRequest
import com.nordic.mediahub.api.AudiobookShelfSessionSyncRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.EOFException

private const val AUDIOBOOK_LIBRARY_PAGE_SIZE = 50

class AudiobookShelfApiException(message: String, val kind: Kind) : Exception(message) {
    enum class Kind { HTTP, AUTH, API }
}

internal fun resolveAudiobookSyncCurrentTimeSeconds(currentTimeSeconds: Int, durationSeconds: Int): Int {
    return currentTimeSeconds.coerceIn(0, durationSeconds.coerceAtLeast(0))
}

class AudiobookShelfRepository(private val config: AudiobookShelfConfig) {
    private val baseUrl = config.normalizedBaseUrl()
    private var cachedBearerToken: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("AudiobookShelfApi", message)
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
        .create(AudiobookShelfApi::class.java)

    private suspend fun <T> executeWithAuthRetry(
        request: suspend (String) -> Response<T>
    ): Response<T> {
        val firstAuth = bearerToken()
        var response = request(firstAuth)
        if (response.code() == 401) {
            cachedBearerToken = null
            val newAuth = bearerToken()
            response = request(newAuth)
        }
        return response
    }

    private suspend fun <T> requireResponseBody(
        action: String,
        request: suspend (String) -> Response<T>
    ): T {
        val response = try {
            executeWithAuthRetry(request)
        } catch (error: EOFException) {
            throw AudiobookShelfApiException(
                "$action: 响应为空",
                AudiobookShelfApiException.Kind.API
            )
        }
        if (!response.isSuccessful) {
            throw AudiobookShelfApiException(
                "$action: HTTP ${response.code()}",
                AudiobookShelfApiException.Kind.HTTP
            )
        }

        return response.body()
            ?: throw AudiobookShelfApiException(
                "$action: 响应为空",
                AudiobookShelfApiException.Kind.API
            )
    }

    private suspend fun requireUnitResponseWithRetry(
        action: String,
        request: suspend (String) -> Response<Unit>
    ) {
        val response = try {
            executeWithAuthRetry(request)
        } catch (error: EOFException) {
            throw AudiobookShelfApiException(
                "$action: 响应为空",
                AudiobookShelfApiException.Kind.API
            )
        }
        if (!response.isSuccessful) {
            throw AudiobookShelfApiException(
                "$action: HTTP ${response.code()}",
                AudiobookShelfApiException.Kind.HTTP
            )
        }
    }

    private suspend fun bearerToken(): String {
        cachedBearerToken?.let { return it }

        val response = try {
            api.login(
                request = com.nordic.mediahub.api.AudiobookShelfLoginRequest(
                    username = config.username,
                    password = config.password
                )
            )
        } catch (error: EOFException) {
            throw AudiobookShelfApiException(
                "登录失败: 响应为空",
                AudiobookShelfApiException.Kind.API
            )
        }

        if (!response.isSuccessful) {
            throw AudiobookShelfApiException(
                "登录失败: HTTP ${response.code()}",
                AudiobookShelfApiException.Kind.HTTP
            )
        }

        val body = response.body()
            ?: throw AudiobookShelfApiException("登录失败: 响应为空", AudiobookShelfApiException.Kind.AUTH)

        val token = body.user?.token?.takeIf { it.isNotBlank() }
            ?: body.user?.accessToken?.takeIf { it.isNotBlank() }
        if (token.isNullOrBlank()) {
            throw AudiobookShelfApiException("登录失败: 未返回 token", AudiobookShelfApiException.Kind.AUTH)
        }
        runCatching { baseUrl.toHttpUrl().originKey() }
            .getOrNull()
            ?.let { origin -> MediaAuthHeaderRegistry.register(origin, "Authorization", "Bearer $token") }
        return "Bearer $token".also { cachedBearerToken = it }
    }

    suspend fun getLibraries(): List<AudiobookLibrarySummary> {
        val body = requireResponseBody("获取书库失败") { auth ->
            api.getLibraries(auth)
        }

        return body.libraries.orEmpty().mapNotNull { dto ->
            val mediaType = dto.mediaType?.trim()?.takeIf { it.equals("book", ignoreCase = true) }
                ?: return@mapNotNull null
            val id = dto.id?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = dto.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AudiobookLibrarySummary(
                id = id,
                name = name,
                mediaType = mediaType
            )
        }
    }

    suspend fun testConnection(): Int {
        return try {
            getLibraries().size
        } catch (e: AudiobookShelfApiException) {
            throw e
        } catch (e: Exception) {
            throw AudiobookShelfApiException(
                "测试 AudiobookShelf 连接失败: ${e.message}",
                AudiobookShelfApiException.Kind.API
            )
        }
    }

    suspend fun getLibraryItems(libraryId: String): List<AudiobookItemSummary> {
        val items = mutableListOf<AudiobookItemSummary>()
        var page = 0
        var fetchedItemCount = 0

        while (true) {
            val body = requireResponseBody("获取有声书列表失败") { auth ->
                api.getLibraryItems(
                    bearerToken = auth,
                    libraryId = libraryId,
                    limit = AUDIOBOOK_LIBRARY_PAGE_SIZE,
                    page = page
                )
            }
            val pageItems = body.results.orEmpty()
            fetchedItemCount += pageItems.size
            items += pageItems.mapNotNull { it.toSummary(fallbackLibraryId = libraryId) }

            val total = body.total
            if (pageItems.isEmpty()) break
            if (total != null && fetchedItemCount >= total) break
            if (pageItems.size < AUDIOBOOK_LIBRARY_PAGE_SIZE) break
            page += 1
        }

        return items
    }

    suspend fun getLibraryItem(itemId: String): AudiobookItemDetail {
        return requireResponseBody("获取有声书详情失败") { auth ->
            api.getLibraryItem(
                bearerToken = auth,
                itemId = itemId
            )
        }.toDetail()
    }

    suspend fun startPlayback(itemId: String): AudiobookPlaybackSession {
        val session = requireResponseBody("启动播放失败") { auth ->
            api.startPlayback(
                bearerToken = auth,
                itemId = itemId,
                request = AudiobookShelfPlayRequest(
                    deviceInfo = AudiobookShelfDeviceInfoRequest(),
                    supportedMimeTypes = listOf(
                        "audio/mpeg",
                        "audio/mp4",
                        "audio/x-m4b",
                        "audio/m4b",
                        "audio/aac"
                    )
                )
            )
        }

        return AudiobookPlaybackSession(
            sessionId = session.id.orEmpty(),
            libraryItemId = session.libraryItemId.orEmpty(),
            displayTitle = session.displayTitle.orEmpty(),
            displayAuthor = session.displayAuthor.orEmpty(),
            coverUrl = session.coverPath.toAbsoluteCoverUrlOrNull(),
            durationSeconds = session.duration.toInt(),
            currentTimeSeconds = session.currentTime.toInt(),
            startTimeSeconds = session.startTime.toInt(),
            chapters = session.chapters.orEmpty().map { chapter ->
                AudiobookChapter(
                    id = chapter.id,
                    title = chapter.title,
                    startSeconds = chapter.start.toInt(),
                    endSeconds = chapter.end.toInt()
                )
            },
            audioTracks = session.audioTracks.orEmpty().mapNotNull { track ->
                val url = track.contentUrl ?: return@mapNotNull null
                AudiobookAudioTrack(
                    index = track.index,
                    title = track.title ?: track.metadata?.filename.orEmpty(),
                    contentUrl = url.toAbsoluteAudioUrl(),
                    startOffsetSeconds = track.startOffset.toInt(),
                    durationSeconds = track.duration.toInt()
                )
            }
        )
    }

    suspend fun syncProgress(session: AudiobookPlaybackSession, currentTimeSeconds: Int, deltaSeconds: Int) {
        try {
            val duration = session.durationSeconds.coerceAtLeast(1)
            val safeCurrentTime = resolveAudiobookSyncCurrentTimeSeconds(currentTimeSeconds, session.durationSeconds)
            val progress = (safeCurrentTime.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
            requireUnitResponseWithRetry("同步有声书进度失败") { auth ->
                api.updateProgress(
                    bearerToken = auth,
                    itemId = session.libraryItemId,
                    request = AudiobookShelfProgressUpdateRequest(
                        duration = duration.toDouble(),
                        currentTime = safeCurrentTime.toDouble(),
                        progress = progress,
                        lastUpdate = System.currentTimeMillis()
                    )
                )
            }
            requireUnitResponseWithRetry("同步有声书播放会话失败") { auth ->
                api.syncSession(
                    bearerToken = auth,
                    sessionId = session.sessionId,
                    request = AudiobookShelfSessionSyncRequest(
                        currentTime = safeCurrentTime.toDouble(),
                        timeListened = deltaSeconds.toDouble().coerceAtLeast(0.0),
                        duration = duration.toDouble()
                    )
                )
            }
        } catch (e: AudiobookShelfApiException) {
            throw e
        } catch (e: Exception) {
            throw AudiobookShelfApiException(
                "同步进度失败: ${e.message}",
                AudiobookShelfApiException.Kind.API
            )
        }
    }

    suspend fun closeSession(session: AudiobookPlaybackSession, currentTimeSeconds: Int) {
        try {
            val duration = session.durationSeconds.coerceAtLeast(1)
            val safeCurrentTime = resolveAudiobookSyncCurrentTimeSeconds(currentTimeSeconds, session.durationSeconds)
            requireUnitResponseWithRetry("关闭有声书播放会话失败") { auth ->
                api.closeSession(
                    bearerToken = auth,
                    sessionId = session.sessionId,
                    request = AudiobookShelfSessionSyncRequest(
                        currentTime = safeCurrentTime.toDouble(),
                        timeListened = 0.0,
                        duration = duration.toDouble()
                    )
                )
            }
        } catch (e: AudiobookShelfApiException) {
            throw e
        } catch (e: Exception) {
            throw AudiobookShelfApiException(
                "关闭会话失败: ${e.message}",
                AudiobookShelfApiException.Kind.API
            )
        }
    }

    suspend fun syncAndCloseSession(session: AudiobookPlaybackSession, currentTimeSeconds: Int, deltaSeconds: Int = 0) {
        try {
            syncProgress(session, currentTimeSeconds, deltaSeconds)
        } finally {
            closeSession(session, currentTimeSeconds)
        }
    }

    private fun AudiobookShelfLibraryItemMinifiedDto.toSummary(
        fallbackLibraryId: String
    ): AudiobookItemSummary? {
        val itemId = id?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val resolvedLibraryId = libraryId?.trim()?.takeIf { it.isNotBlank() } ?: fallbackLibraryId
        val media = media ?: return null
        val metadata = media.metadata ?: return null
        val title = metadata.title?.trim()?.takeIf { it.isNotBlank() } ?: return null

        return AudiobookItemSummary(
            id = itemId,
            libraryId = resolvedLibraryId,
            title = title,
            author = metadata.authorName.orEmpty(),
            narrator = metadata.narratorName.orEmpty(),
            series = metadata.seriesName.orEmpty(),
            coverUrl = media.coverPath.toAbsoluteCoverUrlOrNull(),
            durationSeconds = media.duration.toInt(),
            chapterCount = media.numChapters,
            updatedAtMillis = updatedAt,
            progress = userMediaProgress?.toDomainProgress()
        )
    }

    private fun AudiobookShelfLibraryItemExpandedDto.toDetail(): AudiobookItemDetail {
        return AudiobookItemDetail(
            id = id.orEmpty(),
            libraryId = libraryId.orEmpty(),
            title = media?.metadata?.title.orEmpty(),
            subtitle = media?.metadata?.subtitle.orEmpty(),
            description = media?.metadata?.descriptionPlain
                ?: media?.metadata?.description
                ?: "",
            authors = media?.metadata?.authors.orEmpty().map { it.name },
            narrators = media?.metadata?.narrators.orEmpty(),
            series = media?.metadata?.series.orEmpty().map { series ->
                if (series.sequence.isNullOrBlank()) series.name else "${series.name} #${series.sequence}"
            },
            coverUrl = media?.coverPath.toAbsoluteCoverUrlOrNull(),
            durationSeconds = (media?.duration ?: 0.0).toInt(),
            chapters = media?.chapters.orEmpty().map { chapter ->
                AudiobookChapter(
                    id = chapter.id,
                    title = chapter.title,
                    startSeconds = chapter.start.toInt(),
                    endSeconds = chapter.end.toInt()
                )
            },
            progress = userMediaProgress?.toDomainProgress()
        )
    }

    private fun AudiobookShelfMediaProgressDto.toDomainProgress(): AudiobookProgress {
        return AudiobookProgress(
            currentTimeSeconds = currentTime.toInt(),
            durationSeconds = duration.toInt(),
            progressFraction = progress.toFloat().coerceIn(0f, 1f),
            isFinished = isFinished,
            lastUpdateMillis = lastUpdate
        )
    }

    private fun String?.toAbsoluteCoverUrlOrNull(): String? {
        return this?.takeIf { it.isNotBlank() }?.toAbsoluteCoverUrl()
    }

    private fun String.toAbsoluteCoverUrl(): String {
        return if (startsWith("http://") || startsWith("https://")) this else "$baseUrl$this"
    }

    private fun String.toAbsoluteAudioUrl(): String {
        val absolute = if (startsWith("http://") || startsWith("https://")) this else "$baseUrl$this"
        return stripAuthQuery(absolute)
    }
}
