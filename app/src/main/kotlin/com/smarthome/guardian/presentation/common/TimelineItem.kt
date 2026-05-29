package com.smarthome.guardian.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.AccessAction
import com.smarthome.guardian.domain.model.AccessLog
import com.smarthome.guardian.domain.model.UnlockMethod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PrimaryBlue   = Color(0xFF00D4FF)
private val SuccessGreen  = Color(0xFF00C853)
private val ErrorRed      = Color(0xFFFF4444)
private val TextSecondary = Color(0xFF8899AA)
private val LineColor     = Color(0xFF2A3550)

/**
 * 時間軸列表項目（用於門鎖進出記錄）。
 *
 * @param log         進出記錄資料
 * @param isLast      是否為最後一筆（控制底部連接線顯示）
 */
@Composable
fun TimelineItem(
    log: AccessLog,
    isLast: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dotColor = when {
        !log.isSuccessful              -> ErrorRed
        log.action == AccessAction.LOCK -> TextSecondary
        else                           -> SuccessGreen
    }

    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        // ── 時間軸線 + 圓點 ────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.width(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(dotColor, CircleShape),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(48.dp)
                        .background(LineColor),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // ── 記錄內容 ──────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 4.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = log.action.displayName,
                    color      = if (log.isSuccessful) Color.White else ErrorRed,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text     = formatTime(log.timestamp),
                    color    = TextSecondary,
                    fontSize = 11.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(log.userName, color = TextSecondary, fontSize = 12.sp)
                Text("·", color = TextSecondary, fontSize = 12.sp)
                Text(log.method.displayName, color = PrimaryBlue.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
    }
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(epochMs))

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A)
@Composable
private fun TimelinePreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            listOf(
                AccessLog("1", "d1", "u1", "Alice", AccessAction.UNLOCK, UnlockMethod.APP, System.currentTimeMillis()),
                AccessLog("2", "d1", "u2", "Bob", AccessAction.LOCK, UnlockMethod.AUTO_LOCK, System.currentTimeMillis() - 3600000),
                AccessLog("3", "d1", null, "未知", AccessAction.FAILED_ATTEMPT, UnlockMethod.PIN, System.currentTimeMillis() - 7200000, false),
            ).forEachIndexed { index, log ->
                TimelineItem(log = log, isLast = index == 2)
            }
        }
    }
}
