package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.nordic.mediahub.data.AppPreferences
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.MediaDomain
import com.nordic.mediahub.data.MediaSource
import com.nordic.mediahub.data.MediaSourceState
import com.nordic.mediahub.data.MusicDownloadManagers
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal val LocalAppPreferences = staticCompositionLocalOf { AppPreferences() }
internal val LocalSourceDomain = staticCompositionLocalOf<MediaDomain?> { null }
internal val LocalSourceActions = staticCompositionLocalOf<MediaSourceActions?> { null }

internal data class MediaSourceActions(
    val state: MediaSourceState,
    val busy: Boolean,
    val select: (MediaDomain, String) -> Unit,
    val save: (MediaSource, (Result<MediaSource>) -> Unit) -> Unit,
    val delete: (MediaSource, (Result<Unit>) -> Unit) -> Unit,
    val manage: () -> Unit
)
private data class PendingSourceOperation(val title: String, val message: String, val execute: () -> Unit)

/** Shares the app's playback handoff gate, so a source mutation cannot race a new play request. */
@Composable
internal fun MediaSourceManagementHost(
    state: MediaSourceState,
    repository: ConfigRepository,
    gate: AtomicBoolean,
    isPlaying: (MediaDomain) -> Boolean,
    closePlayback: (MediaDomain, () -> Unit, (String) -> Unit) -> Unit,
    onManage: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingSourceOperation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentClose by rememberUpdatedState(closePlayback)

    fun run(domain: MediaDomain, affectsActive: Boolean, action: suspend () -> Unit, failure: (Throwable) -> Unit) {
        if (!gate.compareAndSet(false, true)) { failure(IllegalStateException("正在处理播放切换，请稍后重试")); return }
        busy = true
        val perform = {
            scope.launch {
                try { action() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failure(e) }
                finally { busy = false; gate.set(false) }
            }
            Unit
        }
        if (affectsActive && currentIsPlaying(domain)) {
            currentClose(domain, perform) { message -> busy = false; gate.set(false); failure(IllegalStateException(message)) }
        } else perform()
    }
    fun confirm(title: String, domain: MediaDomain, affectsActive: Boolean, always: Boolean = false, execute: () -> Unit) {
        if (busy) return
        if (always || (affectsActive && currentIsPlaying(domain))) {
            pending = PendingSourceOperation(title,
                (if (affectsActive && currentIsPlaying(domain)) "将停止当前${domain.label}播放并保存进度。" else "") +
                    if (always) "仅移除连接，已下载音乐和本机记录会保留，可在存储与数据页面单独清理。" else "其他媒体的播放不受影响。", execute)
        } else execute()
    }
    val actions = MediaSourceActions(state, busy,
        select = { domain, id ->
            if (state.activeId(domain) != id) confirm("切换${domain.label}来源？", domain, true) {
                run(domain, true, { repository.selectSource(domain, id) }, { error = it.message })
            }
        },
        save = { draft, result ->
            val old = state.sources.firstOrNull { it.id == draft.id }
            val changed = old != null && old.copy(name = draft.name) != draft
            val affectsActive = changed && state.activeId(draft.domain) == draft.id
            confirm("更新当前服务器？", draft.domain, affectsActive) {
                run(draft.domain, affectsActive, { result(Result.success(repository.saveSource(draft))) }, { result(Result.failure(it)) })
            }
        },
        delete = { source, result ->
            val active = state.activeId(source.domain) == source.id
            confirm("删除 ${source.name}？", source.domain, active, always = true) {
                run(source.domain, active, {
                    MusicDownloadManagers.cancel(source.id)
                    repository.deleteSource(source.id)
                    result(Result.success(Unit))
                }, { result(Result.failure(it)) })
            }
        },
        manage = onManage)
    CompositionLocalProvider(LocalSourceActions provides actions, content = content)
    pending?.let { operation ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(operation.title) }, text = { Text(operation.message) },
            confirmButton = { TextButton(onClick = { pending = null; operation.execute() }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } })
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("未能切换来源") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } })
    }
}

@Composable
internal fun MediaSourceTitle(title: String) {
    val actions = LocalSourceActions.current
    val domain = LocalSourceDomain.current
    var expanded by remember { mutableStateOf(false) }
    if (actions == null || domain == null) { Text(title, style = MaterialTheme.typography.titleLarge); return }
    val active = actions.state.active(domain)
    Box {
        Row(Modifier.heightIn(min = NordicControlSizes.touchTarget)
            .clickable(enabled = !actions.busy, role = Role.Button) { expanded = true },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            Text(active?.name ?: "添加来源", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.ExpandMore, "切换${domain.label}来源", Modifier.size(NordicControlSizes.compactIcon))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            actions.state.sources.filter { it.domain == domain }.forEach { source ->
                DropdownMenuItem(text = { Text((if (source.id == active?.id) "✓ " else "") + source.name) },
                    onClick = { expanded = false; actions.select(domain, source.id) })
            }
            DropdownMenuItem(text = { Text("管理服务器") }, onClick = { expanded = false; actions.manage() })
        }
    }
}