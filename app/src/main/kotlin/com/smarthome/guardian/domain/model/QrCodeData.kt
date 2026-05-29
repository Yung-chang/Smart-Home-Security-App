package com.smarthome.guardian.domain.model

import android.graphics.Bitmap

/**
 * 訪客臨時存取 QR Code 資料。
 *
 * QR Code 編碼內容為後端簽章的 JSON Token，伺服器驗證後
 * 允許訪客在 [expiresAt] 前操作 [allowedDeviceIds] 內的設備。
 *
 * @property id                唯一識別碼（同時作為 revoke 的 key）
 * @property guestLabel        顯示標籤（訪客名稱 / 用途）
 * @property allowedDeviceIds  允許操作的設備 ID 清單
 * @property allowedOperations 允許的操作（預設只讀）
 * @property expiresAt         到期 Unix epoch 毫秒
 * @property createdAt         建立時間
 * @property isRevoked         是否已撤銷
 * @property qrPayload         原始 JSON 字串（後端 HMAC 簽章）
 * @property bitmap            ZXing 產生的 Bitmap（顯示用，不持久化）
 */
data class QrCodeData(
    val id: String,
    val guestLabel: String,
    val allowedDeviceIds: List<String>,
    val allowedOperations: Set<DeviceOperation> = setOf(DeviceOperation.READ),
    val expiresAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isRevoked: Boolean = false,
    val qrPayload: String = "",
    val bitmap: Bitmap? = null,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    val isActive:  Boolean get() = !isRevoked && !isExpired
}
