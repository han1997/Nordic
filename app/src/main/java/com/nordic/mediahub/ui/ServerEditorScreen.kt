package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nordic.mediahub.data.MediaSource
import com.nordic.mediahub.data.MediaSourceKind
import com.nordic.mediahub.data.testMediaSourceConnection
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ConnectionCheck(val message: String, val success: Boolean, val atMillis: Long = System.currentTimeMillis())

@Composable
internal fun ServerEditorScreen(source: MediaSource, isNew: Boolean, onBack: () -> Unit, onSaved: (MediaSource, ConnectionCheck?) -> Unit) {
    val actions = LocalSourceActions.current ?: return
    var draft by remember(source.id) { mutableStateOf(source) }
    var check by remember(source.id) { mutableStateOf<ConnectionCheck?>(null) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var testRevision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val guard = LocalSettingsNavigationGuard.current
    val latestDraft by rememberUpdatedState(draft)
    val latestBusy by rememberUpdatedState(actions.busy)
    var exitAction by remember { mutableStateOf(onBack) }
    DisposableEffect(guard, source.id) {
        val handler: (() -> Unit) -> Unit = { action ->
            if (!latestBusy) {
                if (latestDraft != source) { exitAction = action; discard = true } else action()
            }
        }
        guard?.handler = handler
        onDispose { if (guard?.handler === handler) guard.handler = null }
    }
    fun change(value: MediaSource) {
        testRevision++; testJob?.cancel(); testing = false; draft = value; check = null; error = null
    }
    fun back() { if (actions.busy) return; if (draft != source) { exitAction = onBack; discard = true } else onBack() }
    BackHandler(onBack = ::back)
    ServerEditorContent(
        draft = draft, isNew = isNew, busy = actions.busy, testing = testing,
        check = check, error = error, onChange = ::change, onBack = ::back,
        onTestConnection = {
            val snapshot = draft
            val request = ++testRevision
            testing = true; error = null
            testJob = scope.launch {
                try { val message = testMediaSourceConnection(snapshot); if (request == testRevision) check = ConnectionCheck(message, true) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (request == testRevision) check = ConnectionCheck(e.message ?: "连接失败", false) }
                finally { if (request == testRevision) testing = false }
            }
        },
        onSave = {
            actions.save(draft) { result -> result.onSuccess { onSaved(it, check) }.onFailure { error = it.message ?: "保存失败" } }
        }
    )
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        text = { Text("服务器配置还没有保存。") }, confirmButton = { TextButton(onClick = { discard = false; guard?.handler = null; exitAction() }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}
/** Shared form UI. Networking, persistence and unsaved-navigation guards stay in the host. */
@Composable
internal fun ServerEditorContent(
    draft: MediaSource,
    isNew: Boolean,
    busy: Boolean,
    testing: Boolean,
    check: ConnectionCheck?,
    error: String?,
    onChange: (MediaSource) -> Unit,
    onBack: () -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var passwordVisible by remember(draft.id) { mutableStateOf(false) }
    var apiVisible by remember(draft.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(NordicSpacing.content),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
        item { MediaPageHeader(if (isNew) "添加 ${draft.kind.label}" else "编辑服务器", "${draft.kind.domain.label} · ${draft.kind.label}",
            emptyList(), colors, showBack = true, onBack = onBack) }
        item { SettingsSectionTitle("连接信息", topPadding = NordicSpacing.xs) }
        item { ConfigTextField("显示名称", draft.name, "例如：家庭音乐库", colors, enabled = !busy,
            onValueChange = { onChange(draft.copy(name = it)) }) }
        item { ConfigTextField(if (draft.kind == MediaSourceKind.WEBDAV) "WebDAV 根地址" else "服务器地址",
            draft.serverUrl, if (draft.kind == MediaSourceKind.WEBDAV) "https://example.com/dav/" else "https://example.com",
            colors, enabled = !busy, keyboardType = KeyboardType.Uri,
            supportingText = if (draft.kind == MediaSourceKind.WEBDAV) "填写完整 WebDAV 地址，而不是网页地址。" else null,
            onValueChange = { onChange(draft.copy(serverUrl = it)) }) }
        if (draft.kind == MediaSourceKind.WEBDAV) item {
            ConfigTextField("起始目录", draft.startDirectory, "/", colors, enabled = !busy,
                supportingText = "相对于 WebDAV 根地址；中文路径直接输入，不必手动编码。",
                onValueChange = { onChange(draft.copy(startDirectory = it)) })
        }
        item { SettingsSectionTitle("身份验证", topPadding = NordicSpacing.xs) }
        item { ConfigTextField("用户名", draft.username, if (draft.kind == MediaSourceKind.WEBDAV) "匿名连接可留空" else "请输入用户名",
            colors, enabled = !busy, onValueChange = { onChange(draft.copy(username = it)) }) }
        item { ConfigTextField("密码", draft.password, if (draft.kind == MediaSourceKind.WEBDAV) "匿名连接时与用户名同时留空" else "请输入密码",
            colors, isPassword = !passwordVisible, enabled = !busy, keyboardType = KeyboardType.Password,
            trailingIcon = { IconButton(enabled = !busy, onClick = { passwordVisible = !passwordVisible }) {
                Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (passwordVisible) "隐藏密码" else "显示密码")
            } }, onValueChange = { onChange(draft.copy(password = it)) }) }
        if (draft.kind == MediaSourceKind.EMBY) item {
            ConfigTextField("API Key（可选）", draft.apiKey, "填写后优先使用 API Key", colors,
                isPassword = !apiVisible, enabled = !busy, keyboardType = KeyboardType.Password,
                trailingIcon = { IconButton(enabled = !busy, onClick = { apiVisible = !apiVisible }) {
                    Icon(if (apiVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (apiVisible) "隐藏 API Key" else "显示 API Key")
                } }, onValueChange = { onChange(draft.copy(apiKey = it)) })
        }
        if (draft.serverUrl.trim().startsWith("http://", true) || draft.allowInsecureHttp) item {
            SettingsRow("允许此连接使用 HTTP", "HTTP 不加密账号与媒体数据，仅在你信任的网络中使用。", checked = draft.allowInsecureHttp,
                enabled = !busy, onCheckedChange = { onChange(draft.copy(allowInsecureHttp = it)) })
        }
        if (testing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        check?.let { result -> item {
            MediaStateCard(if (result.success) "连接测试成功" else "连接测试未通过", result.message,
                tone = if (result.success) MediaStateTone.Neutral else MediaStateTone.Error, density = MediaStateDensity.Compact)
        } }
        error?.let { message -> item { MediaStateCard("操作未完成", message, tone = MediaStateTone.Error, density = MediaStateDensity.Compact) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                SecondaryActionButton("测试连接", colors, enabled = !testing && !busy, modifier = Modifier.weight(1f), onClick = onTestConnection)
                PrimaryActionButton(if (busy) "正在保存" else "保存", colors, enabled = !busy, modifier = Modifier.weight(1f), onClick = onSave)
            }
        }
        item { Text("测试不会保存或切换来源；未测试或测试失败的连接可保存为未验证。密码只在保存后进入加密存储。",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}
