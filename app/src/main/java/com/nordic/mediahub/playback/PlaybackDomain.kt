package com.nordic.mediahub.playback

enum class MediaDomain { MUSIC, AUDIOBOOK }

object PlaybackDomain {
    @Volatile
    var activeDomain: MediaDomain? = null
}
