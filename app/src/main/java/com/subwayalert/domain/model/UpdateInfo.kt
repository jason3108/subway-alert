package com.subwayalert.domain.model

data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val apkSize: Long,  // bytes
    val minVersionCode: Int  // minimum version that can update to this
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data object NoUpdate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AppVersion {
    const val VERSION = "1.0.11"
    const val VERSION_CODE = 12
    
    // Update server URL - can be customized
    // For demo, using a simple JSON endpoint
    const val UPDATE_CHECK_URL = "https://files.catbox.moe/user/api.php"
    
    // Local OTA server port - avoid system ports
    const val OTA_SERVER_PORT = 8080  // Using a high port
}
