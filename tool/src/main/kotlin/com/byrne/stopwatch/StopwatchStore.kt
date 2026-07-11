package com.byrne.stopwatch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

private object StopwatchPreferenceKeys {
    val Schema = intPreferencesKey("stopwatch.schema")
    val State = stringPreferencesKey("stopwatch.state")
    val AccumulatedMs = longPreferencesKey("stopwatch.accumulatedMs")
    val AnchorElapsedRealtimeMs = longPreferencesKey("stopwatch.anchorEr")
    val AnchorWallClockMs = longPreferencesKey("stopwatch.anchorWall")
}

class StopwatchStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun load(): StopwatchSnapshot? {
        val preferences = dataStore.data.first()
        return StopwatchSnapshot(
            schema = preferences[StopwatchPreferenceKeys.Schema] ?: return null,
            state = preferences[StopwatchPreferenceKeys.State] ?: return null,
            accumulatedMs = preferences[StopwatchPreferenceKeys.AccumulatedMs] ?: return null,
            anchorElapsedRealtimeMs =
                preferences[StopwatchPreferenceKeys.AnchorElapsedRealtimeMs] ?: return null,
            anchorWallClockMs = preferences[StopwatchPreferenceKeys.AnchorWallClockMs] ?: return null,
        )
    }

    suspend fun save(state: StopwatchState) {
        val snapshot = state.toSnapshot()
        dataStore.edit { preferences ->
            preferences[StopwatchPreferenceKeys.Schema] = snapshot.schema
            preferences[StopwatchPreferenceKeys.State] = snapshot.state
            preferences[StopwatchPreferenceKeys.AccumulatedMs] = snapshot.accumulatedMs
            preferences[StopwatchPreferenceKeys.AnchorElapsedRealtimeMs] =
                snapshot.anchorElapsedRealtimeMs
            preferences[StopwatchPreferenceKeys.AnchorWallClockMs] = snapshot.anchorWallClockMs
        }
    }
}
