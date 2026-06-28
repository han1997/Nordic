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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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

    private suspend fun bearerToken(): String {
        cachedBearerToken?.let { return it }

        val response = api.login(
            request = com.nordic.mediahub.api.AudiobookShelfLoginRequest(
                username = config.username,
                password = config.password
            )
        )

        if (!response.isSuccessful) {
            throw AudiobookShelfApiException(
                "登录失败: HTTP ${response.code()}",
                AudiobookShelfApiException.Kind.HTTP
            )
        }

        val body = response.body()
            ?: throw AudiobookShelfApiException("登录失败: 响应为空", AudiobookShelfApiException.Kind.AUTH)

        val token = body.user.token ?: body.user.accessToken
        if (token.isNullOrBlank()) {
            throw AudiobookShelfApiException("登录失败: 未返回 token", AudiobookShelfApiException.Kind.AUTH)
        }
        return "Bearer $token".also { cachedBearerToken = it }
    }

    suspend fun getLibraries(): List<AudiobookLibrarySummary> {
        val response = api.getLibraries(bearerToken())
        if (!response.isSuccessful) {
            throw AudiobookShelfApiException(
                "获取书库失败: HTTP ${response.code()}",
                AudiobookShelfApiException.Kind.HTTP
            )
        }

        return response.body()?.libraries.orEmpty().mapNotNull { dto ->
            if (dto.mediaType != "book") return@mapNotNull null
            AudiobookLibrarySummary(
                id = dto.id,
                name = dto.name,
                mediaType = dto.mediaType
            )
        }
    }

    suspend fun getLibraryItems(libraryId: String): List<AudiobookItemSummary> {
        val auth = bearerToken()
        val items = mutableListOf<AudiobookItemSummary>()
        var page = 0

        while (true) {
            val response = api.getLibraryItems(
                bearerToken = auth,
                libraryId = libraryId,
                limit = AUDIOBOOK_LIBRARY_PAGE_SIZE,
                page = page
            )
            if (!response.isSuccessful) {
                throw AudiobookShelfApiException(
                    "获取有声书列表失败: HTTP ${response.code()}",
                    AudiobookShelfApiException.Kind.HTTP
                )
            }

            val body = response.body()
                ?: throw AudiobookShelfApiException(
                    "获取有声书列表失败: 响应为空",
                    AudiobookShelfApiException.Kind.API
                )
            val pageItems = body.results
            items += pageItems.map { it.toSummary() }

            val total = body.total
            if (pageItems.isEmpty()) break
            if (total != null && items.size >= total) break
            if (pageItems.size < AUDIOBOOK_LIBRARY_PAGE_SIZE) break
            page += 1
        }

        return items
    }

    suspend fun getLibraryItem(itemId: String): AudiobookItemDetail {
        val auth = bearerToken()
        val response = api.getLibraryItem(
            bearerToken = auth,
            itemId = itemId
        )
        if (!response.isSuccessful) {
            throw AudiobookShelfApiException(
                "获取有声书详情失败: HTTP ${response.code()}",
                AudiobookShelfApiException.Kind.HTTP
            )
        }

        return response.body()?.toDetail()
            ?: throw AudiobookShelfApiException("获取有声书详情失败: 响应为空", AudiobookShelfApiException.Kind.API)
    }

    suspend fun startPlayback(itemId: String): AudiobookPlaybackSession {
        val auth = bearerToken()
        val response = api.startPlayback(
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
        if (!response.isSuccessful) {
            throw AudiobookShelfApiException(
                "启动播放失败: HTTP ${response.code()}",
                AudiobookShelfApiException.Kind.HTTP
            )
        }

        val session = response.body()
            ?: throw AudiobookShelfApiException("启动播放失败: 响应为空", AudiobookShelfApiException.Kind.API)

        val plainToken = auth.removePrefix("Bearer ").trim()

        return AudiobookPlaybackSession(
            sessionId = session.id,
            libraryItemId = session.libraryItemId,
            displayTitle = session.displayTitle,
            displayAuthor = session.displayAuthor.orEmpty(),
            coverUrl = session.coverPath?.toAbsoluteCoverUrl(),
            durationSeconds = session.duration.toInt(),
            currentTimeSeconds = session.currentTime.toInt(),
            startTimeSeconds = session.startTime.toInt(),
            chapters = session.chapters.map { chapter ->
                AudiobookChapter(
                    id = chapter.id,
                    title = chapter.title,
                    startSeconds = chapter.start.toInt(),
                    endSeconds = chapter.end.toInt()
                )
            },
            audioTracks = session.audioTracks.mapNotNull { track ->
                val url = track.contentUrl ?: return@mapNotNull null
                AudiobookAudioTrack(
                    index = track.index,
                    title = track.title ?: track.metadata?.filename.orEmpty(),
                    contentUrl = url.toAbsoluteAudioUrl(plainToken),
                    startOffsetSeconds = track.startOffset.toInt(),
                    durationSeconds = track.duration.toInt()
                )
            }
        )
    }

    suspend fun syncProgress(session: AudiobookPlaybackSession, currentTimeSeconds: Int, deltaSeconds: Int) {
        val auth = bearerToken()
        val duration = session.durationSeconds.coerceAtLeast(1)
        val safeCurrentTime = resolveAudiobookSyncCurrentTimeSeconds(currentTimeSeconds, session.durationSeconds)
        val progress = (safeCurrentTime.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
        api.updateProgress(
            bearerToken = auth,
            itemId = session.libraryItemId,
            request = AudiobookShelfProgressUpdateRequest(
                duration = duration.toDouble(),
                currentTime = safeCurrentTime.toDouble(),
                progress = progress,
                lastUpdate = System.currentTimeMillis()
            )
        ).requireUnitResponse("同步有声书进度失败")
        api.syncSession(
            bearerToken = auth,
            sessionId = session.sessionId,
            request = AudiobookShelfSessionSyncRequest(
                currentTime = safeCurrentTime.toDouble(),
                timeListened = deltaSeconds.toDouble().coerceAtLeast(0.0),
                duration = duration.toDouble()
            )
        ).requireUnitResponse("同步有声书播放会话失败")
    }

    suspend fun closeSession(session: AudiobookPlaybackSession, currentTimeSeconds: Int) {
        val auth = bearerToken()
        val duration = session.durationSeconds.coerceAtLeast(1)
        val safeCurrentTime = resolveAudiobookSyncCurrentTimeSeconds(currentTimeSeconds, session.durationSeconds)
        api.closeSession(
            bearerToken = auth,
            sessionId = session.sessionId,
            request = AudiobookShelfSessionSyncRequest(
                currentTime = safeCurrentTime.toDouble(),
                timeListened = 0.0,
                duration = duration.toDouble()
            )
        ).requireUnitResponse("关闭有声书播放会话失败")
    }

    suspend fun syncAndCloseSession(session: AudiobookPlaybackSession, currentTimeSeconds: Int, deltaSeconds: Int = 0) {
        syncProgress(session, currentTimeSeconds, deltaSeconds)
        closeSession(session, currentTimeSeconds)
    }

    private fun AudiobookShelfLibraryItemMinifiedDto.toSummary(): AudiobookItemSummary {
        return AudiobookItemSummary(
            id = id,
            libraryId = libraryId,
            title = media.metadata.title,
            author = media.metadata.authorName.orEmpty(),
            narrator = media.metadata.narratorName.orEmpty(),
            series = media.metadata.seriesName.orEmpty(),
            coverUrl = media.coverPath?.toAbsoluteCoverUrl(),
            durationSeconds = media.duration.toInt(),
            chapterCount = media.numChapters,
            updatedAtMillis = updatedAt
        )
    }

    private fun AudiobookShelfLibraryItemExpandedDto.toDetail(): AudiobookItemDetail {
        return AudiobookItemDetail(
            id = id,
            libraryId = libraryId,
            title = media.metadata.title,
            subtitle = media.metadata.subtitle.orEmpty(),
            description = media.metadata.descriptionPlain
                ?: media.metadata.description
                ?: "",
            authors = media.metadata.authors.map { it.name },
            narrators = media.metadata.narrators,
            series = media.metadata.series.map { series ->
                if (series.sequence.isNullOrBlank()) series.name else "${series.name} #${series.sequence}"
            },
            coverUrl = media.coverPath?.toAbsoluteCoverUrl(),
            durationSeconds = media.duration.toInt(),
            chapters = media.chapters.map { chapter ->
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

    private fun String.toAbsoluteCoverUrl(): String {
        return if (startsWith("http://") || startsWith("https://")) this else "$baseUrl$this"
    }

    private fun String.toAbsoluteAudioUrl(token: String): String {
        val absolute = if (startsWith("http://") || startsWith("https://")) this else "$baseUrl$this"
        return if (absolute.contains("token=")) absolute else {
            val separator = if (absolute.contains("?")) "&" else "?"
            "$absolute${separator}token=$token"
        }
    }

    private fun Response<Unit>.requireUnitResponse(action: String) {
        if (!isSuccessful) {
            throw AudiobookShelfApiException(
                "$action: HTTP ${code()}",
                AudiobookShelfApiException.Kind.HTTP
            )
        }
    }
}
