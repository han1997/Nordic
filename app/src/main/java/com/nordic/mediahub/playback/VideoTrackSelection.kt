package com.nordic.mediahub.playback

import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.nordic.mediahub.data.VideoItem
import com.nordic.mediahub.data.VideoServerType
import com.nordic.mediahub.data.VideoStreamInfo
import com.nordic.mediahub.data.VideoStreamKind

/** Platform-neutral snapshot of one track, keeping group identity separate from presentation indices. */
internal data class VideoTrackCandidate(
    val groupId: String, val groupIndex: Int, val trackIndex: Int, val kind: VideoStreamKind,
    val formatId: String? = null, val codec: String? = null, val language: String? = null,
    val label: String? = null, val supported: Boolean = true, val selected: Boolean = false
)

@androidx.annotation.OptIn(UnstableApi::class)
internal fun videoTrackCandidates(tracks: Tracks): List<VideoTrackCandidate> = tracks.groups.withIndex().flatMap { (groupIndex, group) ->
    val kind = when (group.type) { C.TRACK_TYPE_AUDIO -> VideoStreamKind.Audio; C.TRACK_TYPE_TEXT -> VideoStreamKind.Subtitle; else -> return@flatMap emptyList() }
    (0 until group.length).map { index ->
        val format = group.getTrackFormat(index)
        VideoTrackCandidate(group.mediaTrackGroup.id, groupIndex, index, kind, format.id, format.codecs ?: format.sampleMimeType,
            format.language, format.label, group.isTrackSupported(index), group.isTrackSelected(index))
    }
}

internal fun availableVideoStreams(video: VideoItem, kind: VideoStreamKind, candidates: List<VideoTrackCandidate>): List<VideoStreamInfo> {
    val relevant = candidates.filter { it.kind == kind }
    val runtime = relevant.filter { it.supported }.map { track ->
        val declared = video.mediaStreams.firstOrNull { it.kind == kind && it.index.toString() == track.formatId }
        VideoStreamInfo((track.groupIndex + 1) * 1000 + track.trackIndex, kind, track.codec, track.language,
            track.label ?: declared?.displayTitle ?: track.language ?: "${if (kind == VideoStreamKind.Audio) "音轨" else "字幕"} ${track.groupIndex + 1}",
            track.formatId?.contains("subtitle-") == true || track.groupId.contains("subtitle-"), track.groupId, track.trackIndex)
    }
    if (kind != VideoStreamKind.Subtitle || video.sourceType == VideoServerType.WEBDAV) return runtime
    val pendingExternal = video.mediaStreams.filter { stream -> stream.kind == kind && stream.isExternal &&
        relevant.none { it.formatId?.contains("emby-subtitle-${stream.index}") == true || it.groupId.contains("emby-subtitle-${stream.index}") }
    }.map { it.copy(trackGroupId = "emby-subtitle-${it.index}") }
    return runtime + pendingExternal
}

internal fun selectedVideoStream(streams: List<VideoStreamInfo>, candidates: List<VideoTrackCandidate>): VideoStreamInfo? =
    streams.firstOrNull { stream -> candidates.any { it.groupId == stream.trackGroupId && it.trackIndex == stream.trackIndex && it.selected } }