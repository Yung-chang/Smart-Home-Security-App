package com.smarthome.guardian.presentation.access.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.AccessRule

private val SurfaceCard   = Color(0xFF121827)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)
private val ErrorRed      = Color(0xFFFF4444)
private val WarningColor  = Color(0xFFFFB300)

/** 存取規則 Tab 內容。 */
@Composable
fun AccessRulesTab(
    rules: List<AccessRule>,
    conflictPairs: List<Pair<AccessRule, AccessRule>>,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    val conflictIds = conflictPairs.flatMap { listOf(it.first.id, it.second.id) }.toSet()

    if (rules.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("尚無規則，點擊 + 新增", color = TextSecondary)
        }
        return
    }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 衝突警告橫幅
        if (conflictPairs.isNotEmpty()) {
            item {
                Surface(
                    color  = WarningColor.copy(alpha = 0.12f),
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, WarningColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = WarningColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "偵測到 ${conflictPairs.size} 組規則衝突（已標示紅框），拒絕優先原則將適用",
                            color = WarningColor, fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            AccessRuleRow(
                rule        = rule,
                hasConflict = rule.id in conflictIds,
                onToggle    = { onToggle(rule.id, it) },
                onDelete    = { onDelete(rule.id) },
            )
        }
    }
}

@Composable
private fun AccessRuleRow(
    rule: AccessRule,
    hasConflict: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = if (hasConflict) ErrorRed.copy(alpha = 0.6f) else Color.Transparent
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = if (hasConflict) ErrorRed.copy(alpha = 0.06f) else SurfaceCard,
        shape    = RoundedCornerShape(10.dp),
        border   = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier            = Modifier.padding(12.dp),
            verticalAlignment   = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 目標摘要
                val target = buildString {
                    append(if (rule.deviceId == "*") "所有設備" else "設備 ${rule.deviceId.take(8)}")
                    append(" → ")
                    append(if (rule.userId == "*") "所有人" else "用戶 ${rule.userId.take(8)}")
                }
                Text(target, color = if (rule.isEnabled) Color.White else TextSecondary,
                    fontWeight = FontWeight.Medium, fontSize = 13.sp)

                // 允許操作
                val ops = rule.allowedOperations.joinToString("、") { it.name.lowercase() }
                Text("允許：$ops", color = PrimaryBlue.copy(if (rule.isEnabled) 1f else 0.4f), fontSize = 12.sp)

                // 時間窗口
                rule.timeWindow?.let { tw ->
                    val days = tw.daysOfWeek.map { it.value }.sorted().joinToString(",")
                    Text("時間：週$days ${tw.startTime}–${tw.endTime}", color = TextSecondary, fontSize = 11.sp)
                }

                // 衝突標記
                if (hasConflict) {
                    Text("⚠ 與其他規則衝突", color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 啟用 Toggle
            Switch(
                checked         = rule.isEnabled,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor  = PrimaryBlue,
                    checkedTrackColor  = PrimaryBlue.copy(alpha = 0.3f),
                ),
                modifier = Modifier.height(24.dp),
            )

            // 刪除按鈕
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "刪除", tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}
