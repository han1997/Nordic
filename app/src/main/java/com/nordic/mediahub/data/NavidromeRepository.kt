package com.nordic.mediahub.data

import android.util.Log
import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeApi
import com.nordic.mediahub.api.NavidromeSong
import com.nordic.mediahub.api.NavidromeStructuredLyrics
import com.nordic.mediahub.api.SubsonicData
import com.nordic.mediahub.api.SubsonicResponse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest
import kotlin.random.Random

class NavidromeRepository(private val config: NavidromeConfig) {
    private val baseUrl = normalizeBaseUrl(config.serverUrl)

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("NavidromeApi", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val api = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .client(OkHttpClient.Builder().addInterceptor(loggingInterceptor).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NavidromeApi::class.java)

    private fun generateSalt(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..12).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getAuthParams(): Pair<String, String> {
        val salt = generateSalt()
        val token = md5("${config.password}$salt")
        return Pair(token, salt)
    }

    private fun normalizeBaseUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun buildAuthedMediaUrl(endpoint: String, id: String): String {
        val (token, salt) = getAuthParams()
        return baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("rest")
            .addPathSegment("$endpoint.view")
            .addQueryParameter("id", id)
            .addQueryParameter("u", config.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Nordic")
            .build()
            .toString()
    }

    private fun buildCoverArtUrl(id: String): String = buildAuthedMediaUrl("getCoverArt", id)

    private fun buildStreamUrl(id: String): String = buildAuthedMediaUrl("stream", id)

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

            val (token, salt) = getAuthParams()
            val subsonic = api.getAlbum(config.username, token, salt, albumId = album.id).requireResponse()
            val albumDetail = subsonic.album ?: continue
            val fallbackCoverArt = albumDetail.coverArt

            songs += albumDetail.song.map { song ->
                song.withCoverArtUrl(fallbackCoverArt)
            }
        }

        return songs.take(limit)
    }

    suspend fun getRecentAlbums() = try {
        val (token, salt) = getAuthParams()
        Log.d("NavidromeRepo", "Getting albums from: $baseUrl")
        val subsonic = api.getAlbumList2(config.username, token, salt).requireResponse()
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
        val (token, salt) = getAuthParams()
        val songs = runCatching {
            val subsonic = api.getRandomSongs(config.username, token, salt).requireResponse()
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
        val (token, salt) = getAuthParams()
        val subsonic = api.getArtists(config.username, token, salt).requireResponse()
        subsonic.artists?.index?.flatMap { index ->
            index.artist.map { artist ->
                artist.copy(coverArt = artist.coverArt?.let(::buildCoverArtUrl))
            }
        } ?: emptyList()
    } catch (e: Exception) {
        if (e.message?.contains("Subsonic错误") == true || e.message?.contains("HTTP错误") == true) {
            throw e
        }
        throw Exception("获取歌手失败: ${e.message}")
    }

    suspend fun getLyrics(song: NavidromeSong): MusicLyrics? {
        val bySongId = runCatching {
            val (token, salt) = getAuthParams()
            api.getLyricsBySongId(config.username, token, salt, songId = song.id)
                .requireResponse()
                .toMusicLyrics(song)
        }.getOrNull()

        if (bySongId != null) return bySongId

        val artist = song.artist?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val (token, salt) = getAuthParams()
            api.getLyrics(config.username, token, salt, artist = artist, title = song.title)
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
