package com.nordic.mediahub.data

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private const val MUSIC_DOWNLOAD_METADATA_SUFFIX = ".metadata.json"
private val musicDownloadMetadataGson = Gson()

enum class DownloadState { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED }

data class DownloadStateEntry(
    val state: DownloadState = DownloadState.NOT_DOWNLOADED,
    val progress: Float = 0f,
    val song: NavidromeSong? = null,
    val errorMessage: String? = null
)

internal fun musicDownloadRoot(context: Context): File =
    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "music")

internal fun musicDownloadDirectory(context: Context, sourceId: String): File {
    require(sourceId.isBlank() || sourceId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
    val root = musicDownloadRoot(context)
    return if (sourceId.isBlank()) root else File(root, sourceId)
}

internal object MusicDownloadManagers {
    private val managers = ConcurrentHashMap<String, MusicDownloadManager>()
    fun get(context: Context, sourceId: String): MusicDownloadManager = managers.computeIfAbsent(sourceId) {
        MusicDownloadManager(context.applicationContext, sourceId)
    }
    fun cancel(sourceId: String) { managers[sourceId]?.cancelAll() }
}

class MusicDownloadManager internal constructor(
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val downloadDir: File,
    private val sourceId: String = ""
) {
    constructor(context: Context, sourceId: String = "") : this(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        OkHttpClient.Builder().addInterceptor(MediaAuthHeaderInterceptor())
            .addNetworkInterceptor(ScopedMediaNetworkInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS).build(),
        musicDownloadDirectory(context, sourceId), sourceId
    )

    private val states = ConcurrentHashMap<String, DownloadStateEntry>()
    private val calls = ConcurrentHashMap<String, Call>()
    private val _downloadStates = MutableStateFlow<Map<String, DownloadStateEntry>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadStateEntry>> = _downloadStates.asStateFlow()
    private fun updateState(id: String, entry: DownloadStateEntry) { states[id] = entry; _downloadStates.value = states.toMap() }

    fun downloadSong(song: NavidromeSong, config: NavidromeConfig) {
        require(config.sourceId == sourceId && (song.sourceId.isBlank() || song.sourceId == sourceId))
        if (!beginDownloading(song)) return
        scope.launch { performDownload(song.copy(sourceId = sourceId), config) }
    }
    internal fun beginDownloading(song: NavidromeSong): Boolean {
        var launch = false
        states.compute(song.id) { _, existing ->
            if (existing?.state == DownloadState.DOWNLOADING) existing else {
                launch = true
                DownloadStateEntry(DownloadState.DOWNLOADING, 0f, song)
            }
        }
        if (launch) _downloadStates.value = states.toMap()
        return launch
    }
    internal suspend fun performDownload(song: NavidromeSong, config: NavidromeConfig) {
        var temp: File? = null
        try {
            require(config.sourceId == sourceId)
            if (!downloadDir.exists() && !downloadDir.mkdirs()) throw IOException("无法创建下载目录")
            ScopedMediaRegistry.registerNavidrome(config)
            val url = config.normalizedBaseUrl().toHttpUrl().newBuilder()
                .addPathSegment("rest").addPathSegment("download.view").addQueryParameter("id", song.id)
                .apply { if (sourceId.isBlank()) addNavidromeAuth(config) }.build().toString().forMediaSource(sourceId)
            val call = client.newCall(Request.Builder().url(url).build())
            calls[song.id] = call
            var target: File? = null
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
                val body = response.body ?: throw IOException("下载响应为空")
                val stem = safeMusicFileId(song.id)
                val extension = extensionFromContentType(response.header("Content-Type").orEmpty())
                target = File(downloadDir, "$stem.$extension")
                temp = File(downloadDir, "$stem.$extension.tmp")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    temp!!.outputStream().use { output ->
                        val buffer = ByteArray(32768)
                        var copied = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            if (total > 0) updateState(song.id, DownloadStateEntry(DownloadState.DOWNLOADING,
                                (copied.toFloat() / total).coerceIn(0f, 1f), song))
                        }
                        if (total >= 0 && copied != total) throw IOException("下载未完成，请重试")
                    }
                }
            }
            coroutineContext.ensureActive()
            val destination = requireNotNull(target)
            if (destination.exists() && !destination.delete()) throw IOException("无法替换下载文件")
            if (temp?.renameTo(destination) != true) throw IOException("保存下载失败")
            saveDownloadedSongMetadata(metadataFile(song.id), song.copy(
                sourceId = sourceId, streamUrl = song.streamUrl?.let(::stripAuthQuery), coverArt = song.coverArt?.let(::stripAuthQuery)))
            updateState(song.id, DownloadStateEntry(DownloadState.DOWNLOADED, 1f, song.copy(sourceId = sourceId)))
        } catch (error: Exception) {
            temp?.delete()
            updateState(song.id, DownloadStateEntry(song = song, errorMessage = "下载未完成，请检查连接后重试"))
            if (error is CancellationException) throw error
        } finally {
            calls.remove(song.id)
        }
    }
    fun cancelDownload(songId: String) { calls[songId]?.cancel() }
    fun cancelAll() { calls.values.forEach { it.cancel() } }
    fun deleteDownload(songId: String) {
        if (states[songId]?.state == DownloadState.DOWNLOADING) return
        val stem = safeMusicFileId(songId)
        downloadDir.listFiles()?.filter { it.isFile && isDownloadedMusicFile(it.name) && it.name.substringBeforeLast('.') == stem }
            ?.forEach { if (!it.delete()) throw IOException("删除下载失败") }
        metadataFile(songId).takeIf { it.exists() }?.let { if (!it.delete()) throw IOException("删除下载信息失败") }
        states.remove(songId)
        _downloadStates.value = states.toMap()
    }
    fun isDownloaded(songId: String): Boolean = states[songId]?.state == DownloadState.DOWNLOADED
    fun getLocalFilePath(songId: String): String? = downloadedAudioFile(songId)?.absolutePath
    fun getDownloadedSongs(): List<NavidromeSong> = states.values.filter { it.state == DownloadState.DOWNLOADED }.mapNotNull { it.song }
    fun restoreDownloadState() {
        val files = downloadDir.listFiles()?.filter { it.isFile && isDownloadedMusicFile(it.name) } ?: emptyList()
        for (file in files) {
            val stem = file.name.substringBeforeLast('.')
            val metadata = loadDownloadedSongMetadata(File(downloadDir, "$stem$MUSIC_DOWNLOAD_METADATA_SUFFIX"))
            val id = metadata?.id ?: stem
            if (states[id]?.state == DownloadState.DOWNLOADING) continue
            states[id] = DownloadStateEntry(DownloadState.DOWNLOADED, 1f, metadata?.copy(sourceId = sourceId))
        }
        _downloadStates.value = states.toMap()
    }
    fun updateSongMetadata(songs: List<NavidromeSong>) {
        for (song in songs) {
            if (song.sourceId.isNotBlank() && song.sourceId != sourceId) continue
            val entry = states[song.id] ?: continue
            if (entry.state == DownloadState.DOWNLOADED) {
                states[song.id] = entry.copy(song = song)
                saveDownloadedSongMetadata(metadataFile(song.id), song)
            }
        }
        _downloadStates.value = states.toMap()
    }
    private fun downloadedAudioFile(id: String): File? = downloadDir.listFiles()?.firstOrNull {
        it.isFile && isDownloadedMusicFile(it.name) && it.name.substringBeforeLast('.') == safeMusicFileId(id)
    }
    private fun metadataFile(id: String) = File(downloadDir, musicDownloadMetadataFileName(safeMusicFileId(id)))
}

