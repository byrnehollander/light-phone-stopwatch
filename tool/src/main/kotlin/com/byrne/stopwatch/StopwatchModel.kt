package com.byrne.stopwatch

import android.os.SystemClock

private const val BOOT_EPOCH_TOLERANCE_MS = 60_000L
private const val DISPLAY_INTERVAL_MS = 100L

internal sealed interface StopwatchState {
    data object Idle : StopwatchState

    data class Running(
        val anchorElapsedRealtimeMs: Long,
        val anchorWallClockMs: Long,
        val accumulatedMs: Long,
    ) : StopwatchState

    data class Paused(val accumulatedMs: Long) : StopwatchState
}

internal enum class StopwatchAction {
    Start,
    Pause,
    Resume,
    Reset,
}

internal data class ClockReading(
    val elapsedRealtimeMs: Long,
    val wallClockMs: Long,
)

internal interface StopwatchTimeSource {
    fun read(): ClockReading
}

internal object SystemStopwatchTimeSource : StopwatchTimeSource {
    override fun read() = ClockReading(
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        wallClockMs = System.currentTimeMillis(),
    )
}

internal data class StopwatchSnapshot(
    val schema: Int,
    val state: String,
    val accumulatedMs: Long,
    val anchorElapsedRealtimeMs: Long,
    val anchorWallClockMs: Long,
)

internal data class StopwatchRestoration(
    val state: StopwatchState,
    val shouldPersist: Boolean = false,
)

internal fun reduceStopwatch(
    state: StopwatchState,
    action: StopwatchAction,
    now: ClockReading,
): StopwatchState = when (state) {
    StopwatchState.Idle -> when (action) {
        StopwatchAction.Start -> StopwatchState.Running(
            anchorElapsedRealtimeMs = now.elapsedRealtimeMs,
            anchorWallClockMs = now.wallClockMs,
            accumulatedMs = 0L,
        )
        else -> state
    }

    is StopwatchState.Running -> when (action) {
        StopwatchAction.Pause -> StopwatchState.Paused(state.elapsedMs(now.elapsedRealtimeMs))
        else -> state
    }

    is StopwatchState.Paused -> when (action) {
        StopwatchAction.Resume -> StopwatchState.Running(
            anchorElapsedRealtimeMs = now.elapsedRealtimeMs,
            anchorWallClockMs = now.wallClockMs,
            accumulatedMs = state.accumulatedMs,
        )
        StopwatchAction.Reset -> StopwatchState.Idle
        else -> state
    }
}

internal fun StopwatchState.elapsedMs(nowElapsedRealtimeMs: Long): Long = when (this) {
    StopwatchState.Idle -> 0L
    is StopwatchState.Paused -> accumulatedMs.coerceAtLeast(0L)
    is StopwatchState.Running -> {
        val delta = (nowElapsedRealtimeMs - anchorElapsedRealtimeMs).coerceAtLeast(0L)
        saturatingAdd(accumulatedMs.coerceAtLeast(0L), delta)
    }
}

internal fun formatElapsedTime(elapsedMs: Long): String {
    val nonNegativeElapsedMs = elapsedMs.coerceAtLeast(0L)
    val totalSeconds = nonNegativeElapsedMs / 1_000L
    val seconds = totalSeconds % 60L
    val tenths = (nonNegativeElapsedMs / DISPLAY_INTERVAL_MS) % 10L
    val totalMinutes = totalSeconds / 60L
    val minutes = totalMinutes % 60L
    val hours = totalMinutes / 60L

    return if (hours == 0L) {
        "${totalMinutes.twoDigits()}:${seconds.twoDigits()}.$tenths"
    } else {
        "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}.$tenths"
    }
}

internal fun delayUntilNextDisplayUpdate(elapsedMs: Long): Long {
    val elapsedWithinInterval = elapsedMs.coerceAtLeast(0L) % DISPLAY_INTERVAL_MS
    return DISPLAY_INTERVAL_MS - elapsedWithinInterval
}

