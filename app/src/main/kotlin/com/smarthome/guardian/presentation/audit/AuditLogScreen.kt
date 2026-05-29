package com.smarthome.guardian.presentation.audit

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.smarthome.guardian.domain.model.AuditAction
import com.smarthome.guardian.domain.model.AuditCategory
import com.smarthome.guardian.domain.model.AuditFilter
import com.smarthome.guardian.domain.model.AuditLog
import com.smarthome.guardian.domain.model.ExportFormat
import com.smarthome.guardian.domain.model.IntegrityResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 設計常數 ──────────────────────────────────────────────────────────────────

private val ColorBackground = Color(0xFF0A0E1A)
private val ColorSurface    = Color(0xFF121827)
private val ColorSurface2   = Color(0xFF1A2235)
private val ColorCyan       = Color(0xFF00D4FF)
private val ColorGreen      = Color(0xFF00C853)
private val ColorRed        = Color(0xFFFF4444)
private val ColorOrange     = Color(0xFFFF6D00)
private val ColorPurple     = Color(0xFF7B2FBE)
private val ColorBlue       = Color(0xFF2196F3)
private val ColorGray       = Color(0xFF8899AA)
private val ColorTextPrimary= Color(0xFFFFFFFF)
private val ColorTextSecond = Color(0xFF8899AA)

// ── 主畫面 ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuditViewModel = hiltViewModel(),
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyItems      = uiState.logs.collectAsLazyPagingItems()
    val snackbarHost   = remember { SnackbarHostState() }
    val context        = LocalContext.current

    // 匯出完成 → 觸發系統分享
    LaunchedEffect(uiState.exportUri) {
        uiState.exportUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享稽核日誌"))
            viewModel.clearExportUri()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            AuditTopBar(
                filterActive   = uiState.filter.isActive,
                searchVisible  = uiState.searchBarVisible,
                isExporting    = uiState.isExporting,
                onNavigateBack = onNavigateBack,
                onToggleSearch = viewModel::toggleSearchBar,
                onToggleFilter = viewModel::toggleFilterSheet,
                onExport       = viewModel::exportLogs,
            )
        },
        snackbarHost  = { SnackbarHost(snackbarHost) },
        containerColor = ColorBackground,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            // 搜尋列（可收合）
            AnimatedVisibility(
                visible = uiState.searchBarVisible,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                AuditSearchBar(
                    query          = uiState.filter.searchQuery,
                    onQueryChange  = viewModel::updateSearchQuery,
                    modifier       = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // 已啟用的篩選條件 Chips
            if (uiState.filter.isActive) {
                ActiveFilterRow(
                    filter  = uiState.filter,
                    onClear = viewModel::clearFilter,
                )
            }

            // 匯出進度
            if (uiState.isExporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color    = ColorCyan,
                    trackColor = ColorSurface,
                )
            }

            // 日誌列表
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier       = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = lazyItems.itemCount,
                        key   = lazyItems.itemKey { it.id },
                    ) { index ->
                        val log     = lazyItems[index] ?: return@items
                        val prevLog = if (index > 0) lazyItems[index - 1] else null

                        // 日期分組標題：日期變化時插入
                        val currDateLabel = formatDateHeader(log.timestamp)
                        val prevDateLabel = prevLog?.let { formatDateHeader(it.timestamp) } ?: ""
                        if (currDateLabel != prevDateLabel) {
                            AuditDateHeader(dateText = currDateLabel)
                        }

                        AuditLogItem(
                            log               = log,
                            isExpanded        = log.id in uiState.expandedLogIds,
                            integrityResult   = uiState.integrityResults[log.id],
                            onToggleExpand    = { viewModel.toggleExpand(log.id) },
                            onVerifyIntegrity = { viewModel.verifyIntegrity(log.id) },
                        )
                    }

                    // 分頁附加載入狀態
                    when (val state = lazyItems.loadState.append) {
                        is LoadState.Loading -> item {
                            Box(
                                modifier         = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color    = ColorCyan,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                        is LoadState.Error -> item {
                            Text(
                                text     = "載入失敗：${state.error.message}",
                                color    = ColorRed,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        else -> Unit
                    }
                }

                // 初始載入中
                if (lazyItems.loadState.refresh is LoadState.Loading) {
                    CircularProgressIndicator(
                        color    = ColorCyan,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // 空狀態
                if (lazyItems.loadState.refresh is LoadState.NotLoading &&
                    lazyItems.itemCount == 0) {
                    EmptyAuditState(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    // 篩選 BottomSheet
    if (uiState.showFilterSheet) {
        AuditFilterSheet(
            filter    = uiState.filter,
            onApply   = viewModel::applyFilter,
            onDismiss = viewModel::toggleFilterSheet,
        )
    }
}

// ── TopAppBar ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditTopBar(
    filterActive: Boolean,
    searchVisible: Boolean,
    isExporting: Boolean,
    onNavigateBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleFilter: () -> Unit,
    onExport: (ExportFormat) -> Unit,
) {
    var showExportMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text       = "稽核日誌",
                color      = ColorTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = ColorTextPrimary)
            }
        },
        actions = {
            // 搜尋切換
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (searchVisible) Icons.Filled.Clear else Icons.Filled.Search,
                    contentDescription = if (searchVisible) "關閉搜尋" else "搜尋",
                    tint = if (searchVisible) ColorCyan else ColorTextSecond,
                )
            }

            // 篩選按鈕（有篩選條件時顯示 Badge）
            BadgedBox(
                badge = {
                    if (filterActive) Badge(containerColor = ColorCyan)
                },
            ) {
                IconButton(onClick = onToggleFilter) {
                    Icon(
                        Icons.Filled.FilterList,
                        contentDescription = "篩選",
                        tint = if (filterActive) ColorCyan else ColorTextSecond,
                    )
                }
            }

            // 匯出 Dropdown
            Box {
                IconButton(
                    onClick  = { showExportMenu = true },
                    enabled  = !isExporting,
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "匯出",
                        tint = if (isExporting) ColorGray else ColorTextSecond,
                    )
                }
                DropdownMenu(
                    expanded        = showExportMenu,
                    onDismissRequest= { showExportMenu = false },
                    containerColor  = ColorSurface2,
                ) {
                    DropdownMenuItem(
                        text    = { Text("匯出 CSV", color = ColorTextPrimary) },
                        onClick = {
                            showExportMenu = false
                            onExport(ExportFormat.CSV)
                        },
                    )
                    DropdownMenuItem(
                        text    = { Text("匯出 PDF", color = ColorTextPrimary) },
                        onClick = {
                            showExportMenu = false
                            onExport(ExportFormat.PDF)
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorSurface),
    )
}

// ── 搜尋列 ────────────────────────────────────────────────────────────────────

@Composable
private fun AuditSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        placeholder   = { Text("搜尋用戶 ID / 動作 / 目標…", color = ColorGray, fontSize = 14.sp) },
        leadingIcon   = { Icon(Icons.Filled.Search, contentDescription = null, tint = ColorGray) },
        trailingIcon  = if (query.isNotBlank()) {
            { IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Filled.Clear, contentDescription = "清除", tint = ColorGray)
            } }
        } else null,
        singleLine    = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = ColorCyan,
            unfocusedBorderColor = ColorGray.copy(alpha = 0.4f),
            focusedTextColor     = ColorTextPrimary,
            unfocusedTextColor   = ColorTextPrimary,
            cursorColor          = ColorCyan,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

