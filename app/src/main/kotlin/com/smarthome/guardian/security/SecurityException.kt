package com.smarthome.guardian.security

/**
 * 所有安全相關錯誤的基礎類別。
 * 子類別區分不同的失敗原因，讓呼叫端可精確處理。
 */
sealed class SecurityException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Android Keystore 金鑰產生或存取失敗。 */
class KeystoreException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

/** AES-GCM 加密操作失敗（IV 錯誤、金鑰遺失等）。 */
class EncryptionException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

/** AES-GCM 解密操作失敗（資料竄改、IV 不符等）。 */
class DecryptionException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

/** JWT Token 格式無效或解析失敗。 */
class TokenParseException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

/** HMAC 簽章驗證失敗（資料已被竄改）。 */
class IntegrityViolationException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

/** 裝置未通過安全性檢查（Root / 模擬器 / ADB）。 */
class DeviceSecurityException(
    message: String,
    val violations: List<SecurityViolation>,
    cause: Throwable? = null,
) : SecurityException(message, cause)
