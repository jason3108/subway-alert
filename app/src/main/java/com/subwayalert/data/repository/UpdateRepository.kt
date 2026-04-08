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
    
    fun getCurrentVersion(): String = AppVersion.VERSION
    
    fun getCurrentVersionCode(): Int = AppVersion.VERSION_CODE
    
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
                
                // 如果服务器版本更新
                if (serverVersionCode > currentCode) {
                    return@withContext UpdateCheckResult.Available(
                        UpdateInfo(
                            version = json.getString("version"),
                            versionCode = serverVersionCode,
                            downloadUrl = json.getString("downloadUrl"),
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
     * Install APK file - directly opens system package installer
     */
    fun installApk(apkFile: File) {
        val uri = getFileUri(apkFile)
        
        // Get the system package installer directly
        val packageInstaller = context.packageManager.packageInstaller
        
        // Build the install intent
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Try to resolve the system package installer first
        val resolveInfo = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                setPackage("com.android.packageinstaller")
            },
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        
        if (resolveInfo != null) {
            // Use system package installer explicitly
            intent.setPackage("com.android.packageinstaller")
        }
        
        context.startActivity(intent)
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
