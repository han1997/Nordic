package com.nordic.mediahub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordic.mediahub.data.AudiobookShelfConfig
import com.nordic.mediahub.data.AudiobookShelfRepository
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.EmbyRepository
import com.nordic.mediahub.data.NavidromeConfig
import com.nordic.mediahub.data.NavidromeRepository
import com.nordic.mediahub.data.VideoServerConfig
import com.nordic.mediahub.data.isReadyForAudiobookSync
import com.nordic.mediahub.data.isReadyForMusicSync
import com.nordic.mediahub.data.isReadyForVideoSync
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlinx.coroutines.launch

private data class ConnectionTestState(
    val isTesting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

@Composable
fun ServerConfigScreen(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    isDark: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val configRepository = remember { ConfigRepository(context) }
    val savedNavidromeConfig by configRepository.navidromeConfig.collectAsStateWithLifecycle(NavidromeConfig())
    val savedAudiobookConfig by configRepository.audiobookConfig.collectAsStateWithLifecycle(AudiobookShelfConfig())
    val savedVideoConfig by configRepository.videoConfig.collectAsStateWithLifecycle(VideoServerConfig())
    val scope = rememberCoroutineScope()

    var navidromeConfig by remember { mutableStateOf(NavidromeConfig()) }
    var audiobookConfig by remember { mutableStateOf(AudiobookShelfConfig()) }
    var videoConfig by remember { mutableStateOf(VideoServerConfig()) }
    var navidromeTestState by remember { mutableStateOf(ConnectionTestState()) }
    var audiobookTestState by remember { mutableStateOf(ConnectionTestState()) }
    var videoTestState by remember { mutableStateOf(ConnectionTestState()) }

    LaunchedEffect(savedNavidromeConfig) { navidromeConfig = savedNavidromeConfig }
    LaunchedEffect(savedAudiobookConfig) { audiobookConfig = savedAudiobookConfig }
    LaunchedEffect(savedVideoConfig) { videoConfig = savedVideoConfig }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NordicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)
    ) {
        item {
            MediaPageHeader(
                title = "配置",
                subtitle = "集中管理音乐、有声书和视频服务器连接",
                actions = listOf(HeaderAction(if (isDark) "☀" else "☾") { onThemeToggle(!isDark) }),
                colorScheme = colorScheme
            )
        }

        item {
            NavidromeConfigCard(
                config = navidromeConfig,
                colorScheme = colorScheme,
                onConfigChange = {
                    navidromeConfig = it
                    navidromeTestState = ConnectionTestState()
                },
                onSave = {
                    scope.launch {
                        configRepository.saveNavidromeConfig(navidromeConfig)
                        navidromeTestState = ConnectionTestState(message = "Navidrome 配置已保存")
                    }
                },
                onTestConnection = {
                    scope.launch {
                        if (!navidromeConfig.isReadyForMusicSync()) {
                            navidromeTestState = ConnectionTestState(message = "请先填写服务器地址、用户名和密码", isError = true)
                            return@launch
                        }
                        navidromeTestState = ConnectionTestState(isTesting = true, message = "正在测试 Navidrome...")
                        try {
                            NavidromeRepository(navidromeConfig).testConnection()
                            navidromeTestState = ConnectionTestState(message = "Navidrome 连接成功")
                        } catch (error: Exception) {
                            navidromeTestState = ConnectionTestState(
                                message = "Navidrome 连接失败: ${error.message ?: "未知错误"}",
                                isError = true
                            )
                        }
                    }
                },
                isTestingConnection = navidromeTestState.isTesting,
                statusMessage = navidromeTestState.message,
                statusIsError = navidromeTestState.isError
            )
        }

        item {
            AudiobookConfigCard(
                config = audiobookConfig,
                colorScheme = colorScheme,
                onConfigChange = {
                    audiobookConfig = it
                    audiobookTestState = ConnectionTestState()
                },
                onSave = {
                    scope.launch {
                        configRepository.saveAudiobookConfig(audiobookConfig)
                        audiobookTestState = ConnectionTestState(message = "AudiobookShelf 配置已保存")
                    }
                },
                onTestConnection = {
                    scope.launch {
                        if (!audiobookConfig.isReadyForAudiobookSync()) {
                            audiobookTestState = ConnectionTestState(message = "请先填写服务器地址、用户名和密码", isError = true)
                            return@launch
                        }
                        audiobookTestState = ConnectionTestState(isTesting = true, message = "正在测试 AudiobookShelf...")
                        try {
                            val libraryCount = AudiobookShelfRepository(audiobookConfig).testConnection()
                            audiobookTestState = ConnectionTestState(message = "AudiobookShelf 连接成功，找到 $libraryCount 个书库")
                        } catch (error: Exception) {
                            audiobookTestState = ConnectionTestState(
                                message = "AudiobookShelf 连接失败: ${error.message ?: "未知错误"}",
                                isError = true
                            )
                        }
                    }
                },
                isTestingConnection = audiobookTestState.isTesting,
                statusMessage = audiobookTestState.message,
                statusIsError = audiobookTestState.isError
            )
        }

        item {
            VideoConfigCard(
                config = videoConfig,
                colorScheme = colorScheme,
                onConfigChange = {
                    videoConfig = it
                    videoTestState = ConnectionTestState()
                },
                onSave = {
                    scope.launch {
                        configRepository.saveVideoConfig(videoConfig)
                        videoTestState = ConnectionTestState(message = "视频服务器配置已保存")
                    }
                },
                onTestConnection = {
                    scope.launch {
                        if (!videoConfig.isReadyForVideoSync()) {
                            videoTestState = ConnectionTestState(message = "请先填写 Emby 服务器地址和认证信息", isError = true)
                            return@launch
                        }
                        videoTestState = ConnectionTestState(isTesting = true, message = "正在测试 Emby...")
                        try {
                            val libraryCount = EmbyRepository(videoConfig).testConnection()
                            videoTestState = ConnectionTestState(message = "Emby 连接成功，找到 $libraryCount 个媒体库")
                        } catch (error: Exception) {
                            videoTestState = ConnectionTestState(
                                message = "Emby 连接失败: ${error.message ?: "未知错误"}",
                                isError = true
                            )
                        }
                    }
                },
                isTestingConnection = videoTestState.isTesting,
                statusMessage = videoTestState.message,
                statusIsError = videoTestState.isError
            )
        }
    }
}
