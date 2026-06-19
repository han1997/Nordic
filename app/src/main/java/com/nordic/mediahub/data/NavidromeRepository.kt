package com.nordic.mediahub.data

import android.util.Log
import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeApi
import com.nordic.mediahub.api.NavidromeArtist
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

private const val RECENT_ALBUM_LIMIT = 20
private const val RECENT_SONG_LIMIT = 20
private const val ALBUM_PAGE_SIZE = 100

class NavidromeApiException(message: String, val kind: Kind) : Exception(message) {
    enum class Kind { HTTP, SUBSONIC }
}

class NavidromeRepository(private val config: NavidromeConfig) : NavidromeMusicDataSource {
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

    private fun buildStreamUrl(id: String): String = buildAuthedMediaUrl("stream", id)

    private fun Response<SubsonicResponse>.requireResponse(): SubsonicData {
        if (!isSuccessful) {
            throw NavidromeApiException("HTTP错误: ${code()}", NavidromeApiException.Kind.HTTP)
        }

        val body = body() ?: throw NavidromeApiException("响应为空", NavidromeApiException.Kind.HTTP)
        if (body.response.status == "ok") {
            return body.response
        }

        val detail = body.response.error?.let { "[${it.code}] ${it.message}" } ?: "未知错误"
        throw NavidromeApiException("Subsonic错误: $detail", NavidromeApiException.Kind.SUBSONIC)
    }

    private fun NavidromeAlbum.withCoverArtUrl(): NavidromeAlbum {
        return copy(coverArt = coverArt?.let(::buildCoverArtUrl))
    }

    private fun NavidromeArtist.withInitials(): NavidromeArtist {
        val computedInitials = name.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { word -> word.first().uppercaseChar().toString() }

        return copy(initials = computedInitials)
    }

    private fun NavidromeSong.withCoverArtUrl(fallbackCoverArt: String? = null): NavidromeSong {
        return copy(
            coverArt = (coverArt ?: fallbackCoverArt)?.let(::buildCoverArtUrl),
            streamUrl = buildStreamUrl(id)
        )
    }

    private suspend fun getAlbumList(
        type: String,
        size: Int,
        offset: Int = 0
    ): List<NavidromeAlbum> {
        val auth = config.authParams()
        val subsonic = api.getAlbumList2(
            username = config.username,
            token = auth.token,
            salt = auth.salt,
            type = type,
            size = size,
            offset = offset
        ).requireResponse()
        return subsonic.albumList2?.album?.map { it.withCoverArtUrl() } ?: emptyList()
    }

    private suspend fun getAllAlbums(): List<NavidromeAlbum> {
        val albums = mutableListOf<NavidromeAlbum>()
        var offset = 0

        while (true) {
            val page = getAlbumList(
                type = "alphabeticalByName",
                size = ALBUM_PAGE_SIZE,
                offset = offset
            )
            if (page.isEmpty()) break

            albums += page
            if (page.size < ALBUM_PAGE_SIZE) break
            offset += page.size
        }

        return albums
    }

    private suspend fun getSongsFromAlbums(albums: List<NavidromeAlbum>, limit: Int? = RECENT_SONG_LIMIT): List<NavidromeSong> {
        if (albums.isEmpty()) return emptyList()

        val songs = mutableListOf<NavidromeSong>()
        for (album in albums) {
            if (limit != null && songs.size >= limit) break

            val auth = config.authParams()
            val subsonic = api.getAlbum(config.username, auth.token, auth.salt, albumId = album.id).requireResponse()
            val albumDetail = subsonic.album ?: continue
            val fallbackCoverArt = albumDetail.coverArt

            songs += albumDetail.song.map { song ->
                song.withCoverArtUrl(fallbackCoverArt)
            }
        }

        return if (limit == null) songs else songs.take(limit)
    }

    suspend fun getAlbumSongs(albumId: String) = try {
        val auth = config.authParams()
        val subsonic = api.getAlbum(config.username, auth.token, auth.salt, albumId = albumId).requireResponse()
        subsonic.album?.let { albumDetail ->
            albumDetail.song.map { song ->
                song.withCoverArtUrl(albumDetail.coverArt)
            }
        } ?: emptyList()
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取专辑曲目失败: ${e.message}")
    }

    override suspend fun getRecentlyAddedSongs(albums: List<NavidromeAlbum>): List<NavidromeSong> {
        return getRecentlyAddedSongs(albums, RECENT_SONG_LIMIT)
    }

    suspend fun getRecentlyAddedSongs(albums: List<NavidromeAlbum>, limit: Int) = try {
        getSongsFromAlbums(albums, limit)
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取最近添加曲目失败: ${e.message}")
    }

    override suspend fun getRecentAlbums() = try {
        Log.d("NavidromeRepo", "Getting albums from: $baseUrl")
        val albums = getAlbumList(type = "newest", size = RECENT_ALBUM_LIMIT)
        Log.d("NavidromeRepo", "Got ${albums.size} albums")
        albums
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
        Log.e("NavidromeRepo", "Error getting albums", e)
        throw Exception("获取专辑失败: ${e.message}")
    }

    override suspend fun getAllSongs() = try {
        getSongsFromAlbums(getAllAlbums(), limit = null)
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取全部歌曲失败: ${e.message}")
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
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取歌曲失败: ${e.message}")
    }

    override suspend fun getArtists() = try {
        val auth = config.authParams()
        val subsonic = api.getArtists(config.username, auth.token, auth.salt).requireResponse()
        val artists = subsonic.artists?.index?.flatMap { index ->
            index.artist
        } ?: emptyList()

        val resolvedArtists = artists.map { it.withInitials() }
        Log.d("NavidromeRepo", "Prepared ${resolvedArtists.size} artists")
        resolvedArtists
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
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

    suspend fun search(query: String): SearchMusicResult {
        if (query.isBlank()) return SearchMusicResult()
        return try {
            val auth = config.authParams()
            val response = api.search3(config.username, auth.token, auth.salt, query = query.trim()).requireResponse()
            val result = response.searchResult3 ?: return SearchMusicResult()
            SearchMusicResult(
                artists = result.artist.map { it.withInitials() },
                albums = result.album.map { it.withCoverArtUrl() },
                songs = result.song.map { it.withCoverArtUrl() }
            )
        } catch (e: NavidromeApiException) {
            throw e
        } catch (e: Exception) {
            throw Exception("搜索失败: ${e.message}")
        }
    }

    suspend fun getArtistAlbums(artistId: String): List<NavidromeAlbum> {
        return try {
            val auth = config.authParams()
            val detail = api.getArtist(config.username, auth.token, auth.salt, artistId = artistId)
                .requireResponse().artistDetail
                ?: return emptyList()
            detail.album.map { it.withCoverArtUrl() }
        } catch (e: NavidromeApiException) {
            throw e
        } catch (e: Exception) {
            throw Exception("获取歌手专辑失败: ${e.message}")
        }
    }
}

data class SearchMusicResult(
    val artists: List<NavidromeArtist> = emptyList(),
    val albums: List<NavidromeAlbum> = emptyList(),
    val songs: List<NavidromeSong> = emptyList()
)
