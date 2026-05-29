package com.smarthome.guardian.presentation.access.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.*
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

private val SurfaceCard   = Color(0xFF1A2235)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)

/**
 * 四步規則新增精靈。
 *
 * Step 1：選擇目標設備（或全部）
 * Step 2：選擇目標使用者（或全部）
 * Step 3：設定時間窗口
 * Step 4：選擇允許操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleWizard(
    users: List<User>,
    devices: List<Device> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (AccessRule) -> Unit,
) {
    // 精靈狀態
    var step        by remember { mutableIntStateOf(1) }
    var deviceId    by remember { mutableStateOf("*") }
    var userId      by remember { mutableStateOf("*") }
    var selectedOps by remember { mutableStateOf(setOf<DeviceOperation>()) }
    var useTimeWindow by remember { mutableStateOf(false) }
    var selectedDays by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var startHour   by remember { mutableIntStateOf(8) }
    var endHour     by remember { mutableIntStateOf(22) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF121827),
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 標題 + 步驟指示 ───────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (step) {
                        1 -> "Step 1：選擇設備"
                        2 -> "Step 2：選擇使用者"
                        3 -> "Step 3：時間限制"
                        4 -> "Step 4：允許操作"
                        else -> "完成"
                    },
                    color      = Color.White,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("$step / 4", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            LinearProgressIndicator(
                progress         = { step / 4f },
                modifier         = Modifier.fillMaxWidth(),
                color            = PrimaryBlue,
                trackColor       = PrimaryBlue.copy(alpha = 0.2f),
            )

            // ── 步驟內容 ──────────────────────────────────────────────────────
            when (step) {
                1 -> Step1DeviceSelect(selectedDeviceId = deviceId, devices = devices, onSelect = { deviceId = it })
                2 -> Step2UserSelect(users = users, selectedUserId = userId, onSelect = { userId = it })
                3 -> Step3TimeWindow(
                    useTimeWindow  = useTimeWindow,
                    selectedDays   = selectedDays,
                    startHour      = startHour,
                    endHour        = endHour,
                    onToggle       = { useTimeWindow = it },
                    onDaysChange   = { selectedDays = it },
                    onStartChange  = { startHour = it },
                    onEndChange    = { endHour = it },
                )
                4 -> Step4Operations(selected = selectedOps, onSelect = { selectedOps = it })
            }

            // ── 導航按鈕 ──────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 1) {
                    OutlinedButton(
                        onClick  = { step-- },
                        modifier = Modifier.weight(1f),
                    ) { Text("上一步", color = TextSecondary) }
                } else {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消", color = TextSecondary)
                    }
                }
                Button(
                    onClick = {
                        if (step < 4) {
                            step++
                        } else {
                            val timeWindow = if (useTimeWindow && selectedDays.isNotEmpty()) {
                                TimeWindow(
                                    daysOfWeek = selectedDays,
                                    startTime  = LocalTime.of(startHour, 0),
                                    endTime    = LocalTime.of(endHour, 0),
                                    timezone   = ZoneId.systemDefault(),
                                )
                            } else null

                            onConfirm(
                                AccessRule(
                                    id                = UUID.randomUUID().toString(),
                                    userId            = userId,
                                    deviceId          = deviceId,
                                    allowedOperations = selectedOps,
                                    timeWindow        = timeWindow,
                                    isEnabled         = true,
                                )
                            )
                        }
                    },
                    enabled  = if (step == 4) selectedOps.isNotEmpty() else true,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                ) {
                    Text(if (step < 4) "下一步" else "建立規則", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Step 1：選擇設備 ──────────────────────────────────────────────────────────

@Composable
private fun Step1DeviceSelect(
    selectedDeviceId: String,
    devices: List<Device>,
    onSelect: (String) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            WizardOption(
                label    = "所有設備",
                subLabel = "規則套用至全部 ${devices.size} 台設備",
                selected = selectedDeviceId == "*",
                onClick  = { onSelect("*") },
            )
        }
        if (devices.isEmpty()) {
            item {
                Text(
                    text     = "尚未載入設備，請稍後再試",
                    color    = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(devices) { device ->
                WizardOption(
                    label    = device.name,
                    subLabel = "${device.type.displayName}・${device.status.name.lowercase()}",
                    selected = selectedDeviceId == device.id,
                    onClick  = { onSelect(device.id) },
                )
            }
        }
    }
}

// ── Step 2：選擇使用者 ────────────────────────────────────────────────────────

@Composable
private fun Step2UserSelect(
    users: List<User>,
    selectedUserId: String,
    onSelect: (String) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            WizardOption(label = "所有使用者", subLabel = "適用所有成員", selected = selectedUserId == "*") { onSelect("*") }
        }
        items(users) { user ->
            WizardOption(label = user.name, subLabel = user.email, selected = selectedUserId == user.id) {
                onSelect(user.id)
            }
        }
    }
}

// ── Step 3：時間窗口 ──────────────────────────────────────────────────────────

@Composable
private fun Step3TimeWindow(
    useTimeWindow: Boolean,
    selectedDays: Set<DayOfWeek>,
    startHour: Int,
    endHour: Int,
    onToggle: (Boolean) -> Unit,
    onDaysChange: (Set<DayOfWeek>) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text("限制時間窗口", color = Color.White, fontSize = 14.sp)
            Switch(
                checked         = useTimeWindow,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue, checkedTrackColor = PrimaryBlue.copy(alpha = 0.3f)),
            )
        }

        if (useTimeWindow) {
            Text("星期幾", color = TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                    val day = DayOfWeek.of(index + 1)
                    FilterChip(
                        selected = day in selectedDays,
                        onClick  = {
                            onDaysChange(if (day in selectedDays) selectedDays - day else selectedDays + day)
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue.copy(alpha = 0.2f),
                            selectedLabelColor     = PrimaryBlue,
                            containerColor         = Color(0xFF0A0E1A),
                            labelColor             = TextSecondary,
                        ),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("開始時間：${startHour}:00", color = TextSecondary, fontSize = 12.sp)
                    Slider(value = startHour.toFloat(), onValueChange = { onStartChange(it.toInt()) },
                        valueRange = 0f..23f, steps = 22,
                        colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("結束時間：${endHour}:00", color = TextSecondary, fontSize = 12.sp)
                    Slider(value = endHour.toFloat(), onValueChange = { onEndChange(it.toInt()) },
                        valueRange = 0f..23f, steps = 22,
                        colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue))
                }
            }
        }
    }
}

// ── Step 4：選擇操作 ──────────────────────────────────────────────────────────

@Composable
private fun Step4Operations(
    selected: Set<DeviceOperation>,
    onSelect: (Set<DeviceOperation>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("至少選擇一項操作", color = TextSecondary, fontSize = 13.sp)
        DeviceOperation.values().forEach { op ->
            val isSelected = op in selected
            WizardOption(
                label    = op.name.lowercase().replaceFirstChar { it.uppercase() },
                subLabel = "",
                selected = isSelected,
                onClick  = { onSelect(if (isSelected) selected - op else selected + op) },
            )
        }
    }
}

// ── 共用選項元件 ──────────────────────────────────────────────────────────────

@Composable
private fun WizardOption(
    label: String,
    subLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = if (selected) PrimaryBlue.copy(alpha = 0.1f) else SurfaceCard,
        shape    = RoundedCornerShape(10.dp),
        border   = if (selected) androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)) else null,
        onClick  = onClick,
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick  = onClick,
                colors   = RadioButtonDefaults.colors(selectedColor = PrimaryBlue),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, color = if (selected) PrimaryBlue else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (subLabel.isNotBlank()) Text(subLabel, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
