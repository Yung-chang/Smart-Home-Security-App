package com.smarthome.guardian.domain.model

/**
 * 門鎖進出記錄（用於 Timeline 顯示）。
 *
 * @property id           記錄唯一識別碼
 * @property deviceId     門鎖設備 ID
 * @property userId       操作使用者 ID（null = 匿名/實體鑰匙）
 * @property userName     顯示名稱
 * @property action       操作類型（解鎖/上鎖/嘗試失敗）
 * @property method       解鎖方式
 * @property timestamp    操作時間 epoch 毫秒
 * @property isSuccessful 操作是否成功
 */
data class AccessLog(
    val id: String,
    val deviceId: String,
    val userId: String? = null,
    val userName: String = "未知使用者",
    val action: AccessAction,
    val method: UnlockMethod,
    val timestamp: Long,
    val isSuccessful: Boolean = true,
)

enum class AccessAction(val displayName: String) {
    UNLOCK("解鎖"),
    LOCK("上鎖"),
    FAILED_ATTEMPT("嘗試失敗"),
    TEMP_CODE_USED("臨時密碼"),
}

enum class UnlockMethod(val displayName: String) {
    APP("APP 控制"),
    FINGERPRINT("指紋"),
    PIN("PIN 碼"),
    TEMP_CODE("臨時密碼"),
    PHYSICAL_KEY("實體鑰匙"),
    AUTO_LOCK("自動上鎖"),
}
