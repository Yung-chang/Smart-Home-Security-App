package com.smarthome.guardian.presentation.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarthome.guardian.domain.model.DeviceType
import com.smarthome.guardian.presentation.devices.panels.*
import com.smarthome.guardian.presentation.devices.panels.AlarmPanel
import com.smarthome.guardian.presentation.devices.panels.ThermostatPanel

private val Background  = Color(0xFF0A0E1A)
private val PrimaryBlue = Color(0xFF00D4FF)

/**
 * 設備詳情畫面（等動收路器）。
 *
 * 根據 [Device.type] 分派至對應的控制面板：
 * - [DeviceType.DOOR_LOCK]    → [DoorLockPanel]
 * - [DeviceType.LIGHT]        → [LightPanel]
 * - [DeviceType.CAMERA]       → [CameraPanel]
 * - [DeviceType.SENSOR_MOTION]、[DeviceType.SENSOR_DOOR] → [SensorPanel]
 * - 其他                      → 通用操作面板
 *
 * @param deviceId    目標設備 ID（由 Navigation 傳入）
 * @param onNavigateUp 返回上一頁
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onNavigateUp: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel(),
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }

    // 錯誤 / 成功訊息
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(uiState.commandSuccess) {
        uiState.commandSuccess?.let { snackbarState.showSnackbar("指令已送出"); viewModel.clearSuccess() }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost   = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = uiState.device?.name ?: "設備詳情",
                        color      = Color.White,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1321),
                ),
                actions = {
                    uiState.device?.type?.let { type ->
                        Text(
                            text     = type.displayName,
                            color    = PrimaryBlue,
                            fontSize = 12.sp,
                            modifier = androidx.compose.ui.Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        color    = PrimaryBlue,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                    )
                }
                uiState.device == null -> {
                    Text(
                        "找不到設備資料",
                        color    = Color(0xFF8899AA),
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                    )
                }
                else -> {
                    DevicePanel(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}

/** 依設備類型分派至對應面板。 */
@Composable
private fun DevicePanel(
    uiState: DeviceUiState,
    viewModel: DeviceViewModel,
) {
    val device = uiState.device ?: return

    when (device.type) {
        DeviceType.DOOR_LOCK -> DoorLockPanel(uiState = uiState, viewModel = viewModel)
        DeviceType.LIGHT,
        DeviceType.OUTLET    -> LightPanel(uiState = uiState, viewModel = viewModel)
        DeviceType.CAMERA    -> CameraPanel(
            uiState  = uiState,
            viewModel = viewModel,
            rtspUrl  = "rtsp://stream.smarthome.local/camera/${device.id}",
        )
        DeviceType.SENSOR_MOTION,
        DeviceType.SENSOR_DOOR -> SensorPanel(uiState = uiState, viewModel = viewModel)
        DeviceType.THERMOSTAT  -> ThermostatPanel(uiState = uiState, viewModel = viewModel)
        DeviceType.ALARM       -> AlarmPanel(uiState = uiState, viewModel = viewModel)
        else -> GenericDevicePanel(device = device, viewModel = viewModel)
    }
}

/** 通用設備面板（溫控器、警報器等尚未有專屬面板的設備）。 */
@Composable
private fun GenericDevicePanel(
    device: com.smarthome.guardian.domain.model.Device,
    viewModel: DeviceViewModel,
) {
    Column(
        modifier            = androidx.compose.ui.Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector        = device.type.icon,
            contentDescription = null,
            tint               = PrimaryBlue,
            modifier           = androidx.compose.ui.Modifier.size(64.dp),
        )
        Text(device.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(device.type.displayName, color = Color(0xFF8899AA), fontSize = 14.sp)
        Spacer(androidx.compose.ui.Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.sendCommand(
                    com.smarthome.guardian.domain.model.DeviceCommand(
                        deviceId = device.id,
                        type     = if (device.isOn)
                            com.smarthome.guardian.domain.model.CommandType.TOGGLE_OFF
                        else
                            com.smarthome.guardian.domain.model.CommandType.TOGGLE_ON,
                    )
                )
            },
            colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
        ) {
            Text(if (device.isOn) "關閉" else "開啟", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
