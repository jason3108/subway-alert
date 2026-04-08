package com.subwayalert.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.subwayalert.domain.model.LocationPoint
import com.subwayalert.domain.model.LocationTrack
import com.subwayalert.domain.model.LocationTrackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trackDataStore: DataStore<Preferences> by preferencesDataStore(name = "location_tracks")

@Singleton
class LocationTrackRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationTrackManager {
    
    private val gson = Gson()
    
    private object Keys {
        val TRACKS = stringPreferencesKey("tracks")
        val CURRENT_TRACK_ID = stringPreferencesKey("current_track_id")
    }
    
    private var currentTrackId: String? = null
    private var trackingPoints = mutableListOf<LocationPoint>()
    
    override suspend fun startTracking() {
        currentTrackId = UUID.randomUUID().toString()
        trackingPoints.clear()
        
        // Initialize track in storage
        context.trackDataStore.edit { prefs ->
            val tracks = getTracksFromPrefs(prefs)
            val newTrack = LocationTrack(
                id = currentTrackId!!,
                startTime = System.currentTimeMillis()
            )
            tracks.add(newTrack)
            prefs[Keys.TRACKS] = gson.toJson(tracks)
            prefs[Keys.CURRENT_TRACK_ID] = currentTrackId!!
        }
    }
    
    override suspend fun stopTracking() {
        currentTrackId?.let { id ->
            context.trackDataStore.edit { prefs ->
                val tracks = getTracksFromPrefs(prefs)
                val index = tracks.indexOfFirst { it.id == id }
                if (index >= 0) {
                    tracks[index] = tracks[index].copy(
                        endTime = System.currentTimeMillis(),
                        points = trackingPoints.toList()
                    )
                    prefs[Keys.TRACKS] = gson.toJson(tracks)
                }
            }
        }
        currentTrackId = null
        trackingPoints.clear()
    }
    
    override suspend fun addPoint(latitude: Double, longitude: Double, accuracy: Float?) {
        val point = LocationPoint(latitude, longitude, System.currentTimeMillis(), accuracy)
        trackingPoints.add(point)
        
        // Also persist to storage periodically (every 10 points)
        if (trackingPoints.size % 10 == 0) {
            currentTrackId?.let { id ->
                context.trackDataStore.edit { prefs ->
                    val tracks = getTracksFromPrefs(prefs)
                    val index = tracks.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        tracks[index] = tracks[index].copy(points = trackingPoints.toList())
                        prefs[Keys.TRACKS] = gson.toJson(tracks)
                    }
                }
            }
        }
    }
    
    override suspend fun getCurrentTrack(): LocationTrack? {
        if (currentTrackId == null) return null
        return LocationTrack(
            id = currentTrackId!!,
            startTime = System.currentTimeMillis(), // Approximate
            points = trackingPoints.toList()
        )
    }
    
    override suspend fun getAllTracks(): List<LocationTrack> {
        val prefs = context.trackDataStore.data.first()
        return getTracksFromPrefs(prefs).filter { it.points.isNotEmpty() }
    }
    
    override suspend fun clearAllTracks() {
        context.trackDataStore.edit { prefs ->
            prefs[Keys.TRACKS] = "[]"
        }
        trackingPoints.clear()
        currentTrackId = null
    }
    
    override suspend fun deleteTrack(trackId: String) {
        context.trackDataStore.edit { prefs ->
            val tracks = getTracksFromPrefs(prefs)
            tracks.removeAll { it.id == trackId }
            prefs[Keys.TRACKS] = gson.toJson(tracks)
        }
    }
    
    override fun isTracking(): Boolean = currentTrackId != null
    
    private fun getTracksFromPrefs(prefs: Preferences): MutableList<LocationTrack> {
        val json = prefs[Keys.TRACKS] ?: "[]"
        val type = object : TypeToken<MutableList<LocationTrack>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }
}
