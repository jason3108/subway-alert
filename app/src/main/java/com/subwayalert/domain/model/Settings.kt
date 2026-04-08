package com.subwayalert.domain.model

data class Settings(
    val geofenceRadius: Float = 300f, // meters
    val vibrateMode: VibrateMode = VibrateMode.LONG,
    val soundEnabled: Boolean = false,
    val monitoringMode: MonitoringMode = MonitoringMode.POLLING,
    val pollingIntervalSeconds: Int = 30,
    val otaServerUrl: String = "", // OTA更新服务器地址
    val trackLocationTrack: Boolean = false // 跟踪位置轨迹
)

enum class VibrateMode {
    SHORT,   // 500ms
    LONG,    // 1500ms
    REPEAT   // 3 x 800ms with 300ms intervals
}

enum class MonitoringMode {
    POLLING,   // Distance-based polling (recommended for Chinese phones)
    GEOFENCE   // Android Geofencing API
}
