package com.nordic.mediahub.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
fun NavidromeConfigCard(config: NavidromeConfig, colorScheme: ColorScheme, onConfigChange: (NavidromeConfig) -> Unit, onSave: () -> Unit) {
    Surface(color = colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Navidrome 服务器", fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            ConfigTextField("服务器地址", config.serverUrl, "https://music.example.com", colorScheme) {
                onConfigChange(config.copy(serverUrl = it))
            }
            ConfigTextField("用户名", config.username, "username", colorScheme) {
                onConfigChange(config.copy(username = it))
            }
            ConfigTextField("密码", config.password, "password", colorScheme, true) {
                onConfigChange(config.copy(password = it))
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("保存配置")
            }
        }
    }
}

@Composable
fun AudiobookConfigCard(config: AudiobookShelfConfig, colorScheme: ColorScheme, onConfigChange: (AudiobookShelfConfig) -> Unit, onSave: () -> Unit) {
    Surface(color = colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AudiobookShelf 服务器", fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            ConfigTextField("服务器地址", config.serverUrl, "https://audiobook.example.com", colorScheme) {
                onConfigChange(config.copy(serverUrl = it))
            }
            ConfigTextField("用户名", config.username, "username", colorScheme) {
                onConfigChange(config.copy(username = it))
            }
            ConfigTextField("密码", config.password, "password", colorScheme, true) {
                onConfigChange(config.copy(password = it))
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("保存配置")
            }
        }
    }
}

@Composable
fun VideoConfigCard(config: VideoServerConfig, colorScheme: ColorScheme, onConfigChange: (VideoServerConfig) -> Unit, onSave: () -> Unit) {
    Surface(color = colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("视频服务器", fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoServerType.values().forEach { type ->
                    val selected = config.type == type
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.01f else 1f,
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                    )
                    Surface(
                        color = if (selected) colorScheme.primary else colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).scale(scale).clickable { onConfigChange(config.copy(type = type)) }
                    ) {
                        Text(
                            type.name,
                            modifier = Modifier.padding(10.dp),
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = config.type,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }
            ) { type ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConfigTextField("服务器地址", config.serverUrl, "https://video.example.com", colorScheme) {
                        onConfigChange(config.copy(serverUrl = it))
                    }
                    when (type) {
                        VideoServerType.EMBY, VideoServerType.PLEX -> {
                            ConfigTextField("用户名", config.username, "username", colorScheme) {
                                onConfigChange(config.copy(username = it))
                            }
                            ConfigTextField("密码", config.password, "password", colorScheme, true) {
                                onConfigChange(config.copy(password = it))
                            }
                            if (type == VideoServerType.EMBY) {
                                ConfigTextField("API Key (可选)", config.apiKey, "api key", colorScheme) {
                                    onConfigChange(config.copy(apiKey = it))
                                }
                            }
                        }
                        VideoServerType.WEBDAV -> {
                            ConfigTextField("用户名", config.username, "username", colorScheme) {
                                onConfigChange(config.copy(username = it))
                            }
                            ConfigTextField("密码", config.password, "password", colorScheme, true) {
                                onConfigChange(config.copy(password = it))
                            }
                        }
                    }
                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                        Text("保存配置")
                    }
                }
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = colorScheme.onSurface.copy(0.7f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = colorScheme.onSurface.copy(0.4f), fontSize = 14.sp) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.onSurface.copy(0.2f),
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
    }
}
