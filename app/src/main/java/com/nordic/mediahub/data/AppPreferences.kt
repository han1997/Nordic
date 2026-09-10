package com.nordic.mediahub.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
enum class StartupPage(val label: String, val tab: Int) {
    MUSIC("音乐", 0), AUDIOBOOK("有声书", 1), VIDEO("视频", 2), LAST("上次媒体页", -1)
}
enum class MusicDefaultView(val label: String) { COVER("封面"), LYRICS("歌词"), REMEMBER("记住上次") }
enum class TrackLanguage(val label: String, val language: String?) {
    DEFAULT("媒体默认", null), OFF("关闭", null), SYSTEM("跟随系统", null), CHINESE("中文优先", "zh"), ENGLISH("英语优先", "en")
}
enum class WebDavSort(val label: String) { NAME("名称"), MODIFIED("修改时间"), SIZE("大小") }

/** Device preferences, independent of whichever media server is selected. */
data class AppPreferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val startupPage: StartupPage = StartupPage.MUSIC,
    val lastMediaTab: Int = 0,
    val musicSpeed: Float = 1f,
    val rememberMusicModes: Boolean = true,
    val musicRepeatMode: Int = 0,
    val musicShuffle: Boolean = false,
    val musicDefaultView: MusicDefaultView = MusicDefaultView.REMEMBER,
    val lastMusicLyrics: Boolean = false,
    val audiobookSpeed: Float = 1f,
    val audiobookSkipBack: Int = 30,
    val audiobookSkipForward: Int = 30,
    val audiobookSleepMinutes: Int = 30,
    val videoSpeed: Float = 1f,
    val videoPip: Boolean = true,
    val videoAutoSkipIntro: Boolean = true,
    val videoQuality: VideoQualityMode = VideoQualityMode.AUTO,
    val videoAspect: String = "FIT",
    val videoSkipBack: Int = 10,
    val videoSkipForward: Int = 30,
    val subtitleLanguage: TrackLanguage = TrackLanguage.OFF,
    val audioLanguage: TrackLanguage = TrackLanguage.DEFAULT,
    val webDavShowHidden: Boolean = false,
    val webDavOnlyVideos: Boolean = true,
    val webDavSort: WebDavSort = WebDavSort.NAME,
    val webDavSortDescending: Boolean = false
)

internal val SEEK_INTERVAL_OPTIONS = listOf(5, 10, 15, 30, 60)
internal val SLEEP_MINUTE_OPTIONS = listOf(10, 20, 30, 45, 60)

internal fun AppPreferences.validated(): AppPreferences = copy(
    theme = theme.takeIf { it in ThemeMode.entries } ?: ThemeMode.SYSTEM,
    startupPage = startupPage.takeIf { it in StartupPage.entries } ?: StartupPage.MUSIC,
    lastMediaTab = lastMediaTab.coerceIn(0, 2),
    musicSpeed = musicSpeed.takeIf { it.isFinite() && it in 0.5f..2f } ?: 1f,
    musicRepeatMode = musicRepeatMode.coerceIn(0, 2),
    musicDefaultView = musicDefaultView.takeIf { it in MusicDefaultView.entries } ?: MusicDefaultView.REMEMBER,
    audiobookSpeed = audiobookSpeed.takeIf { it.isFinite() && it in 0.5f..3f } ?: 1f,
    audiobookSkipBack = audiobookSkipBack.takeIf { it in SEEK_INTERVAL_OPTIONS } ?: 30,
    audiobookSkipForward = audiobookSkipForward.takeIf { it in SEEK_INTERVAL_OPTIONS } ?: 30,
    audiobookSleepMinutes = audiobookSleepMinutes.takeIf { it in SLEEP_MINUTE_OPTIONS } ?: 30,
    videoSpeed = videoSpeed.takeIf { it.isFinite() && it in 0.5f..2f } ?: 1f,
    videoAspect = videoAspect.takeIf { it in listOf("FIT", "CROP", "FILL") } ?: "FIT",
    videoSkipBack = videoSkipBack.takeIf { it in SEEK_INTERVAL_OPTIONS } ?: 10,
    videoSkipForward = videoSkipForward.takeIf { it in SEEK_INTERVAL_OPTIONS } ?: 30,
    videoQuality = videoQuality.takeIf { it in VideoQualityMode.entries } ?: VideoQualityMode.AUTO,
    subtitleLanguage = subtitleLanguage.takeIf { it in TrackLanguage.entries && it != TrackLanguage.DEFAULT } ?: TrackLanguage.OFF,
    audioLanguage = audioLanguage.takeIf { it in TrackLanguage.entries && it != TrackLanguage.OFF } ?: TrackLanguage.DEFAULT,
    webDavSort = webDavSort.takeIf { it in WebDavSort.entries } ?: WebDavSort.NAME
)

internal object AppPreferencesCodec {
    private val gson = Gson()
    fun encode(value: AppPreferences): String = gson.toJson(value.validated())
    fun decode(json: String?): AppPreferences {
        if (json.isNullOrBlank()) return AppPreferences()
        return try {
            val merged = gson.toJsonTree(AppPreferences()).asJsonObject
            JsonParser.parseString(json).asJsonObject.entrySet().forEach { (key, value) ->
                if (merged.has(key) && !value.isJsonNull) merged.add(key, value)
            }
            gson.fromJson(merged, AppPreferences::class.java).validated()
        } catch (error: Exception) {
            throw IOException("应用偏好无法读取，请在隐私与数据中恢复默认值", error)
        }
    }
}