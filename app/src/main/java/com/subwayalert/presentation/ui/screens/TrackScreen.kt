package com.subwayalert.presentation.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subwayalert.domain.model.LocationPoint
import com.subwayalert.domain.model.LocationTrack
import com.subwayalert.presentation.viewmodel.TrackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    onBack: () -> Unit,
    viewModel: TrackViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("位置轨迹", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.tracks.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllTracks() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空所有")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.tracks.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "暂无轨迹记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "开启监控并启用\"跟踪位置轨迹\"后记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.tracks, key = { it.id }) { track ->
                        TrackCard(
                            track = track,
                            isExpanded = uiState.expandedTrackId == track.id,
                            onToggleExpand = { viewModel.toggleTrackExpand(track.id) },
                            onDelete = { viewModel.deleteTrack(track.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrackCard(
    track: LocationTrack,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条轨迹吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = track.getFormattedStartTime(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "${track.points.size} 个点",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDistance(track.getTotalDistance()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        track.getDurationSeconds()?.let { duration ->
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Row {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "收起" else "展开"
                        )
                    }
                }
            }
            
            // Track visualization
            if (track.points.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                TrackMapView(
                    points = track.points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            
            // Expanded details
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "轨迹详情",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                track.points.takeLast(10).forEachIndexed { index, point ->
                    val actualIndex = track.points.size - 10 + index
                    if (actualIndex >= 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "#$actualIndex ${point.getFormattedTime()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.6f, %.6f", point.latitude, point.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
                
                if (track.points.size > 10) {
                    Text(
                        text = "... 还有 ${track.points.size - 10} 个点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackMapView(
    points: List<LocationPoint>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                "点数不足",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    
    // Calculate bounds
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLng = points.minOf { it.longitude }
    val maxLng = points.maxOf { it.longitude }
    
    val latRange = maxLat - minLat
    val lngRange = maxLng - minLng
    
    // Handle edge case where all points are at same location
    val effectiveLatRange = if (latRange < 0.0001) 0.001 else latRange
    val effectiveLngRange = if (lngRange < 0.0001) 0.001 else lngRange
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    Canvas(modifier = modifier) {
        val padding = 16.dp.toPx()
        val width = size.width - padding * 2
        val height = size.height - padding * 2
        
        // Draw path
        val path = Path()
        var isFirst = true
        
        points.forEach { point ->
            val x = padding + ((point.longitude - minLng) / effectiveLngRange * width).toFloat()
            val y = padding + ((maxLat - point.latitude) / effectiveLatRange * height).toFloat()
            
            if (isFirst) {
                path.moveTo(x, y)
                isFirst = false
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Draw start point (green)
        val startPoint = points.first()
        val startX = padding + ((startPoint.longitude - minLng) / effectiveLngRange * width).toFloat()
        val startY = padding + ((maxLat - startPoint.latitude) / effectiveLatRange * height).toFloat()
        drawCircle(
            color = Color.Green,
            radius = 6.dp.toPx(),
            center = Offset(startX, startY)
        )
        
        // Draw end point (red)
        val endPoint = points.last()
        val endX = padding + ((endPoint.longitude - minLng) / effectiveLngRange * width).toFloat()
        val endY = padding + ((maxLat - endPoint.latitude) / effectiveLatRange * height).toFloat()
        drawCircle(
            color = Color.Red,
            radius = 6.dp.toPx(),
            center = Offset(endX, endY)
        )
        
        // Draw intermediate points (small dots)
        points.drop(1).dropLast(1).forEach { point ->
            val x = padding + ((point.longitude - minLng) / effectiveLngRange * width).toFloat()
            val y = padding + ((maxLat - point.latitude) / effectiveLatRange * height).toFloat()
            drawCircle(
                color = tertiaryColor,
                radius = 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

private fun formatDistance(meters: Float): String {
    return when {
        meters < 1000 -> "${meters.toInt()} 米"
        else -> String.format("%.1f 公里", meters / 1000)
    }
}

private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}秒"
        seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
        else -> "${seconds / 3600}小时${(seconds % 3600) / 60}分"
    }
}
