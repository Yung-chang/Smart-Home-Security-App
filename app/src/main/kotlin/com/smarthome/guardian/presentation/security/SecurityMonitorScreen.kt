package com.smarthome.guardian.presentation.security

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarthome.guardian.domain.model.*
import com.smarthome.guardian.presentation.dashboard.components.SeverityBadge
import java.text.SimpleDateFormat
import java.util.*

private val Background    = Color(0xFF0A0E1A)
private val SurfaceCard   = Color(0xFF121827)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)

/**
 * 安全監控主畫面。
 *
 * 佈局：
 * 1. 安全評分圓形進度條（0–100）
 * 2. 家平面圖（Canvas，各房間依警報狀態上色）
 * 3. 即時事件串流（LazyColumn，新事件從頂部滑入）
 *
 * @param onNavigateToHistory 導航至警報歷史頁
 * @param onNavigateUp        返回上一頁
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityMonitorScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost   = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = { Text("安全監控", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.History, "警報歷史", tint = PrimaryBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1321)),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 安全評分 ──────────────────────────────────────────────────────
            item {
                SecurityScoreRing(
                    score = uiState.securityScore,
                    level = uiState.securityLevel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── 家平面圖 ──────────────────────────────────────────────────────
            item {
                Text("威脅等級地圖", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                HomePlanCanvas(
                    devices = uiState.devices,
                    alerts  = uiState.liveAlerts.filter { !it.isAcknowledged },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(SurfaceCard, RoundedCornerShape(12.dp)),
                )
            }

            // ── 即時事件串流標題 ───────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("即時事件", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val unread = uiState.liveAlerts.count { !it.isAcknowledged }
                        if (unread > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFFFF4444).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    "$unread 筆未確認",
                                    color    = Color(0xFFFF4444),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    val hasUnread = uiState.liveAlerts.any { !it.isAcknowledged }
                    if (hasUnread) {
                        TextButton(onClick = { viewModel.acknowledgeAllLive() }) {
                            Text("全部確認", color = PrimaryBlue, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── 事件列表（新事件從頂部插入，帶 AnimatedItem）────────────────────
            if (uiState.liveAlerts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("目前無異常事件", color = TextSecondary)
                        }
                    }
                }
            } else {
                items(
                    items = uiState.liveAlerts,
                    key   = { it.id },
                ) { alert ->
                    AnimatedVisibility(
                        visible = true,
                        enter   = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    ) {
                        LiveAlertItem(
                            alert        = alert,
                            onAcknowledge = { viewModel.acknowledgeAlert(alert.id) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── 安全評分圓形進度條 ────────────────────────────────────────────────────────

@Composable
private fun SecurityScoreRing(
    score: Int,
    level: SecurityLevel,
    modifier: Modifier = Modifier,
) {
    val animatedScore by animateIntAsState(
        targetValue   = score,
        animationSpec = tween(durationMillis = 1200, easing = EaseOut),
        label         = "score_anim",
    )
    val scoreColor = level.displayColor

    Row(
        modifier            = modifier
            .background(SurfaceCard, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier         = Modifier.size(100.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 底圓弧（背景）
                drawArc(
                    color      = scoreColor.copy(alpha = 0.15f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter  = false,
                    style      = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
                )
                // 進度弧
                drawArc(
                    brush      = Brush.sweepGradient(
                        listOf(scoreColor.copy(alpha = 0.6f), scoreColor)
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedScore / 100f,
                    useCenter  = false,
                    style      = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "$animatedScore",
                    color      = scoreColor,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("分", color = TextSecondary, fontSize = 11.sp)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = level.displayText,
                color      = scoreColor,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = when (level) {
                    SecurityLevel.SECURE  -> "所有設備連線正常，無異常活動"
                    SecurityLevel.WARNING -> "偵測到部分異常，請查看事件清單"
                    SecurityLevel.ALERT   -> "高風險威脅！請立即處理警報"
                },
                color    = TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

// ── 家平面圖 Canvas ───────────────────────────────────────────────────────────

/**
 * 簡化的家庭平面圖，各房間以矩形表示。
 * 房間顏色由該房間內的最高嚴重等級警報決定。
 */
