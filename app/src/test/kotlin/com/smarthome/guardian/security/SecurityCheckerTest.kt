package com.smarthome.guardian.security

import android.content.Context
import android.content.ContentResolver
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [SecurityChecker] 的單元測試。
 *
 * Build 屬性與 Settings 需透過 Mockk 的 mockkStatic 模擬，
 * 以便在 JVM 環境中測試裝置偵測邏輯。
 *
 * 注意：RootBeer 呼叫在 JVM 單元測試中無法實際執行，
 * 此部分邏輯應在 Instrumented Test 中覆蓋。
 */
@DisplayName("SecurityChecker")
class SecurityCheckerTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── SecurityCheckResult ────────────────────────────────────────────────────

    @Nested
    @DisplayName("SecurityCheckResult")
    inner class SecurityCheckResultTest {

        @Test
        @DisplayName("無違規時 isPassed 應為 true")
        fun `no violations means passed`() {
            val result = SecurityCheckResult(violations = emptyList(), isBypassed = false)
            assertTrue(result.isPassed)
            assertFalse(result.hasHighRiskViolation)
        }

        @Test
        @DisplayName("含有 ROOT_DETECTED 時 hasHighRiskViolation 應為 true")
        fun `root detected is high risk`() {
            val result = SecurityCheckResult(
                violations = listOf(SecurityViolation.ROOT_DETECTED),
                isBypassed = false,
            )
            assertFalse(result.isPassed)
            assertTrue(result.hasHighRiskViolation)
        }

        @Test
        @DisplayName("含有 EMULATOR_DETECTED 時 hasHighRiskViolation 應為 true")
        fun `emulator detected is high risk`() {
            val result = SecurityCheckResult(
                violations = listOf(SecurityViolation.EMULATOR_DETECTED),
                isBypassed = false,
            )
            assertTrue(result.hasHighRiskViolation)
        }

        @Test
        @DisplayName("只有 USB_DEBUG_ENABLED 時 hasHighRiskViolation 應為 false")
        fun `usb debug alone is not high risk`() {
            val result = SecurityCheckResult(
                violations = listOf(SecurityViolation.USB_DEBUG_ENABLED),
                isBypassed = false,
            )
            assertFalse(result.isPassed)
            assertFalse(result.hasHighRiskViolation)
        }

        @Test
        @DisplayName("isBypassed 為 true 且無違規時 isPassed 應為 true")
        fun `bypassed result with no violations is passed`() {
            val result = SecurityCheckResult(violations = emptyList(), isBypassed = true)
            assertTrue(result.isPassed)
        }
    }

    // ── SecurityViolation ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("SecurityViolation enum")
    inner class SecurityViolationTest {

        @Test
        @DisplayName("所有違規類型均應存在")
        fun `all violation types exist`() {
            val values = SecurityViolation.values()
            assertTrue(values.contains(SecurityViolation.ROOT_DETECTED))
            assertTrue(values.contains(SecurityViolation.EMULATOR_DETECTED))
            assertTrue(values.contains(SecurityViolation.USB_DEBUG_ENABLED))
            assertTrue(values.contains(SecurityViolation.ADB_ENABLED))
        }

        @Test
        @DisplayName("違規清單應有 4 種類型")
        fun `there are exactly 4 violation types`() {
            assertEquals(4, SecurityViolation.values().size)
        }
    }
}
