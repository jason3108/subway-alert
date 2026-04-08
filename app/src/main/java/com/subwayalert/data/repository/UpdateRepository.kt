package com.subwayalert.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.subwayalert.domain.model.AppVersion
import com.subwayalert.domain.model.UpdateCheckResult
import com.subwayalert.domain.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // 默认OTA服务器地址（模拟器用）
        private const val DEFAULT_OTA_URL = "http://10.0.2.2:8080"
        private const val CHECK_UPDATE_PATH = "/api/check-update"
    }
    
    /**
     * Get the actual installed version name from PackageManager
     * This reflects the currently installed app version, not the compiled static value
     */
    fun getCurrentVersion(): String {
        return try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: AppVersion.VERSION
        } catch (e: Exception) {
            AppVersion.VERSION
        }
    }
    
    /**
     * Get the actual installed version code from PackageManager
     */
    fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        } catch (e: Exception) {
            AppVersion.VERSION_CODE
        }
    }
    
    /**
     * Check for updates from OTA server
     * @param serverUrl OTA服务器地址，如果为空则使用默认地址
     * @return UpdateCheckResult 明确区分成功/无更新/错误
     */
    suspend fun checkForUpdate(serverUrl: String = ""): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val baseUrl = serverUrl.ifBlank { DEFAULT_OTA_URL }.trimEnd('/')
            val url = URL("$baseUrl$CHECK_UPDATE_PATH")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                
                val serverVersionCode = json.getInt("versionCode")
                val currentCode = getCurrentVersionCode()
                
                // 如果服务器版本更新，拼接完整的下载URL
                if (serverVersionCode > currentCode) {
                    // downloadUrl可能是相对路径或绝对路径
                    val rawDownloadUrl = json.getString("downloadUrl")
                    val fullDownloadUrl = if (rawDownloadUrl.startsWith("http")) {
                        rawDownloadUrl
                    } else {
                        // 相对路径，拼接到baseUrl
                        "${baseUrl}${if (rawDownloadUrl.startsWith("/")) "" else "/"}$rawDownloadUrl"
                    }
                    return@withContext UpdateCheckResult.Available(
                        UpdateInfo(
                            version = json.getString("version"),
                            versionCode = serverVersionCode,
                            downloadUrl = fullDownloadUrl,
                            releaseNotes = json.getString("releaseNotes"),
                            apkSize = json.getLong("apkSize"),
                            minVersionCode = json.optInt("minVersionCode", 1)
                        )
                    )
                } else {
                    return@withContext UpdateCheckResult.NoUpdate
                }
            } else {
                return@withContext UpdateCheckResult.Error("服务器错误: $responseCode")
            }
        } catch (e: UnknownHostException) {
            return@withContext UpdateCheckResult.Error("服务器地址错误，无法解析域名")
        } catch (e: SocketTimeoutException) {
            return@withContext UpdateCheckResult.Error("连接超时，请检查服务器地址")
        } catch (e: Exception) {
            return@withContext UpdateCheckResult.Error("连接失败: ${e.message ?: "未知错误"}")
        }
    }
    
    /**
     * Download APK file to cache directory
     */
    suspend fun downloadApk(url: String, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "updates")
            cacheDir.mkdirs()
            val apkFile = File(cacheDir, "update_${System.currentTimeMillis()}.apk")
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val totalSize = connection.contentLength
            var downloadedSize = 0
            
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        if (totalSize > 0) {
                            val progress = (downloadedSize * 100 / totalSize).toInt()
                            onProgress(progress)
                        }
                    }
                }
            }
            connection.disconnect()
            
            apkFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Install APK file - opens system package installer
     * Uses ACTION_VIEW with application/vnd.android.package-archive MIME type
     * which Android automatically routes to the system's package installer.
     * No hardcoded package name to ensure compatibility across all devices/ROMs.
     */
    fun installApk(apkFile: File) {
        val uri = getFileUri(apkFile)
        
        // Grant URI permissions first (required for FileProvider)
        context.grantUriPermission(
            context.packageName,
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        
        // Build the install intent
        // Using ACTION_VIEW with package-archive MIME type lets system
        // automatically pick the appropriate installer
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Try to start the activity and let system handle it
        // Don't hardcode package name - some devices have different installers
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // If direct start fails, try with chooser as fallback
            e.printStackTrace()
            val chooserIntent = Intent.createChooser(intent, "安装更新")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooserIntent)
            } catch (e2: Exception) {
                e2.printStackTrace()
                // Last resort: try package installer directly
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    setPackage("com.android.packageinstaller")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }
    
    private fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    /**
     * Start local OTA server to share APK with other devices on the same network
     * Returns the server URL if successful, null otherwise
     */
    @Suppress("DEPRECATION")
    suspend fun startOtaServer(apkFile: File, port: Int = AppVersion.OTA_SERVER_PORT): String? = withContext(Dispatchers.IO) {
        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket(port)
            serverSocket.soTimeout = 0 // No timeout
            val localIp = getLocalIpAddress()
            val serverUrl = "http://$localIp:$port"
            
            // Store reference for cleanup
            serverSocketList.add(serverSocket)
            
            Thread {
                try {
                    while (!serverSocket.isClosed) {
                        val clientSocket = serverSocket.accept()
                        Thread {
                            try {
                                // Read HTTP request (we don't really need to parse it)
                                clientSocket.getInputStream().readBytes()
                                
                                // Read APK file
                                val apkBytes = apkFile.readBytes()
                                
                                // Send HTTP response
                                val response = buildHttpResponse(apkBytes)
                                clientSocket.getOutputStream().use { output ->
                                    output.write(response.toByteArray())
                                    output.flush()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                clientSocket.close()
                            }
                        }.start()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
            
            serverUrl
        } catch (e: Exception) {
            e.printStackTrace()
            serverSocket?.close()
            null
        }
    }
    
    private fun buildHttpResponse(apkBytes: ByteArray): String {
        return """
            HTTP/1.1 200 OK
            Content-Type: application/vnd.android.package-archive
            Content-Length: ${apkBytes.size}
            Content-Disposition: attachment; filename="subway-alert.apk"
            Connection: close
            Cache-Control: no-cache
            
        """.trimIndent() + String(apkBytes, Charsets.ISO_8859_1)
    }
    
    @Suppress("DEPRECATION")
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
    
    fun stopOtaServer() {
        serverSocketList.forEach { it.close() }
        serverSocketList.clear()
    }
    
    private val serverSocketList = mutableListOf<ServerSocket>()
}
