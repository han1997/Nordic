package com.nordic.mediahub.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nordic.mediahub.data.BackupArchiveInfo
import com.nordic.mediahub.data.BackupPayload
import com.nordic.mediahub.data.BackupRepository
import com.nordic.mediahub.data.BackupWebDavConfig
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

private val BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val BACKUP_DIALOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun formatBackupTime(millis: Long, format: DateTimeFormatter = BACKUP_TIME_FORMAT): String =
    runCatching {
        format.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }.getOrDefault("")

@Composable
internal fun BackupSettingsPage() {
    val context = LocalContext.current
    val repository = remember { BackupRepository(context) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<BackupWebDavConfig?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showBackupPassword by rememberSaveable { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<BackupArchiveInfo?>(null) }
    var backups by remember { mutableStateOf<List<BackupArchiveInfo>?>(null) }
    var listMessage by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var showRestartDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { settings = repository.currentSettings() }
    LaunchedEffect(settings, refresh) {
        val current = settings ?: return@LaunchedEffect
        if (!current.isReady) { backups = null; listMessage = null; return@LaunchedEffect }
        try {
            backups = repository.listBackups()
            listMessage = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            backups = emptyList()
            listMessage = e.message
        }
    }

    fun launchAction(action: suspend () -> String?) {
        if (busy) return
        busy = true; message = null; error = null
        scope.launch {
            try { message = action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "操作失败，本地数据未修改" }
            finally { busy = false }
        }
    }

    // This page is hosted inside SettingsScreen's LazyColumn. A second vertical
    // scroll container receives an unbounded height and crashes during measure.
    Column {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        SettingsSectionTitle("备份位置", topPadding = NordicSpacing.sm)
        SettingsRow("WebDAV 服务器", "备份上传到你自己的 WebDAV；本应用不提供云服务。",
            value = settings?.serverUrl?.takeIf { it.isNotBlank() } ?: "未配置",
            icon = Icons.Filled.Cloud, onClick = { showEditor = true })
        SettingsRow("测试连接", "验证备份地址、账号与目录权限。",
            icon = Icons.Filled.Lan, enabled = settings?.isReady == true && !busy, onClick = {
                launchAction {
                    val current = settings ?: throw IllegalStateException("请先配置备份 WebDAV 服务器")
                    repository.testConnection(current)
                    "连接成功，备份目录可用"
                }
            })
        SettingsSectionTitle("备份")
        SettingsRow("立即备份", "使用备份密码加密后上传；云端保留最近 5 份。",
            icon = Icons.Filled.CloudUpload, enabled = settings?.isReady == true && !busy,
            onClick = { showBackupPassword = true })
        SettingsRow("备份密码", "用于加密归档；遗失后无法恢复云端备份，请妥善保管。",
            icon = Icons.Filled.Backup, enabled = !busy, onClick = { showBackupPassword = true })
        SettingsSectionTitle("恢复")
        SettingsRow("刷新云端备份", "读取备份目录中的可用归档；选择一份恢复到本机。",
            icon = Icons.Filled.History, enabled = settings?.isReady == true && !busy, onClick = { refresh++ })
        when {
            settings?.isReady != true -> Unit
            backups == null -> Text("正在读取云端备份…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            backups?.isEmpty() == true -> MediaStateCard("云端暂无备份", "先完成一次“立即备份”，再回到这里恢复。",
                density = MediaStateDensity.Compact)
            else -> backups.orEmpty().forEach { info ->
                SettingsRow("${formatBackupTime(info.createdAtMillis)} 的备份",
                    subtitle = listOfNotNull(
                        info.appVersion.takeIf { it.isNotBlank() }?.let { "由 v$it 创建" },
                        formatMediaBytes(info.sizeBytes)
                    ).joinToString(" · "),
                    icon = Icons.Filled.Restore, enabled = !busy,
                    onClick = { restoreTarget = info })
            }
        }
        listMessage?.let {
            MediaStateCard("读取云端备份失败", it, tone = MediaStateTone.Error, density = MediaStateDensity.Compact)
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        error?.let { MediaStateCard("操作未完成", it, tone = MediaStateTone.Error, density = MediaStateDensity.Compact) }
    }

    if (showEditor) {
        settings?.let { current ->
            BackupConfigDialog(initial = current, busy = busy, onDismiss = { showEditor = false },
                onSave = { next, complete ->
                    if (busy) return@BackupConfigDialog
                    busy = true; message = null; error = null
                    scope.launch {
                        try {
                            repository.saveSettings(next)
                            repository.testConnection(next)
                            settings = repository.currentSettings()
                            message = "备份位置已保存并连接成功"
                            complete(Result.success(Unit))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            complete(Result.failure(e))
                        } finally {
                            busy = false
                        }
                    }
                })
        }
    }
    if (showBackupPassword) {
        BackupPasswordDialog(title = "设置备份密码", confirmLabel = "开始备份",
            description = "输入至少 6 位密码加密本次备份。恢复同一份备份时需要相同密码。",
            busy = busy, onDismiss = { showBackupPassword = false }) { password ->
            showBackupPassword = false
            launchAction {
                repository.createBackup(password)
                settings = repository.currentSettings()
                "备份完成，云端保留最近 5 份"
            }
        }
    }
    restoreTarget?.let { target ->
        RestorePasswordDialog(info = target, repository = repository, busy = busy,
            onDismiss = { restoreTarget = null }) {
            message = "恢复完成，请退出并重新打开应用"
            showRestartDialog = true
        }
    }
    if (showRestartDialog) {
        AlertDialog(onDismissRequest = { showRestartDialog = false },
            title = { Text("恢复完成") },
            text = { Text("本机数据已替换为备份内容。播放已停止，请退出应用后重新打开以加载恢复的数据。") },
            confirmButton = { TextButton(onClick = {
                showRestartDialog = false
                (context as? Activity)?.finishAffinity()
                exitProcess(0)
            }) { Text("退出应用") } },
            dismissButton = { TextButton(onClick = { showRestartDialog = false }) { Text("稍后自行退出") } })
    }
}

@Composable
private fun BackupConfigDialog(
    initial: BackupWebDavConfig,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (BackupWebDavConfig, (Result<Unit>) -> Unit) -> Unit
) {
    var url by remember { mutableStateOf(initial.serverUrl) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var directory by remember { mutableStateOf(initial.directory) }
    var allowInsecure by remember { mutableStateOf(initial.allowInsecureHttp) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("备份 WebDAV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                OutlinedTextField(url, { url = it }, label = { Text("服务器地址") }, singleLine = true,
                    placeholder = { Text("https://example.com/dav/") })
                OutlinedTextField(username, { username = it }, label = { Text("用户名（可留空匿名）") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(directory, { directory = it }, label = { Text("备份目录") }, singleLine = true,
                    placeholder = { Text("nordic-backup") })
                SettingsRow("允许 HTTP", "HTTP 会明文传输备份密码；仅建议内网使用。",
                    checked = allowInsecure, onCheckedChange = { allowInsecure = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                when {
                    url.trim().isBlank() -> error = "请输入服务器地址"
                    directory.contains("..") -> error = "备份目录不能包含上级路径"
                    else -> onSave(BackupWebDavConfig(url.trim(), username.trim(), password, directory.trim(), allowInsecure)) { result ->
                        result.onSuccess { onDismiss() }
                            .onFailure { error = it.message ?: "保存失败，请检查服务器地址、账号和目录" }
                    }
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmLabel: String,
    description: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(password, { password = it }, label = { Text("备份密码") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (password.length < 6) { error = "备份密码至少 6 位"; return@TextButton }
                onConfirm(password.toCharArray())
                password = ""
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun RestorePasswordDialog(info: BackupArchiveInfo, repository: BackupRepository, busy: Boolean,
                                  onDismiss: () -> Unit, onVerified: (BackupPayload) -> Unit) {
    var password by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf<BackupPayload?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!verifying) onDismiss() },
        title = { Text("恢复 ${formatBackupTime(info.createdAtMillis, BACKUP_DIALOG_TIME_FORMAT)} 的备份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                if (payload == null) {
                    Text("输入这份备份的密码以解密并校验。校验通过后会再次确认覆盖。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(password, { password = it }, label = { Text("备份密码") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                } else {
                    Text("校验通过。恢复将完整覆盖本机以下数据，且不可撤销：", style = MaterialTheme.typography.bodyMedium)
                    Text("服务器连接与凭据 · 应用偏好 · 播放历史 · 有声书书签与阅读位置 · WebDAV 收藏与观看进度",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Text("已下载音乐与各类缓存不受影响。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (payload == null) {
                TextButton(enabled = !verifying && password.isNotEmpty(), onClick = {
                    verifying = true; error = null
                    scope.launch {
                        try {
                            payload = repository.prepareRestore(info.fileName, password.toCharArray())
                            password = ""
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "校验失败，本地数据未修改" }
                        finally { verifying = false }
                    }
                }) { Text(if (verifying) "校验中…" else "校验") }
            } else {
                TextButton(enabled = !busy, onClick = {
                    val ready = payload ?: return@TextButton
                    scope.launch {
                        try { repository.applyRestore(ready) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            error = e.message ?: "恢复失败，本地数据可能已部分更新"
                            return@launch
                        }
                        onDismiss()
                        onVerified(ready)
                    }
                }) { Text("覆盖恢复") }
            }
        },
        dismissButton = { TextButton(enabled = !verifying, onClick = onDismiss) { Text("取消") } })
}
