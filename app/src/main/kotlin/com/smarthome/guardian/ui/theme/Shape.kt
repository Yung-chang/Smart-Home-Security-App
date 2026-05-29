package com.smarthome.guardian.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 應用程式形狀系統。
 *
 * | 層級   | 圓角     | 使用場景                  |
 * |--------|----------|--------------------------|
 * | small  | 8dp      | 按鈕、小型 Chip、輸入框   |
 * | medium | 12dp     | 設備卡片、ElevatedCard    |
 * | large  | 24dp top | ModalBottomSheet          |
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ── 命名常數（供元件直接引用，不依賴 MaterialTheme） ──────────────────────────

val ShapeButton       = RoundedCornerShape(8.dp)
val ShapeCard         = RoundedCornerShape(12.dp)
val ShapeBottomSheet  = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val ShapeChip         = RoundedCornerShape(50)   // 完全圓角
val ShapeDialog       = RoundedCornerShape(16.dp)
