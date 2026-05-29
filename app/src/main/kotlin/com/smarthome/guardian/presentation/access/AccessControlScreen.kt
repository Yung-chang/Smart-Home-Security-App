package com.smarthome.guardian.presentation.access

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarthome.guardian.presentation.access.components.*

private val Background  = Color(0xFF0A0E1A)
private val PrimaryBlue = Color(0xFF00D4FF)

/**
 * 存取控制管理主畫面（TabRow 三分頁）。
 *
 * - Tab 0「用戶管理」：用戶列表 + 邀請 FAB
 * - Tab 1「存取規則」：規則列表 + 新增精靈 + 衝突警告
 * - Tab 2「臨時存取」：QR Code 生成 + 已生成列表
 *
 * @param onNavigateUp 返回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessControlScreen(
    onNavigateUp: () -> Unit,
    viewModel: UserManagementViewModel = hiltViewModel(),
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { snackbarState.showSnackbar(it); viewModel.clearSuccess() }
    }

    // BottomSheet 顯示選取的使用者詳情
    uiState.selectedUser?.let { user ->
        UserDetailSheet(
            user           = user,
            onDismiss      = { viewModel.selectUser(null) },
            onUpdateRole   = { role -> viewModel.updateUserRole(user.id, role) },
            onRevokeAccess = { viewModel.revokeUserAccess(user.id) },
        )
    }

    // 邀請對話框
    if (uiState.showInviteDialog) {
        InviteUserDialog(
            onDismiss = viewModel::dismissInviteDialog,
            onInvite  = { email, role -> viewModel.inviteUser(email, role) },
        )
    }

    // 規則新增精靈
    if (uiState.showRuleWizard) {
        AddRuleWizard(
            users     = uiState.users,
            devices   = uiState.devices,
            onDismiss = viewModel::dismissRuleWizard,
            onConfirm = { rule -> viewModel.addRule(rule) },
        )
    }

    Scaffold(
        containerColor = Background,
        snackbarHost   = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title          = { Text("存取控制", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) } },
                colors         = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1321)),
            )
        },
        floatingActionButton = {
            when (uiState.selectedTab) {
                0 -> FloatingActionButton(onClick = viewModel::showInviteDialog, containerColor = PrimaryBlue, contentColor = Color.Black) {
                    Icon(Icons.Filled.PersonAdd, "邀請用戶")
                }
                1 -> FloatingActionButton(onClick = viewModel::showRuleWizard, containerColor = PrimaryBlue, contentColor = Color.Black) {
                    Icon(Icons.Filled.Add, "新增規則")
                }
                else -> {}
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(padding),
        ) {
            // ── Tab 列 ────────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor   = Color(0xFF0D1321),
                contentColor     = PrimaryBlue,
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                        color    = PrimaryBlue,
                    )
                },
            ) {
                listOf("用戶管理", "存取規則", "臨時存取").forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick  = { viewModel.selectTab(index) },
                        text     = { Text(title, fontSize = 13.sp) },
                    )
                }
            }

            // ── Tab 內容 ──────────────────────────────────────────────────────
            when (uiState.selectedTab) {
                0 -> UserManagementTab(
                    users      = uiState.users,
                    isLoading  = uiState.isLoading,
                    onUserClick = { viewModel.selectUser(it) },
                )
                1 -> AccessRulesTab(
                    rules         = uiState.rules,
                    conflictPairs = uiState.conflictPairs,
                    onToggle      = { id, enabled -> viewModel.toggleRule(id, enabled) },
                    onDelete      = { viewModel.deleteRule(it) },
                )
                2 -> GuestAccessTab(
                    qrCodes        = uiState.qrCodes,
                    onGenerate     = { ids, exp, email -> viewModel.generateGuestQrCode(ids, exp, email) },
                    onRevoke       = { viewModel.revokeQrCode(it) },
                )
            }
        }
    }
}
