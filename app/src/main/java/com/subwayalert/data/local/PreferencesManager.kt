package com.subwayalert.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.subwayalert.domain.model.Settings
import com.subwayalert.domain.model.MonitoringMode
import com.subwayalert.domain.model.Station
import com.subwayalert.domain.model.VibrateMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "subway_alert_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val STATIONS = stringPreferencesKey("stations")
        val GEOFENCE_RADIUS = floatPreferencesKey("geofence_radius")
        val VIBRATE_MODE = stringPreferencesKey("vibrate_mode")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val MONITORING_MODE = stringPreferencesKey("monitoring_mode")
        val POLLING_INTERVAL = intPreferencesKey("polling_interval")
        val OTA_SERVER_URL = stringPreferencesKey("ota_server_url")
    }

    val stationsFlow: Flow<List<Station>> = context.dataStore.data.map { prefs ->
        val json = prefs[Keys.STATIONS] ?: ""
        Station.fromJson(json)
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            geofenceRadius = prefs[Keys.GEOFENCE_RADIUS] ?: 300f,
            vibrateMode = try {
                VibrateMode.valueOf(prefs[Keys.VIBRATE_MODE] ?: "LONG")
            } catch (e: Exception) {
                VibrateMode.LONG
            },
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false,
            monitoringMode = try {
                MonitoringMode.valueOf(prefs[Keys.MONITORING_MODE] ?: "POLLING")
            } catch (e: Exception) {
                MonitoringMode.POLLING
            },
            pollingIntervalSeconds = prefs[Keys.POLLING_INTERVAL] ?: 30,
            otaServerUrl = prefs[Keys.OTA_SERVER_URL] ?: ""
        )
    }
    
    suspend fun saveStations(stations: List<Station>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STATIONS] = Station.toJson(stations)
        }
    }

    suspend fun saveSettings(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GEOFENCE_RADIUS] = settings.geofenceRadius
            prefs[Keys.VIBRATE_MODE] = settings.vibrateMode.name
            prefs[Keys.SOUND_ENABLED] = settings.soundEnabled
            prefs[Keys.MONITORING_MODE] = settings.monitoringMode.name
            prefs[Keys.POLLING_INTERVAL] = settings.pollingIntervalSeconds
            prefs[Keys.OTA_SERVER_URL] = settings.otaServerUrl
        }
    }

    suspend fun addStation(station: Station) {
        context.dataStore.edit { prefs ->
            val current = Station.fromJson(prefs[Keys.STATIONS] ?: "")
            val updated = current.filter { it.id != station.id } + station
            prefs[Keys.STATIONS] = Station.toJson(updated)
        }
    }

    suspend fun removeStation(stationId: String) {
        context.dataStore.edit { prefs ->
            val current = Station.fromJson(prefs[Keys.STATIONS] ?: "")
            val updated = current.filter { it.id != stationId }
            prefs[Keys.STATIONS] = Station.toJson(updated)
        }
    }

    suspend fun updateStation(station: Station) {
        context.dataStore.edit { prefs ->
            val current = Station.fromJson(prefs[Keys.STATIONS] ?: "")
            val updated = current.filter { it.id != station.id } + station
            prefs[Keys.STATIONS] = Station.toJson(updated)
        }
    }
}
