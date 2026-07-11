package com.byrne.stopwatch

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

data class StopwatchUiState(
    val isReady: Boolean = false,
    val stopwatchState: StopwatchState = StopwatchState.Idle,
    val elapsedText: String = "00:00",
)

class StopwatchViewModel(
    dataStore: DataStore<Preferences>,
    private val timeSource: StopwatchTimeSource = SystemStopwatchTimeSource,
) : LightViewModel<Unit>() {
    private val store = StopwatchStore(dataStore)
    private val actionMutex = Mutex()
    private val _uiState = MutableStateFlow(StopwatchUiState())
    val uiState: StateFlow<StopwatchUiState> = _uiState.asStateFlow()

    private var state: StopwatchState? = null
    private var tickerJob: Job? = null
    private var isScreenVisible = false

    init {
        viewModelScope.launch {
            actionMutex.withLock {
                val snapshot = runCatching { store.load() }.getOrNull()
                val restoration = restoreStopwatch(snapshot, timeSource.read())
                state = restoration.state
                if (restoration.shouldPersist) {
                    runCatching { store.save(restoration.state) }
                }
                refreshUi()
                restartTicker()
            }
        }
    }

    fun start() = dispatch(StopwatchAction.Start)

    fun pause() = dispatch(StopwatchAction.Pause)

    fun resume() = dispatch(StopwatchAction.Resume)

    fun reset() = dispatch(StopwatchAction.Reset)

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
                runCatching { store.save(next) }
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
                val delayMs = 1_000L - (elapsedMs % 1_000L)
                delay(delayMs)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
