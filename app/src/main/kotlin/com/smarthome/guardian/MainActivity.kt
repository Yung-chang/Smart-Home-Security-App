package com.smarthome.guardian

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smarthome.guardian.presentation.access.AccessControlScreen
import com.smarthome.guardian.presentation.audit.AuditLogScreen
import com.smarthome.guardian.presentation.auth.AuthViewModel
import com.smarthome.guardian.presentation.auth.BiometricScreen
import com.smarthome.guardian.presentation.auth.LoginScreen
import com.smarthome.guardian.presentation.dashboard.DashboardScreen
import com.smarthome.guardian.presentation.devices.DeviceDetailScreen
import com.smarthome.guardian.presentation.navigation.AppRoutes
import com.smarthome.guardian.presentation.security.AlertHistoryScreen
import com.smarthome.guardian.presentation.security.SecurityMonitorScreen
import com.smarthome.guardian.service.NotificationHelper
import com.smarthome.guardian.service.SecurityService
import com.smarthome.guardian.ui.theme.Background
import com.smarthome.guardian.ui.theme.SmartHomeGuardianTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 唯一的 Activity（Single-Activity 架構）。
 *
 * ## 安全措施
 * - [WindowManager.LayoutParams.FLAG_SECURE]：全域防截圖（OWASP M1）
 * - Edge-to-edge + 深色 Status Bar：背景色延伸至系統列
 *
 * ## 深層連結處理
 * 通知被點擊時，`FLAG_ACTIVITY_SINGLE_TOP` 確保只有一個 Activity 實例，
 * 系統呼叫 [onNewIntent] 傳遞新 Intent。
 * [pendingDeepLink] StateFlow 將 Intent 傳遞給 Compose NavGraph 的 LaunchedEffect 處理。
 *
 * ## OWASP M7（不良的程式碼品質）對應
 * 深層連結不信任 Intent 中的任意 String 執行動作，
 * 只允許預定義路由（白名單設計）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 推播通知觸發的深層連結 Intent；Compose NavGraph 監聽並消費。 */
    internal val _pendingDeepLink = MutableStateFlow<Intent?>(null)
    val pendingDeepLink: StateFlow<Intent?> = _pendingDeepLink.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OWASP M2：Release build 防截圖；Debug build 允許（供 Robo Test / 截圖工具使用）
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars     = false
            isAppearanceLightNavigationBars = false
        }

        // 處理啟動時的 deep link（通知被點擊時 APP 從未啟動狀態）
        intent?.let { _pendingDeepLink.value = it }

        setContent {
            SmartHomeGuardianTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Background)
                        .semantics { testTagsAsResourceId = true },
                    color = Background,
                ) {
                    SmartHomeNavGraph()
                }
            }
        }
    }

    /** Activity 已在前景時被重新啟動（如通知點擊）→ 處理 deep link。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _pendingDeepLink.value = intent
    }
}

// ── Navigation Graph ──────────────────────────────────────────────────────────

@Composable
private fun SmartHomeNavGraph() {
    val navController  = rememberNavController()
    val activity       = LocalContext.current as MainActivity
    val pendingLink by activity.pendingDeepLink.collectAsState()

    // 消費深層連結並導航
    LaunchedEffect(pendingLink) {
        pendingLink?.let { intent ->
            handleDeepLink(intent, navController)
            activity._pendingDeepLink.value = null
        }
    }

    NavHost(
        navController    = navController,
        startDestination = AppRoutes.LOGIN,
    ) {
        // ── 認證 ─────────────────────────────────────────────────────────────
        composable(AppRoutes.LOGIN) {
            LoginScreen(
                onAuthenticated = {
                    navController.navigate(AppRoutes.DASHBOARD) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(AppRoutes.BIOMETRIC) {
            BiometricScreen(
                onAuthenticated = {
                    navController.navigate(AppRoutes.DASHBOARD) {
                        popUpTo(AppRoutes.BIOMETRIC) { inclusive = true }
                    }
                },
                onFallbackToPin = {
                    navController.navigate(AppRoutes.PIN) {
                        popUpTo(AppRoutes.BIOMETRIC) { inclusive = true }
                    }
                },
            )
        }

        // ── 主儀表板 ──────────────────────────────────────────────────────────
        composable(AppRoutes.DASHBOARD) {
            val context     = LocalContext.current
            val authViewModel: AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            LaunchedEffect(Unit) { SecurityService.start(context) }
            DashboardScreen(
                onNavigateToSecurity = { navController.navigate(AppRoutes.SECURITY) },
                onNavigateToDevice   = { id -> navController.navigate(AppRoutes.deviceDetail(id)) },
                onNavigateToSettings = { navController.navigate(AppRoutes.ACCESS_CONTROL) },
                onAddDevice          = { /* TODO */ },
                onLogout             = {
                    authViewModel.logout()
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── 設備詳情 ──────────────────────────────────────────────────────────
        composable(
            route     = AppRoutes.DEVICE_DETAIL,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType }),
        ) { back ->
            val deviceId = back.arguments?.getString("deviceId") ?: return@composable
            DeviceDetailScreen(
                deviceId     = deviceId,
                onNavigateUp = { navController.popBackStack() },
            )
        }

        // ── 安全監控 ──────────────────────────────────────────────────────────
        composable(AppRoutes.SECURITY) {
            SecurityMonitorScreen(
                onNavigateToHistory = { navController.navigate(AppRoutes.ALERT_HISTORY) },
                onNavigateUp        = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.ALERT_HISTORY) {
            AlertHistoryScreen(onNavigateUp = { navController.popBackStack() })
        }

        // ── 存取控制 ──────────────────────────────────────────────────────────
        composable(AppRoutes.ACCESS_CONTROL) {
            AccessControlScreen(onNavigateUp = { navController.popBackStack() })
        }

        // ── 稽核日誌 ──────────────────────────────────────────────────────────
        composable(AppRoutes.AUDIT_LOGS) {
            AuditLogScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

// ── 深層連結處理 ──────────────────────────────────────────────────────────────

/**
 * 將通知的 Intent extras 轉譯為 Navigation 路由。
 *
 * 白名單設計：只允許 [AppRoutes] 中預定義的路由，
 * 防止通知偽造任意導航（OWASP M7）。
 */
private fun handleDeepLink(
    intent: Intent,
    navController: androidx.navigation.NavController,
) {
    val navigateTo = intent.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TO) ?: return

    when (navigateTo) {
        "security" -> {
            navController.navigate(AppRoutes.SECURITY) {
                launchSingleTop = true
            }
        }
        "device" -> {
            val deviceId = intent.getStringExtra(NotificationHelper.EXTRA_DEVICE_ID)
                ?: return
            // 白名單：deviceId 必須是有效的非空字串（不執行任意路由）
            if (deviceId.isNotBlank()) {
                navController.navigate(AppRoutes.deviceDetail(deviceId)) {
                    launchSingleTop = true
                }
            }
        }
        "audit" -> navController.navigate(AppRoutes.AUDIT_LOGS) { launchSingleTop = true }
        // 未知路由 → 忽略（Fail-closed）
        else -> android.util.Log.w("DeepLink", "Unknown navigate_to: $navigateTo")
    }
}
