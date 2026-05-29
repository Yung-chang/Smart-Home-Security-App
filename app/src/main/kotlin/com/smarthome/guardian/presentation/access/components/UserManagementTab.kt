package com.smarthome.guardian.presentation.access.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole
import java.text.SimpleDateFormat
import java.util.*

private val SurfaceCard   = Color(0xFF121827)
private val PrimaryBlue   = Color(0xFF00D4FF)
private val TextSecondary = Color(0xFF8899AA)

/** 用戶管理 Tab 內容。 */
@Composable
fun UserManagementTab(
    users: List<User>,
    isLoading: Boolean,
    onUserClick: (User) -> Unit,
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("尚無其他成員，點擊 + 邀請", color = TextSecondary)
        }
        return
    }
    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(users, key = { it.id }) { user ->
            UserRow(user = user, onClick = { onUserClick(user) })
        }
    }
}

@Composable
private fun UserRow(user: User, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        color    = SurfaceCard,
        shape    = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier            = Modifier.padding(12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 頭像
            Box(
                modifier         = Modifier.size(44.dp).background(PrimaryBlue.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(user.name.take(1).uppercase(), color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(user.email, color = TextSecondary, fontSize = 12.sp)
                Text(
                    "最後登入：${formatTime(user.lastLoginAt)}",
                    color = TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp,
                )
            }
            RoleBadge(user.role)
        }
    }
}

@Composable
fun RoleBadge(role: UserRole) {
    val (color, label) = when (role) {
        UserRole.ADMIN         -> Color(0xFFFF6D00) to "管理員"
        UserRole.FAMILY_MEMBER -> PrimaryBlue       to "家庭成員"
        UserRole.GUEST         -> Color(0xFF00C853) to "訪客"
        UserRole.DEVICE_SERVICE -> Color(0xFF8899AA) to "服務帳號"
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text     = label,
            color    = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun formatTime(epochMs: Long): String {
    if (epochMs == 0L) return "從未"
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(epochMs))
}
