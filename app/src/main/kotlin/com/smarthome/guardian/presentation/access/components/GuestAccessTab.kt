package com.smarthome.guardian.presentation.access.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.QrCodeData
import java.text.SimpleDateFormat
import java.util.*

private val SurfaceCard   = Color(0xFF121827)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)
private val ErrorRed      = Color(0xFFFF4444)
private val SuccessGreen  = Color(0xFF00C853)

/** 臨時存取 Tab 內容（QR Code 生成與管理）。 */
@Composable
fun GuestAccessTab(
    qrCodes: List<QrCodeData>,
    onGenerate: (deviceIds: List<String>, expiresAt: Long, guestEmail: String?) -> Unit,
    onRevoke: (code: String) -> Unit,
) {
    var showGenerator by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 生成新 QR Code 按鈕 ────────────────────────────────────────────────
        item {
            Button(
                onClick  = { showGenerator = !showGenerator },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.QrCode, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("生成訪客 QR Code", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // ── 快速生成表單（展開）────────────────────────────────────────────────
        if (showGenerator) {
            item {
                QrCodeGeneratorForm(
                    onGenerate = { ids, exp, email ->
                        onGenerate(ids, exp, email)
                        showGenerator = false
                    },
                    onCancel = { showGenerator = false },
                )
            }
        }

        // ── 已生成清單 ─────────────────────────────────────────────────────────
        if (qrCodes.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("尚無臨時存取碼", color = TextSecondary)
                }
            }
        } else {
            items(qrCodes, key = { it.id }) { qr ->
                QrCodeRow(qrCode = qr, onRevoke = { onRevoke(qr.id) })
            }
        }
    }
}

// ── 快速生成表單 ──────────────────────────────────────────────────────────────

@Composable
private fun QrCodeGeneratorForm(
    onGenerate: (List<String>, Long, String?) -> Unit,
    onCancel: () -> Unit,
) {
    var guestEmail by remember { mutableStateOf("") }
    var hours      by remember { mutableIntStateOf(1) }

    Surface(color = SurfaceCard, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("快速生成訪客 QR Code", color = Color.White, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value         = guestEmail,
                onValueChange = { guestEmail = it },
                label         = { Text("訪客 Email（選填）") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = PrimaryBlue,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    focusedLabelColor    = PrimaryBlue,
                    unfocusedLabelColor  = TextSecondary,
                ),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("有效時間：", color = TextSecondary, fontSize = 13.sp)
                listOf(1, 6, 24, 72).forEach { h ->
                    FilterChip(
                        selected = hours == h,
                        onClick  = { hours = h },
                        label    = { Text("${h}h") },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue.copy(alpha = 0.2f),
                            selectedLabelColor     = PrimaryBlue,
                            containerColor         = Color(0xFF0A0E1A),
                            labelColor             = TextSecondary,
                        ),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消", color = TextSecondary)
                }
                Button(
                    onClick = {
                        val exp = System.currentTimeMillis() + hours * 3_600_000L
                        onGenerate(listOf("*"), exp, guestEmail.ifBlank { null })
                    },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                ) {
                    Text("生成", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── QR Code 行 ────────────────────────────────────────────────────────────────

@Composable
private fun QrCodeRow(qrCode: QrCodeData, onRevoke: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val expStr   = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(qrCode.expiresAt))
    val statusColor = when {
        qrCode.isRevoked -> ErrorRed
        qrCode.isExpired -> TextSecondary
        else             -> SuccessGreen
    }
    val statusLabel = when {
        qrCode.isRevoked -> "已撤銷"
        qrCode.isExpired -> "已過期"
        else             -> "有效中"
    }

    Surface(color = SurfaceCard, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QrCodeScanner, null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(qrCode.guestLabel, color = Color.White, fontSize = 13.sp)
                    Text("到期：$expStr", color = TextSecondary, fontSize = 11.sp)
                }
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(statusLabel, color = statusColor, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(4.dp))
                // 展開顯示 QR 圖片
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null, tint = TextSecondary, modifier = Modifier.size(18.dp),
                    )
                }
                // 撤銷按鈕
                if (qrCode.isActive) {
                    IconButton(onClick = onRevoke, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Cancel, "撤銷", tint = ErrorRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 展開：顯示 QR Code Bitmap
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                qrCode.bitmap?.let { bmp ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap      = bmp.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier    = Modifier.size(160.dp).background(Color.White),
                        )
                    }
                }
            }
        }
    }
}
