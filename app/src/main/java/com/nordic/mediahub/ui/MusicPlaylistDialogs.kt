package com.nordic.mediahub.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

/** Content only: the library retains draft lifetime, mutation requests and concurrency guards. */
@Composable
internal fun MusicPlaylistNameDialog(
    creating: Boolean,
    name: String,
    isRunning: Boolean,
    error: String?,
    colorScheme: ColorScheme,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val canSubmit = name.isNotBlank() && !isRunning
    val submit = {
        if (canSubmit) {
            keyboard?.hide()
            onSubmit()
        }
    }
    MusicPlaylistDialog(
        title = if (creating) "新建歌单" else "重命名歌单",
        isRunning = isRunning, colors = colorScheme, onDismiss = onDismiss,
        confirmButton = {
            Button(onClick = submit, enabled = canSubmit,
                modifier = Modifier.heightIn(min = NordicControlSizes.touchTarget).widthIn(min = NordicControlSizes.touchTarget),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary, contentColor = colorScheme.onPrimary)) {
                Text(if (isRunning) "处理中" else if (creating) "创建" else "保存", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) { compact ->
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "歌单名称" },
            // A floating label adds another scaled line. The compact one-field dialog has
            // its title and accessible field name; keep the entire input visible above IME.
            label = if (compact) null else ({ Text("歌单名称") }),
            placeholder = if (compact) ({ Text("歌单名称") }) else null,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            enabled = !isRunning,
            isError = error != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                focusedLabelColor = colorScheme.onPrimaryContainer,
                unfocusedBorderColor = colorScheme.outline
            ),
            shape = NordicShapes.md,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
        PlaylistActionError(error, colorScheme)
    }
}

@Composable
internal fun MusicPlaylistDeleteDialog(
    playlistName: String,
    isRunning: Boolean,
    error: String?,
    colorScheme: ColorScheme,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    MusicPlaylistDialog(
        title = "删除歌单", isRunning = isRunning, colors = colorScheme, onDismiss = onDismiss,
        confirmButton = {
            Button(onClick = { if (!isRunning) onConfirm() }, enabled = !isRunning,
                modifier = Modifier.heightIn(min = NordicControlSizes.touchTarget).widthIn(min = NordicControlSizes.touchTarget),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.errorContainer, contentColor = colorScheme.onErrorContainer)) {
                Text(if (isRunning) "处理中" else "删除", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) {
        Text("确定删除“${playlistName}”？这个操作会同步到 Navidrome。", style = MaterialTheme.typography.bodyMedium)
        PlaylistActionError(error, colorScheme)
    }
}

/** Full window insets bound the card; title/body scroll together while actions stay pinned. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicPlaylistDialog(
    title: String,
    isRunning: Boolean,
    colors: ColorScheme,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    content: @Composable (compact: Boolean) -> Unit
) {
    Dialog(onDismissRequest = { if (!isRunning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            val compact = maxHeight < 360.dp
            val inset = if (compact) NordicSpacing.md else NordicSpacing.xxl
            Box(Modifier.fillMaxSize().pointerInput(isRunning, onDismiss) {
                detectTapGestures { if (!isRunning) onDismiss() }
            })
            Surface(color = colors.surfaceContainerHigh, contentColor = colors.onSurface, shape = NordicShapes.xl,
                modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.xs)
                    .widthIn(max = 560.dp).fillMaxWidth().heightIn(max = (maxHeight - NordicSpacing.sm).coerceAtLeast(0.dp))
                    .pointerInput(Unit) { detectTapGestures { /* Do not dismiss through the card background. */ } }) {
                Column(Modifier.padding(inset),
                    verticalArrangement = Arrangement.spacedBy(if (compact) NordicSpacing.sm else NordicSpacing.lg)) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        content(compact)
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
                        PlaylistDialogCancel(isRunning, colors, onDismiss)
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDialogCancel(isRunning: Boolean, colors: ColorScheme, onDismiss: () -> Unit) {
    TextButton(onClick = onDismiss, enabled = !isRunning,
        modifier = Modifier.heightIn(min = NordicControlSizes.touchTarget).widthIn(min = NordicControlSizes.touchTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant)) {
        Text("取消", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PlaylistActionError(error: String?, colors: ColorScheme) {
    error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
