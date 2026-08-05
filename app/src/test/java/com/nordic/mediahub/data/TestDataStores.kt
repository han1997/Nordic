package com.nordic.mediahub.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Test-only in-memory `DataStore<Preferences>`. The real file-backed
 * `PreferenceDataStoreFactory` is flaky in pure-JVM tests on Windows because
 * `File.renameTo` for the `.tmp` -> final swap can fail outside Android. This
 * fake implements the two members the cache repositories rely on (`data` flow
 * and `updateData`) so `edit { }` / `data.first()` behave like the real store
 * without touching disk.
 */
internal class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences>
        get() = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        // Single-threaded test usage (runBlocking) means no optimistic-retry is
        // needed; apply the transform and publish the new preferences directly.
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

internal fun fakeDataStore(): DataStore<Preferences> = FakePreferencesDataStore()

/**
 * Runs [block] in a blocking coroutine. Cache repository suspend functions use
 * `dataStore.edit` / `dataStore.data.first()` which need a coroutine context.
 */
internal fun runCacheTest(block: suspend () -> Unit) {
    runBlocking { block() }
}
