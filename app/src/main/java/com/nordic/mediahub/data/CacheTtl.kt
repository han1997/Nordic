package com.nordic.mediahub.data

/**
 * Shared cache freshness / presentation helpers used by every media domain
 * (Music / Audiobook / Video). Keeping these in a single place avoids
 * duplicating the TTL threshold and the cache-age label wording across screens.
 */

internal const val CACHE_TTL_MILLIS = 30L * 60L * 1000L

/**
 * Returns true when a cache stamped with [updatedAtMillis] is still within the
 * browse cache TTL window. `null`, non-positive, or future-dated timestamps are
 * treated as stale so launch refresh still runs when no usable cache exists.
 *
 * Manual refresh (the ↻ button) must always bypass this check and force a
 * network refresh; callers gate the launch-path refresh on this helper, not the
 * manual refresh path.
 */
internal fun isCacheFresh(updatedAtMillis: Long?): Boolean {
    if (updatedAtMillis == null || updatedAtMillis <= 0L) return false
    val elapsed = System.currentTimeMillis() - updatedAtMillis
    return elapsed in 0..CACHE_TTL_MILLIS
}

/**
 * Formats a human-readable cache-age label (e.g. "3 分钟前更新") for header
 * subtitles. Returns `null` when there is no usable timestamp, so callers can
 * treat a `null` result as "no cache to describe".
 */
internal fun formatCacheAge(updatedAtMillis: Long?): String? {
    if (updatedAtMillis == null || updatedAtMillis <= 0L) return null

    val elapsedMillis = (System.currentTimeMillis() - updatedAtMillis).coerceAtLeast(0L)
    val elapsedMinutes = elapsedMillis / 60_000L
    val elapsedHours = elapsedMillis / 3_600_000L
    val elapsedDays = elapsedMillis / 86_400_000L

    return when {
        elapsedMinutes < 1L -> "刚刚更新"
        elapsedMinutes < 60L -> "${elapsedMinutes} 分钟前更新"
        elapsedHours < 24L -> "${elapsedHours} 小时前更新"
        else -> "${elapsedDays} 天前更新"
    }
}
