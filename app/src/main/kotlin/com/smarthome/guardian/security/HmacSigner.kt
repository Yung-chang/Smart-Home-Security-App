package com.smarthome.guardian.security

import java.util.Base64
import timber.log.Timber
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HMAC-SHA256 資料簽章與驗證工具。
 *
 * 提供兩種使用模式：
 * 1. **Keystore 模式**（預設）：使用由 [SecurityModule] 注入的 Android Keystore 金鑰，
 *    私鑰不可匯出，適用於稽核日誌簽章。
 * 2. **ByteArray 模式**：傳入原始位元組金鑰，適用於設備指令簽章（每台設備獨立金鑰）。
 *
 * ## OWASP M4 — 不安全驗證對應
 * 所有設備控制指令與稽核日誌均須附帶 HMAC 簽章，
 * 確保指令在傳輸過程中未被竄改。
 *
 * @param keystoreKey 由 Android Keystore 提供的 HMAC-SHA256 金鑰（可為 null，此時只能使用 ByteArray 模式）
 */
@Singleton
class HmacSigner @Inject constructor(
    private val keystoreKey: SecretKey? = null,
) {

    // ── 簽章 ──────────────────────────────────────────────────────────────────

    /**
     * 使用 Keystore 金鑰對 [data] 計算 HMAC-SHA256 簽章。
     *
     * @param data 欲簽章的原始字串（UTF-8 編碼）
     * @return Base64 編碼的 HMAC-SHA256 簽章字串
     * @throws SecurityException Keystore 金鑰不可用或簽章失敗時
     */
    fun sign(data: String): String {
        val key = keystoreKey
            ?: throw KeystoreException("No Keystore key available — use sign(data, key) overload")
        return computeHmac(data.toByteArray(Charsets.UTF_8), key)
    }

    /**
     * 使用指定的 [key]（原始位元組）對 [data] 計算 HMAC-SHA256 簽章。
     *
     * 適用於設備指令簽章，各設備使用獨立的對稱金鑰。
     *
     * @param data 欲簽章的原始字串（UTF-8 編碼）
     * @param key  HMAC 金鑰原始位元組（建議 32 bytes / 256 bits）
     * @return Base64 編碼的 HMAC-SHA256 簽章字串
     * @throws IntegrityViolationException 簽章計算失敗時
     */
    fun sign(data: String, key: ByteArray): String {
        require(key.isNotEmpty()) { "HMAC key must not be empty" }
        val secretKey = SecretKeySpec(key, HMAC_ALGORITHM)
        return computeHmac(data.toByteArray(Charsets.UTF_8), secretKey)
    }

    // ── 驗證 ──────────────────────────────────────────────────────────────────

    /**
     * 使用 Keystore 金鑰驗證 [signature] 是否為 [data] 的合法簽章。
     *
     * 採用常數時間比較（[MessageDigest.isEqual]）防止時序攻擊。
     *
     * @param data      原始資料字串
     * @param signature 欲驗證的 Base64 簽章字串
     * @return `true` 表示簽章合法；`false` 表示資料已被竄改
     * @throws SecurityException Keystore 金鑰不可用時
     */
    fun verify(data: String, signature: String): Boolean {
        val key = keystoreKey
            ?: throw KeystoreException("No Keystore key available — use verify(data, signature, key) overload")
        return runCatching {
            val expected = computeHmac(data.toByteArray(Charsets.UTF_8), key)
            constantTimeEquals(expected, signature)
        }.getOrElse { cause ->
            Timber.e(cause, "HMAC verification error")
            false
        }
    }

    /**
     * 使用指定的 [key]（原始位元組）驗證 [signature] 是否為 [data] 的合法簽章。
     *
     * @param data      原始資料字串
     * @param signature 欲驗證的 Base64 簽章字串
     * @param key       HMAC 金鑰原始位元組
     * @return `true` 表示簽章合法
     */
    fun verify(data: String, signature: String, key: ByteArray): Boolean {
        require(key.isNotEmpty()) { "HMAC key must not be empty" }
        return runCatching {
            val secretKey = SecretKeySpec(key, HMAC_ALGORITHM)
            val expected  = computeHmac(data.toByteArray(Charsets.UTF_8), secretKey)
            constantTimeEquals(expected, signature)
        }.getOrElse { cause ->
            Timber.e(cause, "HMAC verification error")
            false
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private fun computeHmac(data: ByteArray, key: SecretKey): String {
        return runCatching {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(key)
            val hmacBytes = mac.doFinal(data)
            Base64.getEncoder().encodeToString(hmacBytes)
        }.getOrElse { cause ->
            throw IntegrityViolationException("HMAC computation failed", cause)
        }
    }

    /**
     * 常數時間字串比較，防止時序攻擊（Timing Attack）。
     * 不論字串內容為何，比較時間均相同。
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
