package com.nordic.mediahub.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nordic.mediahub.data.MediaAuthHeaderInterceptor
import com.nordic.mediahub.data.stripAuthQuery
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var cache: SimpleCache? = null

    companion object {
        @Volatile
        var audioSessionId: Int = 0
            private set
    }

    override fun onCreate() {
        super.onCreate()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(MediaAuthHeaderInterceptor())
                        .addNetworkInterceptor(com.nordic.mediahub.data.ScopedMediaNetworkInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Nordic")

        val cacheDir = File(cacheDir, "exo_player_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        cache = try {
            SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024),
                StandaloneDatabaseProvider(this)
            )
        } catch (e: Exception) {
            Log.e("MusicPlayback", "Cache init failed, continuing without cache", e)
            null
        }

        val upstreamFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val cacheDataSourceFactory = cache?.let { c ->
            CacheDataSource.Factory()
                .setCache(c)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheKeyFactory { dataSpec -> stripAuthQuery(dataSpec.uri) }
                .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
        } ?: upstreamFactory

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2500,
                50000,
                1000,
                2000
            )
            .build()

        val trackSelector = DefaultTrackSelector(this)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(cacheDataSourceFactory)
            )
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build().apply {
                setTrackSelectionParameters(
                    TrackSelectionParameters.Builder(this@MusicPlaybackService)
                        .setAudioOffloadPreferences(
                            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                                .setAudioOffloadMode(
                                    TrackSelectionParameters.AudioOffloadPreferences
                                        .AUDIO_OFFLOAD_MODE_ENABLED
                                )
                                .build()
                        )
                        .build()
                )
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
            }

        mediaSession = MediaSession.Builder(this, player)
            .setId("NordicMusicSession")
            .setSessionActivity(createSessionActivity())
            .build()

        audioSessionId = player.audioSessionId
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return if (controllerInfo.packageName == packageName) mediaSession else null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (
            player == null ||
            player.mediaItemCount == 0 ||
            !player.playWhenReady ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        cache?.release()
        cache = null
        super.onDestroy()
    }

    private fun createSessionActivity(): PendingIntent {
        val className = resolveSessionActivityClassName()
        val intent = if (className != null) {
            Intent().apply {
                setClassName(this@MusicPlaybackService, className)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent().apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun resolveSessionActivityClassName(): String? {
        val componentName = ComponentName(this, MusicPlaybackService::class.java)
        val flags = PackageManager.GET_META_DATA
        return try {
            val serviceInfo = packageManager.getServiceInfo(componentName, flags)
            serviceInfo.metaData?.getString("com.nordic.mediahub.session-activity")
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
