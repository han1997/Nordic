package com.nordic.mediahub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheTtlTest {
    @Test
    fun isCacheFresh_returnsFalseForNullTimestamp() {
        assertFalse(isCacheFresh(null))
    }

    @Test
    fun isCacheFresh_returnsFalseForNonPositiveTimestamp() {
        assertFalse(isCacheFresh(0L))
        assertFalse(isCacheFresh(-1L))
    }

    @Test
    fun isCacheFresh_returnsTrueJustInsideTtlBoundary() {
        val now = System.currentTimeMillis()
        // Exactly 30 minutes ago is still fresh (elapsed == CACHE_TTL_MILLIS).
        val justInside = now - CACHE_TTL_MILLIS
        assertTrue(isCacheFresh(justInside))
    }

    @Test
    fun isCacheFresh_returnsFalseJustBeyondTtlBoundary() {
        val now = System.currentTimeMillis()
        // One millisecond beyond the TTL window is stale.
        val justBeyond = now - (CACHE_TTL_MILLIS + 1L)
        assertFalse(isCacheFresh(justBeyond))
    }

    @Test
    fun isCacheFresh_returnsFalseForFutureDatedTimestamp() {
        val future = System.currentTimeMillis() + 60_000L
        assertFalse(isCacheFresh(future))
    }

    @Test
    fun isCacheFresh_returnsTrueForRecentlyStampedCache() {
        val now = System.currentTimeMillis()
        assertTrue(isCacheFresh(now - 60_000L))
    }

    @Test
    fun formatCacheAge_returnsNullForNullOrNonPositiveTimestamp() {
        assertNull(formatCacheAge(null))
        assertNull(formatCacheAge(0L))
        assertNull(formatCacheAge(-1L))
    }

    @Test
    fun formatCacheAge_returnsJustNowForSubMinuteAge() {
        val now = System.currentTimeMillis()
        assertEquals("刚刚更新", formatCacheAge(now - 5_000L))
    }

    @Test
    fun formatCacheAge_returnsMinutesLabelForSubHourAge() {
        val now = System.currentTimeMillis()
        assertEquals("12 分钟前更新", formatCacheAge(now - 12 * 60_000L))
    }

    @Test
    fun formatCacheAge_returnsHoursLabelForSubDayAge() {
        val now = System.currentTimeMillis()
        assertEquals("3 小时前更新", formatCacheAge(now - 3 * 3_600_000L))
    }

    @Test
    fun formatCacheAge_returnsDaysLabelForMultiDayAge() {
        val now = System.currentTimeMillis()
        assertEquals("2 天前更新", formatCacheAge(now - 2 * 86_400_000L))
    }

    @Test
    fun formatCacheAge_clampsFutureDatedTimestampToZero() {
        val future = System.currentTimeMillis() + 60_000L
        // Future timestamps are clamped to 0 elapsed, so they render as "刚刚更新".
        assertEquals("刚刚更新", formatCacheAge(future))
    }
}
