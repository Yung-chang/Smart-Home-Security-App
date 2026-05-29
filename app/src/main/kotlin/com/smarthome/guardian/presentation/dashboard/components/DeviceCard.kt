package com.smarthome.guardian.presentation.dashboard.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.Device
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.domain.model.DeviceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color

private val BackgroundCard  = Color(0xFF1A2235)
private val PrimaryBlue     = Color(0xFF00D4FF)
private val TextSecondary   = Color(0xFF8899AA)
private val ErrorRed        = Color(0xFFFF4444)
private val SuccessGreen    = Color(0xFF00C853)

/**
 * 設備卡片元件。
 *
 * 功能：
 * - 點擊：跳至設備詳情
 * - 長按：顯示快速操作選單（鎖定/解鎖/詳情/日誌）
 * - 設備離線：灰階顯示
 * - 支援開關的設備：顯示 Toggle
 * - [AnimatedVisibility] 控制狀態指示燈與 Toggle 的顯隱
 *
 * @param device        設備資料
 * @param onToggle      Toggle 狀態改變的回呼
 * @param onViewDetail  點擊查看詳情
 * @param onViewLogs    查看稽核日誌
 * @param onLock        鎖定設備
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceCard(
    device: Device,
    onToggle: (Boolean) -> Unit,
    onViewDetail: () -> Unit,
    onViewLogs: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val isOffline = device.status != DeviceStatus.ONLINE

    // 離線時降低飽和度（灰階效果）
    val saturation = if (isOffline) 0f else 1f
    val colorMatrix = remember(saturation) {
        ColorMatrix().apply { setToSaturation(saturation) }
    }

    ElevatedCard(
        modifier = modifier
            .graphicsLayer {
                // 離線時降低透明度
                alpha = if (isOffline) 0.6f else 1f
            }
            .combinedClickable(
                onClick      = onViewDetail,
                onLongClick  = { showContextMenu = true },
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = BackgroundCard,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 頂部：圖示 + 狀態燈 ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector        = device.type.icon,
                    contentDescription = device.type.displayName,
                    tint               = if (isOffline) TextSecondary else PrimaryBlue,
                    modifier           = Modifier.size(28.dp),
                )
                StatusIndicatorDot(status = device.status)
            }

            // ── 設備名稱 ──────────────────────────────────────────────────────
            Text(
                text       = device.name,
                color      = if (isOffline) TextSecondary else Color.White,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )

            // ── 最後活動時間 ──────────────────────────────────────────────────
            Text(
                text     = formatLastSeen(device.lastSeen),
                color    = TextSecondary,
                fontSize = 11.sp,
            )

            // ── Toggle（僅可操控設備 + 未被管理員鎖定）────────────────────────
            AnimatedVisibility(
                visible = device.type.isToggleable,
                enter   = fadeIn(tween(200)) + expandVertically(),
                exit    = fadeOut(tween(200)) + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text     = if (device.isOn) "開啟" else "關閉",
                        color    = if (device.isOn) PrimaryBlue else TextSecondary,
                        fontSize = 12.sp,
                    )
                    Switch(
                        checked         = device.isOn,
                        onCheckedChange = { if (!device.isLocked && !isOffline) onToggle(it) },
                        enabled         = !device.isLocked && !isOffline,
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = PrimaryBlue,
                            checkedTrackColor   = PrimaryBlue.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.height(24.dp),
                    )
                }
            }

            // ── 鎖定標記 ──────────────────────────────────────────────────────
            AnimatedVisibility(visible = device.isLocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Filled.Lock,
                        contentDescription = "管理員鎖定",
                        tint               = ErrorRed,
                        modifier           = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("管理員鎖定", color = ErrorRed, fontSize = 10.sp)
                }
            }
        }

        // ── 長按選單 ──────────────────────────────────────────────────────────
        DropdownMenu(
            expanded        = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text    = { Text("查看詳情") },
                onClick = { showContextMenu = false; onViewDetail() },
                leadingIcon = { Icon(Icons.Filled.Info, null) },
            )
            DropdownMenuItem(
                text    = { Text("查看日誌") },
                onClick = { showContextMenu = false; onViewLogs() },
                leadingIcon = { Icon(Icons.Filled.History, null) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text    = { Text(if (device.isLocked) "解除鎖定" else "鎖定設備") },
                onClick = { showContextMenu = false; onLock() },
                leadingIcon = {
                    Icon(
                        if (device.isLocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        null,
                    )
                },
            )
        }
    }
}

/** 設備狀態指示燈（帶脈衝動畫的圓點）。 */
@Composable
fun StatusIndicatorDot(status: DeviceStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        DeviceStatus.ONLINE  -> SuccessGreen
        DeviceStatus.OFFLINE -> TextSecondary
        DeviceStatus.ERROR   -> ErrorRed
    }

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dot_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation  = tween(800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "dot_alpha",
    )

    Box(
        modifier = modifier.size(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 外圈（脈衝，僅 ONLINE 狀態顯示）
        if (status == DeviceStatus.ONLINE) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawCircle(color = color.copy(alpha = pulseAlpha * 0.4f), radius = size.minDimension / 2)
            }
        }
        // 實心圓點
        androidx.compose.foundation.Canvas(Modifier.size(6.dp)) {
            drawCircle(color = color)
        }
    }
}

private fun formatLastSeen(epochMs: Long): String {
    if (epochMs == 0L) return "從未上線"
    val diff = System.currentTimeMillis() - epochMs
    return when {
        diff < 60_000         -> "剛剛"
        diff < 3_600_000      -> "${diff / 60_000} 分鐘前"
        diff < 86_400_000     -> "${diff / 3_600_000} 小時前"
        else                  -> SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun DeviceCardOnlinePreview() {
    MaterialTheme {
        DeviceCard(
            device = Device(
                id = "1", name = "客廳主燈", type = DeviceType.LIGHT,
                roomId = "living", status = DeviceStatus.ONLINE,
                isOn = true, lastSeen = System.currentTimeMillis() - 30_000,
            ),
            onToggle = {}, onViewDetail = {}, onViewLogs = {}, onLock = {},
            modifier = Modifier.width(160.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun DeviceCardOfflinePreview() {
    MaterialTheme {
        DeviceCard(
            device = Device(
                id = "2", name = "大門門鎖", type = DeviceType.DOOR_LOCK,
                roomId = "entrance", status = DeviceStatus.OFFLINE,
                isLocked = true, lastSeen = System.currentTimeMillis() - 7_200_000,
            ),
            onToggle = {}, onViewDetail = {}, onViewLogs = {}, onLock = {},
            modifier = Modifier.width(160.dp),
        )
    }
}
