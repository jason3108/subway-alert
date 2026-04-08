package com.subwayalert.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subwayalert.data.local.PreferencesManager
import com.subwayalert.data.repository.UpdateRepository
import com.subwayalert.domain.model.Settings
import com.subwayalert.domain.model.UpdateCheckResult
import com.subwayalert.domain.model.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val isUpdateAvailable: Boolean = false,
    val downloadProgress: Int = 0,
    val isDownloading: Boolean = false,
    val downloadedFile: File? = null,
    val error: String? = null,
    val currentVersion: String = "",
    val isOtaServerRunning: Boolean = false,
    val otaServerUrl: String? = null,
    val isStartingOtaServer: Boolean = false,
    val checkSuccess: Boolean = false,  // 标记检查是否成功（区分无更新和错误）
    val currentSettings: Settings = Settings()  // 当前设置（包含OTA服务器地址）
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    application: Application,
    private val updateRepository: UpdateRepository,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.settingsFlow.collect { settings ->
                _uiState.update { it.copy(currentSettings = settings) }
            }
        }
        _uiState.update { it.copy(currentVersion = updateRepository.getCurrentVersion()) }
    }

    fun checkForUpdate(serverUrl: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, error = null, checkSuccess = false) }
            
            // 保存服务器地址到设置
            if (serverUrl.isNotBlank()) {
                val currentSettings = preferencesManager.settingsFlow.first()
                preferencesManager.saveSettings(currentSettings.copy(otaServerUrl = serverUrl))
            }
            
            when (val result = updateRepository.checkForUpdate(serverUrl)) {
                is UpdateCheckResult.Available -> {
                    _uiState.update { 
                        it.copy(
                            isChecking = false,
                            updateInfo = result.info,
                            isUpdateAvailable = true,
                            checkSuccess = true
                        )
                    }
                }
                is UpdateCheckResult.NoUpdate -> {
                    _uiState.update { 
                        it.copy(
                            isChecking = false,
                            updateInfo = null,
                            isUpdateAvailable = false,
                            checkSuccess = true
                        )
                    }
                }
                is UpdateCheckResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isChecking = false,
                            updateInfo = null,
                            isUpdateAvailable = false,
                            error = result.message,
                            checkSuccess = false
                        )
                    }
                }
            }
        }
    }

    fun downloadUpdate() {
        val updateInfo = _uiState.value.updateInfo ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0, error = null) }
            
            val file = updateRepository.downloadApk(updateInfo.downloadUrl) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            
            if (file != null) {
                _uiState.update { 
                    it.copy(
                        isDownloading = false,
                        downloadedFile = file,
                        downloadProgress = 100
                    )
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isDownloading = false,
                        error = "下载失败，请重试"
                    )
                }
            }
        }
    }

    fun installUpdate() {
        val file = _uiState.value.downloadedFile ?: return
        updateRepository.installApk(file)
    }

    fun startOtaServer() {
        val apkFile = _uiState.value.downloadedFile
        if (apkFile == null || !apkFile.exists()) {
            _uiState.update { it.copy(error = "请先下载更新包") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isStartingOtaServer = true, error = null) }
            
            val serverUrl = updateRepository.startOtaServer(apkFile)
            
            if (serverUrl != null) {
                _uiState.update { 
                    it.copy(
                        isStartingOtaServer = false,
                        isOtaServerRunning = true,
                        otaServerUrl = serverUrl
                    )
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isStartingOtaServer = false,
                        error = "启动OTA服务失败"
                    )
                }
            }
        }
    }

    fun stopOtaServer() {
        updateRepository.stopOtaServer()
        _uiState.update { 
            it.copy(
                isOtaServerRunning = false,
                otaServerUrl = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
