package com.nordic.mediahub.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
            VideoServerType.values().forEach { type ->
                val supported = type == supportedType
                val selected = type == supportedType
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.01f else 1f,
                    animationSpec = tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
                )
                Surface(
                    color = when {
                        selected -> colorScheme.primary.copy(alpha = 0.16f)
                        supported -> colorScheme.surfaceVariant.copy(alpha = 0.56f)
                        else -> colorScheme.surfaceVariant.copy(alpha = 0.32f)
                    },
                    contentColor = when {
                        selected -> colorScheme.primary
                        supported -> colorScheme.onSurface
                        else -> colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
                    },
                    shape = NordicShapes.full,
                    border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.06f)),
                    modifier = Modifier
                        .weight(1f)
                        .scale(scale)
                        .clickable(enabled = supported) { onConfigChange(config.copy(type = type)) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
                    ) {
                        Text(
                            videoServerTypeLabel(type),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (!supported) {
                            Text(
                                "后续支持",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = NordicAlpha.faint)
                            )
                        }
                    }
                }
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
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("保存配置")
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
            OutlinedButton(
                onClick = onTestConnection,
                enabled = !isTestingConnection,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isTestingConnection) "测试中..." else "测试连接")
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text("保存配置")
            }
        }
    }

    if (statusMessage != null) {
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (statusIsError) colorScheme.error else colorScheme.primary
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
        color = colorScheme.surfaceVariant,
        shape = NordicShapes.sm,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(NordicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface
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
    Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal, color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface.copy(alpha = NordicAlpha.faint)) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
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
}
