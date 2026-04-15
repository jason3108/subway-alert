package com.subwayalert.presentation.viewmodel

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.subwayalert.data.local.PreferencesManager
import com.subwayalert.data.repository.GeocodingRepository
import com.subwayalert.data.repository.LocationTrackRepository
import com.subwayalert.data.repository.StationRepository
import com.subwayalert.domain.model.MonitoringMode
import com.subwayalert.domain.model.Settings
import com.subwayalert.domain.model.Station
import com.subwayalert.domain.model.VibrateMode
import com.subwayalert.service.LocationMonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MainUiState(
    val stations: List<Station> = emptyList(),
    val settings: Settings = Settings(),
    val isMonitoring: Boolean = false,
    val inputText: String = "",
    val manualLat: String = "",
    val manualLng: String = "",
    val isGeocoding: Boolean = false,
    val geocodingError: String? = null,
    val currentLocation: Location? = null,
    val stationDistances: Map<String, Float> = emptyMap(),
    val showAddDialog: Boolean = false,
    val permissionsGranted: Boolean = false,
    val alertedStationId: String? = null  // ID of station that triggered the alert
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stationRepository: StationRepository,
    private val geocodingRepository: GeocodingRepository,
    private val preferencesManager: PreferencesManager,
    private val locationTrackRepository: LocationTrackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    init {
        loadData()
        registerAlertReceiver()
    }

    // BroadcastReceiver for station alerts from LocationMonitoringService
    private val stationAlertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val alertStationName = it.getStringExtra(LocationMonitoringService.EXTRA_ALERT_STATION_NAME)
                if (alertStationName != null) {
                    // Find station by name and update UI state
                    val station = _uiState.value.stations.find { s -> s.name == alertStationName }
                    station?.let { s ->
                        _uiState.update { state -> state.copy(alertedStationId = s.id) }
                    }
                }
            }
        }
    }

    private fun registerAlertReceiver() {
        val filter = IntentFilter(LocationMonitoringService.ACTION_STATION_ALERT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(stationAlertReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(stationAlertReceiver, filter)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(stationAlertReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }

    // Periodic location update job
    private var locationUpdateJob: Job? = null
    
    // Track stations that have already been alerted (to avoid repeated alerts)
    private val alertedStations = mutableSetOf<String>()
    
    // Last location for track recording
    private var lastTrackLocation: Location? = null
    private val TRACK_DISTANCE_THRESHOLD = 500f // meters

    private fun startPeriodicLocationUpdates() {
        locationUpdateJob?.cancel()
        val intervalMs = _uiState.value.settings.pollingIntervalSeconds * 1000L
        locationUpdateJob = viewModelScope.launch {
            while (true) {
                delay(intervalMs)
                if (_uiState.value.isMonitoring) {
                    updateCurrentLocation()
                }
            }
        }
    }

    private fun stopPeriodicLocationUpdates() {
        locationUpdateJob?.cancel()
        locationUpdateJob = null
        alertedStations.clear()
    }

    private fun loadData() {
        viewModelScope.launch {
            stationRepository.stationsFlow.collect { stations ->
                _uiState.update { it.copy(stations = stations) }
            }
        }
        viewModelScope.launch {
            stationRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            preferencesManager.isMonitoringFlow.collect { isMonitoring ->
                _uiState.update { it.copy(isMonitoring = isMonitoring) }
                // Restore monitoring service if needed
                if (isMonitoring) {
                    startMonitoringService()
                }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text, geocodingError = null) }
    }

    fun onShowAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, inputText = "", geocodingError = null) }
    }

    fun onDismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, inputText = "", geocodingError = null, isGeocoding = false) }
    }
    
    fun onResetGeocodingState() {
        _uiState.update { it.copy(isGeocoding = false, geocodingError = null) }
    }

    fun onAddStation() {
        val stationName = _uiState.value.inputText.trim()
        if (stationName.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGeocoding = true, geocodingError = null) }

            val result = geocodingRepository.geocodeStationName(stationName)

            if (result != null) {
                val station = Station(
                    id = UUID.randomUUID().toString(),
                    name = result.displayName.ifEmpty { stationName },
                    latitude = result.latitude,
                    longitude = result.longitude,
                    radius = _uiState.value.settings.geofenceRadius,
                    isMonitoring = true
                )
                addStationAndStartMonitoring(station)
            } else {
                _uiState.update { 
                    it.copy(
                        isGeocoding = false, 
                        geocodingError = "未找到: $stationName"
                    ) 
                }
            }
        }
    }

    fun onAddStationWithCoords(name: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            val station = Station(
                id = UUID.randomUUID().toString(),
                name = name.ifEmpty { "($lat, $lng)" },
                latitude = lat,
                longitude = lng,
                radius = _uiState.value.settings.geofenceRadius,
                isMonitoring = true
            )
            stationRepository.addStation(station)
            
            _uiState.update { 
                it.copy(
                    inputText = "",
                    showAddDialog = false,
                    isMonitoring = true
                ) 
            }
            
            startMonitoringService()
        }
    }

    private fun addStationAndStartMonitoring(station: Station) {
        viewModelScope.launch {
            stationRepository.addStation(station)
            
            _uiState.update { 
                it.copy(
                    isMonitoring = true,
                    inputText = "", 
                    showAddDialog = false,
                    alertedStationId = null // Clear alert state when adding new station
                ) 
            }
            
            startMonitoringService()
        }
    }

    fun onRemoveStation(station: Station) {
        viewModelScope.launch {
            stationRepository.removeStation(station.id)
            
            val remaining = _uiState.value.stations.filter { it.id != station.id }
            val wasMonitoring = _uiState.value.isMonitoring
            val anyMonitoring = remaining.any { s -> s.isMonitoring }
            
            _uiState.update { 
                it.copy(
                    stations = remaining,
                    isMonitoring = anyMonitoring,
                    alertedStationId = if (station.id == it.alertedStationId) null else it.alertedStationId
                )
            }
            
            if (remaining.isEmpty()) {
                stopMonitoringService()
                preferencesManager.setMonitoring(false)
            } else if (wasMonitoring && !anyMonitoring) {
                preferencesManager.setMonitoring(false)
            }
        }
    }

    fun onTestAlert() {
        LocationMonitoringService.testAlert(context, _uiState.value.settings.vibrateMode)
    }

    fun onToggleMonitoring() {
        viewModelScope.launch {
            if (_uiState.value.isMonitoring) {
                stopMonitoringService()
                _uiState.update { it.copy(isMonitoring = false, alertedStationId = null) }
                preferencesManager.setMonitoring(false)
                // Stop tracking
                if (_uiState.value.settings.trackLocationTrack) {
                    locationTrackRepository.stopTracking()
                }
            } else {
                startMonitoringService()
                _uiState.update { it.copy(isMonitoring = true, alertedStationId = null) }
                preferencesManager.setMonitoring(true)
                // Start tracking
                if (_uiState.value.settings.trackLocationTrack) {
                    locationTrackRepository.startTracking()
                    lastTrackLocation = null // Reset last location
                }
            }
        }
    }

    private fun startMonitoringService() {
        val monitoringStations = _uiState.value.stations.filter { it.isMonitoring }
        val settings = _uiState.value.settings
        
        // Build station coordinates map
        val stationCoords = monitoringStations.associate { it.name to (it.latitude to it.longitude) }
        
        // Start the notification service with station coordinates
        LocationMonitoringService.startService(
            context, 
            settings.vibrateMode,
            monitoringStations.map { it.name },
            stationCoords,
            settings.geofenceRadius,
            settings.pollingIntervalSeconds
        )
        
        // Start polling-based location monitoring (only way, removed geofence)
        startPeriodicLocationUpdates()
        
        // Immediately get current location and calculate distances
        updateCurrentLocation()
        
        // Start location tracking if enabled
        if (settings.trackLocationTrack) {
            viewModelScope.launch {
                locationTrackRepository.startTracking()
            }
            lastTrackLocation = null
        }
    }

    private fun stopMonitoringService() {
        LocationMonitoringService.stopService(context)
        stopPeriodicLocationUpdates()
        
        // Stop location tracking
        if (_uiState.value.settings.trackLocationTrack) {
            viewModelScope.launch {
                locationTrackRepository.stopTracking()
            }
            lastTrackLocation = null
        }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            stationRepository.saveSettings(settings)
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(permissionsGranted = granted) }
    }

    fun checkPermissions(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val granted = fineLocation && backgroundLocation && notification
        _uiState.update { it.copy(permissionsGranted = granted) }
        return granted
    }

    private fun updateCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        viewModelScope.launch {
            try {
                val location = getLastKnownLocation()
                
                location?.let { loc ->
                    processLocationUpdate(loc)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Process location update - shared by both foreground and background modes
     */
    private fun processLocationUpdate(loc: Location) {
        val alertedId = _uiState.value.alertedStationId
        val settings = _uiState.value.settings
        
        _uiState.update { state ->
            val distances = state.stations.associate { station ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    loc.latitude, loc.longitude,
                    station.latitude, station.longitude,
                    results
                )
                station.id to results[0]
            }
            
            // If alerted station is now out of range, clear the alerted state
            val newAlertedId = if (alertedId != null) {
                val distance = distances[alertedId]
                if (distance == null || distance > state.settings.geofenceRadius) {
                    null // User left the station, clear alert
                } else {
                    alertedId // Still within range
                }
            } else null
            
            state.copy(currentLocation = loc, stationDistances = distances, alertedStationId = newAlertedId)
        }
        
        // Check for new stations entering range and trigger alert
        if (!_uiState.value.isMonitoring) return
        
        for (station in _uiState.value.stations) {
            val distance = _uiState.value.stationDistances[station.id] ?: continue
            if (distance <= settings.geofenceRadius) {
                // Within range - check if not already alerted
                if (alertedId == null || alertedId != station.id) {
                    // New station entering range - trigger full-screen alert
                    LocationMonitoringService.triggerAlert(
                        context,
                        station.name,
                        distance.toInt(),
                        settings.vibrateMode
                    )
                    _uiState.update { it.copy(alertedStationId = station.id) }
                    break // Only alert for one station at a time
                }
            }
        }
        
        // Record location track if enabled
        if (_uiState.value.settings.trackLocationTrack) {
            viewModelScope.launch {
                recordLocationTrack(loc)
            }
        }
    }
    
    private suspend fun recordLocationTrack(loc: Location) {
        val lastLoc = lastTrackLocation
        if (lastLoc != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                lastLoc.latitude, lastLoc.longitude,
                loc.latitude, loc.longitude,
                results
            )
            val distance = results[0]
            if (distance >= TRACK_DISTANCE_THRESHOLD) {
                // Moved more than 500m, record this point
                locationTrackRepository.addPoint(loc.latitude, loc.longitude, loc.accuracy)
                lastTrackLocation = loc
            }
        } else {
            // First point, just record it
            locationTrackRepository.addPoint(loc.latitude, loc.longitude, loc.accuracy)
            lastTrackLocation = loc
        }
    }

    @Suppress("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        // Try Android native LocationManager first (more reliable in China)
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            
            // Network provider first (WiFi/cell towers work better in subway)
            // GPS requires clear sky view which doesn't work underground
            val providers = listOf(
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER,
                android.location.LocationManager.GPS_PROVIDER
            )
            
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null) {
                        return location
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return null
    }

    fun refreshLocation() {
        updateCurrentLocation()
    }

    fun testGeofenceAlert() {
        val state = _uiState.value
        if (state.stations.isEmpty()) return
        
        // Sort stations by distance (nearest first, within-range first)
        val sortedStations = state.stations.sortedBy { station ->
            val distance = state.stationDistances[station.id] ?: Float.MAX_VALUE
            val isWithinRange = distance <= state.settings.geofenceRadius
            if (isWithinRange) -distance else distance
        }
        
        val nearestStation = sortedStations.firstOrNull() ?: return
        val distance = state.stationDistances[nearestStation.id]?.toInt() ?: 0
        LocationMonitoringService.triggerAlert(context, nearestStation.name, distance, state.settings.vibrateMode)
    }
}
