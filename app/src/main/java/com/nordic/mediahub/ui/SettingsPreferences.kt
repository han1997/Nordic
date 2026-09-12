package com.nordic.mediahub.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.nordic.mediahub.data.*
import com.nordic.mediahub.playback.PLAYBACK_SPEED_OPTIONS
import com.nordic.mediahub.playback.resolvePlaybackSpeedLabel

internal enum class SettingsPage(val title: String) {
    HOME("设置"), SERVERS("媒体服务器"), APPEARANCE("外观与启动"), MUSIC("音乐播放"), AUDIOBOOK("有声书播放"),
    VIDEO("视频播放"), STORAGE("存储与下载"), PRIVACY("隐私与数据"), ABOUT("关于与帮助"),
    ADD_SERVER("添加服务器"), EDIT_SERVER("编辑服务器"), DOWNLOADS("已下载音乐"), LEGACY("待归属旧数据"), HELP("连接帮助"), LICENSES("开源声明")
}
internal val SETTINGS_HOME_PAGES = listOf(SettingsPage.SERVERS, SettingsPage.APPEARANCE, SettingsPage.MUSIC,
    SettingsPage.AUDIOBOOK, SettingsPage.VIDEO, SettingsPage.STORAGE, SettingsPage.PRIVACY, SettingsPage.ABOUT)
internal data class SettingsSearchEntry(val page: SettingsPage, val id: String, val title: String, val keywords: String = "")
internal val SETTINGS_SEARCH_ENTRIES = listOf(
    SettingsSearchEntry(SettingsPage.SERVERS, "servers", "媒体服务器", "Navidrome AudiobookShelf Emby WebDAV 账号 地址 密码 多服务器"),
    SettingsSearchEntry(SettingsPage.APPEARANCE, "theme", "主题", "深色 浅色 跟随系统"),
    SettingsSearchEntry(SettingsPage.APPEARANCE, "startup", "启动页", "首页 默认页面"),
    SettingsSearchEntry(SettingsPage.MUSIC, "music_speed", "音乐默认倍速"),
    SettingsSearchEntry(SettingsPage.MUSIC, "music_modes", "记住循环与随机模式"),
    SettingsSearchEntry(SettingsPage.MUSIC, "music_view", "播放器默认视图", "封面 歌词"),
    SettingsSearchEntry(SettingsPage.AUDIOBOOK, "book_speed", "有声书默认倍速"),
    SettingsSearchEntry(SettingsPage.AUDIOBOOK, "book_back", "有声书后退步长"),
    SettingsSearchEntry(SettingsPage.AUDIOBOOK, "book_forward", "有声书前进步长"),
    SettingsSearchEntry(SettingsPage.AUDIOBOOK, "book_sleep", "睡眠定时预选时长"),
    SettingsSearchEntry(SettingsPage.VIDEO, "video_speed", "视频默认倍速"),
    SettingsSearchEntry(SettingsPage.VIDEO, "video_pip", "画中画", "PiP 小窗"),
    SettingsSearchEntry(SettingsPage.VIDEO, "video_auto_play_next", "自动连播", "下一集 连续播放 Emby WebDAV"),
    SettingsSearchEntry(SettingsPage.VIDEO, "video_aspect", "画面比例", "适应 裁切 拉伸"),
    SettingsSearchEntry(SettingsPage.VIDEO, "video_back", "视频后退步长"),
    SettingsSearchEntry(SettingsPage.VIDEO, "video_forward", "视频前进步长"),
    SettingsSearchEntry(SettingsPage.VIDEO, "subtitle", "默认字幕", "语言 中文 英语"),
    SettingsSearchEntry(SettingsPage.VIDEO, "audio_track", "首选音轨", "语言 中文 英语"),
    SettingsSearchEntry(SettingsPage.STORAGE, "storage", "缓存与音乐下载", "空间 占用 删除 清理"),
    SettingsSearchEntry(SettingsPage.PRIVACY, "privacy", "本机历史与书签", "进度 恢复默认 隐私 旧数据 归属"),
    SettingsSearchEntry(SettingsPage.ABOUT, "about", "版本与帮助", "开源 协议 连接指南")
)
internal fun searchSettings(query: String): List<SettingsSearchEntry> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return emptyList()
    return SETTINGS_SEARCH_ENTRIES.filter { entry -> terms.all { "${entry.title} ${entry.page.title} ${entry.keywords}".contains(it, true) } }
}

