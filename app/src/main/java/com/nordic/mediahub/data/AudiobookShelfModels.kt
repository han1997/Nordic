package com.nordic.mediahub.data

import androidx.compose.runtime.Stable

@Stable
data class AudiobookLibrarySummary(
    val id: String,
    val name: String,
    val mediaType: String
)

@Stable
data class AudiobookItemSummary(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String,
    val narrator: String,
    val series: String,
    val coverUrl: String?,
    val durationSeconds: Int,
    val chapterCount: Int,
    val updatedAtMillis: Long,
    val progress: AudiobookProgress? = null
)

@Stable
data class AudiobookItemDetail(
    val id: String,
    val libraryId: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val authors: List<String>,
    val narrators: List<String>,
    val series: List<String>,
    val coverUrl: String?,
    val durationSeconds: Int,
    val chapters: List<AudiobookChapter>,
    val progress: AudiobookProgress?
)

@Stable
data class AudiobookChapter(
    val id: Int,
    val title: String,
    val startSeconds: Int,
    val endSeconds: Int
)

@Stable
data class AudiobookProgress(
    val currentTimeSeconds: Int,
    val durationSeconds: Int,
    val progressFraction: Float,
    val isFinished: Boolean,
    val lastUpdateMillis: Long
)

@Stable
data class AudiobookPlaybackSession(
    val sessionId: String,
    val libraryItemId: String,
    val displayTitle: String,
    val displayAuthor: String,
    val coverUrl: String?,
    val durationSeconds: Int,
    val currentTimeSeconds: Int,
    val startTimeSeconds: Int,
    val chapters: List<AudiobookChapter>,
    val audioTracks: List<AudiobookAudioTrack>
)

@Stable
data class AudiobookAudioTrack(
    val index: Int,
    val title: String,
    val contentUrl: String,
    val startOffsetSeconds: Int,
    val durationSeconds: Int
)
