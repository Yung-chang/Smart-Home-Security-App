package com.smarthome.guardian.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarthome.guardian.domain.model.*
import com.smarthome.guardian.presentation.dashboard.components.*
import java.text.SimpleDateFormat
import java.util.*

private val Background    = Color(0xFF0A0E1A)
private val SurfaceCard   = Color(0xFF121827)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)

/**
 * 主儀表板畫面。
 *
 * @param onNavigateToSecurity  導航至安全監控
 * @param onNavigateToDevice    導航至設備詳情（傳入 deviceId）
 * @param onNavigateToSettings  導航至設定
 * @param onAddDevice           新增設備 FAB 回呼
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSecurity: () -> Unit,
    onNavigateToDevice: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAuditLog: () -> Unit = {},
    onAddDevice: () -> Unit,
    onLogout: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog    by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("確認登出") },
            text  = { Text("登出後需要重新驗證身份才能使用 APP。") },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("登出", color = Color(0xFFFF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showAddDeviceDialog) {
        AddDeviceDialog(
            onDismiss = { showAddDeviceDialog = false },
            onConfirm = { name, type, roomId ->
                viewModel.addDevice(name, type, roomId)
                showAddDeviceDialog = false
            },
        )
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            DashboardTopBar(
                user             = uiState.currentUser,
                unreadAlertCount = uiState.unreadAlertCount,
                onNotifications  = onNavigateToSecurity,
                onSettings       = onNavigateToSettings,
                onAuditLog       = onNavigateToAuditLog,
                onLogout         = { showLogoutDialog = true },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick          = { showAddDeviceDialog = true },
                containerColor   = PrimaryBlue,
                contentColor     = Color.Black,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增設備")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh    = viewModel::refreshDevices,
            modifier     = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(Background, Color(0xFF0D1321)))),
        ) {
            if (uiState.isLoading) {
                DashboardSkeleton()
            } else {
                DashboardContent(
                    uiState             = uiState,
                    onSecurityBannerClick = onNavigateToSecurity,
                    onRoomSelect        = viewModel::selectRoom,
                    onDeviceToggle      = { id, state -> viewModel.toggleDevice(id, state) },
                    onDeviceClick       = onNavigateToDevice,
                    onAlertClick        = { onNavigateToSecurity() },
                )
            }
        }
    }
}

// ── TopAppBar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    user: User?,
    unreadAlertCount: Int,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    onAuditLog: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text       = "SmartHome Guardian",
                    color      = PrimaryBlue,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                user?.let {
                    Text(
                        text     = "歡迎回來，${it.name}",
                        color    = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        actions = {
            // 通知鈴鐺 + Badge
            Box {
                IconButton(onClick = onNotifications) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "通知",
                        tint = Color.White,
                    )
                }
                if (unreadAlertCount > 0) {
                    NotificationBadge(
                        count    = unreadAlertCount,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = 4.dp),
                    )
                }
            }
            // 設定
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "設定", tint = Color.White)
            }
            // 用戶頭像（點擊展開個人資訊）
            user?.let { u ->
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.padding(end = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.2f))
                            .clickable { expanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = u.name.take(1).uppercase(),
                            color      = PrimaryBlue,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    DropdownMenu(
                        expanded         = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        // 姓名
                        DropdownMenuItem(
                            text    = {
                                Column {
                                    Text(u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(u.email, color = TextSecondary, fontSize = 12.sp)
                                }
                            },
                            onClick = {},
                            enabled = false,
                        )
                        // 角色
                        DropdownMenuItem(
                            text    = {
                                Text(
                                    text      = "角色：${u.role.name}",
                                    color     = PrimaryBlue,
                                    fontSize  = 12.sp,
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                        HorizontalDivider()
                        // 稽核日誌
                        DropdownMenuItem(
                            text    = { Text("稽核日誌") },
                            onClick = { expanded = false; onAuditLog() },
                            leadingIcon = {
                                Icon(Icons.Filled.Shield, contentDescription = null, tint = PrimaryBlue)
                            },
                        )
                        HorizontalDivider()
                        // 登出
                        DropdownMenuItem(
                            text    = { Text("登出", color = Color(0xFFFF4444)) },
                            onClick = { expanded = false; onLogout() },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.ExitToApp,
                                    contentDescription = null,
                                    tint = Color(0xFFFF4444),
                                )
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = Color(0xFF0D1321),
            scrolledContainerColor = Color(0xFF0D1321),
        ),
    )
}

// ── 主內容 ────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onSecurityBannerClick: () -> Unit,
    onRoomSelect: (Room) -> Unit,
    onDeviceToggle: (String, Boolean) -> Unit,
    onDeviceClick: (String) -> Unit,
    onAlertClick: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── 安全狀態橫幅 ──────────────────────────────────────────────────────
        item {
            SecurityStatusBanner(
                securityLevel    = uiState.securityLevel,
                unreadAlertCount = uiState.unreadAlertCount,
                onClick          = onSecurityBannerClick,
            )
        }

        // ── 房間選擇器 ────────────────────────────────────────────────────────
        item {
            RoomSelector(
                rooms        = uiState.rooms,
                selectedRoom = uiState.selectedRoom,
                onSelect     = onRoomSelect,
            )
        }

        // ── 設備卡片 Grid 標題 ─────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "設備",
                    color      = Color.White,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text     = "${uiState.devices.size} 台",
                    color    = TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        // ── 設備卡片 Grid（2 欄）─────────────────────────────────────────────
        if (uiState.devices.isEmpty()) {
            item {
                EmptyDevicesPlaceholder()
            }
        } else {
            // LazyColumn 內嵌 Grid：使用分頁方式每 2 個一組
            items(uiState.devices.chunked(2)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { device ->
                        DeviceCard(
                            device      = device,
                            onToggle    = { state -> onDeviceToggle(device.id, state) },
                            onViewDetail = { onDeviceClick(device.id) },
                            onViewLogs  = { onDeviceClick(device.id) },
                            onLock      = { /* PROMPT 07 實作 */ },
                            modifier    = Modifier.weight(1f),
                        )
                    }
                    // 奇數設備時補空白
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ── 最近警報（最新 3 筆）────────────────────────────────────────────
        if (uiState.recentAlerts.isNotEmpty()) {
            item {
                Text(
                    text       = "最近警報",
                    color      = Color.White,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(uiState.recentAlerts) { alert ->
                RecentAlertItem(alert = alert, onClick = { onAlertClick(alert.id) })
            }
        }

        item { Spacer(Modifier.height(80.dp)) } // FAB 遮擋緩衝
    }
}

