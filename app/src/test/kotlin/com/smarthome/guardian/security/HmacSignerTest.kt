package com.smarthome.guardian.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.crypto.spec.SecretKeySpec

/**
 * [HmacSigner] 的單元測試。
 *
 * 使用 [SecretKeySpec]（非 Keystore 金鑰）作為測試金鑰，
 * 因為 Android Keystore 在 JVM 單元測試中無法使用。
 */
@DisplayName("HmacSigner")
class HmacSignerTest {

    private val testKeyBytes = ByteArray(32) { it.toByte() } // 32 bytes = 256 bits
    private val testKey = SecretKeySpec(testKeyBytes, "HmacSHA256")

    private lateinit var signerWithKey: HmacSigner
    private lateinit var signerWithoutKey: HmacSigner

    @BeforeEach
    fun setUp() {
        signerWithKey    = HmacSigner(keystoreKey = testKey)
        signerWithoutKey = HmacSigner(keystoreKey = null)
    }

    // ── sign(data, key: ByteArray) ────────────────────────────────────────────

    @Nested
    @DisplayName("sign(data, key: ByteArray)")
    inner class SignWithByteArrayKeyTest {

        @Test
        @DisplayName("相同資料與金鑰應產生相同簽章")
        fun `same data and key produce same signature`() {
            val sig1 = signerWithoutKey.sign("hello world", testKeyBytes)
            val sig2 = signerWithoutKey.sign("hello world", testKeyBytes)
            assertEquals(sig1, sig2)
        }

        @Test
        @DisplayName("不同資料應產生不同簽章")
        fun `different data produce different signatures`() {
            val sig1 = signerWithoutKey.sign("data1", testKeyBytes)
            val sig2 = signerWithoutKey.sign("data2", testKeyBytes)
            assertNotEquals(sig1, sig2)
        }

        @Test
        @DisplayName("不同金鑰應產生不同簽章")
        fun `different keys produce different signatures`() {
            val key2 = ByteArray(32) { (it + 1).toByte() }
            val sig1 = signerWithoutKey.sign("data", testKeyBytes)
            val sig2 = signerWithoutKey.sign("data", key2)
            assertNotEquals(sig1, sig2)
        }

        @Test
        @DisplayName("簽章應為有效的 Base64 字串")
        fun `signature is valid base64`() {
            val sig = signerWithoutKey.sign("test data", testKeyBytes)
            assertDoesNotThrow {
                java.util.Base64.getDecoder().decode(sig)
            }
        }

        @Test
        @DisplayName("空金鑰應拋出 IllegalArgumentException")
        fun `empty key throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                signerWithoutKey.sign("data", byteArrayOf())
            }
        }

        @Test
        @DisplayName("空字串資料應可正常簽章")
        fun `empty string data signs without error`() {
            assertDoesNotThrow {
                signerWithoutKey.sign("", testKeyBytes)
            }
        }
    }

    // ── verify(data, signature, key: ByteArray) ───────────────────────────────

    @Nested
    @DisplayName("verify(data, signature, key: ByteArray)")
    inner class VerifyWithByteArrayKeyTest {

        @Test
        @DisplayName("正確簽章驗證應回傳 true")
        fun `valid signature verifies successfully`() {
            val data = "audit_log_entry_12345"
            val sig  = signerWithoutKey.sign(data, testKeyBytes)
            assertTrue(signerWithoutKey.verify(data, sig, testKeyBytes))
        }

        @Test
        @DisplayName("篡改資料後驗證應回傳 false")
        fun `tampered data fails verification`() {
            val data    = "original data"
            val sig     = signerWithoutKey.sign(data, testKeyBytes)
            val tampered = "tampered data"
            assertFalse(signerWithoutKey.verify(tampered, sig, testKeyBytes))
        }

        @Test
        @DisplayName("錯誤簽章驗證應回傳 false")
        fun `wrong signature fails verification`() {
            val data     = "data"
            val wrongSig = "aW52YWxpZF9zaWduYXR1cmU=" // base64("invalid_signature")
            assertFalse(signerWithoutKey.verify(data, wrongSig, testKeyBytes))
        }

        @Test
        @DisplayName("不同金鑰驗證應回傳 false")
        fun `wrong key fails verification`() {
            val data = "data"
            val sig  = signerWithoutKey.sign(data, testKeyBytes)
            val key2 = ByteArray(32) { (it + 100).toByte() }
            assertFalse(signerWithoutKey.verify(data, sig, key2))
        }
    }

    // ── sign(data) / verify(data, signature) — Keystore 模式 ─────────────────

    @Nested
    @DisplayName("sign/verify 使用 Keystore 金鑰")
    inner class SignVerifyWithKeystoreKeyTest {

        @Test
        @DisplayName("Keystore 金鑰簽章後驗證應通過")
        fun `keystore key sign and verify round trip`() {
            val data = "device_command:lock:door01"
            val sig  = signerWithKey.sign(data)
            assertTrue(signerWithKey.verify(data, sig))
        }

        @Test
        @DisplayName("無 Keystore 金鑰時 sign 應拋出 KeystoreException")
        fun `sign without keystore key throws`() {
            assertThrows<KeystoreException> {
                signerWithoutKey.sign("data")
            }
        }

        @Test
        @DisplayName("無 Keystore 金鑰時 verify 應拋出 KeystoreException")
        fun `verify without keystore key throws`() {
            assertThrows<KeystoreException> {
                signerWithoutKey.verify("data", "sig")
            }
        }
    }

    // ── 防竄改情境測試 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("防竄改情境")
    inner class TamperingScenarioTest {

        @Test
        @DisplayName("稽核日誌欄位竄改後驗證應失敗")
        fun `audit log field tampering is detected`() {
            val original  = """{"userId":"alice","action":"DEVICE_TOGGLE","deviceId":"light-01"}"""
            val tampered  = """{"userId":"alice","action":"DEVICE_TOGGLE","deviceId":"door-lock-01"}"""
            val signature = signerWithoutKey.sign(original, testKeyBytes)

            assertTrue(signerWithoutKey.verify(original, signature, testKeyBytes))
            assertFalse(signerWithoutKey.verify(tampered, signature, testKeyBytes))
        }

        @Test
        @DisplayName("不同稽核日誌不可共用簽章")
        fun `signatures are not interchangeable between different logs`() {
            val log1 = """{"action":"LOGIN","userId":"alice"}"""
            val log2 = """{"action":"LOGOUT","userId":"alice"}"""

            val sig1 = signerWithoutKey.sign(log1, testKeyBytes)
            val sig2 = signerWithoutKey.sign(log2, testKeyBytes)

            assertFalse(signerWithoutKey.verify(log1, sig2, testKeyBytes))
            assertFalse(signerWithoutKey.verify(log2, sig1, testKeyBytes))
        }
    }
}
