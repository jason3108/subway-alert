package com.subwayalert.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 位置轨迹点
 */
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val accuracy: Float? = null
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getFormattedDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

/**
 * 一条轨迹记录（一次监控会话）
 */
data class LocationTrack(
    val id: String,
    val startTime: Long,
    val endTime: Long? = null,
    val points: List<LocationPoint> = emptyList()
) {
    fun getFormattedStartTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(startTime))
    }
    
    fun getDurationSeconds(): Long? {
        return endTime?.let { (it - startTime) / 1000 }
    }
    
    fun getTotalDistance(): Float {
        if (points.size < 2) return 0f
        var total = 0f
        for (i in 1 until points.size) {
            total += distanceBetween(points[i - 1], points[i])
        }
        return total
    }
    
    private fun distanceBetween(p1: LocationPoint, p2: LocationPoint): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0]
    }
}

/**
 * 轨迹管理器接口
 */
interface LocationTrackManager {
    suspend fun startTracking()
    suspend fun stopTracking()
    suspend fun addPoint(latitude: Double, longitude: Double, accuracy: Float? = null)
    suspend fun getCurrentTrack(): LocationTrack?
    suspend fun getAllTracks(): List<LocationTrack>
    suspend fun clearAllTracks()
    suspend fun deleteTrack(trackId: String)
    fun isTracking(): Boolean
}
