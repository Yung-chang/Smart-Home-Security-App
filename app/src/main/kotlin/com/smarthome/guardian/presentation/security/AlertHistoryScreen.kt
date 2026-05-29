package com.smarthome.guardian.presentation.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarthome.guardian.domain.model.AlertType
import com.smarthome.guardian.domain.model.SecurityAlert
import com.smarthome.guardian.domain.model.Severity
import com.smarthome.guardian.presentation.dashboard.components.SeverityBadge
import java.text.SimpleDateFormat
import java.util.*

private val Background    = Color(0xFF0A0E1A)
private val SurfaceCard   = Color(0xFF121827)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)
private val ErrorRed      = Color(0xFFFF4444)

/**
 * 警報歷史頁面。
 *
 * 功能：
 * - 嚴重程度 / 類型篩選 Chip
 * - 只顯示未確認 Toggle
 * - 按日期分組的時間軸列表
 * - 點擊展開警報詳情
 * - 長按進入批次選取模式
 * - 批次確認 FAB
 * - 匯出 CSV / PDF
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryScreen(
    onNavigateUp: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val state         by viewModel.historyState.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost   = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSelectionMode) {
                        Text("已選取 ${state.selectedIds.size} 筆", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    } else {
                        Text("警報歷史", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (state.isSelectionMode) viewModel::clearSelection else onNavigateUp) {
                        Icon(
                            if (state.isSelectionMode) Icons.Filled.Close else Icons.Filled.ArrowBack,
                            null, tint = Color.White,
                        )
                    }
                },
                actions = {
                    // 匯出按鈕
                    if (!state.isSelectionMode) {
                        IconButton(onClick = { viewModel.exportAlerts("CSV") }) {
                            Icon(Icons.Filled.Download, "匯出 CSV", tint = PrimaryBlue)
                        }
                        IconButton(onClick = { viewModel.exportAlerts("PDF") }) {
                            Icon(Icons.Filled.PictureAsPdf, "匯出 PDF", tint = PrimaryBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1321)),
            )
        },
        floatingActionButton = {
            if (state.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick          = { viewModel.bulkAcknowledge(state.selectedIds.toList()) },
                    containerColor   = PrimaryBlue,
                    contentColor     = Color.Black,
                    icon             = { Icon(Icons.Filled.CheckCircle, null) },
                    text             = { Text("確認 ${state.selectedIds.size} 筆", fontWeight = FontWeight.Bold) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(padding),
        ) {
            // ── 篩選區 ────────────────────────────────────────────────────────
            FilterBar(
                filter   = state.filter,
                onUpdate = viewModel::updateFilter,
                onClear  = viewModel::clearFilter,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else if (state.groupedAlerts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("沒有符合條件的警報", color = TextSecondary)
                    }
                }
            } else {
                // ── 警報列表（按日期分組）──────────────────────────────────────
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.groupedAlerts.forEach { (date, alerts) ->
                        item(key = "date_$date") {
                            Text(
                                text     = date,
                                color    = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        items(alerts, key = { it.id }) { alert ->
                            AlertHistoryItem(
                                alert        = alert,
                                isSelected   = alert.id in state.selectedIds,
                                isSelectMode = state.isSelectionMode,
                                onToggleSelect = { viewModel.toggleAlertSelection(alert.id) },
                                onAcknowledge  = { viewModel.acknowledgeAlert(alert.id) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ── 篩選列 ─────────────────────────────────────────────────────────────────────

@Composable
private fun FilterBar(
    filter: AlertFilter,
    onUpdate: (AlertFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 嚴重程度 Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = filter.onlyUnread,
                    onClick  = { onUpdate(filter.copy(onlyUnread = !filter.onlyUnread)) },
                    label    = { Text("未確認") },
                    colors   = filterChipColors(),
                )
            }
            items(Severity.values()) { severity ->
                val selected = severity in filter.severities
                FilterChip(
                    selected = selected,
                    onClick  = {
                        val newSet = if (selected) filter.severities - severity else filter.severities + severity
                        onUpdate(filter.copy(severities = newSet))
                    },
                    label  = { Text(severity.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = severity.color.copy(alpha = 0.2f),
                        selectedLabelColor     = severity.color,
                        containerColor         = SurfaceCard,
                        labelColor             = TextSecondary,
                    ),
                )
            }
            if (filter.isActive) {
                item {
                    AssistChip(
                        onClick = onClear,
                        label   = { Text("清除", color = ErrorRed) },
                        leadingIcon = { Icon(Icons.Filled.Close, null, tint = ErrorRed, modifier = Modifier.size(14.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = PrimaryBlue.copy(alpha = 0.2f),
    selectedLabelColor     = PrimaryBlue,
    containerColor         = SurfaceCard,
    labelColor             = TextSecondary,
)

// ── 警報歷史項目（可展開）─────────────────────────────────────────────────────

@Composable
private fun AlertHistoryItem(
    alert: SecurityAlert,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onToggleSelect: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alert.timestamp))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { if (isSelectMode) onToggleSelect() else expanded = !expanded }
            .animateContentSize(),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else SurfaceCard,
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)) else null,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 選取 Checkbox
                if (isSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue),
                        modifier = Modifier.size(20.dp),
                    )
                }
                SeverityBadge(alert.severity)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = alert.type.displayName,
                        color      = if (alert.isAcknowledged) TextSecondary else Color.White,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text     = alert.message,
                        color    = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                    )
                }
                Text(timeStr, color = TextSecondary, fontSize = 11.sp)
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }

            // 展開詳情
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))
                    alert.deviceId?.let {
                        DetailRow("設備 ID", it)
                    }
                    DetailRow("狀態", if (alert.isAcknowledged) "已確認" else "未確認")
                    alert.actionTaken?.let { DetailRow("系統處理", it) }
                    Spacer(Modifier.height(8.dp))
                    if (!alert.isAcknowledged && !isSelectMode) {
                        Button(
                            onClick  = onAcknowledge,
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            shape    = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("確認此警報", fontSize = 13.sp, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label：", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}
