package com.byrne.stopwatch

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "StopwatchViewModel"
private const val DISPLAY_INTERVAL_MS = 100L

internal data class StopwatchUiState(
    val isReady: Boolean = false,
    val stopwatchState: StopwatchState = StopwatchState.Idle,
    val elapsedText: String = "00:00.0",
)

class StopwatchViewModel internal constructor(
    dataStore: DataStore<Preferences>,
    private val timeSource: StopwatchTimeSource = SystemStopwatchTimeSource,
) : LightViewModel<Unit>() {
    private val store = StopwatchStore(dataStore)
    private val actionMutex = Mutex()
    private val _uiState = MutableStateFlow(StopwatchUiState())
    internal val uiState: StateFlow<StopwatchUiState> = _uiState.asStateFlow()

    private var state: StopwatchState? = null
    private var tickerJob: Job? = null
    private var isScreenVisible = false

    init {
        viewModelScope.launch {
            actionMutex.withLock {
                val snapshot = storageOrNull("load") { store.load() }
                val restoration = restoreStopwatch(snapshot, timeSource.read())
                state = restoration.state
                if (restoration.shouldPersist) {
                    storageOrNull("save recovered") { store.save(restoration.state) }
                }
                refreshUi()
                restartTicker()
            }
        }
    }

    internal fun start() = dispatch(StopwatchAction.Start)

    internal fun pause() = dispatch(StopwatchAction.Pause)

    internal fun resume() = dispatch(StopwatchAction.Resume)

    internal fun reset() = dispatch(StopwatchAction.Reset)

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        isScreenVisible = true
        refreshUi()
        restartTicker()
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        isScreenVisible = false
        stopTicker()
    }

    override fun onAppPause() {
        super.onAppPause()
        isScreenVisible = false
        stopTicker()
    }

    private fun dispatch(action: StopwatchAction) {
        viewModelScope.launch {
            actionMutex.withLock {
                val current = state ?: return@withLock
                val next = reduceStopwatch(current, action, timeSource.read())
                if (next == current) return@withLock

                state = next
                refreshUi()
                restartTicker()
                storageOrNull("save") { store.save(next) }
            }
        }
    }

    private fun refreshUi() {
        val current = state ?: return
        val elapsedMs = current.elapsedMs(timeSource.read().elapsedRealtimeMs)
        _uiState.value = StopwatchUiState(
            isReady = true,
            stopwatchState = current,
            elapsedText = formatElapsedTime(elapsedMs),
        )
    }

    private fun restartTicker() {
        stopTicker()
        if (!isScreenVisible || state !is StopwatchState.Running) return

        tickerJob = viewModelScope.launch {
            while (isActive && isScreenVisible && state is StopwatchState.Running) {
                refreshUi()
                val current = state ?: break
                val elapsedMs = current.elapsedMs(timeSource.read().elapsedRealtimeMs)
                val delayMs = DISPLAY_INTERVAL_MS - (elapsedMs % DISPLAY_INTERVAL_MS)
                delay(delayMs)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun <T> storageOrNull(
        operation: String,
        block: suspend () -> T,
    ): T? = try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Log.e(TAG, "Could not $operation stopwatch state", exception)
        null
    }
}
