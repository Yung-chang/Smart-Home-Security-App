package com.smarthome.guardian.security

import java.util.Base64

/**
 * AES-256-GCM 加密結果的容器。
 *
 * @property ciphertext 加密後的密文（含 GCM authentication tag）
 * @property iv         初始向量（12 bytes，GCM 標準長度）
 * @property keyAlias   用於加密的 Android Keystore 金鑰別名
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val keyAlias: String,
) {
    /**
     * 序列化為 "iv_base64:ciphertext_base64:keyAlias" 格式，供儲存或傳輸使用。
     */
    fun serialize(): String {
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val ctB64 = Base64.getEncoder().encodeToString(ciphertext)
        return "$ivB64:$ctB64:$keyAlias"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedData) return false
        return ciphertext.contentEquals(other.ciphertext) &&
            iv.contentEquals(other.iv) &&
            keyAlias == other.keyAlias
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + keyAlias.hashCode()
        return result
    }

    companion object {
        /**
         * 從 [serialize] 產生的字串還原 [EncryptedData]。
         * @throws IllegalArgumentException 格式不符時拋出
         */
        fun deserialize(value: String): EncryptedData {
            val parts = value.split(":")
            require(parts.size == 3 && parts[0].isNotEmpty()) { "Invalid EncryptedData format" }
            return EncryptedData(
                ciphertext = Base64.getDecoder().decode(parts[1]),
                iv = Base64.getDecoder().decode(parts[0]),
                keyAlias = parts[2],
            )
        }
    }
}
