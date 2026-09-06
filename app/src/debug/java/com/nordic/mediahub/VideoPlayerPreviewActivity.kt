package com.nordic.mediahub

import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.playback.VideoPlaybackState
import com.nordic.mediahub.playback.resolveNextAspectRatioMode
import com.nordic.mediahub.playback.updateVideoEpisodeProgress
import com.nordic.mediahub.ui.VideoPlayerScreen
import com.nordic.mediahub.ui.resolveNextVideoEpisode
import com.nordic.mediahub.ui.theme.NordicTheme

/** A debug-only visual/interaction fixture. Never creates a repository or a playback engine. */
class VideoPlayerPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val scenario = intent.getStringExtra("scenario").orEmpty()
        setContent {
            NordicTheme(darkTheme = true) {
                var episodes by remember { mutableStateOf(previewEpisodes()) }
                var state by remember {
                    val episode = episodes[3]
                    val video = when (scenario) {
                        "movie" -> episode.copy(type = "Movie", title = "布局验证 · 电影", seriesId = null, seriesName = null)
                        "empty" -> null
                        "unknown" -> episode.copy(durationSeconds = 0)
                        else -> episode
                    }
                    mutableStateOf(VideoPlaybackState(
                        video = video, durationSeconds = if (scenario == "unknown") 0 else 2700,
                        positionSeconds = if (scenario == "end") 2690 else 754,
                        bufferedPositionSeconds = 1200, isPlaying = scenario == "end",
                        isBuffering = scenario == "buffering",
                        errorMessage = if (scenario == "error") "预览：视频流暂时不可用" else null
                    ))
                }
                var fullscreen by remember { mutableStateOf(intent.getBooleanExtra("fullscreen", false)) }
                LaunchedEffect(fullscreen) {
                    requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars())
                    else controller.show(WindowInsetsCompat.Type.systemBars())
                }
                val surfaces = remember { mutableMapOf<SurfaceView, SurfaceHolder.Callback>() }
                fun select(video: VideoItem) {
                    state.video?.let { episodes = updateVideoEpisodeProgress(episodes, it, state.positionSeconds) }
                    state = state.copy(video = video, positionSeconds = video.playbackPositionSeconds,
                        isPlaying = false, isBuffering = false, errorMessage = null)
                }
                val next = state.video?.let { resolveNextVideoEpisode(it, episodes) }
                VideoPlayerScreen(
                    state = state, colorScheme = MaterialTheme.colorScheme,
                    onSurfaceReady = { view ->
                        val callback = object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = drawPreview(holder)
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = drawPreview(holder)
                            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                        }
                        surfaces[view] = callback
                        view.holder.addCallback(callback)
                        if (view.holder.surface.isValid) drawPreview(view.holder)
                    },
                    onSurfaceDisposed = { view -> surfaces.remove(view)?.let { view.holder.removeCallback(it) } },
                    onSeek = { state = state.copy(positionSeconds = it) },
                    onSeekRelative = { state = state.copy(positionSeconds = (state.positionSeconds + it).coerceAtLeast(0)) },
                    onPlayPause = { state = state.copy(isPlaying = !state.isPlaying, isBuffering = false, errorMessage = null) },
                    onCycleAspectRatio = { state = state.copy(aspectRatioMode = resolveNextAspectRatioMode(state.aspectRatioMode)) },
                    onSetPlaybackSpeed = { state = state.copy(playbackSpeed = it) },
                    nextEpisode = next, episodeContext = episodes, onPlayEpisode = ::select,
                    onPlayNextEpisode = { next?.let(::select) },
                    onToggleFullscreen = { fullscreen = !fullscreen }, isFullscreen = fullscreen,
                    onClose = ::finish, onCloseAnyway = ::finish
                )
            }
        }
    }
}

private fun previewEpisodes(): List<VideoItem> = (1..24).map { index ->
    VideoItem(
        id = "preview-$index", libraryId = "preview", type = "Episode",
        title = if (index == 4) "布局验证 · 冰原与海岸之间的一段漫长旅程" else "布局验证 · 第 $index 集",
        seriesId = "preview-series", seriesName = "Nordic · 播放器布局验证",
        seasonNumber = if (index <= 12) 1 else 2, episodeNumber = (index - 1) % 12 + 1,
        durationSeconds = 2700, playbackPositionSeconds = if (index == 5) 180 else 0,
        isPlayed = index < 4, streamUrl = if (index == 8) null else "preview://$index",
        overview = "仅用于验证布局、选集和手势。此预览不访问媒体服务器，不会修改真实观看记录。"
    )
}

private fun drawPreview(holder: SurfaceHolder) {
    val canvas: Canvas = holder.lockCanvas() ?: return
    try {
        canvas.drawColor(android.graphics.Color.rgb(29, 43, 55))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(71, 98, 111)
        }
        canvas.drawRect(0f, canvas.height * 0.62f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
        paint.color = android.graphics.Color.rgb(181, 200, 203)
        paint.textSize = canvas.width * 0.04f
        canvas.drawText("NORDIC  /  PLAYER PREVIEW", canvas.width * 0.06f, canvas.height * 0.25f, paint)
    } finally {
        holder.unlockCanvasAndPost(canvas)
    }
}