// ── 子元件 ────────────────────────────────────────────────────────────────────

@Composable
private fun RoomSelector(
    rooms: List<Room>,
    selectedRoom: Room,
    onSelect: (Room) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rooms) { room ->
            FilterChip(
                selected = room.id == selectedRoom.id,
                onClick  = { onSelect(room) },
                label    = { Text(room.name) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor  = PrimaryBlue.copy(alpha = 0.2f),
                    selectedLabelColor      = PrimaryBlue,
                    containerColor          = SurfaceCard,
                    labelColor              = TextSecondary,
                ),
            )
        }
    }
}

@Composable
private fun RecentAlertItem(
    alert: SecurityAlert,
    onClick: () -> Unit,
) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alert.timestamp))

    Surface(
        modifier      = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color         = SurfaceCard,
        onClick       = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SeverityBadge(alert.severity)
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.type.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(alert.message, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
            Text(timeStr, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyDevicesPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.DevicesOther, null, tint = TextSecondary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text("尚無設備，點擊 + 新增", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DashboardSkeleton() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(5) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (it == 0) 70.dp else 48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color    = SurfaceCard.copy(alpha = 0.5f),
            ) {}
        }
    }
}

// ── 新增設備 Dialog ──────────────────────────────────────────────────────────

@Composable
private fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: DeviceType, roomId: String) -> Unit,
) {
    var name        by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DeviceType.LIGHT) }
    var selectedRoom by remember { mutableStateOf(Room.defaults[1]) } // 預設客廳
    var typeExpanded by remember { mutableStateOf(false) }
    var roomExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增設備") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 設備名稱
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("設備名稱") },
                    placeholder   = { Text("例如：客廳燈光") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )

                // 設備類型
                ExposedDropdownMenuBox(
                    expanded         = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value            = selectedType.displayName,
                        onValueChange    = {},
                        readOnly         = true,
                        label            = { Text("設備類型") },
                        leadingIcon      = { Icon(selectedType.icon, null, modifier = Modifier.size(20.dp)) },
                        trailingIcon     = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier         = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded         = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        DeviceType.entries.forEach { type ->
                            DropdownMenuItem(
                                text         = { Text(type.displayName) },
                                leadingIcon  = { Icon(type.icon, null, modifier = Modifier.size(18.dp)) },
                                onClick      = { selectedType = type; typeExpanded = false },
                            )
                        }
                    }
                }

                // 房間
                ExposedDropdownMenuBox(
                    expanded         = roomExpanded,
                    onExpandedChange = { roomExpanded = it },
                ) {
                    OutlinedTextField(
                        value            = selectedRoom.name,
                        onValueChange    = {},
                        readOnly         = true,
                        label            = { Text("所在房間") },
                        trailingIcon     = { ExposedDropdownMenuDefaults.TrailingIcon(roomExpanded) },
                        modifier         = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded         = roomExpanded,
                        onDismissRequest = { roomExpanded = false },
                    ) {
                        Room.defaults.filter { it.id != "all" }.forEach { room ->
                            DropdownMenuItem(
                                text    = { Text(room.name) },
                                onClick = { selectedRoom = room; roomExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (name.isNotBlank()) onConfirm(name, selectedType, selectedRoom.id) },
                enabled  = name.isNotBlank(),
            ) { Text("新增") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A, name = "Dashboard Preview")
@Composable
private fun DashboardPreview() {
    val sampleDevices = listOf(
        Device("1", "客廳主燈", DeviceType.LIGHT, "living", DeviceStatus.ONLINE, isOn = true, lastSeen = System.currentTimeMillis()),
        Device("2", "大門門鎖", DeviceType.DOOR_LOCK, "entrance", DeviceStatus.ONLINE, isOn = false, lastSeen = System.currentTimeMillis() - 60000),
        Device("3", "走廊攝影機", DeviceType.CAMERA, "entrance", DeviceStatus.OFFLINE, lastSeen = System.currentTimeMillis() - 3600000),
        Device("4", "臥室感應器", DeviceType.SENSOR_MOTION, "bedroom", DeviceStatus.ONLINE, lastSeen = System.currentTimeMillis() - 5000),
    )
    val sampleAlerts = listOf(
        SecurityAlert("a1", AlertType.DEVICE_OFFLINE, Severity.MEDIUM, "3", "走廊攝影機已離線", System.currentTimeMillis() - 3600000),
        SecurityAlert("a2", AlertType.INTRUSION, Severity.HIGH, null, "偵測到非授權時段的設備操作", System.currentTimeMillis() - 7200000),
    )
    MaterialTheme {
        DashboardContent(
            uiState = DashboardUiState(
                devices          = sampleDevices,
                securityLevel    = SecurityLevel.WARNING,
                recentAlerts     = sampleAlerts,
                unreadAlertCount = 2,
                isLoading        = false,
                currentUser      = User("u1", "test@example.com", "Alice", UserRole.ADMIN),
            ),
            onSecurityBannerClick = {},
            onRoomSelect          = {},
            onDeviceToggle        = { _, _ -> },
            onDeviceClick         = {},
            onAlertClick          = {},
        )
    }
}
