package com.smarthome.guardian.presentation.access.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smarthome.guardian.domain.model.UserRole

private val SurfaceCard   = Color(0xFF1A2235)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)

/**
 * 邀請使用者對話框。
 *
 * 輸入受邀 Email 並選擇角色（排除 ADMIN 自我授權）。
 */
@Composable
fun InviteUserDialog(
    onDismiss: () -> Unit,
    onInvite: (email: String, role: UserRole) -> Unit,
) {
    var email       by remember { mutableStateOf("") }
    var role        by remember { mutableStateOf(UserRole.FAMILY_MEMBER) }
    var emailError  by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceCard,
        shape            = RoundedCornerShape(16.dp),
        title = { Text("邀請新成員", color = Color.White, fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = email,
                    onValueChange = { email = it; emailError = null },
                    label         = { Text("Email") },
                    isError       = emailError != null,
                    supportingText = emailError?.let { { Text(it, color = Color(0xFFFF4444)) } },
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

                Text("角色", color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(UserRole.FAMILY_MEMBER, UserRole.GUEST).forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick  = { role = r },
                            label    = {
                                Text(when (r) {
                                    UserRole.FAMILY_MEMBER -> "家庭成員"
                                    UserRole.GUEST         -> "訪客"
                                    else                   -> r.name
                                })
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue.copy(alpha = 0.2f),
                                selectedLabelColor     = PrimaryBlue,
                                containerColor         = Color(0xFF0A0E1A),
                                labelColor             = TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        emailError = "請輸入有效 Email"
                    } else {
                        onInvite(email.trim(), role)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            ) {
                Text("發送邀請", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        },
    )
}
