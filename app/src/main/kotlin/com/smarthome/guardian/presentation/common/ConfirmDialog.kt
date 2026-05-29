package com.smarthome.guardian.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning

private val SurfaceColor  = Color(0xFF1A2235)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val ErrorRed      = Color(0xFFFF4444)
private val TextSecondary = Color(0xFF8899AA)

/**
 * 重要操作確認對話框（用於門鎖上鎖/解鎖、設定變更等）。
 *
 * 依照 OWASP Mobile Top 10 的最佳實踐，高風險操作（如解鎖前門）
 * 必須顯示確認步驟，防止誤觸。
 *
 * @param title         對話框標題
 * @param message       操作說明文字
 * @param confirmText   確認按鈕文字（預設「確認」）
 * @param dismissText   取消按鈕文字（預設「取消」）
 * @param icon          標題旁圖示（可選）
 * @param isDangerous   是否為高風險操作（確認按鈕顯示紅色）
 * @param onConfirm     確認回呼
 * @param onDismiss     取消回呼
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "確認",
    dismissText: String = "取消",
    icon: ImageVector? = null,
    isDangerous: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceColor,
        shape            = RoundedCornerShape(16.dp),
        icon = icon?.let {
            {
                Icon(
                    imageVector        = it,
                    contentDescription = null,
                    tint               = if (isDangerous) ErrorRed else PrimaryBlue,
                    modifier           = Modifier.size(36.dp),
                )
            }
        },
        title = {
            Text(
                text       = title,
                color      = Color.White,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text     = message,
                color    = TextSecondary,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = if (isDangerous) ErrorRed else PrimaryBlue,
                    contentColor   = if (isDangerous) Color.White else Color.Black,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = TextSecondary)
            }
        },
    )
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun ConfirmDialogPreview() {
    MaterialTheme {
        ConfirmDialog(
            title       = "解鎖大門",
            message     = "確定要解鎖前門門鎖嗎？請確認周遭環境安全。",
            icon        = Icons.Filled.Warning,
            isDangerous = true,
            onConfirm   = {},
            onDismiss   = {},
        )
    }
}
