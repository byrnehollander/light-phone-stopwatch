package com.byrne.stopwatch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

private object StopwatchPreferenceKeys {
    val schema = intPreferencesKey("stopwatch.schema")
    val state = stringPreferencesKey("stopwatch.state")
    val accumulatedMs = longPreferencesKey("stopwatch.accumulatedMs")
    val anchorElapsedRealtimeMs = longPreferencesKey("stopwatch.anchorEr")
    val anchorWallClockMs = longPreferencesKey("stopwatch.anchorWall")
}

internal class StopwatchStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun load(): StopwatchSnapshot? {
        val preferences = dataStore.data.first()
        return StopwatchSnapshot(
            schema = preferences[StopwatchPreferenceKeys.schema] ?: return null,
            state = preferences[StopwatchPreferenceKeys.state] ?: return null,
            accumulatedMs = preferences[StopwatchPreferenceKeys.accumulatedMs] ?: return null,
            anchorElapsedRealtimeMs =
                preferences[StopwatchPreferenceKeys.anchorElapsedRealtimeMs] ?: return null,
            anchorWallClockMs = preferences[StopwatchPreferenceKeys.anchorWallClockMs] ?: return null,
        )
    }

    suspend fun save(state: StopwatchState) {
        val snapshot = state.toSnapshot()
        dataStore.edit { preferences ->
            preferences[StopwatchPreferenceKeys.schema] = snapshot.schema
            preferences[StopwatchPreferenceKeys.state] = snapshot.state
            preferences[StopwatchPreferenceKeys.accumulatedMs] = snapshot.accumulatedMs
            preferences[StopwatchPreferenceKeys.anchorElapsedRealtimeMs] =
                snapshot.anchorElapsedRealtimeMs
            preferences[StopwatchPreferenceKeys.anchorWallClockMs] = snapshot.anchorWallClockMs
        }
    }
}
