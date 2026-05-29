package com.smarthome.guardian.domain.model

/**
 * 系統使用者的領域模型。
 *
 * @property id          唯一識別碼（UUID）
 * @property email       登入 Email
 * @property name        顯示名稱
 * @property role        角色（決定 RBAC 權限範圍）
 * @property avatarUrl   頭像 URL（可為 null）
 * @property lastLoginAt 最後登入時間（Unix epoch 毫秒）
 */
data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: UserRole,
    val avatarUrl: String? = null,
    val lastLoginAt: Long = 0L,
)

/**
 * 使用者角色，對應 RBAC 權限矩陣。
 *
 * | 角色              | 權限範圍                              |
 * |------------------|--------------------------------------|
 * | ADMIN            | 全部設備 + 使用者管理 + 設定 + 稽核日誌 |
 * | FAMILY_MEMBER    | 指定設備控制 + 查看自己的操作紀錄      |
 * | GUEST            | 特定時段 + 特定設備（唯讀/限制操作）   |
 * | DEVICE_SERVICE   | 僅 MQTT 發布/訂閱，無 UI 存取          |
 */
enum class UserRole {
    ADMIN,
    FAMILY_MEMBER,
    GUEST,
    DEVICE_SERVICE,
}
