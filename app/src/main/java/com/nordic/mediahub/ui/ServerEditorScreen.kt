package com.nordic.mediahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var passwordVisible by remember(source.id) { mutableStateOf(false) }
    var apiVisible by remember(source.id) { mutableStateOf(false) }
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
    val colors = MaterialTheme.colorScheme
    fun change(value: MediaSource) {
        testRevision++; testJob?.cancel(); testing = false; draft = value; check = null; error = null
    }
    fun back() { if (actions.busy) return; if (draft != source) { exitAction = onBack; discard = true } else onBack() }
    BackHandler(onBack = ::back)
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(NordicSpacing.content),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
        item { MediaPageHeader(if (isNew) "添加 ${source.kind.label}" else "编辑服务器", "${source.kind.domain.label} · ${source.kind.label}",
            emptyList(), colors, showBack = true, onBack = ::back) }
        item { OutlinedTextField(draft.name, { change(draft.copy(name = it)) }, label = { Text("显示名称") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !actions.busy) }
        item {
            OutlinedTextField(draft.serverUrl, { change(draft.copy(serverUrl = it)) }, label = { Text("${if (source.kind == MediaSourceKind.WEBDAV) "WebDAV 根" else "服务器"}地址") },
                placeholder = { Text(if (source.kind == MediaSourceKind.WEBDAV) "https://example.com/dav/" else "https://example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true,
                modifier = Modifier.fillMaxWidth(), enabled = !actions.busy)
        }
        if (source.kind == MediaSourceKind.WEBDAV) item {
            Text("填写完整 WebDAV 地址，而不是网页地址。匿名连接时同时留空用户名和密码。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        item { OutlinedTextField(draft.username, { change(draft.copy(username = it)) }, label = { Text("用户名") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !actions.busy) }
        item { OutlinedTextField(draft.password, { change(draft.copy(password = it)) }, label = { Text("密码") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true,
            trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "显示或隐藏密码") } },
            modifier = Modifier.fillMaxWidth(), enabled = !actions.busy) }
        if (source.kind == MediaSourceKind.EMBY) {
            item { Text("也可使用 API Key；填写后优先使用 API Key 认证。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            item { OutlinedTextField(draft.apiKey, { change(draft.copy(apiKey = it)) }, label = { Text("API Key（可选）") },
                visualTransformation = if (apiVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { apiVisible = !apiVisible }) { Icon(Icons.Filled.Visibility, "显示或隐藏 API Key") } },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !actions.busy) }
        }
        if (source.kind == MediaSourceKind.WEBDAV) item {
            OutlinedTextField(draft.startDirectory, { change(draft.copy(startDirectory = it)) }, label = { Text("起始目录") },
                supportingText = { Text("相对于 WebDAV 根地址，默认 /；中文路径直接输入，不必手动编码。") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !actions.busy)
        }
        if (draft.serverUrl.trim().startsWith("http://", true) || draft.allowInsecureHttp) item {
            SettingsRow("允许此连接使用 HTTP", "HTTP 不加密账号与媒体数据，仅在你信任的网络中使用。", checked = draft.allowInsecureHttp,
                enabled = !actions.busy, onCheckedChange = { change(draft.copy(allowInsecureHttp = it)) })
        }
        if (testing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        check?.let { result -> item { Text(result.message, color = if (result.success) colors.primary else colors.error, style = MaterialTheme.typography.bodyMedium) } }
        error?.let { message -> item { Text(message, color = colors.error, style = MaterialTheme.typography.bodyMedium) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                SecondaryActionButton(text = "测试连接", colorScheme = colors, enabled = !testing && !actions.busy, modifier = Modifier.weight(1f), onClick = {
                    val snapshot = draft
                    val request = ++testRevision
                    testing = true; error = null
                    testJob = scope.launch {
                        try { val message = testMediaSourceConnection(snapshot); if (request == testRevision) check = ConnectionCheck(message, true) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { if (request == testRevision) check = ConnectionCheck(e.message ?: "连接失败", false) }
                        finally { if (request == testRevision) testing = false }
                    }
                })
                PrimaryActionButton(text = if (actions.busy) "正在保存" else "保存", colorScheme = colors, enabled = !actions.busy, modifier = Modifier.weight(1f), onClick = {
                    actions.save(draft) { result -> result.onSuccess { onSaved(it, check) }.onFailure { error = it.message ?: "保存失败" } }
                })
            }
        }
        item { Text("测试不会保存或切换来源；未测试或测试失败的连接可保存为未验证。密码只在保存后进入加密存储。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        text = { Text("服务器配置还没有保存。") }, confirmButton = { TextButton(onClick = { discard = false; guard?.handler = null; exitAction() }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}