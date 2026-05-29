package com.smarthome.guardian.presentation.devices.panels

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.smarthome.guardian.domain.model.CommandType
import com.smarthome.guardian.domain.model.DeviceCommand
import com.smarthome.guardian.presentation.devices.DeviceUiState
import com.smarthome.guardian.presentation.devices.DeviceViewModel
import com.smarthome.guardian.presentation.devices.RecordingEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PrimaryBlue   = Color(0xFF00D4FF)
private val SurfaceCard   = Color(0xFF1A2235)
private val TextSecondary = Color(0xFF8899AA)
private val ErrorRed      = Color(0xFFFF4444)

/**
 * 監控攝影機控制面板。
 *
 * 功能：
 * - ExoPlayer RTSP 串流預覽
 * - 截圖按鈕（實際儲存邏輯由後端處理）
 * - 偵測靈敏度 Slider
 * - Canvas 移動偵測區域繪製遮罩
 * - 錄影記錄列表
 */
@Composable
fun CameraPanel(
    uiState: DeviceUiState,
    viewModel: DeviceViewModel,
    rtspUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier            = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── RTSP 串流預覽 ──────────────────────────────────────────────────────
        RtspPlayerView(
            context  = context,
            rtspUrl  = rtspUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .background(Color.Black, RoundedCornerShape(12.dp)),
        )

        // ── 截圖按鈕 ──────────────────────────────────────────────────────────
        Button(
            onClick  = viewModel::captureScreenshot,
            enabled  = !uiState.isSendingCommand,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
            shape    = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Filled.CameraAlt, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("截圖（儲存至加密相簿）", color = PrimaryBlue)
        }

        // ── 靈敏度 Slider ──────────────────────────────────────────────────────
        Column {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("移動偵測靈敏度", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("${uiState.sensitivity.toInt()}%", color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
            Slider(
                value         = uiState.sensitivity,
                onValueChange = viewModel::setSensitivity,
                valueRange    = 0f..100f,
                colors        = SliderDefaults.colors(
                    thumbColor         = PrimaryBlue,
                    activeTrackColor   = PrimaryBlue,
                    inactiveTrackColor = SurfaceCard,
                ),
            )
        }

        // ── 移動偵測區域 ───────────────────────────────────────────────────────
        Text("移動偵測區域", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        MotionDetectionCanvas(modifier = Modifier.fillMaxWidth().height(180.dp))

        // ── 錄影記錄列表 ───────────────────────────────────────────────────────
        if (uiState.recordings.isNotEmpty()) {
            Text("錄影記錄", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            uiState.recordings.forEach { entry ->
                RecordingRow(entry = entry)
            }
        }
    }
}

// ── ExoPlayer RTSP 播放器 ─────────────────────────────────────────────────────

@Composable
private fun RtspPlayerView(
    context: Context,
    rtspUrl: String,
    modifier: Modifier = Modifier,
) {
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(rtspUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory  = {
            PlayerView(it).apply {
                player               = exoPlayer
                useController        = true
                keepScreenOn         = true
            }
        },
        modifier = modifier,
    )
}

// ── 移動偵測遮罩 Canvas ───────────────────────────────────────────────────────

@Composable
private fun MotionDetectionCanvas(modifier: Modifier = Modifier) {
    // 儲存使用者繪製的矩形遮罩區域
    var startOffset by remember { mutableStateOf(Offset.Zero) }
    var endOffset   by remember { mutableStateOf(Offset.Zero) }
    var isDrawing   by remember { mutableStateOf(false) }
    val detectionRects = remember { mutableStateListOf<Rect>() }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            startOffset = offset
                            endOffset   = offset
                            isDrawing   = true
                        },
                        onDrag = { _, dragAmount ->
                            endOffset = Offset(
                                endOffset.x + dragAmount.x,
                                endOffset.y + dragAmount.y,
                            )
                        },
                        onDragEnd = {
                            if (isDrawing) {
                                detectionRects.add(
                                    Rect(
                                        left   = minOf(startOffset.x, endOffset.x),
                                        top    = minOf(startOffset.y, endOffset.y),
                                        right  = maxOf(startOffset.x, endOffset.x),
                                        bottom = maxOf(startOffset.y, endOffset.y),
                                    )
                                )
                                isDrawing = false
                            }
                        },
                    )
                },
        ) {
            // 已確認的偵測區域
            detectionRects.forEach { rect ->
                drawRect(
                    color  = PrimaryBlue.copy(alpha = 0.25f),
                    topLeft = Offset(rect.left, rect.top),
                    size   = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                )
                drawRect(
                    color   = PrimaryBlue,
                    topLeft = Offset(rect.left, rect.top),
                    size    = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                    style   = Stroke(width = 2.dp.toPx()),
                )
            }
            // 正在繪製的區域
            if (isDrawing) {
                val left   = minOf(startOffset.x, endOffset.x)
                val top    = minOf(startOffset.y, endOffset.y)
                val width  = kotlin.math.abs(endOffset.x - startOffset.x)
                val height = kotlin.math.abs(endOffset.y - startOffset.y)
                drawRect(
                    color   = PrimaryBlue.copy(alpha = 0.15f),
                    topLeft = Offset(left, top),
                    size    = androidx.compose.ui.geometry.Size(width, height),
                )
                drawRect(
                    color   = PrimaryBlue.copy(alpha = 0.8f),
                    topLeft = Offset(left, top),
                    size    = androidx.compose.ui.geometry.Size(width, height),
                    style   = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        // 提示文字
        if (detectionRects.isEmpty() && !isDrawing) {
            Text(
                text     = "拖曳設定移動偵測區域",
                color    = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 清除按鈕
        if (detectionRects.isNotEmpty()) {
            TextButton(
                onClick  = { detectionRects.clear() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            ) {
                Text("清除", color = ErrorRed, fontSize = 12.sp)
            }
        }
    }
}

// ── 錄影記錄列表項目 ──────────────────────────────────────────────────────────

@Composable
private fun RecordingRow(entry: RecordingEntry) {
    val startStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(entry.startTime))
    val context  = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = SurfaceCard,
        shape    = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier            = Modifier.padding(12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.VideoFile, null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(startStr, color = Color.White, fontSize = 13.sp)
                Text("${entry.durationSeconds}s", color = TextSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = {
                android.widget.Toast.makeText(
                    context,
                    "播放功能需要後端串流伺服器連線（rtsp://）",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }) {
                Icon(Icons.Filled.PlayCircle, null, tint = PrimaryBlue)
            }
        }
    }
}
