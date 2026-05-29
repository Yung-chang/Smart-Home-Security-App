package com.smarthome.guardian.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * [CryptoManager] 的單元測試。
 *
 * 注意：Android Keystore 在 JVM 單元測試環境中無法使用，
 * 因此本測試類別著重測試 CryptoManager 的公開契約與錯誤處理，
 * 實際 Keystore 加解密循環應在 Instrumented Test 中驗證。
 */
@DisplayName("CryptoManager")
class CryptoManagerTest {

    // ── EncryptedData 序列化測試（不需要 Keystore）────────────────────────────

    @Nested
    @DisplayName("EncryptedData 序列化")
    inner class EncryptedDataSerializationTest {

        @Test
        @DisplayName("序列化後可正確還原")
        fun `serialize and deserialize round trip`() {
            val original = EncryptedData(
                ciphertext = byteArrayOf(1, 2, 3, 4, 5),
                iv         = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100.toByte(), 110.toByte(), 120.toByte()),
                keyAlias   = "test_alias",
            )

            val serialized   = original.serialize()
            val deserialized = EncryptedData.deserialize(serialized)

            assertEquals(original, deserialized)
            assertArrayEquals(original.ciphertext, deserialized.ciphertext)
            assertArrayEquals(original.iv, deserialized.iv)
            assertEquals(original.keyAlias, deserialized.keyAlias)
        }

        @Test
        @DisplayName("格式無效時 deserialize 應拋出 IllegalArgumentException")
        fun `deserialize with invalid format throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                EncryptedData.deserialize("only_two:parts")
            }
        }

        @Test
        @DisplayName("空 ciphertext 仍可序列化還原")
        fun `empty ciphertext serializes correctly`() {
            val data = EncryptedData(
                ciphertext = byteArrayOf(),
                iv         = ByteArray(12) { it.toByte() },
                keyAlias   = "empty_alias",
            )
            val result = EncryptedData.deserialize(data.serialize())
            assertArrayEquals(byteArrayOf(), result.ciphertext)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", ":", "::", "a:", "a:b"])
        @DisplayName("各種無效格式均應拋出異常")
        fun `various invalid formats throw`(invalid: String) {
            assertThrows(Exception::class.java) {
                EncryptedData.deserialize(invalid)
            }
        }
    }

    // ── equals / hashCode 測試 ────────────────────────────────────────────────

    @Nested
    @DisplayName("EncryptedData equals/hashCode")
    inner class EncryptedDataEqualityTest {

        @Test
        @DisplayName("相同內容的 EncryptedData 應相等")
        fun `same content instances are equal`() {
            val a = EncryptedData(byteArrayOf(1, 2), byteArrayOf(3, 4), "alias")
            val b = EncryptedData(byteArrayOf(1, 2), byteArrayOf(3, 4), "alias")
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        @DisplayName("不同 ciphertext 的 EncryptedData 應不相等")
        fun `different ciphertext instances are not equal`() {
            val a = EncryptedData(byteArrayOf(1, 2), byteArrayOf(3, 4), "alias")
            val b = EncryptedData(byteArrayOf(5, 6), byteArrayOf(3, 4), "alias")
            assertNotEquals(a, b)
        }
    }
}
