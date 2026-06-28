package com.nordic.mediahub.data

import android.util.Log
import com.nordic.mediahub.api.NavidromeAlbum
import com.nordic.mediahub.api.NavidromeApi
import com.nordic.mediahub.api.NavidromeArtist
import com.nordic.mediahub.api.NavidromePlaylist
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
import java.io.EOFException

private const val RECENT_ALBUM_LIMIT = 20
private const val RECENT_SONG_LIMIT = 20
private const val ALBUM_PAGE_SIZE = 100
private const val RELEASE_YEAR_SORT_FROM_YEAR = 2100
private const val RELEASE_YEAR_SORT_TO_YEAR = 1900
private val lrcTimestampPattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val lrcOffsetPattern = Regex("""^\[offset:([+-]?\d+)]$""", RegexOption.IGNORE_CASE)
private val lrcMetadataLinePattern = Regex("""^\[([A-Za-z][A-Za-z0-9_-]*):.*]$""")
private val lrcMetadataKeys = setOf(
    "al",
    "album",
    "ar",
    "artist",
    "au",
    "by",
    "length",
    "offset",
    "re",
    "ti",
    "title",
    "ve"
)

enum class NavidromeAlbumSort {
    RecentlyAdded,
    ReleaseYear,
    Name
}

private data class AlbumListRequest(
    val type: String,
    val fromYear: Int? = null,
    val toYear: Int? = null
)

class NavidromeApiException(message: String, val kind: Kind) : Exception(message) {
    enum class Kind { HTTP, SUBSONIC, API }
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

    private fun String?.toCoverArtUrlOrNull(): String? {
        return this?.takeIf { it.isNotBlank() }?.let(::buildCoverArtUrl)
    }

    private fun buildStreamUrl(id: String): String = buildAuthedMediaUrl("stream", id)

    private suspend fun requestSubsonic(request: suspend () -> Response<SubsonicResponse>): SubsonicData {
        val response = try {
            request()
        } catch (error: EOFException) {
            throw NavidromeApiException("响应为空", NavidromeApiException.Kind.API)
        }

        return response.requireResponse()
    }

    private fun Response<SubsonicResponse>.requireResponse(): SubsonicData {
        if (!isSuccessful) {
            throw NavidromeApiException("HTTP错误: ${code()}", NavidromeApiException.Kind.HTTP)
        }

        val body = body() ?: throw NavidromeApiException("响应为空", NavidromeApiException.Kind.API)
        if (body.response.status == "ok") {
            return body.response
        }

        val detail = body.response.error?.let { "[${it.code}] ${it.message}" } ?: "未知错误"
        throw NavidromeApiException("Subsonic错误: $detail", NavidromeApiException.Kind.SUBSONIC)
    }

    private fun NavidromeAlbum.withCoverArtUrl(): NavidromeAlbum {
        return copy(coverArt = coverArt.toCoverArtUrlOrNull())
    }

