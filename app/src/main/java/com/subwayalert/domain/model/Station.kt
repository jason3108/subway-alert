package com.subwayalert.domain.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Station(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float = 300f, // meters
    val isMonitoring: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        private val gson = Gson()
        
        fun toJson(stations: List<Station>): String {
            return gson.toJson(stations)
        }
        
        fun fromJson(json: String): List<Station> {
            if (json.isEmpty()) return emptyList()
            val type = object : TypeToken<List<Station>>() {}.type
            return gson.fromJson(json, type)
        }
    }
}
