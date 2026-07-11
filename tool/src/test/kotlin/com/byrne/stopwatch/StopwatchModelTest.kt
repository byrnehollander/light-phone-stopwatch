package com.byrne.stopwatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StopwatchModelTest {
    @Test
    fun startCreatesRunningStateAtCurrentClockReading() {
        val now = clock(elapsedRealtimeMs = 12_000L, wallClockMs = 50_000L)

        assertEquals(
            StopwatchState.Running(
                anchorElapsedRealtimeMs = 12_000L,
                anchorWallClockMs = 50_000L,
                accumulatedMs = 0L,
            ),
            reduceStopwatch(StopwatchState.Idle, StopwatchAction.Start, now),
        )
    }

    @Test
    fun pauseBanksElapsedTime() {
        val running = StopwatchState.Running(
            anchorElapsedRealtimeMs = 10_000L,
            anchorWallClockMs = 50_000L,
            accumulatedMs = 2_500L,
        )

        assertEquals(
            StopwatchState.Paused(accumulatedMs = 7_500L),
            reduceStopwatch(
                running,
                StopwatchAction.Pause,
                clock(elapsedRealtimeMs = 15_000L, wallClockMs = 55_000L),
            ),
        )
    }

    @Test
    fun resumePreservesBankedTimeAndUsesFreshAnchors() {
        val now = clock(elapsedRealtimeMs = 20_000L, wallClockMs = 80_000L)

        assertEquals(
            StopwatchState.Running(
                anchorElapsedRealtimeMs = 20_000L,
                anchorWallClockMs = 80_000L,
                accumulatedMs = 7_500L,
            ),
            reduceStopwatch(
                StopwatchState.Paused(accumulatedMs = 7_500L),
                StopwatchAction.Resume,
                now,
            ),
        )
    }

    @Test
    fun resetOnlyChangesPausedState() {
        val now = clock()
        val running = StopwatchState.Running(1L, 2L, 3L)

        assertSame(
            running,
            reduceStopwatch(running, StopwatchAction.Reset, now),
        )
        assertEquals(
            StopwatchState.Idle,
            reduceStopwatch(StopwatchState.Paused(3L), StopwatchAction.Reset, now),
        )
    }

    @Test
    fun unsupportedAndRepeatedActionsAreIgnored() {
        val now = clock()
        val running = StopwatchState.Running(1L, 2L, 3L)
        val paused = StopwatchState.Paused(3L)

        assertSame(
            StopwatchState.Idle,
            reduceStopwatch(StopwatchState.Idle, StopwatchAction.Pause, now),
        )
        assertSame(
            StopwatchState.Idle,
            reduceStopwatch(StopwatchState.Idle, StopwatchAction.Resume, now),
        )
        assertSame(
            StopwatchState.Idle,
            reduceStopwatch(StopwatchState.Idle, StopwatchAction.Reset, now),
        )
        assertSame(running, reduceStopwatch(running, StopwatchAction.Start, now))
        assertSame(running, reduceStopwatch(running, StopwatchAction.Resume, now))
        assertSame(paused, reduceStopwatch(paused, StopwatchAction.Pause, now))
        assertSame(paused, reduceStopwatch(paused, StopwatchAction.Start, now))
    }

    @Test
    fun elapsedTimeDerivesFromClockRatherThanTicks() {
        val running = StopwatchState.Running(
            anchorElapsedRealtimeMs = 10_000L,
            anchorWallClockMs = 50_000L,
            accumulatedMs = 2_500L,
        )

        assertEquals(2_500L, running.elapsedMs(10_000L))
        assertEquals(7_500L, running.elapsedMs(15_000L))
        assertEquals(102_500L, running.elapsedMs(110_000L))
    }

    @Test
    fun elapsedTimeNeverBecomesNegative() {
        val running = StopwatchState.Running(
            anchorElapsedRealtimeMs = 10_000L,
            anchorWallClockMs = 50_000L,
            accumulatedMs = 0L,
        )

        assertEquals(0L, running.elapsedMs(5_000L))
        assertEquals(0L, StopwatchState.Paused(-1L).elapsedMs(0L))
    }

    @Test
    fun elapsedTimeSaturatesInsteadOfOverflowing() {
        val running = StopwatchState.Running(
            anchorElapsedRealtimeMs = 0L,
            anchorWallClockMs = 0L,
            accumulatedMs = Long.MAX_VALUE - 5L,
        )

        assertEquals(Long.MAX_VALUE, running.elapsedMs(10L))
    }

    @Test
    fun formatsTenthsAndTimeBoundaries() {
        assertEquals("00:00.0", formatElapsedTime(0L))
        assertEquals("00:00.0", formatElapsedTime(99L))
        assertEquals("00:00.1", formatElapsedTime(100L))
        assertEquals("00:00.9", formatElapsedTime(999L))
        assertEquals("00:59.9", formatElapsedTime(59_999L))
        assertEquals("01:00.0", formatElapsedTime(60_000L))
        assertEquals("59:59.9", formatElapsedTime(3_599_999L))
        assertEquals("1:00:00.0", formatElapsedTime(3_600_000L))
        assertEquals("9:59:59.9", formatElapsedTime(35_999_999L))
        assertEquals("10:00:00.0", formatElapsedTime(36_000_000L))
        assertEquals("100:00:00.0", formatElapsedTime(360_000_000L))
        assertEquals("00:00.0", formatElapsedTime(-1L))
        assertEquals("2562047788015:12:55.8", formatElapsedTime(Long.MAX_VALUE))
    }

    private fun clock(
        elapsedRealtimeMs: Long = 0L,
        wallClockMs: Long = 0L,
    ) = ClockReading(elapsedRealtimeMs, wallClockMs)
}
