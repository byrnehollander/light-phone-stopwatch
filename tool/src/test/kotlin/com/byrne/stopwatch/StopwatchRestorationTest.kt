package com.byrne.stopwatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StopwatchRestorationTest {
    @Test
    fun missingOrWrongSchemaSnapshotRestoresIdle() {
        assertEquals(
            StopwatchRestoration(StopwatchState.Idle),
            restoreStopwatch(null, clock()),
        )
        assertEquals(
            StopwatchRestoration(StopwatchState.Idle),
            restoreStopwatch(snapshot(schema = 2), clock()),
        )
    }

    @Test
    fun invalidStateOrNegativeAccumulationRestoresIdle() {
        assertEquals(
            StopwatchState.Idle,
            restoreStopwatch(snapshot(state = "unknown"), clock()).state,
        )
        assertEquals(
            StopwatchState.Idle,
            restoreStopwatch(snapshot(accumulatedMs = -1L), clock()).state,
        )
    }

    @Test
    fun pausedSnapshotRestoresExactly() {
        val restoration = restoreStopwatch(
            snapshot(state = STOPWATCH_STATE_PAUSED, accumulatedMs = 12_345L),
            clock(elapsedRealtimeMs = 80_000L, wallClockMs = 1_000_080_000L),
        )

        assertEquals(StopwatchState.Paused(12_345L), restoration.state)
        assertFalse(restoration.shouldPersist)
    }

    @Test
    fun runningSnapshotUsesMonotonicClockOnSameBoot() {
        val restoration = restoreStopwatch(
            snapshot(
                state = STOPWATCH_STATE_RUNNING,
                accumulatedMs = 2_000L,
                anchorElapsedRealtimeMs = 10_000L,
                anchorWallClockMs = 1_000_010_000L,
            ),
            clock(elapsedRealtimeMs = 15_000L, wallClockMs = 1_000_015_000L),
        )

        assertEquals(7_000L, restoration.state.elapsedMs(15_000L))
        assertFalse(restoration.shouldPersist)
    }

    @Test
    fun smallWallClockCorrectionStillUsesMonotonicClock() {
        val restoration = restoreStopwatch(
            snapshot(
                state = STOPWATCH_STATE_RUNNING,
                accumulatedMs = 2_000L,
                anchorElapsedRealtimeMs = 10_000L,
                anchorWallClockMs = 1_000_010_000L,
            ),
            clock(
                elapsedRealtimeMs = 15_000L,
                wallClockMs = 1_000_045_000L,
            ),
        )

        assertEquals(7_000L, restoration.state.elapsedMs(15_000L))
        assertFalse(restoration.shouldPersist)
    }

    @Test
    fun rebootWithLowerUptimeFallsBackToWallClockAndReanchors() {
        val now = clock(elapsedRealtimeMs = 500L, wallClockMs = 1_000_020_000L)
        val restoration = restoreStopwatch(
            snapshot(
                state = STOPWATCH_STATE_RUNNING,
                accumulatedMs = 2_000L,
                anchorElapsedRealtimeMs = 10_000L,
                anchorWallClockMs = 1_000_010_000L,
            ),
            now,
        )

        assertEquals(
            StopwatchState.Running(
                anchorElapsedRealtimeMs = 500L,
                anchorWallClockMs = 1_000_020_000L,
                accumulatedMs = 12_000L,
            ),
            restoration.state,
        )
        assertTrue(restoration.shouldPersist)
    }

    @Test
    fun shiftedBootEpochDetectsRebootEvenWhenNewUptimeIsHigher() {
        val restoration = restoreStopwatch(
            snapshot(
                state = STOPWATCH_STATE_RUNNING,
                accumulatedMs = 2_000L,
                anchorElapsedRealtimeMs = 10_000L,
                anchorWallClockMs = 1_000_010_000L,
            ),
            clock(elapsedRealtimeMs = 20_000L, wallClockMs = 1_100_020_000L),
        )

        assertEquals(100_012_000L, restoration.state.elapsedMs(20_000L))
        assertTrue(restoration.shouldPersist)
    }

    @Test
    fun backwardWallClockDuringRecoveryPausesAtBankedTime() {
        val restoration = restoreStopwatch(
            snapshot(
                state = STOPWATCH_STATE_RUNNING,
                accumulatedMs = 2_000L,
                anchorElapsedRealtimeMs = 10_000L,
                anchorWallClockMs = 1_000_010_000L,
            ),
            clock(elapsedRealtimeMs = 500L, wallClockMs = 900_000_000L),
        )

        assertEquals(StopwatchState.Paused(2_000L), restoration.state)
        assertTrue(restoration.shouldPersist)
    }

    @Test
    fun invalidRunningClocksRestoreIdle() {
        val snapshotsAndClocks = listOf(
            snapshot(
                state = STOPWATCH_STATE_RUNNING,
                anchorElapsedRealtimeMs = -1L,
            ) to clock(),
            snapshot(
                state = STOPWATCH_STATE_RUNNING,
                anchorElapsedRealtimeMs = 2_000L,
                anchorWallClockMs = 1_000L,
            ) to clock(elapsedRealtimeMs = 3_000L, wallClockMs = 10_000L),
            snapshot(
                state = STOPWATCH_STATE_RUNNING,
                anchorElapsedRealtimeMs = 1_000L,
                anchorWallClockMs = 10_000L,
            ) to clock(elapsedRealtimeMs = -1L, wallClockMs = 10_000L),
            snapshot(
                state = STOPWATCH_STATE_RUNNING,
                anchorElapsedRealtimeMs = 1_000L,
                anchorWallClockMs = 10_000L,
            ) to clock(elapsedRealtimeMs = 3_000L, wallClockMs = 2_000L),
        )

        snapshotsAndClocks.forEach { (snapshot, now) ->
            assertEquals(StopwatchState.Idle, restoreStopwatch(snapshot, now).state)
        }
    }

    private fun snapshot(
        schema: Int = STOPWATCH_SCHEMA,
        state: String = STOPWATCH_STATE_IDLE,
        accumulatedMs: Long = 0L,
        anchorElapsedRealtimeMs: Long = 0L,
        anchorWallClockMs: Long = 0L,
    ) = StopwatchSnapshot(
        schema = schema,
        state = state,
        accumulatedMs = accumulatedMs,
        anchorElapsedRealtimeMs = anchorElapsedRealtimeMs,
        anchorWallClockMs = anchorWallClockMs,
    )

    private fun clock(
        elapsedRealtimeMs: Long = 0L,
        wallClockMs: Long = 0L,
    ) = ClockReading(elapsedRealtimeMs, wallClockMs)
}
