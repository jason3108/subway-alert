package com.subwayalert.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.subwayalert.R
import com.subwayalert.domain.model.VibrateMode
import com.subwayalert.presentation.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocationMonitoringService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, com.subwayalert.receiver.GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
        }
        PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private var currentVibrateMode: VibrateMode = VibrateMode.LONG
    private var monitoredStations: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)
        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentVibrateMode = intent.getStringExtra(EXTRA_VIBRATE_MODE)?.let {
                    try { VibrateMode.valueOf(it) } catch (e: Exception) { VibrateMode.LONG }
                } ?: VibrateMode.LONG
                monitoredStations = intent.getStringArrayListExtra(EXTRA_STATIONS) ?: emptyList()
                startForeground(NOTIFICATION_ID, createNotification())
                startLocationUpdates()
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                releaseWakeLock()
                stopSelf()
            }
            ACTION_TEST_ALERT -> {
                // Get vibrate mode from extra
                val mode = intent.getStringExtra(EXTRA_VIBRATE_MODE)?.let {
                    try { VibrateMode.valueOf(it) } catch (e: Exception) { VibrateMode.LONG }
                } ?: VibrateMode.LONG
                triggerVibrationWithMode(mode)
            }
            ACTION_TRIGGER_ALERT -> {
                val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: "地铁站"
                // Parse vibrate mode from extra, use LONG as fallback if parsing fails
                val mode = intent.getStringExtra(EXTRA_VIBRATE_MODE)?.let {
                    try { VibrateMode.valueOf(it) } catch (e: Exception) { VibrateMode.LONG }
                } ?: VibrateMode.LONG
                triggerAlert(stationName, mode)
            }
            ACTION_DISMISS_ALERT -> {
                stopAlert()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SubwayAlert::LocationWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        // Main monitoring channel - LOW importance to reduce battery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val lowChannel = NotificationChannel(
                CHANNEL_ID_LOW,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "地铁站位置监控中"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(lowChannel)

            // Alert channel - HIGH importance for alerts
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERT,
                "到站提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "接近地铁站时发出提醒"
                enableVibration(true)
                setBypassDnd(false)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stationText = if (monitoredStations.isNotEmpty()) {
            "监控 ${monitoredStations.size} 个站点"
        } else {
            "等待添加站点..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_LOW)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(stationText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            // Use BALANCED for better subway coverage (uses WiFi/cell towers, not just GPS)
            // HIGH_ACCURACY waits for GPS which doesn't work underground
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL)
            // Don't wait for accurate GPS - we need updates even with low accuracy
            setWaitForAccurateLocation(false)
            setMaxUpdateDelayMillis(MAX_UPDATE_DELAY)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // Location updates received - service is alive
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                // Location unavailable - might need to restart
            }
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            geofencingClient.removeGeofences(geofencePendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var isAlertActive = false
    private var currentAlertStation: String = ""

    private fun triggerAlert(stationName: String, vibrateMode: VibrateMode = currentVibrateMode) {
        currentAlertStation = stationName
        isAlertActive = true
        
        // Show persistent notification with dismiss button
        showAlertNotification(stationName)
        // Start continuous vibration like alarm
        startAlarmVibration()
    }

    private fun stopAlert() {
        isAlertActive = false
        stopVibration()
        // Cancel the notification
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
    }

    private fun showAlertNotification(stationName: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        // Create dismiss intent
        val dismissIntent = Intent(this, LocationMonitoringService::class.java).apply {
            action = ACTION_DISMISS_ALERT
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create open intent
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, stationName.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setContentTitle("🚇 到站提醒 - $stationName")
            .setContentText("点击关闭提醒")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)  // Persistent notification
            .addAction(R.drawable.ic_notification, "关闭", dismissPendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        }
    }

    fun startAlarmVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Alarm pattern: vibrate 1s, pause 0.5s, repeat indefinitely
            val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0)
            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000, 500), -1)
        }
    }

    fun stopVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
    }

    // Keep triggerVibrationWithMode for settings preview
    fun triggerVibrationWithMode(mode: VibrateMode) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (mode) {
                VibrateMode.SHORT -> VibrationEffect.createOneShot(500, 255)
                VibrateMode.LONG -> VibrationEffect.createOneShot(1500, 255)
                VibrateMode.REPEAT -> VibrationEffect.createWaveform(
                    longArrayOf(0, 800, 300, 800, 300, 800),
                    intArrayOf(0, 255, 0, 255, 0, 255),
                    -1
                )
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (mode) {
                VibrateMode.SHORT -> vibrator.vibrate(500)
                VibrateMode.LONG -> vibrator.vibrate(1500)
                VibrateMode.REPEAT -> vibrator.vibrate(longArrayOf(0, 800, 300, 800, 300, 800), -1)
            }
        }
    }

    companion object {
        const val CHANNEL_ID_LOW = "location_monitoring_channel"
        const val CHANNEL_ID_ALERT = "station_alert_channel"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
        const val ACTION_START = "com.subwayalert.ACTION_START"
        const val ACTION_STOP = "com.subwayalert.ACTION_STOP"
        const val ACTION_TEST_ALERT = "com.subwayalert.ACTION_TEST_ALERT"
        const val ACTION_TRIGGER_ALERT = "com.subwayalert.ACTION_TRIGGER_ALERT"
        const val ACTION_GEOFENCE_EVENT = "com.subwayalert.ACTION_GEOFENCE_EVENT"
        const val ACTION_DISMISS_ALERT = "com.subwayalert.ACTION_DISMISS_ALERT"
        const val EXTRA_VIBRATE_MODE = "vibrate_mode"
        const val EXTRA_STATIONS = "stations"
        const val EXTRA_STATION_NAME = "station_name"

        private const val LOCATION_UPDATE_INTERVAL = 15000L // 15 seconds
        private const val FASTEST_LOCATION_INTERVAL = 10000L // 10 seconds
        private const val MAX_UPDATE_DELAY = 30000L // Allow up to 30s between updates

        fun startService(context: Context, vibrateMode: VibrateMode, stations: List<String>) {
            val intent = Intent(context, LocationMonitoringService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VIBRATE_MODE, vibrateMode.name)
                putStringArrayListExtra(EXTRA_STATIONS, ArrayList(stations))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun triggerAlert(context: Context, stationName: String, vibrateMode: VibrateMode? = null) {
            val intent = Intent(context, LocationMonitoringService::class.java).apply {
                action = ACTION_TRIGGER_ALERT
                putExtra(EXTRA_STATION_NAME, stationName)
                vibrateMode?.let { putExtra(EXTRA_VIBRATE_MODE, it.name) }
            }
            context.startService(intent)
        }

        fun testAlert(context: Context, vibrateMode: VibrateMode = VibrateMode.REPEAT) {
            val intent = Intent(context, LocationMonitoringService::class.java).apply {
                action = ACTION_TEST_ALERT
                putExtra(EXTRA_VIBRATE_MODE, vibrateMode.name)
            }
            context.startService(intent)
        }
    }
}
