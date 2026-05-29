package com.smarthome.guardian.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 應用程式字型系統。
 *
 * ## 字體選擇
 * - **主要字體**：Roboto（Android 系統內建，無需額外打包）
 * - **等寬字體**：[MonoFamily]（顯示 IP / MAC / JSON / 裝置指紋等資料）
 *
 * ## 要使用 Roboto Mono
 * 1. 在 `res/font/` 加入 `roboto_mono_regular.ttf`（Google Fonts 可下載）
 * 2. 取消下方 `MonoFamily` 的 Font() 定義並替換 `FontFamily.Monospace`
 */

/** 等寬字體（用於 HMAC 簽章、IP、裝置指紋等技術性資訊顯示）。 */
val MonoFamily: FontFamily = FontFamily.Monospace
// 若已在 res/font 加入 Roboto Mono，替換為：
// val MonoFamily = FontFamily(
//     Font(R.font.roboto_mono_regular, FontWeight.Normal),
//     Font(R.font.roboto_mono_medium,  FontWeight.Medium),
// )

val AppTypography = Typography(
    // ── 標題 ────────────────────────────────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize   = 57.sp,
        lineHeight = 64.sp,
        color      = TextPrimary,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 45.sp,
        lineHeight = 52.sp,
        color      = TextPrimary,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 36.sp,
        lineHeight = 44.sp,
        color      = TextPrimary,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize   = 32.sp,
        lineHeight = 40.sp,
        color      = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 28.sp,
        lineHeight = 36.sp,
        color      = TextPrimary,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 24.sp,
        lineHeight = 32.sp,
        color      = TextPrimary,
    ),
    // ── 標籤標題 ─────────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        lineHeight = 28.sp,
        color      = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        color      = TextPrimary,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        color      = TextPrimary,
    ),
    // ── 內文 ─────────────────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        color      = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        color      = TextPrimary,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        color      = TextSecondary,
    ),
    // ── 標籤 ─────────────────────────────────────────────────────────────────
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        color      = TextPrimary,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        color      = TextSecondary,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
        color      = TextSecondary,
    ),
)

// ── 應用程式專用文字樣式（不在 Material 3 規範內） ───────────────────────────

/** 等寬資料顯示（HMAC / IP / MAC / JSON）。 */
val MonoBodyMedium = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize   = 12.sp,
    lineHeight = 16.sp,
    color      = TextSecondary,
)

/** 安全評分大數字。 */
val ScoreDisplay = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize   = 48.sp,
    lineHeight = 56.sp,
    color      = TextPrimary,
)
