package com.smarthome.guardian.ui.theme

import androidx.compose.ui.graphics.Color

// ── 主調色盤 ──────────────────────────────────────────────────────────────────

/** 主色：青藍色（科技感，按鈕/連結/高亮） */
val Primary    = Color(0xFF00D4FF)

/** 次要色：紫色（資安感，二次動作/Badge） */
val Secondary  = Color(0xFF7B2FBE)

/** 背景色：深海軍藍 */
val Background = Color(0xFF0A0E1A)

/** 卡片/表面色 */
val Surface    = Color(0xFF121827)

/** 卡片次層色（展開區域/BottomSheet 內容） */
val Surface2   = Color(0xFF1A2235)

/** 外框/分隔線色 */
val Outline    = Color(0xFF2A3550)

/** 錯誤色：鮮紅 */
val Error      = Color(0xFFFF4444)

/** 成功色：翠綠 */
val Success    = Color(0xFF00C853)

/** 警告色：琥珀 */
val Warning    = Color(0xFFFFB300)

/** 高危操作色：橙紅（介於 Warning 與 Error 之間） */
val Danger     = Color(0xFFFF6D00)

// ── On-color ─────────────────────────────────────────────────────────────────

val OnPrimary    = Color(0xFF000000)   // Primary 上的文字/圖示（黑）
val OnSecondary  = Color(0xFFFFFFFF)
val OnBackground = Color(0xFFFFFFFF)
val OnSurface    = Color(0xFFFFFFFF)
val OnError      = Color(0xFFFFFFFF)

// ── 文字色 ────────────────────────────────────────────────────────────────────

val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8899AA)
val TextDisabled  = Color(0xFF4A5568)

// ── 安全等級色 ────────────────────────────────────────────────────────────────

val SecuritySecure   = Color(0xFF00C853)  // SECURE：系統正常
val SecurityWarning  = Color(0xFFFFB300)  // WARNING：有待確認的中度警報
val SecurityCritical = Color(0xFFFF4444)  // ALERT：緊急事件

// ── 嚴重程度色（Severity） ────────────────────────────────────────────────────

val SeverityCritical = Color(0xFFFF4444)
val SeverityHigh     = Color(0xFFFF6D00)
val SeverityMedium   = Color(0xFFFFB300)
val SeverityLow      = Color(0xFF00C853)

// ── 設備類別色（AuditCategory / DeviceType） ─────────────────────────────────

val CategoryAuth     = Color(0xFF00D4FF)  // AUTH
val CategoryDevice   = Color(0xFF2196F3)  // DEVICE
val CategoryAccess   = Color(0xFFFF6D00)  // ACCESS
val CategoryUser     = Color(0xFF7B2FBE)  // USER
val CategorySecurity = Color(0xFFFF4444)  // SECURITY
val CategorySystem   = Color(0xFF8899AA)  // SYSTEM
