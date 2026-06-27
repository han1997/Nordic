package com.nordic.mediahub.data

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.nordic.mediahub.api.NavidromeSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val MUSIC_DOWNLOAD_METADATA_SUFFIX = ".metadata.json"
private val musicDownloadMetadataGson = Gson()

enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED
}

data class DownloadStateEntry(
    val state: DownloadState = DownloadState.NOT_DOWNLOADED,
    val progress: Float = 0f,
    val song: NavidromeSong? = null
)

class MusicDownloadManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            }
        )
        .build()

    private val downloadDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), ".").also { it.mkdirs() }

    private val states = ConcurrentHashMap<String, DownloadStateEntry>()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadStateEntry>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadStateEntry>> = _downloadStates.asStateFlow()

    private fun updateState(songId: String, entry: DownloadStateEntry) {
        states[songId] = entry
        _downloadStates.value = states.toMap()
    }

    private fun removeState(songId: String) {
        states.remove(songId)
        _downloadStates.value = states.toMap()
    }

    fun downloadSong(song: NavidromeSong, config: NavidromeConfig) {
        if (states[song.id]?.state == DownloadState.DOWNLOADING) return

        updateState(song.id, DownloadStateEntry(state = DownloadState.DOWNLOADING, progress = 0f, song = song))

        scope.launch {
            try {
                val auth = config.authParams()
                val baseUrl = config.normalizedBaseUrl()
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("rest")
                    .addPathSegment("download.view")
                    .addQueryParameter("u", config.username)
                    .addQueryParameter("t", auth.token)
                    .addQueryParameter("s", auth.salt)
                    .addQueryParameter("v", NAVIDROME_API_VERSION)
                    .addQueryParameter("c", NAVIDROME_CLIENT_NAME)
                    .addQueryParameter("id", song.id)
                    .build()
                    .toString()

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    updateState(song.id, DownloadStateEntry(state = DownloadState.NOT_DOWNLOADED, progress = 0f))
                    return@launch
                }

                val body = response.body ?: run {
                    updateState(song.id, DownloadStateEntry(state = DownloadState.NOT_DOWNLOADED, progress = 0f))
                    return@launch
                }

                val contentLength = body.contentLength().coerceAtLeast(0L)
                val contentType = response.header("Content-Type", "audio/mpeg") ?: "audio/mpeg"
                val extension = extensionFromContentType(contentType)
                val fileName = "${song.id}.${extension}"
                val targetFile = File(downloadDir, fileName)
                val tempFile = File(downloadDir, "$fileName.tmp")

                withContext(Dispatchers.IO) {
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Long = 0
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesRead += read
                                if (contentLength > 0L) {
                                    val progress = (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                    updateState(
                                        song.id,
                                        DownloadStateEntry(state = DownloadState.DOWNLOADING, progress = progress, song = song)
                                    )
                                }
                            }
                        }
                    }
                }

                if (tempFile.exists()) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                }
                saveDownloadedSongMetadata(metadataFile(song.id), song)

                updateState(song.id, DownloadStateEntry(state = DownloadState.DOWNLOADED, progress = 1f, song = song))
            } catch (_: Exception) {
                val tempFile = File(downloadDir, "${song.id}.tmp")
                if (tempFile.exists()) tempFile.delete()
                updateState(song.id, DownloadStateEntry(state = DownloadState.NOT_DOWNLOADED, progress = 0f))
            }
        }
    }

    fun deleteDownload(songId: String) {
        val entry = states[songId]
        if (entry?.state == DownloadState.DOWNLOADING) return

        downloadDir.listFiles()
            ?.filter { file -> isDownloadedMusicFile(file.name) && file.name.substringBeforeLast(".") == songId }
            ?.forEach { file -> file.delete() }
        metadataFile(songId).delete()
        removeState(songId)
    }

    fun isDownloaded(songId: String): Boolean {
        val entry = states[songId]
        return entry?.state == DownloadState.DOWNLOADED
    }

    fun getLocalFilePath(songId: String): String? {
        val entry = states[songId]
        if (entry?.state != DownloadState.DOWNLOADED) return null
        val file = downloadedAudioFile(songId)
        return if (file.exists()) file.absolutePath else null
    }

    fun getDownloadedSongs(): List<NavidromeSong> {
        return states.values.filter { it.state == DownloadState.DOWNLOADED && it.song != null }.map { it.song!! }
    }

    fun restoreDownloadState() {
        val dir = downloadDir
        if (!dir.exists()) return

        val existingFiles = dir.listFiles()?.filter { file -> isDownloadedMusicFile(file.name) } ?: emptyList()
        for (file in existingFiles) {
            val songId = file.name.substringBeforeLast(".")
            if (states[songId]?.state == DownloadState.DOWNLOADED) continue
            states[songId] = DownloadStateEntry(
                state = DownloadState.DOWNLOADED,
                progress = 1f,
                song = loadDownloadedSongMetadata(metadataFile(songId))
            )
        }
        _downloadStates.value = states.toMap()
    }

    fun updateSongMetadata(songs: List<NavidromeSong>) {
        val songMap = songs.associateBy { it.id }
        var changed = false
        for ((id, entry) in states) {
            val song = songMap[id]
            if (song != null && entry.state == DownloadState.DOWNLOADED) {
                if (entry.song == null) {
                    states[id] = entry.copy(song = song)
                    changed = true
                }
                saveDownloadedSongMetadata(metadataFile(id), song)
            } else if (song != null && entry.song == null) {
                states[id] = entry.copy(song = song)
                changed = true
            }
        }
        if (changed) {
            _downloadStates.value = states.toMap()
        }
    }

    private fun downloadedAudioFile(songId: String): File {
        return downloadDir.listFiles()?.firstOrNull { file ->
            isDownloadedMusicFile(file.name) && file.name.substringBeforeLast(".") == songId
        } ?: File(downloadDir, "$songId.mp3")
    }

    private fun metadataFile(songId: String): File {
        return File(downloadDir, musicDownloadMetadataFileName(songId))
    }
}

internal fun extensionFromContentType(contentType: String): String {
    return when {
        contentType.contains("ogg", ignoreCase = true) -> "ogg"
        contentType.contains("flac", ignoreCase = true) -> "flac"
        contentType.contains("wav", ignoreCase = true) -> "wav"
        contentType.contains("aac", ignoreCase = true) -> "aac"
        contentType.contains("m4a", ignoreCase = true) -> "m4a"
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
