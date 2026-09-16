package com.nordic.mediahub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nordic.mediahub.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class WebDavBrowserState(
    val path: String = "/", val entries: List<WebDavEntry> = emptyList(), val loading: Boolean = true,
    val preparing: Boolean = false, val error: String? = null, val fetchedAt: Long? = null,
    val favorites: List<WebDavFolder> = emptyList(), val progress: List<WebDavProgress> = emptyList()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class WebDavBrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val configRepository = ConfigRepository(application)
    private val _state = MutableStateFlow(WebDavBrowserState())
    val state = _state.asStateFlow()
    val query = MutableStateFlow("")
    val visibleEntries = combine(_state.map { it.entries }.distinctUntilChanged(), query, configRepository.preferences) { rows, search, prefs ->
        Triple(rows, search, prefs)
    }.mapLatest { (rows, search, prefs) -> withContext(Dispatchers.Default) { visibleWebDavEntries(rows, search, prefs) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var config: VideoServerConfig? = null
    private var repository: WebDavRepository? = null
    private var local: WebDavLocalRepository? = null
    private var setupJob: Job? = null
    private var browseJob: Job? = null
    private var playJob: Job? = null
    private var progressJob: Job? = null
    private var favoritesJob: Job? = null
    private var revision = 0
    val initialPath: String get() = repository?.initialDirectory ?: "/"
    fun displayPath(path: String): String = runCatching { repository?.paths?.display(path).orEmpty() }.getOrDefault(path)

    fun configure(value: VideoServerConfig) {
        if (config == value) { open(_state.value.path); return }
        cancelRequests(); setupJob?.cancel(); progressJob?.cancel(); favoritesJob?.cancel()
        config = value
        _state.value = WebDavBrowserState()
        setupJob = viewModelScope.launch {
            try {
                val remote = WebDavRepository(value)
                val saved = WebDavLocalRepository(getApplication(), value.sourceId)
                repository = remote; local = saved
                progressJob = viewModelScope.launch { saved.progress.collect { rows -> _state.update { it.copy(progress = rows) } } }
                favoritesJob = viewModelScope.launch { saved.browse.collect { data -> _state.update { it.copy(favorites = data.favorites) } } }
                val last = saved.browse.first().lastDirectory?.takeIf { runCatching { remote.paths.url(it) }.isSuccess }
                open(last ?: remote.initialDirectory)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { _state.update { it.copy(loading = false, error = error.message ?: "WebDAV 配置不可用") } }
        }
    }
    fun open(path: String, force: Boolean = false) {
        val remote = repository ?: return
        val saved = local ?: return
        browseJob?.cancel()
        val request = ++revision
        val samePath = _state.value.path == path
        _state.update { it.copy(path = path, loading = true, error = null,
            entries = if (samePath) it.entries else emptyList(), fetchedAt = if (samePath) it.fetchedAt else null) }
        if (!samePath) query.value = ""
        browseJob = viewModelScope.launch {
            try {
                val cached = saved.directory(path)
                if (request != revision) return@launch
                cached?.let { cache -> _state.update { it.copy(entries = cache.entries, fetchedAt = cache.fetchedAtMillis) } }
                if (!force && cached != null && isCacheFresh(cached.fetchedAtMillis)) {
                    _state.update { it.copy(loading = false) }; return@launch
                }
                val result = remote.listDirectory(path)
                if (request != revision) return@launch
                saved.saveDirectory(result)
                _state.update { it.copy(path = result.path, entries = result.entries, fetchedAt = result.fetchedAtMillis, loading = false, error = null) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (request == revision) _state.update { it.copy(loading = false, error = error.message ?: "目录加载失败") }
            }
        }
    }
    fun refresh() = open(_state.value.path, force = true)
    fun cancelRequests() { revision++; browseJob?.cancel(); playJob?.cancel(); _state.update { it.copy(preparing = false) } }
    fun favorite(path: String, name: String) {
        viewModelScope.launch { try { local?.toggleFavorite(path, name) }
            catch (error: Exception) { if (error is CancellationException) throw error; _state.update { it.copy(error = "收藏目录保存失败") } } }
    }
    fun removeProgress(path: String) {
        viewModelScope.launch { try { local?.removeProgress(path) }
            catch (error: Exception) { if (error is CancellationException) throw error; _state.update { it.copy(error = "清除进度失败") } } }
    }
    fun play(entry: WebDavEntry, fromStart: Boolean, onReady: (VideoItem, Boolean, List<VideoItem>) -> Unit) {
        val remote = repository ?: return
        val saved = local ?: return
        playJob?.cancel()
        _state.update { it.copy(preparing = true, error = null) }
        playJob = viewModelScope.launch {
            try {
                val parent = webDavParent(entry.path)
                val siblings = if (_state.value.path == parent && _state.value.entries.any { it.path == entry.path }) _state.value.entries
                    else remote.listDirectory(parent).also { saved.saveDirectory(it) }.entries
                val history = saved.progress.first()
                val context = withContext(Dispatchers.Default) { remote.prepareEpisodeContext(siblings, history) }
                val video = context.firstOrNull { it.id == entry.path }
                    ?: throw WebDavException(WebDavException.Kind.NOT_FOUND, "视频文件不存在或已被移动")
                // Publish only a fully prepared snapshot of the clicked file's directory, before playback.
                onReady(video, fromStart, context)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { _state.update { it.copy(error = error.message ?: "无法准备播放") }
            } finally { _state.update { it.copy(preparing = false) } }
        }
    }
}

internal fun showWebDavEmptyState(loading: Boolean, error: String?, visibleCount: Int): Boolean =
    !loading && error == null && visibleCount == 0

internal fun webDavResumeCardWidth(fontScale: Float): Dp {
    val scale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(1f) ?: 1f
    return (220.dp * scale).coerceAtMost(300.dp)
}
