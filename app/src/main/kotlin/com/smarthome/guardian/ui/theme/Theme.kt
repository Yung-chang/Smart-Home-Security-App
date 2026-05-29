package com.smarthome.guardian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * SmartHome Guardian 深色主題。
 *
 * ## 設計決策
 * - **僅深色主題**：安全 APP 避免淺色模式，防止螢幕內容在暗處被他人偷窺
 * - **停用動態顏色（Material You）**：Android 12+ 動態顏色受桌布影響，
 *   可能導致安全等級色（紅/黃/綠）對比度不足，進而誤導使用者判斷
 * - **Status Bar 設定**：由 [com.smarthome.guardian.MainActivity] 透過
 *   `enableEdgeToEdge()` 與 `WindowCompat` 統一處理，不在 Composable 層設定
 *
 * ## 使用方式
 * ```kotlin
 * SmartHomeGuardianTheme {
 *     // 所有 Composable 在此 scope 內自動套用主題
 * }
 * ```
 */
private val DarkColorScheme = darkColorScheme(
    primary              = Primary,
    onPrimary            = OnPrimary,
    primaryContainer     = Primary.copy(alpha = 0.15f),
    onPrimaryContainer   = Primary,

    secondary            = Secondary,
    onSecondary          = OnSecondary,
    secondaryContainer   = Secondary.copy(alpha = 0.15f),
    onSecondaryContainer = Secondary,

    tertiary             = Warning,
    onTertiary           = Color.Black,
    tertiaryContainer    = Warning.copy(alpha = 0.15f),
    onTertiaryContainer  = Warning,

    background           = Background,
    onBackground         = OnBackground,

    surface              = Surface,
    onSurface            = OnSurface,
    surfaceVariant       = Surface2,
    onSurfaceVariant     = TextSecondary,

    outline              = Outline,
    outlineVariant       = Outline.copy(alpha = 0.5f),

    error                = Error,
    onError              = OnError,
    errorContainer       = Error.copy(alpha = 0.15f),
    onErrorContainer     = Error,

    inverseSurface       = TextPrimary,
    inverseOnSurface     = Background,
    inversePrimary       = Primary,

    scrim                = Color.Black.copy(alpha = 0.6f),
)

@Composable
fun SmartHomeGuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content,
    )
}