    private fun NavidromePlaylist.withCoverArtUrl(): NavidromePlaylist {
        return copy(coverArt = coverArt.toCoverArtUrlOrNull())
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
            coverArt = coverArt.toCoverArtUrlOrNull() ?: fallbackCoverArt.toCoverArtUrlOrNull(),
            streamUrl = buildStreamUrl(id)
        )
    }

    private suspend fun getAlbumList(
        type: String,
        size: Int,
        offset: Int = 0,
        fromYear: Int? = null,
        toYear: Int? = null
    ): List<NavidromeAlbum> {
        val auth = config.authParams()
        val subsonic = requestSubsonic {
            api.getAlbumList2(
                username = config.username,
                token = auth.token,
                salt = auth.salt,
                type = type,
                size = size,
                offset = offset,
                fromYear = fromYear,
                toYear = toYear
            )
        }
        return subsonic.albumList2?.album.orEmpty().map { it.withCoverArtUrl() }
    }

    private fun NavidromeAlbumSort.toAlbumListRequest(): AlbumListRequest {
        return when (this) {
            NavidromeAlbumSort.RecentlyAdded -> AlbumListRequest(type = "newest")
            NavidromeAlbumSort.ReleaseYear -> AlbumListRequest(
                type = "byYear",
                fromYear = RELEASE_YEAR_SORT_FROM_YEAR,
                toYear = RELEASE_YEAR_SORT_TO_YEAR
            )
            NavidromeAlbumSort.Name -> AlbumListRequest(type = "alphabeticalByName")
        }
    }

    private suspend fun getPagedAlbums(sort: NavidromeAlbumSort): List<NavidromeAlbum> {
        val request = sort.toAlbumListRequest()
        val albums = mutableListOf<NavidromeAlbum>()
        var offset = 0

        while (true) {
            val page = getAlbumList(
                type = request.type,
                size = ALBUM_PAGE_SIZE,
                offset = offset,
                fromYear = request.fromYear,
                toYear = request.toYear
            )
            if (page.isEmpty()) break

            albums += page
            if (page.size < ALBUM_PAGE_SIZE) break
            offset += page.size
        }

        return albums
    }

    private suspend fun getAllAlbums(): List<NavidromeAlbum> {
        return getPagedAlbums(NavidromeAlbumSort.Name)
    }

    private suspend fun getSongsFromAlbums(albums: List<NavidromeAlbum>, limit: Int? = RECENT_SONG_LIMIT): List<NavidromeSong> {
        if (albums.isEmpty()) return emptyList()

        val songs = mutableListOf<NavidromeSong>()
        for (album in albums) {
            if (limit != null && songs.size >= limit) break

            val auth = config.authParams()
            val subsonic = requestSubsonic {
                api.getAlbum(config.username, auth.token, auth.salt, albumId = album.id)
            }
            val albumDetail = subsonic.album ?: continue
            val fallbackCoverArt = albumDetail.coverArt

            songs += albumDetail.song.orEmpty().map { song ->
                song.withCoverArtUrl(fallbackCoverArt)
            }
        }

        return if (limit == null) songs else songs.take(limit)
    }

    suspend fun getAlbumSongs(albumId: String) = try {
        val auth = config.authParams()
        val subsonic = requestSubsonic {
            api.getAlbum(config.username, auth.token, auth.salt, albumId = albumId)
        }
        subsonic.album?.let { albumDetail ->
            albumDetail.song.orEmpty().map { song ->
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

    suspend fun getAlbums(sort: NavidromeAlbumSort) = try {
        getPagedAlbums(sort)
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取专辑列表失败: ${e.message}")
    }

    suspend fun getPlaylists() = try {
        val auth = config.authParams()
        val subsonic = requestSubsonic {
            api.getPlaylists(config.username, auth.token, auth.salt)
        }
        subsonic.playlists?.playlist.orEmpty().map { it.withCoverArtUrl() }
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取歌单失败: ${e.message}")
    }

    suspend fun getPlaylistSongs(playlistId: String) = try {
        val auth = config.authParams()
        val playlist = requestSubsonic {
            api.getPlaylist(config.username, auth.token, auth.salt, playlistId = playlistId)
        }.playlist
        playlist?.let { detail ->
            detail.entry.orEmpty().map { song ->
                song.withCoverArtUrl(detail.coverArt)
            }
        } ?: emptyList()
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取歌单曲目失败: ${e.message}")
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
            val subsonic = requestSubsonic {
                api.getRandomSongs(config.username, auth.token, auth.salt)
            }
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
        val subsonic = requestSubsonic {
            api.getArtists(config.username, auth.token, auth.salt)
        }
        val artists = subsonic.artists?.index.orEmpty().flatMap { index ->
            index.artist.orEmpty()
        }

        val resolvedArtists = artists.map { it.withInitials() }
        resolvedArtists
    } catch (e: NavidromeApiException) {
        throw e
    } catch (e: Exception) {
        throw Exception("获取歌手失败: ${e.message}")
    }

    suspend fun getLyrics(song: NavidromeSong): MusicLyrics? {
        val bySongId = runCatching {
            val auth = config.authParams()
            requestSubsonic {
                api.getLyricsBySongId(config.username, auth.token, auth.salt, songId = song.id)
            }
                .toMusicLyrics()
        }.getOrNull()

        if (bySongId != null) return bySongId

        val artist = song.artist?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val auth = config.authParams()
            requestSubsonic {
                api.getLyrics(config.username, auth.token, auth.salt, artist = artist, title = song.title)
            }
                .toMusicLyrics()
        }.getOrNull()
    }

    private fun SubsonicData.toMusicLyrics(): MusicLyrics? {
        val structured = lyricsList?.structuredLyrics
            ?.filter { lyrics -> lyrics.line.any { it.value.isNotBlank() } }
            ?.sortedByDescending { it.synced }
            ?.firstOrNull()
            ?.toMusicLyrics()

        if (structured != null) return structured

        return lyrics?.value
            ?.takeIf { it.isNotBlank() }
            ?.parsePlainLyrics()
    }

    private fun NavidromeStructuredLyrics.toMusicLyrics(): MusicLyrics? {
        val offsetMillis = offset?.toInt() ?: 0
        val parsedLines = line.mapNotNull { lyricLine ->
            val text = lyricLine.value.trim()
            if (text.isBlank()) return@mapNotNull null

            MusicLyricsLine(
                startMillis = lyricLine.start
                    ?.toInt()
                    ?.coerceAtLeast(0)
                    ?.let { startMillis -> resolveLyricStartMillisWithOffset(startMillis, offsetMillis) },
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
        val rawLines = lines()
        val offsetMillis = rawLines.firstNotNullOfOrNull { rawLine ->
            rawLine.toLrcOffsetMillisOrNull()
        } ?: 0
        val parsedLines = rawLines.flatMap { rawLine ->
            val matches = lrcTimestampPattern.findAll(rawLine).toList()
            val text = rawLine.replace(lrcTimestampPattern, "").trim()
            if (text.isBlank()) return@flatMap emptyList()

            if (matches.isEmpty()) {
                if (rawLine.isLrcMetadataLine()) {
                    emptyList()
                } else {
                    listOf(MusicLyricsLine(text = text))
                }
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
                        startMillis = resolveLrcStartMillis(minutes, seconds, millis, offsetMillis),
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

    private fun String.isLrcMetadataLine(): Boolean {
        val key = lrcMetadataLinePattern.matchEntire(trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            ?: return false
        return key in lrcMetadataKeys
    }

    private fun String.toLrcOffsetMillisOrNull(): Int? {
        return lrcOffsetPattern.matchEntire(trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun resolveLrcStartMillis(minutes: Int, seconds: Int, millis: Int, offsetMillis: Int): Int {
        val startMillis = minutes * 60_000 + seconds * 1000 + millis
        return resolveLyricStartMillisWithOffset(startMillis, offsetMillis)
    }

    private fun resolveLyricStartMillisWithOffset(startMillis: Int, offsetMillis: Int): Int {
        return (startMillis - offsetMillis).coerceAtLeast(0)
    }

    suspend fun search(query: String): SearchMusicResult {
        if (query.isBlank()) return SearchMusicResult()
        return try {
            val auth = config.authParams()
            val response = requestSubsonic {
                api.search3(config.username, auth.token, auth.salt, query = query.trim())
            }
            val result = response.searchResult3 ?: return SearchMusicResult()
            SearchMusicResult(
                artists = result.artist.orEmpty().map { it.withInitials() },
                albums = result.album.orEmpty().map { it.withCoverArtUrl() },
                songs = result.song.orEmpty().map { it.withCoverArtUrl() }
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
            val detail = requestSubsonic {
                api.getArtist(config.username, auth.token, auth.salt, artistId = artistId)
            }.artistDetail
                ?: return emptyList()
            detail.album.orEmpty().map { it.withCoverArtUrl() }
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
