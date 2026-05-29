package com.smarthome.guardian.presentation.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.SecurityLevel

/**
 * 安全狀態橫幅，顯示於儀表板頂部。
 *
 * 根據 [securityLevel] 動態變色（動畫漸變），並顯示對應文字說明。
 * 點擊後跳至安全監控頁面。
 *
 * @param securityLevel    目前的安全等級
 * @param unreadAlertCount 未確認的警報數量
 * @param onClick          點擊回呼（導航至安全監控頁）
 */
@Composable
fun SecurityStatusBanner(
    securityLevel: SecurityLevel,
    unreadAlertCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedColor by animateColorAsState(
        targetValue  = securityLevel.displayColor,
        animationSpec = tween(durationMillis = 600),
        label        = "banner_color",
    )

    val (icon, subText) = when (securityLevel) {
        SecurityLevel.SECURE  -> Icons.Filled.CheckCircle to "所有設備運作正常"
        SecurityLevel.WARNING -> Icons.Filled.Warning to
            if (unreadAlertCount > 0) "$unreadAlertCount 個警報待處理" else "部分設備狀態異常"
        SecurityLevel.ALERT   -> Icons.Filled.Error to "偵測到高風險威脅，請立即處理"
    }

    Surface(
        modifier  = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color     = animatedColor.copy(alpha = 0.15f),
        shape     = RoundedCornerShape(12.dp),
        border    = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = animatedColor.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = animatedColor,
                modifier           = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = securityLevel.displayText,
                    color      = animatedColor,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text     = subText,
                    color    = animatedColor.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }
            // 右側箭頭提示可點擊
            Icon(
                imageVector        = Icons.Filled.Error, // chevron_right placeholder
                contentDescription = "查看詳情",
                tint               = animatedColor.copy(alpha = 0.6f),
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun BannerSecurePreview() {
    MaterialTheme {
        SecurityStatusBanner(
            securityLevel    = SecurityLevel.SECURE,
            unreadAlertCount = 0,
            onClick          = {},
            modifier         = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun BannerWarningPreview() {
    MaterialTheme {
        SecurityStatusBanner(
            securityLevel    = SecurityLevel.WARNING,
            unreadAlertCount = 3,
            onClick          = {},
            modifier         = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun BannerAlertPreview() {
    MaterialTheme {
        SecurityStatusBanner(
            securityLevel    = SecurityLevel.ALERT,
            unreadAlertCount = 7,
            onClick          = {},
            modifier         = Modifier.padding(16.dp),
        )
    }
}