@Composable
internal fun PreferenceSettingsPage(
    page: SettingsPage, prefs: AppPreferences, videoSource: MediaSource?, highlight: String?,
    update: ((AppPreferences) -> AppPreferences) -> Unit,
    choice: (SettingsChoiceRequest) -> Unit
) {
    fun choose(title: String, current: String, values: List<SettingsChoice>, select: (String) -> Unit) {
        choice(SettingsChoiceRequest(title, current, values, select))
    }
    fun speed(title: String, id: String, value: Float, options: List<Float>, set: (AppPreferences, Float) -> AppPreferences): @Composable () -> Unit = {
        SettingsRow(title, value = resolvePlaybackSpeedLabel(value), id = id, highlighted = highlight == id, onClick = {
            choose(title, value.toString(), options.map { SettingsChoice(it.toString(), resolvePlaybackSpeedLabel(it)) }) { selected -> update { set(it, selected.toFloat()) } }
        })
    }
    fun interval(title: String, id: String, value: Int, set: (AppPreferences, Int) -> AppPreferences): @Composable () -> Unit = {
        SettingsRow(title, value = "$value 秒", id = id, highlighted = highlight == id, onClick = {
            choose(title, value.toString(), SEEK_INTERVAL_OPTIONS.map { SettingsChoice(it.toString(), "$it 秒") }) { selected -> update { set(it, selected.toInt()) } }
        })
    }
    Column {
        when (page) {
            SettingsPage.APPEARANCE -> {
                SettingsRow("主题", value = prefs.theme.label, id = "theme", highlighted = highlight == "theme", onClick = {
                    choose("主题", prefs.theme.name, ThemeMode.entries.map { SettingsChoice(it.name, it.label) }) { selected -> update { it.copy(theme = ThemeMode.valueOf(selected)) } }
                })
                SettingsRow("启动页", value = prefs.startupPage.label, id = "startup", highlighted = highlight == "startup", onClick = {
                    choose("启动页", prefs.startupPage.name, StartupPage.entries.map { SettingsChoice(it.name, it.label) }) { selected -> update { it.copy(startupPage = StartupPage.valueOf(selected)) } }
                })
                SettingsRow("字体与显示大小", "跟随 Android 系统设置，兼顾大字体与无障碍阅读。")
            }
            SettingsPage.MUSIC -> {
                speed("默认倍速", "music_speed", prefs.musicSpeed, PLAYBACK_SPEED_OPTIONS) { p, v -> p.copy(musicSpeed = v) }()
                SettingsRow("记住循环与随机模式", "关闭后，新播放队列使用顺序播放。", checked = prefs.rememberMusicModes, id = "music_modes",
                    highlighted = highlight == "music_modes", onCheckedChange = { value -> update { it.copy(rememberMusicModes = value) } })
                SettingsRow("播放器默认视图", value = prefs.musicDefaultView.label, id = "music_view", highlighted = highlight == "music_view", onClick = {
                    choose("播放器默认视图", prefs.musicDefaultView.name, MusicDefaultView.entries.map { SettingsChoice(it.name, it.label) }) { selected -> update { it.copy(musicDefaultView = MusicDefaultView.valueOf(selected)) } }
                })
            }
            SettingsPage.AUDIOBOOK -> {
                speed("默认倍速", "book_speed", prefs.audiobookSpeed, AUDIOBOOK_PLAYBACK_SPEED_OPTIONS) { p, v -> p.copy(audiobookSpeed = v) }()
                interval("后退步长", "book_back", prefs.audiobookSkipBack) { p, v -> p.copy(audiobookSkipBack = v) }()
                interval("前进步长", "book_forward", prefs.audiobookSkipForward) { p, v -> p.copy(audiobookSkipForward = v) }()
                SettingsRow("睡眠定时预选时长", "只改变预选值，不自动启动定时器。", value = "${prefs.audiobookSleepMinutes} 分钟", id = "book_sleep",
                    highlighted = highlight == "book_sleep", onClick = {
                        choose("睡眠定时预选时长", prefs.audiobookSleepMinutes.toString(), SLEEP_MINUTE_OPTIONS.map { SettingsChoice(it.toString(), "$it 分钟") }) { selected -> update { it.copy(audiobookSleepMinutes = selected.toInt()) } }
                    })
            }
            SettingsPage.VIDEO -> {
                speed("默认倍速", "video_speed", prefs.videoSpeed, VIDEO_PLAYBACK_SPEED_OPTIONS) { p, v -> p.copy(videoSpeed = v) }()
                SettingsRow("画中画", "播放中返回桌面时进入小窗，需系统允许画中画。", checked = prefs.videoPip,
                    id = "video_pip", highlighted = highlight == "video_pip", onCheckedChange = { value -> update { it.copy(videoPip = value) } })
                SettingsRow("自动连播", "仅前台生效，播完后倒计时 5 秒播放下一集，可随时取消。", checked = prefs.videoAutoPlayNext,
                    id = "video_auto_play_next", highlighted = highlight == "video_auto_play_next",
                    onCheckedChange = { value -> update { it.copy(videoAutoPlayNext = value) } })
                val aspect = mapOf("FIT" to "适应", "CROP" to "裁切填充", "FILL" to "拉伸填充")
                SettingsRow("画面比例", value = aspect[prefs.videoAspect], id = "video_aspect", highlighted = highlight == "video_aspect", onClick = {
                    choose("画面比例", prefs.videoAspect, aspect.map { SettingsChoice(it.key, it.value) }) { selected -> update { it.copy(videoAspect = selected) } }
                })
                interval("后退步长", "video_back", prefs.videoSkipBack) { p, v -> p.copy(videoSkipBack = v) }()
                interval("前进步长", "video_forward", prefs.videoSkipForward) { p, v -> p.copy(videoSkipForward = v) }()
                SettingsSectionTitle("字幕与音轨 · 下次播放生效")
                SettingsRow("默认字幕", value = prefs.subtitleLanguage.label, id = "subtitle", highlighted = highlight == "subtitle", onClick = {
                    choose("默认字幕", prefs.subtitleLanguage.name, TrackLanguage.entries.filter { it != TrackLanguage.DEFAULT }.map { SettingsChoice(it.name, it.label) }) { selected -> update { it.copy(subtitleLanguage = TrackLanguage.valueOf(selected)) } }
                })
                SettingsRow("首选音轨", value = prefs.audioLanguage.label, id = "audio_track", highlighted = highlight == "audio_track", onClick = {
                    choose("首选音轨", prefs.audioLanguage.name, TrackLanguage.entries.filter { it != TrackLanguage.OFF }.map { SettingsChoice(it.name, it.label) }) { selected -> update { it.copy(audioLanguage = TrackLanguage.valueOf(selected)) } }
                })
                if (videoSource?.kind == MediaSourceKind.EMBY) {
                    SettingsSectionTitle("Emby · ${videoSource.name}")
                    SettingsRow("默认清晰度", "下次播放生效，限码率档位需要服务器转码。", value = prefs.videoQuality.label, onClick = {
                        choose("默认清晰度", prefs.videoQuality.name, VideoQualityMode.entries.map { SettingsChoice(it.name, it.label) }) { selected -> update { it.copy(videoQuality = VideoQualityMode.valueOf(selected)) } }
                    })
                    SettingsRow("自动跳过片头", "仅对服务器提供片头标记的视频生效。", checked = prefs.videoAutoSkipIntro,
                        onCheckedChange = { value -> update { it.copy(videoAutoSkipIntro = value) } })
                }
                if (videoSource?.kind == MediaSourceKind.WEBDAV) {
                    SettingsSectionTitle("WebDAV · ${videoSource.name}")
                    SettingsRow("目录排序", value = prefs.webDavSort.label, onClick = {
                        choose("目录排序", prefs.webDavSort.name, WebDavSort.entries.map { SettingsChoice(it.name, it.label) }) { selected -> update { it.copy(webDavSort = WebDavSort.valueOf(selected)) } }
                    })
                    SettingsRow("倒序排列", checked = prefs.webDavSortDescending, onCheckedChange = { value -> update { it.copy(webDavSortDescending = value) } })
                    SettingsRow("显示隐藏文件", checked = prefs.webDavShowHidden, onCheckedChange = { value -> update { it.copy(webDavShowHidden = value) } })
                    SettingsRow("仅显示视频与文件夹", checked = prefs.webDavOnlyVideos, onCheckedChange = { value -> update { it.copy(webDavOnlyVideos = value) } })
                    SettingsRow("原画播放", "WebDAV 不提供服务器转码；格式兼容性取决于设备与播放器。")
                }
            }
            else -> Unit
        }
    }
}