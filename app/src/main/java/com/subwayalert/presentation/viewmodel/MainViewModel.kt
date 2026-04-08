package com.subwayalert.presentation.viewmodel

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.subwayalert.data.local.PreferencesManager
import com.subwayalert.data.repository.GeocodingRepository
import com.subwayalert.data.repository.LocationTrackRepository
import com.subwayalert.data.repository.StationRepository
import com.subwayalert.domain.model.MonitoringMode
import com.subwayalert.domain.model.Settings
import com.subwayalert.domain.model.Station
import com.subwayalert.domain.model.VibrateMode
import com.subwayalert.receiver.GeofenceBroadcastReceiver
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
    val permissionsGranted: Boolean = false
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
    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = LocationMonitoringService.ACTION_GEOFENCE_EVENT
        }
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    init {
        loadData()
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
                    showAddDialog = false
                ) 
            }
            
            startMonitoringService()
        }
    }

    fun onRemoveStation(station: Station) {
        viewModelScope.launch {
            removeGeofence(station.id)
            stationRepository.removeStation(station.id)
            
            val remaining = _uiState.value.stations.filter { it.id != station.id }
            val wasMonitoring = _uiState.value.isMonitoring
            val anyMonitoring = remaining.any { s -> s.isMonitoring }
            
            _uiState.update { 
                it.copy(
                    stations = remaining,
                    isMonitoring = anyMonitoring
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
                _uiState.update { it.copy(isMonitoring = false) }
                preferencesManager.setMonitoring(false)
                // Stop tracking
                if (_uiState.value.settings.trackLocationTrack) {
                    locationTrackRepository.stopTracking()
                }
            } else {
                startMonitoringService()
                _uiState.update { it.copy(isMonitoring = true) }
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
        
        // Start the notification service
        LocationMonitoringService.startService(
            context, 
            settings.vibrateMode,
            monitoringStations.map { it.name }
        )
        
        // Setup based on monitoring mode
        when (settings.monitoringMode) {
            MonitoringMode.GEOFENCE -> {
                // Add geofences for Android's built-in geofencing
                monitoringStations.forEach { station ->
                    addGeofence(station)
                }
                // Still start polling as backup for distance display
                startPeriodicLocationUpdates()
            }
            MonitoringMode.POLLING -> {
                // Remove any existing geofences
                geofencingClient.removeGeofences(geofencePendingIntent)
                // Start polling-based distance monitoring
                startPeriodicLocationUpdates()
            }
        }
        
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
        geofencingClient.removeGeofences(geofencePendingIntent)
        stopPeriodicLocationUpdates()
        
        // Stop location tracking
        if (_uiState.value.settings.trackLocationTrack) {
            viewModelScope.launch {
                locationTrackRepository.stopTracking()
            }
            lastTrackLocation = null
        }
    }

    private fun addGeofence(station: Station) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(station.id)
            .setCircularRegion(
                station.latitude,
                station.longitude,
                station.radius
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(30)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
    }

    private fun removeGeofence(stationId: String) {
        geofencingClient.removeGeofences(listOf(stationId))
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
                        state.copy(currentLocation = loc, stationDistances = distances)
                    }
                    
                    // Record location track if enabled and moved > 500m
                    if (_uiState.value.settings.trackLocationTrack && _uiState.value.isMonitoring) {
                        recordLocationTrack(loc)
                    }
                    
                    // Check if any station is within range and trigger alert
                    // Use current settings radius, not each station's stored radius
                    val currentStations = _uiState.value.stations
                    val currentDistances = _uiState.value.stationDistances
                    val currentRadius = _uiState.value.settings.geofenceRadius
                    
                    for (station in currentStations) {
                        val distance = currentDistances[station.id]
                        if (distance != null && distance <= currentRadius) {
                            // Only alert if not already alerted for this station
                            if (!alertedStations.contains(station.id)) {
                                alertedStations.add(station.id)
                                LocationMonitoringService.triggerAlert(context, station.name, _uiState.value.settings.vibrateMode)
                            }
                        } else {
                            // Reset alerted state if moved out of range
                            alertedStations.remove(station.id)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        val stations = _uiState.value.stations
        if (stations.isNotEmpty()) {
            LocationMonitoringService.triggerAlert(context, stations.first().name, _uiState.value.settings.vibrateMode)
        }
    }
}
