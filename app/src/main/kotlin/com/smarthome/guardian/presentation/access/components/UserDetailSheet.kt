package com.smarthome.guardian.presentation.access.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole

private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)
private val ErrorRed      = Color(0xFFFF4444)

/**
 * 使用者詳情 BottomSheet。
 *
 * 顯示使用者資訊，並提供角色修改與撤銷存取的操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailSheet(
    user: User,
    onDismiss: () -> Unit,
    onUpdateRole: (UserRole) -> Unit,
    onRevokeAccess: () -> Unit,
) {
    var showRoleMenu     by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }

    if (showRevokeDialog) {
        com.smarthome.guardian.presentation.common.ConfirmDialog(
            title       = "撤銷存取",
            message     = "確定要撤銷「${user.name}」的所有存取權限嗎？此操作無法復原，使用者的 Token 將立即失效。",
            confirmText = "撤銷",
            icon        = Icons.Filled.PersonOff,
            isDangerous = true,
            onConfirm   = { showRevokeDialog = false; onRevokeAccess() },
            onDismiss   = { showRevokeDialog = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1A2235),
        shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 頭像 + 基本資訊 ────────────────────────────────────────────────
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier         = Modifier.size(56.dp).background(PrimaryBlue.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(user.name.take(1).uppercase(), color = PrimaryBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(user.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(user.email, color = TextSecondary, fontSize = 13.sp)
                }
            }

            HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))

            // ── 角色修改 ──────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("目前角色", color = TextSecondary, fontSize = 13.sp)
                Box {
                    TextButton(onClick = { showRoleMenu = true }) {
                        RoleBadge(user.role)
                        Icon(Icons.Filled.ArrowDropDown, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded         = showRoleMenu,
                        onDismissRequest = { showRoleMenu = false },
                    ) {
                        UserRole.values()
                            .filter { it != UserRole.DEVICE_SERVICE && it != user.role }
                            .forEach { role ->
                                DropdownMenuItem(
                                    text    = { RoleBadge(role) },
                                    onClick = { showRoleMenu = false; onUpdateRole(role) },
                                )
                            }
                    }
                }
            }

            // ── 撤銷存取 ──────────────────────────────────────────────────────
            Button(
                onClick  = { showRevokeDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.15f), contentColor = ErrorRed),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.PersonOff, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("撤銷存取權限", fontWeight = FontWeight.Bold)
            }
        }
    }
}
