package com.subwayalert.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.subwayalert.service.LocationMonitoringService

/**
 * WakefulBroadcastReceiver ensures the device wakes up when a geofence transition occurs.
 * This is critical for Android 8.0+ where background services are restricted.
 */
class GeofenceWakefulReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL
        ) {
            // Get the station name from geofence request ID
            val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
            val stationName = triggeringGeofences.firstOrNull()?.requestId ?: "地铁站"
            
            // Trigger alert via service - this will wake up the device
            LocationMonitoringService.triggerAlert(context, stationName)
        }
    }
}
