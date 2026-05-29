package com.smarthome.guardian.domain.repository

import com.smarthome.guardian.domain.model.QrCodeData
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * 使用者管理的 Repository 介面。
 */
interface UserRepository {

    /** 訂閱所有使用者清單（含角色資訊）。 */
    fun getUsers(): Flow<List<User>>

    /** 訂閱單一使用者。 */
    fun getUser(userId: String): Flow<User?>

    /**
     * 邀請新使用者加入家庭。
     * 後端發送邀請 Email；接受後自動建立帳號。
     */
    suspend fun inviteUser(email: String, role: UserRole): Result<Unit>

    /** 更新使用者角色（僅管理員可呼叫）。 */
    suspend fun updateUserRole(userId: String, role: UserRole): Result<Unit>

    /**
     * 撤銷使用者存取權限。
     * 將使目前 Token 失效，並刪除所有相關 AccessRule。
     */
    suspend fun revokeUserAccess(userId: String): Result<Unit>

    /**
     * 生成訪客 QR Code。
     *
     * @param deviceIds  允許操作的設備 ID 清單
     * @param expiresAt  到期時間 epoch 毫秒
     * @param guestEmail 受邀訪客 Email（可選）
     */
    suspend fun generateGuestQrCode(
        deviceIds: List<String>,
        expiresAt: Long,
        guestEmail: String? = null,
    ): Result<QrCodeData>

    /** 取得所有已生成的 QR Code（含已過期/已撤銷）。 */
    fun getQrCodes(): Flow<List<QrCodeData>>

    /** 撤銷指定 QR Code。 */
    suspend fun revokeQrCode(code: String): Result<Unit>
}