@Composable
private fun HomePlanCanvas(
    devices: List<Device>,
    alerts: List<SecurityAlert>,
    modifier: Modifier = Modifier,
) {
    // 2×2 平面圖（比例座標 0..1，roomId 對應 DemoSimulator 的設備房間）
    val rooms = listOf(
        RoomDef("客廳", "living_room", 0.04f, 0.04f, 0.54f, 0.52f),
        RoomDef("臥室", "bedroom",     0.58f, 0.04f, 0.96f, 0.52f),
        RoomDef("廚房", "kitchen",     0.04f, 0.56f, 0.54f, 0.96f),
        RoomDef("門口", "entrance",    0.58f, 0.56f, 0.96f, 0.96f),
    )

    // 建立 roomId → 最高嚴重等級的 Map
    val roomAlertLevel: Map<String, Severity> = buildMap {
        alerts.forEach { alert ->
            alert.deviceId?.let { devId ->
                val roomId = devices.find { it.id == devId }?.roomId ?: return@let
                val current = this[roomId]
                if (current == null || alert.severity.ordinal > current.ordinal) {
                    this[roomId] = alert.severity
                }
            }
        }
    }

    Canvas(modifier = modifier.padding(12.dp)) {
        val w = size.width
        val h = size.height

        rooms.forEach { room ->
            val left   = room.left * w
            val top    = room.top * h
            val right  = room.right * w
            val bottom = room.bottom * h

            // 房間顏色（依警報等級，使用 roomId 精確對應）
            val severity = roomAlertLevel[room.roomId]
            val fillColor = when (severity) {
                Severity.CRITICAL -> Color(0xFFFF4444).copy(alpha = 0.30f)
                Severity.HIGH     -> Color(0xFFFF6D00).copy(alpha = 0.25f)
                Severity.MEDIUM   -> Color(0xFFFFB300).copy(alpha = 0.20f)
                Severity.LOW      -> Color(0xFF00C853).copy(alpha = 0.15f)
                null              -> Color(0xFF00D4FF).copy(alpha = 0.08f)
            }
            val borderColor = when (severity) {
                null -> Color(0xFF00D4FF).copy(alpha = 0.3f)
                else -> severity.color.copy(alpha = 0.7f)
            }

            // 填充
            drawRect(color = fillColor, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
            // 邊框
            drawRect(
                color   = borderColor,
                topLeft = Offset(left, top),
                size    = Size(right - left, bottom - top),
                style   = Stroke(width = 1.5.dp.toPx()),
            )

            // 房間名稱（簡化：只用 Canvas drawContext）
            // 注意：Compose Canvas 不直接支援 drawText，實際應使用 drawIntoCanvas + Paint
        }

        // 繪製房間標籤使用 native canvas
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(180, 136, 153, 170)
            textSize = 28f
            isAntiAlias = true
        }
        val nativeCanvas = drawContext.canvas.nativeCanvas
        rooms.forEach { room ->
            val cx = ((room.left + room.right) / 2) * w
            val cy = ((room.top + room.bottom) / 2) * h
            val textWidth = paint.measureText(room.label)
            nativeCanvas.drawText(room.label, cx - textWidth / 2, cy + 10f, paint)
        }
    }
}

private data class RoomRect(val name: String, val left: Float, val top: Float, val right: Float, val bottom: Float)
private data class RoomDef(val label: String, val roomId: String,
                           val left: Float, val top: Float, val right: Float, val bottom: Float)

// ── 即時警報項目 ──────────────────────────────────────────────────────────────

@Composable
private fun LiveAlertItem(
    alert: SecurityAlert,
    onAcknowledge: () -> Unit,
) {
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = if (alert.isAcknowledged)
            SurfaceCard.copy(alpha = 0.5f) else SurfaceCard,
        shape    = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier            = Modifier.padding(12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SeverityBadge(alert.severity)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = alert.type.displayName,
                    color      = if (alert.isAcknowledged) TextSecondary else Color.White,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text     = alert.message,
                    color    = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(timeStr, color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
            }
            if (!alert.isAcknowledged) {
                IconButton(onClick = onAcknowledge, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Check, "確認", tint = Color(0xFF00C853), modifier = Modifier.size(18.dp))
                }
            } else {
                Icon(Icons.Filled.CheckCircle, null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun SecurityScorePreview() {
    MaterialTheme {
        SecurityScoreRing(score = 72, level = SecurityLevel.WARNING, modifier = Modifier.padding(16.dp))
    }
}
