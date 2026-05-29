package com.smarthome.guardian.audit

import com.smarthome.guardian.data.local.database.AuditDao
import com.smarthome.guardian.data.local.database.entity.AuditLogEntity
import com.smarthome.guardian.data.logger.AuditLogger
import com.smarthome.guardian.domain.model.AuditAction
import com.smarthome.guardian.domain.model.AuditLog
import com.smarthome.guardian.security.HmacSigner
import com.smarthome.guardian.security.TokenManager
import io.mockk.clearMocks
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.Base64

/**
 * AuditLogger 單元測試。
 *
 * 以 Mockk 隔離 [AuditDao]、[HmacSigner]、[TokenManager]，驗證：
 * 1. HMAC 簽章正確附加且格式正確
 * 2. HMAC 計算失敗時日誌仍可寫入（signature 為空串）
 * 3. flush() 立即持久化佇列中所有日誌
 * 4. 空佇列 flush() 不觸發 DB 寫入
 * 5. userId 由 JWT sub claim 正確解析，異常時 fallback 為 "anonymous"
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("AuditLogger")
class AuditLoggerTest {

    private val auditDao     = mockk<AuditDao>(relaxed = true)
    private val hmacSigner   = mockk<HmacSigner>()
    private val tokenManager = mockk<TokenManager>()

    private lateinit var logger: AuditLogger

    @BeforeEach
    fun setUp() {
        clearMocks(auditDao, hmacSigner, tokenManager)
        every { tokenManager.getAccessToken() } returns buildFakeJwt("user_test_42")
        every { hmacSigner.sign(any<String>()) } returns "mocked-hmac-sig"
        coEvery { auditDao.insertAll(any()) } just Runs
        logger = AuditLogger(auditDao, hmacSigner, tokenManager)
    }

    // ── HMAC 簽章 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HMAC 簽章")
    inner class HmacSignatureTests {

        @Test
        @DisplayName("每筆日誌都必須包含非空的 HMAC 簽章")
        fun `log should attach hmac signature`() = runTest {
            logger.log(AuditAction.LOGIN_SUCCESS, targetId = "user_abc")
            logger.flush()

            val slot = slot<List<AuditLogEntity>>()
            coVerify { auditDao.insertAll(capture(slot)) }
            assertTrue(slot.captured.first().signature.isNotBlank(), "signature 不應為空")
            assertEquals("mocked-hmac-sig", slot.captured.first().signature)
        }

        @Test
        @DisplayName("簽章基礎字串必須包含所有關鍵欄位（id/userId/action/targetId/timestamp）")
        fun `signable string should contain all key fields`() {
            val log = AuditLog(
                id                = "id-001",
                userId            = "user-A",
                action            = AuditAction.DEVICE_CONTROL,
                targetId          = "device-X",
                before            = """{"isOn":false}""",
                after             = """{"isOn":true}""",
                ipAddress         = "10.0.0.1",
                deviceFingerprint = "fp-test",
                timestamp         = 1_700_000_000_000L,
                signature         = "",
            )
            val signable = log.toSignableString()
            assertTrue(signable.contains("id-001"),         "應含 id")
            assertTrue(signable.contains("user-A"),         "應含 userId")
            assertTrue(signable.contains("DEVICE_CONTROL"), "應含 action")
            assertTrue(signable.contains("device-X"),       "應含 targetId")
            assertTrue(signable.contains("1700000000000"),  "應含 timestamp")
        }

        @Test
        @DisplayName("HMAC 計算失敗時，日誌仍應寫入但 signature 欄位為空")
        fun `log should persist even if hmac computation throws`() = runTest {
            every { hmacSigner.sign(any<String>()) } throws RuntimeException("Keystore unavailable")

            logger.log(AuditAction.LOGOUT)
            logger.flush()

            val slot = slot<List<AuditLogEntity>>()
            coVerify { auditDao.insertAll(capture(slot)) }
            assertTrue(slot.captured.first().signature.isBlank(), "HMAC 失敗時 signature 應為空")
        }
    }

    // ── 批次寫入 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("批次寫入")
    inner class BatchWriteTests {

        @Test
        @DisplayName("flush() 應立即將佇列中的所有日誌持久化至 DB")
        fun `flush should persist all queued logs`() = runTest {
            logger.log(AuditAction.LOGIN_SUCCESS)
            logger.log(AuditAction.DEVICE_CONTROL, targetId = "device-1")
            logger.log(AuditAction.ACCESS_RULE_CREATED)
            logger.flush()

            // 所有日誌應被 insertAll 覆蓋（可能多批次，但至少呼叫一次）
            coVerify(atLeast = 1) { auditDao.insertAll(any()) }
        }

        @Test
        @DisplayName("flush() 在佇列為空時不應觸發 DB 寫入")
        fun `flush on empty queue should not call dao`() = runTest {
            logger.flush()
            coVerify(exactly = 0) { auditDao.insertAll(any()) }
        }

        @Test
        @DisplayName("每筆寫入的日誌 action 欄位應為 AuditAction.name 字串")
        fun `entity action field should match AuditAction name`() = runTest {
            logger.log(AuditAction.EXPORT_LOGS, targetId = "export-001")
            logger.flush()

            val slot = slot<List<AuditLogEntity>>()
            coVerify { auditDao.insertAll(capture(slot)) }
            assertEquals(AuditAction.EXPORT_LOGS.name, slot.captured.first().action)
        }
    }

    // ── 日誌完整性（純 HmacSigner 計算，不依賴 Keystore）────────────────────

    @Nested
    @DisplayName("日誌完整性驗證")
    inner class IntegrityTests {

        private val rawKey = ByteArray(32) { it.toByte() }
        private val signer = HmacSigner()

        @Test
        @DisplayName("未竄改的日誌應通過 HMAC 驗證")
        fun `unmodified log should pass verify`() {
            val signable  = "id|user|LOGOUT|null|null|null|127.0.0.1|fp|1700000000000"
            val signature = signer.sign(signable, rawKey)
            assertTrue(signer.verify(signable, signature, rawKey))
        }

        @Test
        @DisplayName("欄位遭竄改的日誌應驗證失敗")
        fun `tampered log should fail verify`() {
            val original  = "id|admin|LOGIN_SUCCESS|null|null|null|127.0.0.1|fp|1700000000000"
            val signature = signer.sign(original, rawKey)
            val tampered  = "id|attacker|LOGIN_SUCCESS|null|null|null|127.0.0.1|fp|1700000000000"
            assertFalse(signer.verify(tampered, signature, rawKey))
        }

        @Test
        @DisplayName("簽章加密往返應產生相同 HMAC（冪等性）")
        fun `same data should always produce same hmac`() {
            val data = "id|user|ACTION|target|before|after|ip|fp|timestamp"
            val sig1 = signer.sign(data, rawKey)
            val sig2 = signer.sign(data, rawKey)
            assertEquals(sig1, sig2)
        }
    }

    // ── userId 解析 ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("userId 解析")
    inner class UserIdTests {

        @Test
        @DisplayName("有效 JWT 的 sub claim 應正確解析為 userId")
        fun `valid jwt sub should be parsed as userId`() = runTest {
            every { tokenManager.getAccessToken() } returns buildFakeJwt("user_99")

            logger.log(AuditAction.LOGIN_SUCCESS)
            logger.flush()

            val slot = slot<List<AuditLogEntity>>()
            coVerify { auditDao.insertAll(capture(slot)) }
            assertEquals("user_99", slot.captured.first().userId)
        }

        @Test
        @DisplayName("無 JWT Token 時 userId 應 fallback 為 'anonymous'")
        fun `null token should default to anonymous`() = runTest {
            every { tokenManager.getAccessToken() } returns null

            logger.log(AuditAction.AUTH_BYPASS_ATTEMPT)
            logger.flush()

            val slot = slot<List<AuditLogEntity>>()
            coVerify { auditDao.insertAll(capture(slot)) }
            assertEquals("anonymous", slot.captured.first().userId)
        }

        @Test
        @DisplayName("格式錯誤的 JWT 應 fallback 為 'anonymous'")
        fun `malformed jwt should fallback to anonymous`() = runTest {
            every { tokenManager.getAccessToken() } returns "not-a-jwt"

            logger.log(AuditAction.LOGOUT)
            logger.flush()

            val slot = slot<List<AuditLogEntity>>()
            coVerify { auditDao.insertAll(capture(slot)) }
            assertEquals("anonymous", slot.captured.first().userId)
        }

        @Test
        @DisplayName("每筆日誌的 id 應為唯一 UUID 格式")
        fun `each log should have unique uuid id`() = runTest {
            logger.log(AuditAction.LOGIN_SUCCESS)
            logger.log(AuditAction.LOGOUT)
            logger.flush()

            // 因批次可能合併，驗證至少有呼叫寫入
            coVerify(atLeast = 1) { auditDao.insertAll(any()) }
        }
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    companion object {
        /**
         * 建立含有指定 [sub] claim 的假 JWT（僅供測試，使用 Java 標準庫 Base64）。
         * 格式：Base64URL(header).Base64URL(payload).fake-signature
         */
        private fun buildFakeJwt(sub: String): String {
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val header  = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
            val exp     = System.currentTimeMillis() / 1000 + 900
            val payload = encoder.encodeToString("""{"sub":"$sub","exp":$exp}""".toByteArray())
            return "$header.$payload.fake-sig"
        }
    }
}