internal fun StopwatchState.toSnapshot(): StopwatchSnapshot = when (this) {
    StopwatchState.Idle -> StopwatchSnapshot(
        schema = STOPWATCH_SCHEMA,
        state = STOPWATCH_STATE_IDLE,
        accumulatedMs = 0L,
        anchorElapsedRealtimeMs = 0L,
        anchorWallClockMs = 0L,
    )
    is StopwatchState.Paused -> StopwatchSnapshot(
        schema = STOPWATCH_SCHEMA,
        state = STOPWATCH_STATE_PAUSED,
        accumulatedMs = accumulatedMs,
        anchorElapsedRealtimeMs = 0L,
        anchorWallClockMs = 0L,
    )
    is StopwatchState.Running -> StopwatchSnapshot(
        schema = STOPWATCH_SCHEMA,
        state = STOPWATCH_STATE_RUNNING,
        accumulatedMs = accumulatedMs,
        anchorElapsedRealtimeMs = anchorElapsedRealtimeMs,
        anchorWallClockMs = anchorWallClockMs,
    )
}

internal fun restoreStopwatch(
    snapshot: StopwatchSnapshot?,
    now: ClockReading,
): StopwatchRestoration {
    if (snapshot == null || snapshot.schema != STOPWATCH_SCHEMA || snapshot.accumulatedMs < 0L) {
        return StopwatchRestoration(StopwatchState.Idle)
    }

    return when (snapshot.state) {
        STOPWATCH_STATE_IDLE -> StopwatchRestoration(StopwatchState.Idle)
        STOPWATCH_STATE_PAUSED -> StopwatchRestoration(
            StopwatchState.Paused(snapshot.accumulatedMs),
        )
        STOPWATCH_STATE_RUNNING -> restoreRunningStopwatch(snapshot, now)
        else -> StopwatchRestoration(StopwatchState.Idle)
    }
}

private fun restoreRunningStopwatch(
    snapshot: StopwatchSnapshot,
    now: ClockReading,
): StopwatchRestoration {
    if (
        snapshot.anchorElapsedRealtimeMs < 0L ||
        snapshot.anchorWallClockMs < 0L ||
        now.elapsedRealtimeMs < 0L ||
        now.wallClockMs < 0L
    ) {
        return StopwatchRestoration(StopwatchState.Idle)
    }

    val oldBootEpoch = snapshot.anchorWallClockMs - snapshot.anchorElapsedRealtimeMs
    val currentBootEpoch = now.wallClockMs - now.elapsedRealtimeMs
    val oldWallClockCanRepresentBoot = snapshot.anchorWallClockMs >=
        snapshot.anchorElapsedRealtimeMs
    val currentWallClockCanRepresentBoot = now.wallClockMs >= now.elapsedRealtimeMs
    val wallClockValidityChanged = oldWallClockCanRepresentBoot !=
        currentWallClockCanRepresentBoot
    val sameBoot = now.elapsedRealtimeMs >= snapshot.anchorElapsedRealtimeMs &&
        (wallClockValidityChanged ||
            valuesAreWithin(oldBootEpoch, currentBootEpoch, BOOT_EPOCH_TOLERANCE_MS))

    if (sameBoot) {
        return StopwatchRestoration(
            StopwatchState.Running(
                anchorElapsedRealtimeMs = snapshot.anchorElapsedRealtimeMs,
                anchorWallClockMs = snapshot.anchorWallClockMs,
                accumulatedMs = snapshot.accumulatedMs,
            ),
        )
    }

    if (!oldWallClockCanRepresentBoot || !currentWallClockCanRepresentBoot) {
        return StopwatchRestoration(
            state = StopwatchState.Paused(snapshot.accumulatedMs),
            shouldPersist = true,
        )
    }

    val wallDelta = now.wallClockMs - snapshot.anchorWallClockMs
    if (wallDelta < 0L) {
        return StopwatchRestoration(
            state = StopwatchState.Paused(snapshot.accumulatedMs),
            shouldPersist = true,
        )
    }

    return StopwatchRestoration(
        state = StopwatchState.Running(
            anchorElapsedRealtimeMs = now.elapsedRealtimeMs,
            anchorWallClockMs = now.wallClockMs,
            accumulatedMs = saturatingAdd(snapshot.accumulatedMs, wallDelta),
        ),
        shouldPersist = true,
    )
}

internal const val STOPWATCH_SCHEMA = 1
internal const val STOPWATCH_STATE_IDLE = "idle"
internal const val STOPWATCH_STATE_RUNNING = "running"
internal const val STOPWATCH_STATE_PAUSED = "paused"

private fun Long.twoDigits(): String = toString().padStart(2, '0')

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right

private fun valuesAreWithin(left: Long, right: Long, tolerance: Long): Boolean {
    return if (left >= right) {
        left - right <= tolerance
    } else {
        right - left <= tolerance
    }
}
