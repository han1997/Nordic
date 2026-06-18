package com.nordic.mediahub.data

import android.util.Log
import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeApi
import com.nordic.mediahub.api.NavidromeArtist
import com.nordic.mediahub.api.NavidromeSong
import com.nordic.mediahub.api.NavidromeStructuredLyrics
import com.nordic.mediahub.api.SubsonicData
import com.nordic.mediahub.api.SubsonicResponse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NavidromeRepository(private val config: NavidromeConfig) {
    private val baseUrl = config.normalizedBaseUrl()

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("NavidromeApi", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val apiClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()

    private val api = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .client(apiClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NavidromeApi::class.java)

    private fun buildAuthedMediaUrl(
        endpoint: String,
        id: String,
        extraParams: Map<String, String> = emptyMap()
    ): String {
        val builder = baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("rest")
            .addPathSegment("$endpoint.view")
            .addQueryParameter("id", id)
            .addNavidromeAuth(config)
        extraParams.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun buildCoverArtUrl(
        id: String,
        size: Int? = null,
        square: Boolean? = null
    ): String {
        val params = buildMap {
            size?.let { put("size", it.toString()) }
            square?.let { put("square", it.toString()) }
        }
        return buildAuthedMediaUrl("getCoverArt", id, params)
    }

    private fun buildArtistCoverArtUrl(coverArtId: String): String {
        return buildCoverArtUrl(coverArtId, size = 600, square = true)
    }

    private fun buildStreamUrl(id: String): String = buildAuthedMediaUrl("stream", id)

    private fun normalizeImageUrl(url: String): String {
        val trimmed = url.trim()
        val resolvedUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed.toHttpUrlOrNull()
        } else {
            "$baseUrl/".toHttpUrl().resolve(trimmed)
        }

        return resolvedUrl
            ?.withNavidromeAuthIfNeeded()
            ?.toString()
            ?: trimmed
    }

    private fun HttpUrl.withNavidromeAuthIfNeeded(): HttpUrl {
        val serverUrl = "$baseUrl/".toHttpUrl()
        val sameServer = scheme == serverUrl.scheme && host == serverUrl.host && port == serverUrl.port
        if (!sameServer || !encodedPath.contains("/rest/") || queryParameter("u") != null) {
            return this
        }

        return newBuilder()
            .addNavidromeAuth(config)
            .build()
    }

    private fun Response<SubsonicResponse>.requireResponse(): SubsonicData {
        if (!isSuccessful) {
            throw Exception("HTTP错误: ${code()}")
        }

        val body = body() ?: throw Exception("响应为空")
        if (body.response.status == "ok") {
            return body.response
        }

        val detail = body.response.error?.let { "[${it.code}] ${it.message}" } ?: "未知错误"
        throw Exception("Subsonic错误: $detail")
    }

    private fun NavidromeAlbum.withCoverArtUrl(): NavidromeAlbum {
        return copy(coverArt = coverArt?.let(::buildCoverArtUrl))
    }

    private fun NavidromeArtist.withArtistImageUrl(): NavidromeArtist {
        val imageUrl = coverArt
            ?.takeIf { it.isNotBlank() }
            ?.let(::buildArtistCoverArtUrl)
            ?: artistImageUrl
                ?.takeIf { it.isNotBlank() }
                ?.let(::normalizeImageUrl)

        return copy(
            coverArt = imageUrl,
            artistImageUrl = imageUrl
        )
    }

    private fun NavidromeSong.withCoverArtUrl(fallbackCoverArt: String? = null): NavidromeSong {
        return copy(
            coverArt = (coverArt ?: fallbackCoverArt)?.let(::buildCoverArtUrl),
            streamUrl = buildStreamUrl(id)
        )
    }

    private suspend fun getSongsFromAlbums(albums: List<NavidromeAlbum>, limit: Int = 20): List<NavidromeSong> {
        if (albums.isEmpty()) return emptyList()

        val songs = mutableListOf<NavidromeSong>()
        for (album in albums) {
            if (songs.size >= limit) break

            val auth = config.authParams()
            val subsonic = api.getAlbum(config.username, auth.token, auth.salt, albumId = album.id).requireResponse()
            val albumDetail = subsonic.album ?: continue
            val fallbackCoverArt = albumDetail.coverArt

            songs += albumDetail.song.map { song ->
                song.withCoverArtUrl(fallbackCoverArt)
            }
        }

        return songs.take(limit)
    }

    suspend fun getAlbumSongs(albumId: String) = try {
        val auth = config.authParams()
        val subsonic = api.getAlbum(config.username, auth.token, auth.salt, albumId = albumId).requireResponse()
        subsonic.album?.let { albumDetail ->
            albumDetail.song.map { song ->
                song.withCoverArtUrl(albumDetail.coverArt)
            }
        } ?: emptyList()
    } catch (e: Exception) {
        if (e.message?.contains("Subsonic错误") == true || e.message?.contains("HTTP错误") == true) {
            throw e
        }
        throw Exception("获取专辑曲目失败: ${e.message}")
    }

    suspend fun getRecentlyAddedSongs(albums: List<NavidromeAlbum>, limit: Int = 20) = try {
        getSongsFromAlbums(albums, limit)
    } catch (e: Exception) {
        if (e.message?.contains("Subsonic错误") == true || e.message?.contains("HTTP错误") == true) {
            throw e
        }
        throw Exception("获取最近添加曲目失败: ${e.message}")
    }

    suspend fun getRecentAlbums() = try {
        val auth = config.authParams()
        Log.d("NavidromeRepo", "Getting albums from: $baseUrl")
        val subsonic = api.getAlbumList2(config.username, auth.token, auth.salt).requireResponse()
        val albums = subsonic.albumList2?.album?.map { it.withCoverArtUrl() } ?: emptyList()
        Log.d("NavidromeRepo", "Got ${albums.size} albums")
        albums
    } catch (e: Exception) {
        Log.e("NavidromeRepo", "Error getting albums", e)
        if (e.message?.contains("Subsonic错误") == true || e.message?.contains("HTTP错误") == true) {
            throw e
        }
        throw Exception("获取专辑失败: ${e.message}")
    }

    suspend fun getRecentSongs() = try {
        val auth = config.authParams()
        val songs = runCatching {
            val subsonic = api.getRandomSongs(config.username, auth.token, auth.salt).requireResponse()
            subsonic.randomSongs?.song?.map { it.withCoverArtUrl() } ?: emptyList()
        }.getOrElse {
            getSongsFromAlbums(getRecentAlbums())
        }

        if (songs.isNotEmpty()) songs else getSongsFromAlbums(getRecentAlbums())
    } catch (e: Exception) {
        if (e.message?.contains("Subsonic错误") == true || e.message?.contains("HTTP错误") == true) {
            throw e
        }
        throw Exception("获取歌曲失败: ${e.message}")
    }

    suspend fun getArtists() = try {
        val auth = config.authParams()
        val subsonic = api.getArtists(config.username, auth.token, auth.salt).requireResponse()
        val artists = subsonic.artists?.index?.flatMap { index ->
            index.artist
        } ?: emptyList()

        val resolvedArtists = artists.map { artist ->
            artist.withArtistImageUrl()
        }
        Log.d(
            "NavidromeRepo",
            "Prepared ${resolvedArtists.count { !it.coverArt.isNullOrBlank() }} artist image URLs from getArtists, total=${resolvedArtists.size}"
        )
        resolvedArtists
    } catch (e: Exception) {
        if (e.message?.contains("Subsonic错误") == true || e.message?.contains("HTTP错误") == true) {
            throw e
        }
        throw Exception("获取歌手失败: ${e.message}")
    }

    suspend fun getLyrics(song: NavidromeSong): MusicLyrics? {
        val bySongId = runCatching {
            val auth = config.authParams()
            api.getLyricsBySongId(config.username, auth.token, auth.salt, songId = song.id)
                .requireResponse()
                .toMusicLyrics(song)
        }.getOrNull()

        if (bySongId != null) return bySongId

        val artist = song.artist?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val auth = config.authParams()
            api.getLyrics(config.username, auth.token, auth.salt, artist = artist, title = song.title)
                .requireResponse()
                .toMusicLyrics(song)
        }.getOrNull()
    }

    private fun SubsonicData.toMusicLyrics(song: NavidromeSong): MusicLyrics? {
        val structured = lyricsList?.structuredLyrics
            ?.filter { lyrics -> lyrics.line.any { it.value.isNotBlank() } }
            ?.sortedByDescending { it.synced }
            ?.firstOrNull()
            ?.toMusicLyrics(song)

        if (structured != null) return structured

        return lyrics?.value
            ?.takeIf { it.isNotBlank() }
            ?.parsePlainLyrics()
    }

    private fun NavidromeStructuredLyrics.toMusicLyrics(song: NavidromeSong): MusicLyrics? {
        val parsedLines = line.mapNotNull { lyricLine ->
            val text = lyricLine.value.trim()
            if (text.isBlank()) return@mapNotNull null

            MusicLyricsLine(
                startMillis = lyricLine.start?.toLyricStartMillis(song.duration),
                text = text
            )
        }

        if (parsedLines.isEmpty()) return null

        return MusicLyrics(
            lines = parsedLines,
            synced = synced && parsedLines.any { it.startMillis != null }
        )
    }

    private fun String.parsePlainLyrics(): MusicLyrics? {
        val lrcPattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val parsedLines = lines().flatMap { rawLine ->
            val matches = lrcPattern.findAll(rawLine).toList()
            val text = rawLine.replace(lrcPattern, "").trim()
            if (text.isBlank()) return@flatMap emptyList()

            if (matches.isEmpty()) {
                listOf(MusicLyricsLine(text = text))
            } else {
                matches.map { match ->
                    val minutes = match.groupValues[1].toIntOrNull() ?: 0
                    val seconds = match.groupValues[2].toIntOrNull() ?: 0
                    val fraction = match.groupValues.getOrNull(3).orEmpty()
                    val millis = when (fraction.length) {
                        1 -> fraction.toInt() * 100
                        2 -> fraction.toInt() * 10
                        3 -> fraction.toInt()
                        else -> 0
                    }
                    MusicLyricsLine(
                        startMillis = minutes * 60_000 + seconds * 1000 + millis,
                        text = text
                    )
                }
            }
        }

        if (parsedLines.isEmpty()) return null

        val synced = parsedLines.any { it.startMillis != null }
        return MusicLyrics(
            lines = if (synced) parsedLines.sortedBy { it.startMillis ?: Int.MAX_VALUE } else parsedLines,
            synced = synced
        )
    }

    private fun Double.toLyricStartMillis(songDurationSeconds: Int): Int {
        val looksLikeSeconds = songDurationSeconds > 0 && this <= songDurationSeconds + 1
        return if (looksLikeSeconds) {
            (this * 1000).toInt()
        } else {
            toInt()
        }.coerceAtLeast(0)
    }
}
