package com.nordic.mediahub.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.nordic.mediahub.api.NavidromeSong

private const val EXTRA_ID = "com.nordic.mediahub.extra.ID"
private const val EXTRA_TITLE = "com.nordic.mediahub.extra.TITLE"
private const val EXTRA_ARTIST = "com.nordic.mediahub.extra.ARTIST"
private const val EXTRA_ALBUM = "com.nordic.mediahub.extra.ALBUM"
private const val EXTRA_DURATION = "com.nordic.mediahub.extra.DURATION"
private const val EXTRA_COVER_ART = "com.nordic.mediahub.extra.COVER_ART"
private const val EXTRA_STREAM_URL = "com.nordic.mediahub.extra.STREAM_URL"
private const val EXTRA_CREATED = "com.nordic.mediahub.extra.CREATED"

fun NavidromeSong.toMediaItem(): MediaItem {
    val streamUrl = streamUrl.orEmpty()
    val extras = bundleOf(
        EXTRA_ID to id,
        EXTRA_TITLE to title,
        EXTRA_ARTIST to artist,
        EXTRA_ALBUM to album,
        EXTRA_DURATION to duration,
        EXTRA_COVER_ART to coverArt,
        EXTRA_STREAM_URL to streamUrl,
        EXTRA_CREATED to created
    )

    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setExtras(extras)

    coverArt?.let { metadataBuilder.setArtworkUri(Uri.parse(it)) }

    return MediaItem.Builder()
        .setUri(streamUrl)
        .setMediaId(id)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}

fun MediaItem.toNavidromeSong(): NavidromeSong? {
    val extras = mediaMetadata.extras ?: Bundle.EMPTY
    val resolvedId = mediaId.ifBlank { extras.getString(EXTRA_ID).orEmpty() }
    val resolvedTitle = mediaMetadata.title?.toString()
        ?: extras.getString(EXTRA_TITLE)
        ?: return null

    return NavidromeSong(
        id = resolvedId,
        title = resolvedTitle,
        artist = mediaMetadata.artist?.toString() ?: extras.getString(EXTRA_ARTIST),
        album = mediaMetadata.albumTitle?.toString() ?: extras.getString(EXTRA_ALBUM),
        duration = extras.getInt(EXTRA_DURATION, 0),
        coverArt = mediaMetadata.artworkUri?.toString() ?: extras.getString(EXTRA_COVER_ART),
        streamUrl = localConfiguration?.uri?.toString() ?: extras.getString(EXTRA_STREAM_URL),
        created = extras.getString(EXTRA_CREATED)
    )
}
