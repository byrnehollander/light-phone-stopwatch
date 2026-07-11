package com.byrne.stopwatch

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class StopwatchStoreTest {
    @Test
    fun snapshotsRoundTripThroughPreferencesDataStore() = runBlocking {
        val directory = Files.createTempDirectory("stopwatch-store-test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { directory.resolve("stopwatch.preferences_pb").toFile() },
        )
        val store = StopwatchStore(dataStore)

        try {
            assertNull(store.load())

            val states = listOf(
                StopwatchState.Idle,
                StopwatchState.Running(
                    anchorElapsedRealtimeMs = 12_000L,
                    anchorWallClockMs = 1_000_012_000L,
                    accumulatedMs = 3_000L,
                ),
                StopwatchState.Paused(accumulatedMs = 15_000L),
            )

            states.forEach { state ->
                store.save(state)
                assertEquals(state.toSnapshot(), store.load())
            }
        } finally {
            scope.cancel()
            directory.toFile().deleteRecursively()
        }
    }
}
