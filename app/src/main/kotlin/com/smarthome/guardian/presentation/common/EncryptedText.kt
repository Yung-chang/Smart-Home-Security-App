package com.smarthome.guardian.presentation.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.ui.theme.MonoFamily
import com.smarthome.guardian.ui.theme.Primary
import com.smarthome.guardian.ui.theme.SmartHomeGuardianTheme
import com.smarthome.guardian.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * 敏感資訊顯示元件：預設以遮罩字元隱藏，點擊後短暫揭露真實內容。
 *
 * ## 安全設計
 * - 預設顯示 [maskChar] 重複字串，不暴露敏感資料
 * - 點擊後顯示 [revealDurationMs] 毫秒，自動復原遮罩
 * - 鎖頭圖示提示使用者此欄位為敏感資訊
 * - 適用場景：臨時密碼、設備加密金鑰、API Key 後半段
 *
 * ## OWASP M2 — 不安全的資料儲存
 * 此元件確保敏感資料在 UI 上不被持續暴露，
 * 即使螢幕被旁觀者看到，也只看到遮罩字元。
 *
 * @param text           真實敏感內容
 * @param label          標籤文字（顯示在遮罩文字上方）
 * @param maskChar       遮罩字元（預設 `•`）
 * @param maskLength     遮罩字元數（預設 8，與原文長度無關）
 * @param revealDurationMs 揭露持續時間（毫秒，預設 3000）
 * @param textStyle      文字樣式
 * @param modifier       Modifier
 */
@Composable
fun EncryptedText(
    text: String,
    label: String? = null,
    maskChar: Char = '•',
    maskLength: Int = 8,
    revealDurationMs: Long = 3_000L,
    textStyle: TextStyle = TextStyle(
        fontFamily = MonoFamily,
        fontSize   = 14.sp,
        color      = TextSecondary,
    ),
    modifier: Modifier = Modifier,
) {
    var isRevealed by remember { mutableStateOf(false) }

    // 揭露計時：到期後自動回復遮罩
    LaunchedEffect(isRevealed) {
        if (isRevealed) {
            delay(revealDurationMs)
            isRevealed = false
        }
    }

    val maskedText = maskChar.toString().repeat(maskLength)

    Column(modifier = modifier) {
        label?.let {
            Text(
                text       = it,
                color      = TextSecondary,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.padding(bottom = 2.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { isRevealed = !isRevealed },
        ) {
            Icon(
                imageVector        = if (isRevealed) Icons.Filled.LockOpen else Icons.Filled.Lock,
                contentDescription = if (isRevealed) "隱藏" else "揭露",
                tint               = if (isRevealed) Primary else TextSecondary.copy(alpha = 0.6f),
                modifier           = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))

            AnimatedContent(
                targetState = isRevealed,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "encryptedTextAnim",
            ) { revealed ->
                Text(
                    text  = if (revealed) text else maskedText,
                    style = textStyle.copy(
                        color = if (revealed) Color.White else TextSecondary,
                    ),
                )
            }

            if (isRevealed) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text     = "${revealDurationMs / 1000}s",
                    color    = Primary.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun EncryptedTextPreview() {
    SmartHomeGuardianTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            EncryptedText(
                text  = "A3F8-B2C1-9D74",
                label = "臨時存取碼",
            )
            EncryptedText(
                text      = "eyJhbGciOiJIUzI1NiJ9",
                label     = "加密金鑰（前 20 字元）",
                maskLength= 12,
            )
        }
    }
}