// ── 已啟用篩選條件 ────────────────────────────────────────────────────────────

@Composable
private fun ActiveFilterRow(
    filter: AuditFilter,
    onClear: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) {
        filter.action?.let { action ->
            item {
                FilterChip(
                    selected = true,
                    onClick  = {},
                    label    = { Text(action.displayName, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ColorCyan.copy(alpha = 0.2f),
                        selectedLabelColor     = ColorCyan,
                    ),
                )
            }
        }
        filter.categories.forEach { cat ->
            item {
                FilterChip(
                    selected = true,
                    onClick  = {},
                    label    = { Text(cat.name, fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ColorPurple.copy(alpha = 0.2f),
                        selectedLabelColor     = ColorPurple,
                    ),
                )
            }
        }
        if (filter.onlyTampered) {
            item {
                FilterChip(
                    selected = true,
                    onClick  = {},
                    label    = { Text("僅顯示竄改", fontSize = 12.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ColorRed.copy(alpha = 0.2f),
                        selectedLabelColor     = ColorRed,
                    ),
                )
            }
        }
        item {
            TextButton(onClick = onClear) {
                Text("清除篩選", color = ColorGray, fontSize = 12.sp)
            }
        }
    }
}

// ── 日期分組標題 ──────────────────────────────────────────────────────────────

@Composable
private fun AuditDateHeader(dateText: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = ColorGray.copy(alpha = 0.2f),
            thickness = 1.dp,
        )
        Text(
            text     = dateText,
            color    = ColorGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = ColorGray.copy(alpha = 0.2f),
            thickness = 1.dp,
        )
    }
}

// ── 日誌項目 ──────────────────────────────────────────────────────────────────

