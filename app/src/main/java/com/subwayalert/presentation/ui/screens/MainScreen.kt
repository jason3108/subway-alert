package com.subwayalert.presentation.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subwayalert.domain.model.MonitoringMode
import com.subwayalert.presentation.viewmodel.UpdateUiState
import com.subwayalert.presentation.viewmodel.UpdateViewModel
import com.subwayalert.domain.model.PresetStations
import com.subwayalert.domain.model.Settings
import com.subwayalert.service.LocationMonitoringService
import com.subwayalert.domain.model.Station
import com.subwayalert.domain.model.VibrateMode
import com.subwayalert.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToTrack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var stationToDelete by remember { mutableStateOf<Station?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
        } else true
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true
        
        viewModel.onPermissionsResult(fineLocation && backgroundLocation && notification)
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
        if (!uiState.permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("地铁提醒", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (uiState.isMonitoring) {
                        IconButton(onClick = { showDebugDialog = true }) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "调试信息",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = { viewModel.onTestAlert() }) {
                            Icon(
                                Icons.Default.Vibration,
                                contentDescription = "测试提醒",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showUpdateDialog = true }) {
                        Icon(
                            Icons.Default.Update,
                            contentDescription = "检查更新",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToTrack) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = "位置轨迹",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onShowAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加站点")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isMonitoring)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (uiState.isMonitoring) Icons.Default.LocationOn else Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = if (uiState.isMonitoring)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (uiState.isMonitoring) "正在监控" else "未在监控",
                                fontWeight = FontWeight.Medium
                            )
                            if (uiState.isMonitoring && uiState.stations.isNotEmpty()) {
                                Text(
                                    text = "监控 ${uiState.stations.size} 个站点",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    if (uiState.stations.isNotEmpty()) {
                        if (uiState.isMonitoring) {
                            TextButton(onClick = { viewModel.onToggleMonitoring() }) {
                                Text("停止")
                            }
                        } else {
                            TextButton(onClick = { viewModel.onToggleMonitoring() }) {
                                Text("开始")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stations List
            if (uiState.stations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Subway,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无监控站点",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "点击 + 添加地铁站",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Text(
                    text = "已监控站点",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sortedStations = uiState.stations.sortedBy { station ->
                        val distance = uiState.stationDistances[station.id] ?: Float.MAX_VALUE
                        val isWithinRange = distance <= uiState.settings.geofenceRadius
                        // Sort: within range first, then by distance
                        if (isWithinRange) -distance else distance
                    }
                    items(sortedStations, key = { it.id }) { station ->
                        val distance = uiState.stationDistances[station.id]
                        val isWithinRange = distance != null && distance <= uiState.settings.geofenceRadius
                        val isAlerted = uiState.alertedStationId == station.id
                        StationCard(
                            station = station,
                            distance = distance,
                            isWithinRange = isWithinRange,
                            isAlerted = isAlerted,
                            onRemove = { stationToDelete = station }
                        )
                    }
                }
            }
        }
    }

    // Add Station Dialog
    if (uiState.showAddDialog) {
        AddStationDialog(
            inputText = uiState.inputText,
            isLoading = uiState.isGeocoding,
            error = uiState.geocodingError,
            onInputChange = viewModel::onInputTextChanged,
            onDismiss = viewModel::onDismissAddDialog,
            onConfirm = viewModel::onAddStation,
            onAddWithCoords = viewModel::onAddStationWithCoords,
            onSelectPreset = { name, line, lat, lng -> 
                viewModel.onAddStationWithCoords("${name}（${line}）", lat, lng)
            },
            onTabChange = { tab ->
                if (tab == 1) {
                    viewModel.onResetGeocodingState()
                }
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            settings = uiState.settings,
            onDismiss = { showSettingsDialog = false },
            onSave = { settings ->
                viewModel.updateSettings(settings)
                showSettingsDialog = false
            }
        )
    }

    // Debug Dialog
    if (showDebugDialog) {
        DebugDialog(
            stations = uiState.stations,
            distances = uiState.stationDistances,
            currentLocation = uiState.currentLocation,
            geofenceRadius = uiState.settings.geofenceRadius,
            onRefresh = { viewModel.refreshLocation() },
            onTestGeofence = { viewModel.testGeofenceAlert() },
            onDismiss = { showDebugDialog = false }
        )
    }

    // Update Dialog
    if (showUpdateDialog) {
        val updateViewModel: UpdateViewModel = hiltViewModel()
        UpdateDialog(
            updateViewModel = updateViewModel,
            onDismiss = { showUpdateDialog = false }
        )
    }

    // Permission Request
    if (!uiState.permissionsGranted) {
        PermissionRequestDialog(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            },
            onOpenSettings = {
                val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
    }

    // Delete Confirmation Dialog
    if (stationToDelete != null) {
        AlertDialog(
            onDismissRequest = { stationToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除监控站点「${stationToDelete!!.name}」吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onRemoveStation(stationToDelete!!)
                        stationToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { stationToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun StationCard(
    station: Station,
    distance: Float?,
    isWithinRange: Boolean = false,
    isAlerted: Boolean = false,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isAlerted -> Color(0xFF4CAF50).copy(alpha = 0.5f) // Stronger green for alerted station
                isWithinRange -> Color(0xFF4CAF50).copy(alpha = 0.2f) // Light green background
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isAlerted) CardDefaults.outlinedCardBorder().copy(
            width = 2.dp,
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF4CAF50))
        ) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Subway,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = station.name,
                        fontWeight = FontWeight.Medium
                    )
                    if (distance != null) {
                        Text(
                            text = formatDistance(distance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStationDialog(
    inputText: String,
    isLoading: Boolean,
    error: String?,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onAddWithCoords: (String, Double, Double) -> Unit,
    onSelectPreset: (String, String, Double, Double) -> Unit,  // name, line, lat, lng
    onTabChange: (Int) -> Unit = {}
) {
    var showManualInput by remember { mutableStateOf(false) }
    var manualLat by remember { mutableStateOf("") }
    var manualLng by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var coordError by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=preset, 1=search, 2=manual

    val presetStations = remember { PresetStations.ALL_STATIONS }
    val groupedPresets = remember { 
        presetStations.groupBy { it.line }.toList().sortedBy { it.first }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加地铁站") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Tab buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("预设站点") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { 
                            selectedTab = 1
                            onTabChange(1)
                        },
                        label = { Text("搜索") }
                    )
                    FilterChip(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("手动输入") }
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                when (selectedTab) {
                    0 -> {
                        // Two-step: first select line, then select station
                        var selectedLine by remember { mutableStateOf<String?>(null) }
                        val currentLine = selectedLine
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (currentLine == null) {
                                // Show all lines
                                Text(
                                    text = "选择线路",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                groupedPresets.forEach { (line, _) ->
                                    FilterChip(
                                        selected = currentLine == line,
                                        onClick = { selectedLine = line },
                                        label = { Text(line) },
                                        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                                    )
                                }
                            } else {
                                // Show stations of selected line
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    TextButton(onClick = { selectedLine = null }) {
                                        Text("← 返回")
                                    }
                                    Text(
                                        text = currentLine,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                groupedPresets.find { it.first == currentLine }?.second?.forEach { station ->
                                    TextButton(
                                        onClick = { 
                                            onSelectPreset(station.name, station.line, station.latitude, station.longitude)
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = station.name,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Search mode
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = onInputChange,
                                label = { Text("地铁站名称") },
                                placeholder = { Text("例如: 长椿街") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                isError = error != null,
                                enabled = !isLoading
                            )
                            if (error != null) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (isLoading) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )
                                Text(
                                    text = "正在查找位置...",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    2 -> {
                        // Manual input mode
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            OutlinedTextField(
                                value = manualName,
                                onValueChange = { manualName = it },
                                label = { Text("站点名称") },
                                placeholder = { Text("例如: 长椿街站") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualLat,
                                onValueChange = { 
                                    manualLat = it
                                    coordError = null
                                },
                                label = { Text("纬度") },
                                placeholder = { Text("例如: 39.899467") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = coordError != null,
                                enabled = !isLoading
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualLng,
                                onValueChange = { 
                                    manualLng = it
                                    coordError = null
                                },
                                label = { Text("经度") },
                                placeholder = { Text("例如: 116.363354") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = coordError != null,
                                enabled = !isLoading
                            )
                            if (coordError != null) {
                                Text(
                                    text = coordError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1) {
                TextButton(
                    onClick = onConfirm,
                    enabled = inputText.isNotBlank() && !isLoading
                ) {
                    Text("搜索并添加")
                }
            } else if (selectedTab == 2) {
                TextButton(
                    onClick = {
                        val lat = manualLat.toDoubleOrNull()
                        val lng = manualLng.toDoubleOrNull()
                        if (lat == null || lng == null) {
                            coordError = "请输入有效的数字"
                        } else if (lat < -90 || lat > 90) {
                            coordError = "纬度必须在 -90 到 90 之间"
                        } else if (lng < -180 || lng > 180) {
                            coordError = "经度必须在 -180 到 180 之间"
                        } else {
                            onAddWithCoords(manualName.ifBlank { "地铁站" }, lat, lng)
                            onDismiss()
                        }
                    },
                    enabled = manualLat.isNotBlank() && manualLng.isNotBlank() && !isLoading
                ) {
                    Text("添加")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun SettingsDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onSave: (Settings) -> Unit
) {
    val context = LocalContext.current
    var radius by remember { mutableFloatStateOf(settings.geofenceRadius) }
    var pollingInterval by remember { mutableIntStateOf(settings.pollingIntervalSeconds) }
    var soundEnabled by remember { mutableStateOf(settings.soundEnabled) }
    var trackLocationTrack by remember { mutableStateOf(settings.trackLocationTrack) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Radius setting
                Text(
                    text = "监控半径: ${radius.toInt()} 米",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 100f..1000f,
                    steps = 17,  // (1000-100)/50 = 18 steps, but steps means intervals, so 17 gives 18 values
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Polling interval
                Text(
                    text = "检查间隔: ${pollingInterval} 秒",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = pollingInterval.toFloat(),
                    onValueChange = { pollingInterval = it.toInt() },
                    valueRange = 10f..120f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "间隔越短越灵敏，但更耗电",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Sound setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "提醒音效",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Track Location setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "跟踪位置轨迹",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "开启监控后记录位置变化（>500米）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = trackLocationTrack,
                        onCheckedChange = { trackLocationTrack = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(Settings(
                        geofenceRadius = radius,
                        vibrateMode = VibrateMode.LONG,  // Fixed vibration mode
                        soundEnabled = soundEnabled,
                        monitoringMode = MonitoringMode.POLLING,  // Fixed to POLLING mode
                        pollingIntervalSeconds = pollingInterval,
                        trackLocationTrack = trackLocationTrack
                    ))
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun PermissionRequestDialog(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("需要权限") },
        text = {
            Text("地铁提醒需要位置权限来监控你的位置，当接近地铁站时发出提醒。请授予权限。")
        },
        confirmButton = {
            TextButton(onClick = onRequestPermission) {
                Text("授予权限")
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenSettings) {
                Text("打开设置")
            }
        }
    )
}

private fun formatDistance(meters: Float): String {
    return when {
        meters < 1000 -> "${meters.toInt()} 米"
        else -> String.format("%.1f 公里", meters / 1000)
    }
}

@Composable
fun DebugDialog(
    stations: List<Station>,
    distances: Map<String, Float>,
    currentLocation: android.location.Location?,
    geofenceRadius: Float,
    onRefresh: () -> Unit,
    onTestGeofence: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调试信息") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Current location
                Text(
                    text = "当前位置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (currentLocation != null) {
                    Text(
                        text = "纬度: ${String.format("%.6f", currentLocation.latitude)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "经度: ${String.format("%.6f", currentLocation.longitude)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "精度: ±${currentLocation.accuracy.toInt()}米",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "无法获取位置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Stations
                Text(
                    text = "监控站点 (${stations.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (stations.isEmpty()) {
                    Text(
                        text = "暂无监控站点",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Sort stations by distance when current location is available
                    val sortedStations = if (currentLocation != null) {
                        stations.sortedBy { station ->
                            distances[station.id] ?: Float.MAX_VALUE
                        }
                    } else {
                        stations
                    }
                    sortedStations.forEach { station ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "站点坐标: ${String.format("%.6f", station.latitude)}, ${String.format("%.6f", station.longitude)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        val distance = distances[station.id]
                        if (distance != null) {
                            val isWithinRange = distance <= geofenceRadius
                            Text(
                                text = "距离: ${formatDistance(distance)} (范围${geofenceRadius.toInt()}米内)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isWithinRange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isWithinRange) {
                                Text(
                                    text = "✓ 在监控范围内",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                val remaining = distance - geofenceRadius
                                Text(
                                    text = "✗ 还差 ${formatDistance(remaining)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Text(
                                text = "距离: 计算中...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Help text
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "提示",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "如果距离显示正确但没有提醒，可能是地理围栏没有正确创建。检查手机是否允许了后台位置权限。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onTestGeofence) {
                    Text("测试提醒")
                }
                TextButton(onClick = onRefresh) {
                    Text("刷新")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun UpdateDialog(
    updateViewModel: UpdateViewModel,
    onDismiss: () -> Unit
) {
    val uiState by updateViewModel.uiState.collectAsState()
    var serverUrl by remember { mutableStateOf("") }
    var hasInitialized by remember { mutableStateOf(false) }
    
    // Update serverUrl when dialog opens and when settings change
    val currentOtaUrl by rememberUpdatedState(uiState.currentSettings.otaServerUrl)
    LaunchedEffect(Unit) {
        // Small delay to ensure flow has been collected
        kotlinx.coroutines.delay(100)
        if (serverUrl.isEmpty() && currentOtaUrl.isNotEmpty()) {
            serverUrl = currentOtaUrl
        }
        hasInitialized = true
    }
    
    // Also update if settings change after initial load
    LaunchedEffect(currentOtaUrl) {
        if (hasInitialized && serverUrl.isEmpty() && currentOtaUrl.isNotEmpty()) {
            serverUrl = currentOtaUrl
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Update, contentDescription = null)
                Text("检查更新")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current version
                Text(
                    text = "当前版本: ${uiState.currentVersion}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // OTA Server URL input
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("OTA服务器地址") },
                    placeholder = { Text("http://192.168.1.100:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (serverUrl.isNotEmpty()) {
                            IconButton(onClick = { serverUrl = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    }
                )
                
                when {
                    uiState.isChecking -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("检查更新中...")
                        }
                    }
                    
                    // 服务器地址错误
                    uiState.error != null && !uiState.checkSuccess -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "服务器地址错误",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    // 已是最新版本（检查成功且无更新）
                    !uiState.isUpdateAvailable && uiState.checkSuccess && !uiState.isDownloading -> {
                        Text(
                            text = "已是最新版本",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    uiState.isUpdateAvailable && uiState.updateInfo != null -> {
                        val info = uiState.updateInfo!!
                        
                        Text(
                            text = "发现新版本: ${info.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "大小: ${info.apkSize / 1024 / 1024} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (uiState.isDownloading) {
                            Column {
                                LinearProgressIndicator(
                                    progress = uiState.downloadProgress / 100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "下载中: ${uiState.downloadProgress}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // OTA Server Section
                if (uiState.downloadedFile != null) {
                    Divider()
                    
                    Text(
                        text = "OTA 局域网更新",
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Text(
                        text = "在同一WiFi下，其他设备可以扫描二维码下载安装",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (uiState.isOtaServerRunning) {
                        Text(
                            text = "服务地址: ${uiState.otaServerUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Error message
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    uiState.isChecking -> {
                        // Nothing
                    }
                    
                    uiState.isUpdateAvailable && !uiState.isDownloading && uiState.downloadedFile == null -> {
                        Button(onClick = { updateViewModel.downloadUpdate() }) {
                            Text("下载更新")
                        }
                    }
                    
                    uiState.isDownloading -> {
                        // Show nothing while downloading
                    }
                    
                    uiState.downloadedFile != null && !uiState.isOtaServerRunning -> {
                        Button(onClick = { updateViewModel.startOtaServer() }) {
                            Text("分享链接")
                        }
                        Button(
                            onClick = { updateViewModel.installUpdate() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("安装")
                        }
                    }
                    
                    uiState.isOtaServerRunning -> {
                        Button(onClick = { updateViewModel.stopOtaServer() }) {
                            Text("停止分享")
                        }
                        Button(onClick = { updateViewModel.installUpdate() }) {
                            Text("安装")
                        }
                    }
                    
                    else -> {
                        Button(onClick = { updateViewModel.checkForUpdate(serverUrl) }) {
                            Text("检查更新")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
