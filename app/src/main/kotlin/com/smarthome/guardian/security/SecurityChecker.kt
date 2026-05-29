package com.smarthome.guardian.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.scottyab.rootbeer.RootBeer
import com.smarthome.guardian.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 裝置安全性檢查器。
 *
 * 執行多項裝置完整性檢查，並將結果彙整為 [SecurityCheckResult]。
 * 在 Debug 模式下，所有檢查均視為通過（允許開發環境使用模擬器）。
 *
 * ## 檢查項目
 * 1. Root 偵測（RootBeer 多層次掃描）
 * 2. 模擬器偵測（Build 屬性分析）
 * 3. USB 除錯模式
 * 4. ADB 連線狀態（Android 9 以下）
 *
 * ## OWASP M8（Code Tampering）對應
 * 發現違規時，呼叫端應拒絕執行敏感操作或要求用戶確認。
 */
@Singleton
class SecurityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 執行全部安全性檢查並回傳彙整結果。
     *
     * 在 [BuildConfig.DEBUG] 為 `true` 時，所有檢查自動通過，
     * 僅記錄 Timber 警告，不影響開發流程。
     *
     * @return [SecurityCheckResult] 包含所有違規項目清單
     */
    fun runSecurityChecks(): SecurityCheckResult {
        if (BuildConfig.DEBUG) {
            Timber.w("Security checks bypassed — DEBUG build")
            return SecurityCheckResult(violations = emptyList(), isBypassed = true)
        }

        val violations = mutableListOf<SecurityViolation>()

        if (isRooted())        violations += SecurityViolation.ROOT_DETECTED
        if (isEmulator())      violations += SecurityViolation.EMULATOR_DETECTED
        if (isUsbDebugEnabled()) violations += SecurityViolation.USB_DEBUG_ENABLED
        if (isAdbEnabled())    violations += SecurityViolation.ADB_ENABLED

        if (violations.isNotEmpty()) {
            Timber.w("Security violations detected: $violations")
        } else {
            Timber.d("All security checks passed")
        }

        return SecurityCheckResult(violations = violations, isBypassed = false)
    }

    // ── 個別檢查 ──────────────────────────────────────────────────────────────

    /**
     * 使用 RootBeer 執行多層次 Root 偵測：
     * - su 二進位檔存在
     * - 已知 Root 管理應用程式（Magisk、SuperSU 等）
     * - Busybox 安裝
     * - 系統分割區可寫
     * - 測試金鑰簽章
     */
    private fun isRooted(): Boolean = runCatching {
        RootBeer(context).isRooted
    }.getOrElse { cause ->
        Timber.e(cause, "RootBeer check failed — assuming rooted for safety")
        true
    }

    /**
     * 透過 Build 屬性特徵值偵測 Android 模擬器。
     * 涵蓋 AVD（Android Virtual Device）、Genymotion 及 BlueStacks。
     */
    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model       = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand       = Build.BRAND.lowercase()
        val device      = Build.DEVICE.lowercase()
        val product     = Build.PRODUCT.lowercase()
        val hardware    = Build.HARDWARE.lowercase()

        return fingerprint.contains("generic") ||
            fingerprint.contains("unknown") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for x86") ||
            manufacturer.contains("genymotion") ||
            brand.contains("generic") ||
            brand.contains("android") ||
            device.contains("generic") ||
            product.contains("sdk_gphone") ||
            product.contains("sdk_x86") ||
            product.contains("vbox86p") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            hardware.contains("vbox86")
    }

    /**
     * 偵測 USB 除錯模式是否啟用（對應 OWASP M1 — 不當平台使用）。
     * USB 除錯開啟時，攻擊者可透過 ADB 存取 APP 資料目錄。
     */
    private fun isUsbDebugEnabled(): Boolean = runCatching {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        ) == 1
    }.getOrDefault(false)

    /**
     * 偵測是否有 ADB 無線連線（Android 9 以下透過 Settings.Secure）。
     * Android 11+ 已移除此設定項，回傳 `false`。
     */
    @Suppress("DEPRECATION")
    private fun isAdbEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return false
        return runCatching {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ADB_ENABLED,
                0
            ) == 1
        }.getOrDefault(false)
    }
}

// ── 結果資料類別 ──────────────────────────────────────────────────────────────

/**
 * [SecurityChecker.runSecurityChecks] 的回傳結果。
 *
 * @property violations 所有偵測到的違規項目（空列表表示通過）
 * @property isBypassed 是否因 DEBUG 模式而略過所有檢查
 */
data class SecurityCheckResult(
    val violations: List<SecurityViolation>,
    val isBypassed: Boolean,
) {
    /** 是否所有檢查均通過（或已略過）。 */
    val isPassed: Boolean get() = violations.isEmpty()

    /** 是否偵測到高風險違規（Root 或模擬器）。 */
    val hasHighRiskViolation: Boolean
        get() = violations.any {
            it == SecurityViolation.ROOT_DETECTED ||
                it == SecurityViolation.EMULATOR_DETECTED
        }
}

/** 安全性違規類型。 */
enum class SecurityViolation {
    /** 裝置已 Root（Magisk / SuperSU / su 二進位）。 */
    ROOT_DETECTED,

    /** 執行於模擬器環境（AVD / Genymotion / BlueStacks）。 */
    EMULATOR_DETECTED,

    /** USB 除錯模式已啟用。 */
    USB_DEBUG_ENABLED,

    /** ADB 無線連線已啟用（Android 9 以下）。 */
    ADB_ENABLED,
}