internal fun safeMusicFileId(id: String): String =
    if (id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) id else MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray()).joinToString("") { "%02x".format(it) }

internal fun extensionFromContentType(contentType: String): String {
    return when {
        contentType.contains("ogg", ignoreCase = true) -> "ogg"
        contentType.contains("flac", ignoreCase = true) -> "flac"
        contentType.contains("wav", ignoreCase = true) -> "wav"
        contentType.contains("aac", ignoreCase = true) -> "aac"
        contentType.contains("m4a", ignoreCase = true) -> "m4a"
        contentType.contains("m4b", ignoreCase = true) -> "m4b"
        contentType.contains("mp4", ignoreCase = true) -> "m4a"
        contentType.contains("opus", ignoreCase = true) -> "opus"
        contentType.contains("wma", ignoreCase = true) -> "wma"
        else -> "mp3"
    }
}

internal fun musicDownloadMetadataFileName(songId: String): String {
    return "$songId$MUSIC_DOWNLOAD_METADATA_SUFFIX"
}

internal fun isDownloadedMusicFile(fileName: String): Boolean {
    return !fileName.endsWith(".tmp") && !fileName.endsWith(MUSIC_DOWNLOAD_METADATA_SUFFIX)
}

internal fun saveDownloadedSongMetadata(file: File, song: NavidromeSong) {
    runCatching {
        file.parentFile?.mkdirs()
        file.writeText(musicDownloadMetadataGson.toJson(song), Charsets.UTF_8)
    }
}

internal fun loadDownloadedSongMetadata(file: File): NavidromeSong? {
    return runCatching {
        if (!file.exists()) return null
        musicDownloadMetadataGson.fromJson(file.readText(Charsets.UTF_8), NavidromeSong::class.java)
    }.getOrNull()
}

/** One-time upgrade hygiene; old downloads remain unassigned and their media bytes are untouched. */
internal fun sanitizeLegacyMusicMetadata(root: File) {
    val canonical = root.canonicalFile
    root.listFiles()?.filter { it.isFile && it.name.endsWith(MUSIC_DOWNLOAD_METADATA_SUFFIX) }?.forEach { file ->
        if (file.canonicalFile.parentFile != canonical) return@forEach
        val song = loadDownloadedSongMetadata(file) ?: return@forEach
        val clean = song.copy(streamUrl = song.streamUrl?.let(::stripAuthQuery), coverArt = song.coverArt?.let(::stripAuthQuery))
        if (song != clean) file.writeText(musicDownloadMetadataGson.toJson(clean), Charsets.UTF_8)
    }
}