@Composable
private fun AuditLogItem(
    log: AuditLog,
    isExpanded: Boolean,
    integrityResult: IntegrityResult?,
    onToggleExpand: () -> Unit,
    onVerifyIntegrity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = log.action.category.toColor()
    val chevronAngle  by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")

    ElevatedCard(
        modifier  = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onToggleExpand),
        colors    = CardDefaults.elevatedCardColors(containerColor = ColorSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左側類別色條
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (isExpanded && (log.before != null || log.after != null)) 120.dp else 72.dp)
                    .background(categoryColor),
            )

            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                // 主資訊列
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 動作圖示
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector  = log.action.category.toIcon(),
                            contentDescription = null,
                            tint         = categoryColor,
                            modifier     = Modifier.size(18.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = log.action.displayName,
                            color      = ColorTextPrimary,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        Text(
                            text     = "用戶：${log.userId.take(12)}${if (log.userId.length > 12) "…" else ""}",
                            color    = ColorTextSecond,
                            fontSize = 11.sp,
                        )
                    }

                    // HMAC 完整性徽章
                    IntegrityBadge(
                        result     = integrityResult,
                        onTap      = onVerifyIntegrity,
                        modifier   = Modifier.padding(start = 6.dp),
                    )

                    // 展開箭頭
                    if (log.before != null || log.after != null) {
                        Icon(
                            imageVector        = Icons.Filled.ArrowDropDown,
                            contentDescription = if (isExpanded) "收合" else "展開",
                            tint               = ColorGray,
                            modifier           = Modifier
                                .size(20.dp)
                                .rotate(chevronAngle),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 次資訊列（目標 + 時間）
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = log.targetId?.let { "目標：$it" } ?: "（無目標）",
                        color    = ColorTextSecond,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text     = formatTime(log.timestamp),
                        color    = ColorGray,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        // 展開區：before / after JSON diff
        AnimatedVisibility(
            visible = isExpanded && (log.before != null || log.after != null),
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ColorSurface2)
                    .padding(12.dp),
            ) {
                HorizontalDivider(color = ColorGray.copy(alpha = 0.2f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                if (log.before != null) {
                    JsonDiffSection(label = "操作前", json = log.before, labelColor = ColorOrange)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (log.after != null) {
                    JsonDiffSection(label = "操作後", json = log.after, labelColor = ColorGreen)
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 裝置指紋摘要
                Text(
                    text     = "指紋：${log.deviceFingerprint.take(24)}…",
                    color    = ColorGray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ── HMAC 完整性徽章 ────────────────────────────────────────────────────────────

@Composable
private fun IntegrityBadge(
    result: IntegrityResult?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, color, label) = when (result) {
        is IntegrityResult.Valid        -> Triple(Icons.Filled.CheckCircle, ColorGreen, "有效")
        is IntegrityResult.Tampered     -> Triple(Icons.Filled.Error,       ColorRed,   "已竄改")
        is IntegrityResult.Unverifiable -> Triple(Icons.Filled.Help,        ColorGray,  "無法驗證")
        null                            -> Triple(Icons.Filled.Security,    ColorGray.copy(alpha = 0.5f), "驗證")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = color,
            modifier           = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

// ── JSON Diff 區塊 ────────────────────────────────────────────────────────────

@Composable
private fun JsonDiffSection(label: String, json: String, labelColor: Color) {
    Column {
        Text(
            text       = label,
            color      = labelColor,
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text       = json.take(300).let { if (json.length > 300) "$it…" else it },
            color      = ColorTextSecond,
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            modifier   = Modifier
                .fillMaxWidth()
                .background(ColorBackground.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(6.dp),
        )
    }
}

// ── 空狀態 ────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyAuditState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = modifier.padding(32.dp),
    ) {
        Icon(
            imageVector        = Icons.Filled.Security,
            contentDescription = null,
            tint               = ColorGray.copy(alpha = 0.4f),
            modifier           = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text     = "尚無稽核日誌",
            color    = ColorGray,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text     = "系統操作記錄會自動出現在此處",
            color    = ColorGray.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ── 篩選 BottomSheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AuditFilterSheet(
    filter: AuditFilter,
    onApply: (AuditFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 本地草稿狀態（按下「套用」才生效）
    var draft by remember { mutableStateOf(filter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = ColorSurface,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = ColorGray) },
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {

            Text(
                text       = "篩選稽核日誌",
                color      = ColorTextPrimary,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(vertical = 12.dp),
            )

            // ── 動作分類 ──────────────────────────────────────────────────────
            Text("動作分類", color = ColorGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuditCategory.entries.forEach { cat ->
                    val selected = cat in draft.categories
                    FilterChip(
                        selected = selected,
                        onClick  = {
                            val newCats = if (selected) draft.categories - cat else draft.categories + cat
                            draft = draft.copy(categories = newCats)
                        },
                        label  = { Text(cat.name, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cat.toColor().copy(alpha = 0.2f),
                            selectedLabelColor     = cat.toColor(),
                            labelColor             = ColorGray,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled          = true,
                            selected         = selected,
                            selectedBorderColor = cat.toColor().copy(alpha = 0.6f),
                            borderColor      = ColorGray.copy(alpha = 0.3f),
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = ColorGray.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── 時間範圍 (快速選擇) ────────────────────────────────────────────
            Text("時間範圍", color = ColorGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            val presets = listOf(
                "最近 24h" to (System.currentTimeMillis() - 86_400_000L),
                "最近 7天"  to (System.currentTimeMillis() - 7L * 86_400_000L),
                "最近 30天" to (System.currentTimeMillis() - 30L * 86_400_000L),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { (label, startMs) ->
                    val isSelected = draft.startMs == startMs && draft.endMs == null
                    FilterChip(
                        selected = isSelected,
                        onClick  = {
                            draft = if (isSelected)
                                draft.copy(startMs = null, endMs = null)
                            else
                                draft.copy(startMs = startMs, endMs = null)
                        },
                        label  = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorCyan.copy(alpha = 0.15f),
                            selectedLabelColor     = ColorCyan,
                            labelColor             = ColorGray,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = ColorGray.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            // ── 僅顯示疑似竄改 ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("僅顯示疑似竄改記錄", color = ColorTextPrimary, fontSize = 14.sp)
                    Text("HMAC 驗證失敗的日誌", color = ColorGray, fontSize = 11.sp)
                }
                Switch(
                    checked         = draft.onlyTampered,
                    onCheckedChange = { draft = draft.copy(onlyTampered = it) },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor       = ColorRed,
                        checkedTrackColor       = ColorRed.copy(alpha = 0.3f),
                        uncheckedThumbColor     = ColorGray,
                        uncheckedTrackColor     = ColorGray.copy(alpha = 0.2f),
                    ),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 操作按鈕 ───────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick  = {
                        draft = AuditFilter.NONE
                        onApply(AuditFilter.NONE)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("重設", color = ColorGray)
                }
                androidx.compose.material3.Button(
                    onClick  = { onApply(draft) },
                    modifier = Modifier.weight(2f),
                    colors   = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ColorCyan,
                        contentColor   = Color.Black,
                    ),
                ) {
                    Text("套用篩選", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── 工具函數 ──────────────────────────────────────────────────────────────────

private fun formatDateHeader(epochMs: Long): String =
    SimpleDateFormat("yyyy年 M月 d日 (EEE)", Locale.TRADITIONAL_CHINESE).format(Date(epochMs))

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))

private fun AuditCategory.toColor(): Color = when (this) {
    AuditCategory.AUTH     -> Color(0xFF00D4FF)
    AuditCategory.DEVICE   -> Color(0xFF2196F3)
    AuditCategory.ACCESS   -> Color(0xFFFF6D00)
    AuditCategory.USER     -> Color(0xFF7B2FBE)
    AuditCategory.SECURITY -> Color(0xFFFF4444)
    AuditCategory.SYSTEM   -> Color(0xFF8899AA)
}

private fun AuditCategory.toIcon(): ImageVector = when (this) {
    AuditCategory.AUTH     -> Icons.Filled.Lock
    AuditCategory.DEVICE   -> Icons.Filled.Smartphone
    AuditCategory.ACCESS   -> Icons.Filled.Key
    AuditCategory.USER     -> Icons.Filled.Person
    AuditCategory.SECURITY -> Icons.Filled.Warning
    AuditCategory.SYSTEM   -> Icons.Filled.Settings
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun AuditLogItemPreview() {
    val log = AuditLog(
        id                = "abc-123",
        userId            = "user_admin_01",
        action            = AuditAction.DEVICE_CONTROL,
        targetId          = "device_front_door",
        before            = """{"isLocked":true}""",
        after             = """{"isLocked":false}""",
        ipAddress         = "192.168.1.10",
        deviceFingerprint = "google/redfin/redfin:12/SQ3A.220605.009",
        timestamp         = System.currentTimeMillis() - 300_000,
        signature         = "dGVzdHNpZ25hdHVyZQ==",
    )
    AuditLogItem(
        log               = log,
        isExpanded        = true,
        integrityResult   = IntegrityResult.Valid,
        onToggleExpand    = {},
        onVerifyIntegrity = {},
    )
}
