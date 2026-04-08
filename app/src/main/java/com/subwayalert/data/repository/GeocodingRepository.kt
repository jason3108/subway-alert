package com.subwayalert.data.repository

import android.content.Context
import com.subwayalert.domain.model.PresetStations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 地理编码仓库
 * 优先从本地北京地铁站点数据库搜索，其次使用在线地理编码服务
 */
@Singleton
class GeocodingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 搜索地铁站
     * 1. 首先在本地预设站点中精确匹配
     * 2. 然后模糊匹配
     * 3. 最后才使用在线服务
     */
    suspend fun geocodeStationName(stationName: String): GeocodingResult? = withContext(Dispatchers.IO) {
        // Step 1: 精确匹配本地站点
        val exactMatch = PresetStations.ALL_STATIONS.find { 
            it.name == stationName || it.name.contains(stationName) || stationName.contains(it.name)
        }
        
        if (exactMatch != null) {
            return@withContext GeocodingResult(
                latitude = exactMatch.latitude,
                longitude = exactMatch.longitude,
                displayName = "${exactMatch.name} (${exactMatch.line})"
            )
        }
        
        // Step 2: 模糊匹配 - 搜索名称包含关键词的站点
        val keywords = stationName.filter { it.isLetterOrDigit() }.lowercase()
        val fuzzyMatches = PresetStations.ALL_STATIONS.filter { station ->
            station.name.filter { it.isLetterOrDigit() }.lowercase().contains(keywords) ||
            keywords.contains(station.name.filter { it.isLetterOrDigit() }.lowercase())
        }.take(5)
        
        if (fuzzyMatches.isNotEmpty()) {
            // 返回第一个模糊匹配结果
            val match = fuzzyMatches.first()
            return@withContext GeocodingResult(
                latitude = match.latitude,
                longitude = match.longitude,
                displayName = "${match.name} (${match.line})"
            )
        }
        
        // Step 3: 如果本地没有匹配，使用在线服务（仅作后备）
        tryOnlineGeocoding(stationName)
    }
    
    /**
     * 在线地理编码作为后备方案
     */
    private suspend fun tryOnlineGeocoding(stationName: String): GeocodingResult? = withContext(Dispatchers.IO) {
        try {
            val searchQuery = if (!stationName.contains("北京") && !stationName.contains("地铁")) {
                "$stationName 北京 地铁站"
            } else {
                stationName
            }
            
            val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
            val urlStr = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&addressdetails=1"
            
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "SubwayAlert/1.0 (Android App)")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            
            parseNominatimResponse(response, stationName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 解析 Nominatim 返回的 JSON 响应
     */
    private fun parseNominatimResponse(jsonResponse: String, originalName: String): GeocodingResult? {
        try {
            val results = mutableListOf<NominatimEntry>()
            
            var current = 0
            while (current < jsonResponse.length) {
                val objStart = jsonResponse.indexOf("{", current)
                if (objStart == -1) break
                val objEnd = jsonResponse.indexOf("}", objStart)
                if (objEnd == -1) break
                
                val objStr = jsonResponse.substring(objStart, objEnd + 1)
                parseNominatimObject(objStr)?.let { results.add(it) }
                current = objEnd + 1
            }
            
            val bestMatch = results.firstOrNull { entry ->
                val type = entry.type.lowercase()
                val display = entry.displayName.lowercase()
                type.contains("station") || type.contains("metro") || 
                type.contains("subway") || display.contains("地铁站")
            } ?: results.firstOrNull { entry ->
                val lat = entry.lat.toDoubleOrNull() ?: 0.0
                val lon = entry.lon.toDoubleOrNull() ?: 0.0
                lat in 39.0..41.0 && lon in 116.0..118.0
            } ?: results.firstOrNull()
            
            return bestMatch?.let {
                GeocodingResult(
                    latitude = it.lat.toDoubleOrNull() ?: 0.0,
                    longitude = it.lon.toDoubleOrNull() ?: 0.0,
                    displayName = it.displayName.ifEmpty { originalName }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun parseNominatimObject(objStr: String): NominatimEntry? {
        try {
            fun getValue(key: String): String {
                val patterns = listOf(
                    "\"$key\"\\s*:\\s*\"([^\"]*)\"",
                    "\"$key\"\\s*:\\s*'([^']*)'",
                    "\"$key\"\\s*:\\s*([0-9.]+)"
                )
                for (pattern in patterns) {
                    val regex = Regex(pattern)
                    val match = regex.find(objStr)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
                return ""
            }
            
            val lat = getValue("lat")
            val lon = getValue("lon")
            val displayName = getValue("display_name")
            val type = getValue("type")
            
            if (lat.isNotEmpty() && lon.isNotEmpty()) {
                return NominatimEntry(lat, lon, displayName, type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private data class NominatimEntry(
        val lat: String,
        val lon: String,
        val displayName: String,
        val type: String
    )
}

data class GeocodingResult(
    val latitude: Double,
    val longitude: Double,
    val displayName: String
)
