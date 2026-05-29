package com.smarthome.guardian.domain.model

/**
 * 稽核日誌記錄（不可竄改）。
 *
 * 每筆記錄以 HMAC-SHA256 簽章，任何欄位被修改後
 * [signature] 驗證將失敗，可立即偵測竄改。
 *
 * @property id                唯一識別碼（UUID）
 * @property userId            操作使用者 ID
 * @property action            操作類型（[AuditAction]）
 * @property targetId          操作目標 ID（設備 ID / 用戶 ID 等）
 * @property before            操作前狀態（JSON 字串）
 * @property after             操作後狀態（JSON 字串）
 * @property ipAddress         操作來源 IP（可能為本機回環）
 * @property deviceFingerprint 裝置指紋（Build.FINGERPRINT 前 64 字元）
 * @property timestamp         操作時間 epoch 毫秒
 * @property signature         HMAC-SHA256 簽章（以 Android Keystore 金鑰計算）
 */
data class AuditLog(
    val id: String,
    val userId: String,
    val action: AuditAction,
    val targetId: String? = null,
    val before: String? = null,
    val after: String? = null,
    val ipAddress: String = "127.0.0.1",
    val deviceFingerprint: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = "",
) {
    /** 用於簽章計算的正規化字串（排除 signature 本身）。 */
    fun toSignableString(): String =
        "$id|$userId|$action|$targetId|$before|$after|$ipAddress|$deviceFingerprint|$timestamp"
}

/**
 * 稽核動作枚舉，對應不同操作類型的顯示顏色與圖示。
 */
enum class AuditAction(val displayName: String, val category: AuditCategory) {
    // 驗證
    LOGIN_SUCCESS("登入成功",       AuditCategory.AUTH),
    LOGIN_FAILED("登入失敗",        AuditCategory.AUTH),
    LOGOUT("登出",                  AuditCategory.AUTH),
    TOKEN_REFRESH("Token 更新",     AuditCategory.AUTH),
    AUTH_BYPASS_ATTEMPT("嘗試繞過驗證", AuditCategory.SECURITY),

    // 設備
    DEVICE_CONTROL("控制設備",      AuditCategory.DEVICE),
    DEVICE_SETTINGS_CHANGED("修改設備設定", AuditCategory.DEVICE),

    // 存取規則
    ACCESS_RULE_CREATED("建立存取規則", AuditCategory.ACCESS),
    ACCESS_RULE_DELETED("刪除存取規則", AuditCategory.ACCESS),

    // 使用者
    USER_INVITED("邀請使用者",      AuditCategory.USER),
    USER_ROLE_CHANGED("修改角色",   AuditCategory.USER),
    USER_REVOKED("撤銷存取",        AuditCategory.USER),

    // 警報
    ALERT_ACKNOWLEDGED("確認警報",  AuditCategory.SECURITY),

    // 系統
    SYSTEM_SETTING_CHANGED("修改系統設定", AuditCategory.SYSTEM),
    EXPORT_LOGS("匯出日誌",         AuditCategory.SYSTEM),
}

enum class AuditCategory {
    AUTH, DEVICE, ACCESS, USER, SECURITY, SYSTEM
}

/**
 * 日誌完整性驗證結果。
 */
sealed class IntegrityResult {
    /** 簽章驗證通過，日誌未被竄改。 */
    data object Valid : IntegrityResult()

    /** 簽章驗證失敗，日誌內容已被修改。 */
    data class Tampered(val reason: String) : IntegrityResult()

    /** 無法驗證（金鑰遺失或 signature 為空）。 */
    data class Unverifiable(val reason: String) : IntegrityResult()
}
