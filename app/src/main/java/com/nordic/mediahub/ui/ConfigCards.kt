package com.nordic.mediahub.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.data.AudiobookShelfConfig
import com.nordic.mediahub.data.NavidromeConfig
import com.nordic.mediahub.data.VideoServerConfig
import com.nordic.mediahub.data.VideoServerType
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicMotion
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing

@Composable
fun NavidromeConfigCard(
    config: NavidromeConfig,
    colorScheme: ColorScheme,
    onConfigChange: (NavidromeConfig) -> Unit,
    onSave: () -> Unit,
    onTestConnection: (() -> Unit)? = null,
    isTestingConnection: Boolean = false,
    statusMessage: String? = null,
    statusIsError: Boolean = false
) {
    ServerConfigCard(title = "Navidrome 服务器", colorScheme = colorScheme) {
        ConfigTextField("服务器地址", config.serverUrl, "https://music.example.com", colorScheme) {
            onConfigChange(config.copy(serverUrl = it))
        }
        ConfigTextField("用户名", config.username, "username", colorScheme) {
            onConfigChange(config.copy(username = it))
        }
        ConfigTextField("密码", config.password, "password", colorScheme, true) {
            onConfigChange(config.copy(password = it))
        }
        ServerConfigActions(
            colorScheme = colorScheme,
            onSave = onSave,
            onTestConnection = onTestConnection,
            isTestingConnection = isTestingConnection,
            statusMessage = statusMessage,
            statusIsError = statusIsError
        )
    }
}

@Composable
fun AudiobookConfigCard(
    config: AudiobookShelfConfig,
    colorScheme: ColorScheme,
    onConfigChange: (AudiobookShelfConfig) -> Unit,
    onSave: () -> Unit,
    onTestConnection: (() -> Unit)? = null,
    isTestingConnection: Boolean = false,
    statusMessage: String? = null,
    statusIsError: Boolean = false
) {
    ServerConfigCard(title = "AudiobookShelf 服务器", colorScheme = colorScheme) {
        ConfigTextField("服务器地址", config.serverUrl, "https://audiobook.example.com", colorScheme) {
            onConfigChange(config.copy(serverUrl = it))
        }
        ConfigTextField("用户名", config.username, "username", colorScheme) {
            onConfigChange(config.copy(username = it))
        }
        ConfigTextField("密码", config.password, "password", colorScheme, true) {
            onConfigChange(config.copy(password = it))
        }
        ServerConfigActions(
            colorScheme = colorScheme,
            onSave = onSave,
            onTestConnection = onTestConnection,
            isTestingConnection = isTestingConnection,
            statusMessage = statusMessage,
            statusIsError = statusIsError
        )
    }
}

@Composable
fun VideoConfigCard(
    config: VideoServerConfig,
    colorScheme: ColorScheme,
    onConfigChange: (VideoServerConfig) -> Unit,
    onSave: () -> Unit,
    onTestConnection: (() -> Unit)? = null,
    isTestingConnection: Boolean = false,
    statusMessage: String? = null,
    statusIsError: Boolean = false
) {
    val supportedType = VideoServerType.EMBY
    ServerConfigCard(title = "视频服务器", colorScheme = colorScheme) {
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
        ) {
            VideoServerType.values().forEach { type ->
                val supported = type == supportedType
                MediaChoiceChip(
                    text = videoServerTypeLabel(type),
                    selected = type == supportedType,
                    colorScheme = colorScheme,
                    enabled = supported,
                    modifier = Modifier.weight(1f),
                    onClick = { onConfigChange(config.copy(type = type)) }
                )
            }
        }
        Text(
            "当前版本仅支持 Emby 视频媒体库；Plex 和 WebDAV 将在后续版本接入。",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
        )
        AnimatedContent(
            targetState = supportedType,
            transitionSpec = { fadeIn(tween(NordicMotion.durationMedium)) togetherWith fadeOut(tween(NordicMotion.durationShort)) },
            label = "video-config-type"
        ) { type ->
            Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
                ConfigTextField("服务器地址", config.serverUrl, "https://video.example.com", colorScheme) {
                    onConfigChange(config.copy(type = supportedType, serverUrl = it))
                }
                when (type) {
                    VideoServerType.EMBY -> {
                        VideoServerCredentialsFields(config, colorScheme, onConfigChange)
                        ConfigTextField("API Key（可选）", config.apiKey, "api key", colorScheme) {
                            onConfigChange(config.copy(type = supportedType, apiKey = it))
                        }
                    }

                    VideoServerType.PLEX, VideoServerType.WEBDAV -> {
                        VideoServerCredentialsFields(config, colorScheme, onConfigChange)
                    }
                }
                ServerConfigActions(
                    colorScheme = colorScheme,
                    onSave = onSave,
                    onTestConnection = onTestConnection,
                    isTestingConnection = isTestingConnection,
                    statusMessage = statusMessage,
                    statusIsError = statusIsError
                )
            }
        }
    }
}

private fun videoServerTypeLabel(type: VideoServerType): String {
    return when (type) {
        VideoServerType.EMBY -> "Emby"
        VideoServerType.PLEX -> "Plex"
        VideoServerType.WEBDAV -> "WebDAV"
    }
}

@Composable
private fun ServerConfigActions(
    colorScheme: ColorScheme,
    onSave: () -> Unit,
    onTestConnection: (() -> Unit)?,
    isTestingConnection: Boolean,
    statusMessage: String?,
    statusIsError: Boolean
) {
    if (onTestConnection == null) {
        PrimaryActionButton(text = "保存配置", colorScheme = colorScheme, onClick = onSave)
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
            SecondaryActionButton(
                text = if (isTestingConnection) "测试中..." else "测试连接",
                colorScheme = colorScheme,
                enabled = !isTestingConnection,
                onClick = onTestConnection,
                modifier = Modifier.weight(1f)
            )
            PrimaryActionButton(
                text = "保存配置",
                colorScheme = colorScheme,
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (statusMessage != null) {
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (statusIsError) colorScheme.error else colorScheme.primary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoServerCredentialsFields(
    config: VideoServerConfig,
    colorScheme: ColorScheme,
    onConfigChange: (VideoServerConfig) -> Unit
) {
    ConfigTextField("用户名", config.username, "username", colorScheme) {
        onConfigChange(config.copy(type = VideoServerType.EMBY, username = it))
    }
    ConfigTextField("密码", config.password, "password", colorScheme, true) {
        onConfigChange(config.copy(type = VideoServerType.EMBY, password = it))
    }
}

@Composable
private fun ServerConfigCard(
    title: String,
    colorScheme: ColorScheme,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = NordicShapes.md,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(NordicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            content()
        }
    }
}

@Composable
fun ConfigTextField(
    label: String,
    value: String,
    placeholder: String,
    colorScheme: ColorScheme,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colorScheme.onSurface.copy(alpha = NordicAlpha.faint)) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.2f),
            focusedTextColor = colorScheme.onSurface,
            unfocusedTextColor = colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = NordicShapes.sm,
        singleLine = true
    )
}
