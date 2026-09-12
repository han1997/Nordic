package com.nordic.mediahub.data

import android.content.SharedPreferences
import java.security.GeneralSecurityException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class EncryptedPreferencesInstanceTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(10)

    @Test
    fun concurrentStoresOpenOnlyOneWrapperAndListenerRegistry() {
        val instance = EncryptedPreferencesInstance()
        val opens = AtomicInteger()
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = (1..8).map {
                executor.submit<SharedPreferences> {
                    ready.countDown()
                    check(start.await(2, TimeUnit.SECONDS))
                    instance.getOrCreate {
                        opens.incrementAndGet()
                        FakeSharedPreferences()
                    }
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            val wrappers = results.map { it.get(2, TimeUnit.SECONDS) }
            assertEquals(1, opens.get())
            wrappers.forEach { assertSame(wrappers.first(), it) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun initializationFailureIsPropagatedAndRetriedWithoutCachingFailure() {
        val instance = EncryptedPreferencesInstance()
        val expectedFailure = GeneralSecurityException("Keystore unavailable")
        val actualFailure = assertThrows(GeneralSecurityException::class.java) {
            instance.getOrCreate { throw expectedFailure }
        }
        assertSame(expectedFailure, actualFailure)

        val preferences = FakeSharedPreferences()
        assertSame(preferences, instance.getOrCreate { preferences })
        assertSame(preferences, instance.getOrCreate { error("Must reuse the successful wrapper") })
    }
}
