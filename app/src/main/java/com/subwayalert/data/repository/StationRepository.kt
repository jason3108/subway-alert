package com.subwayalert.data.repository

import com.subwayalert.data.local.PreferencesManager
import com.subwayalert.domain.model.Settings
import com.subwayalert.domain.model.Station
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepository @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    val stationsFlow: Flow<List<Station>> = preferencesManager.stationsFlow
    val settingsFlow: Flow<Settings> = preferencesManager.settingsFlow

    suspend fun addStation(station: Station) {
        preferencesManager.addStation(station)
    }

    suspend fun removeStation(stationId: String) {
        preferencesManager.removeStation(stationId)
    }

    suspend fun updateStation(station: Station) {
        preferencesManager.updateStation(station)
    }

    suspend fun saveSettings(settings: Settings) {
        preferencesManager.saveSettings(settings)
    }
}
