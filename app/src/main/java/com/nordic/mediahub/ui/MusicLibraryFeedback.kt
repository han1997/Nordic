package com.nordic.mediahub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.nordic.mediahub.ui.theme.NordicSpacing

/** Presentation only. The host still owns configuration, cache and request lifetimes. */
internal data class MusicLibraryFeedbackState(
    val resetNotice: String? = null,
    val detailNotice: String? = null,
    val error: String? = null,
    val hasContent: Boolean = false,
    val isInitialLoading: Boolean = false,
    val showSetup: Boolean = false
) {
    val standaloneError: String? get() = standaloneMediaError(error, hasContent)
    val suppressesEmptyState: Boolean get() = error != null || isInitialLoading || showSetup
    val isVisible: Boolean get() = resetNotice != null || detailNotice != null ||
        standaloneError != null || isInitialLoading || showSetup
}

/** Rendered inside each page's scroll container, never above a zero-height page viewport. */
@Composable
internal fun MusicLibraryFeedback(state: MusicLibraryFeedbackState, onRetry: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.md)) {
        state.resetNotice?.let {
            MediaStateCard("已应用新的音乐配置", it, density = MediaStateDensity.Compact)
        }
        state.detailNotice?.let {
            MediaStateCard("详情已更新", it, density = MediaStateDensity.Compact)
        }
        when {
            state.isInitialLoading -> MediaLoadingCard("正在同步 Navidrome", "加载专辑、歌曲和歌手…")
            state.standaloneError != null -> {
                MediaStateCard("加载失败", state.standaloneError.orEmpty(),
                    tone = MediaStateTone.Error, density = MediaStateDensity.Compact)
                if (onRetry != null) SecondaryActionButton("重试", MaterialTheme.colorScheme, onClick = onRetry)
            }
            state.showSetup -> MediaStateCard(
                "先接入你的音乐库", "在设置中填写 Navidrome 地址、用户名和密码，即可同步音乐。",
                hint = "前往设置中的音乐配置"
            )
        }
    }
}
