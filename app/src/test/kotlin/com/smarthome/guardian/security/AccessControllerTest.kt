package com.smarthome.guardian.security

import com.smarthome.guardian.domain.model.AccessRule
import com.smarthome.guardian.domain.model.DeviceOperation
import com.smarthome.guardian.domain.model.TimeWindow
import com.smarthome.guardian.domain.repository.AccessRuleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

/**
 * [AccessController] 單元測試。
 *
 * 驗證存取控制核心邏輯：
 * - Fail-closed（無規則 = 拒絕）
 * - Wildcard 萬用字元匹配
 * - 拒絕優先（Deny-overrides-Allow）
 * - 時間窗口限制
 * - 規則過期
 * - 已停用規則
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@DisplayName("AccessController")
class AccessControllerTest {

    private val repository = mockk<AccessRuleRepository>()
    private lateinit var controller: AccessController

    @BeforeEach
    fun setUp() {
        controller = AccessController(repository)
    }

    // ── Fail-closed ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fail-closed 預設拒絕")
    inner class FailClosedTests {

        @Test
        @DisplayName("無任何規則 → 拒絕存取")
        fun `no rules means access denied`() = runTest {
            every { repository.getRules() } returns flowOf(emptyList())

            assertFalse(
                controller.canUserOperate("user1", "device1", DeviceOperation.LOCK),
                "無規則時應預設拒絕（Fail-closed）",
            )
        }

        @Test
        @DisplayName("所有規則均停用 → 拒絕存取")
        fun `all disabled rules means access denied`() = runTest {
            val disabledRule = buildRule(
                userId    = "user1",
                deviceId  = "device1",
                ops       = setOf(DeviceOperation.LOCK),
                isEnabled = false,
            )
            every { repository.getRules() } returns flowOf(listOf(disabledRule))

            assertFalse(controller.canUserOperate("user1", "device1", DeviceOperation.LOCK))
        }

        @Test
        @DisplayName("規則已過期 → 拒絕存取")
        fun `expired rule means access denied`() = runTest {
            val expiredRule = buildRule(
                userId    = "user1",
                deviceId  = "device1",
                ops       = setOf(DeviceOperation.LOCK),
                expiresAt = System.currentTimeMillis() - 1_000L, // 已過期
            )
            every { repository.getRules() } returns flowOf(listOf(expiredRule))

            assertFalse(controller.canUserOperate("user1", "device1", DeviceOperation.LOCK))
        }
    }

    // ── 允許存取 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("允許存取")
    inner class AllowAccessTests {

        @Test
        @DisplayName("精確匹配規則含目標操作 → 允許")
        fun `exact match rule with operation allows access`() = runTest {
            val rule = buildRule(
                userId   = "user1",
                deviceId = "device1",
                ops      = setOf(DeviceOperation.LOCK, DeviceOperation.UNLOCK),
            )
            every { repository.getRules() } returns flowOf(listOf(rule))

            assertTrue(controller.canUserOperate("user1", "device1", DeviceOperation.LOCK))
            assertTrue(controller.canUserOperate("user1", "device1", DeviceOperation.UNLOCK))
        }

        @Test
        @DisplayName("用戶萬用字元 '*' → 對所有用戶允許")
        fun `wildcard user allows any user`() = runTest {
            val rule = buildRule(
                userId   = "*",  // 所有用戶
                deviceId = "device1",
                ops      = setOf(DeviceOperation.TOGGLE_ON),
            )
            every { repository.getRules() } returns flowOf(listOf(rule))

            assertTrue(controller.canUserOperate("guest",    "device1", DeviceOperation.TOGGLE_ON))
            assertTrue(controller.canUserOperate("admin",    "device1", DeviceOperation.TOGGLE_ON))
            assertTrue(controller.canUserOperate("stranger", "device1", DeviceOperation.TOGGLE_ON))
        }

        @Test
        @DisplayName("設備萬用字元 '*' → 對所有設備允許")
        fun `wildcard device allows any device`() = runTest {
            val rule = buildRule(
                userId   = "admin",
                deviceId = "*",  // 所有設備
                ops      = setOf(DeviceOperation.LOCK),
            )
            every { repository.getRules() } returns flowOf(listOf(rule))

            assertTrue(controller.canUserOperate("admin", "door_lock_front", DeviceOperation.LOCK))
            assertTrue(controller.canUserOperate("admin", "door_lock_back",  DeviceOperation.LOCK))
        }

        @Test
        @DisplayName("未過期的規則（expiresAt = 未來）→ 允許")
        fun `future expiry rule allows access`() = runTest {
            val rule = buildRule(
                userId    = "user1",
                deviceId  = "device1",
                ops       = setOf(DeviceOperation.UNLOCK),
                expiresAt = System.currentTimeMillis() + 86_400_000L, // 明天
            )
            every { repository.getRules() } returns flowOf(listOf(rule))

            assertTrue(controller.canUserOperate("user1", "device1", DeviceOperation.UNLOCK))
        }

        @Test
        @DisplayName("永不過期規則（expiresAt = null）→ 允許")
        fun `null expiry rule always allows access`() = runTest {
            val rule = buildRule(
                userId    = "user1",
                deviceId  = "device1",
                ops       = setOf(DeviceOperation.TOGGLE_ON),
                expiresAt = null,
            )
            every { repository.getRules() } returns flowOf(listOf(rule))

            assertTrue(controller.canUserOperate("user1", "device1", DeviceOperation.TOGGLE_ON))
        }
    }

    // ── 拒絕優先 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("拒絕優先（Deny-overrides-Allow）")
    inner class DenyOverrideTests {

        @Test
        @DisplayName("目標操作不在允許集合 → 拒絕")
        fun `operation not in allowed set is denied`() = runTest {
            val rule = buildRule(
                userId   = "user1",
                deviceId = "device1",
                ops      = setOf(DeviceOperation.TOGGLE_ON),  // 只允許開燈
            )
            every { repository.getRules() } returns flowOf(listOf(rule))

            // 試圖解鎖門（不在允許集合）
            assertFalse(controller.canUserOperate("user1", "device1", DeviceOperation.UNLOCK))
        }

        @Test
        @DisplayName("多條規則中有一條不含目標操作 → 拒絕（Deny wins）")
        fun `one rule denying operation blocks access despite other allowing`() = runTest {
            val allowRule = buildRule(
                userId   = "user1",
                deviceId = "device1",
                ops      = setOf(DeviceOperation.LOCK, DeviceOperation.UNLOCK),
            )
            val limitedRule = buildRule(
                userId   = "user1",
                deviceId = "device1",
                ops      = setOf(DeviceOperation.LOCK),  // 不含 UNLOCK
            )
            every { repository.getRules() } returns flowOf(listOf(allowRule, limitedRule))

            assertFalse(
                controller.canUserOperate("user1", "device1", DeviceOperation.UNLOCK),
                "limitedRule 中無 UNLOCK，應觸發 deny-override",
            )
            assertTrue(
                controller.canUserOperate("user1", "device1", DeviceOperation.LOCK),
                "兩條規則均含 LOCK，應允許",
            )
        }
    }

    // ── 時間窗口 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("時間窗口限制")
    inner class TimeWindowTests {

        @Test
        @DisplayName("timeWindow = null → 不限時間，應允許")
        fun `null time window always allows`() = runTest {
            val rule = buildRule(
                userId      = "user1",
                deviceId    = "device1",
                ops         = setOf(DeviceOperation.TOGGLE_ON),
                timeWindow  = null,
            )
            every { repository.getRules() } returns flowOf(listOf(rule))

            assertTrue(controller.canUserOperate("user1", "device1", DeviceOperation.TOGGLE_ON))
        }

        @Test
        @DisplayName("時間窗口涵蓋所有星期且全天 → 允許")
        fun `all-day all-week window allows at any time`() = runTest {
            val alwaysOpen = TimeWindow(
                daysOfWeek = DayOfWeek.entries.toSet(),  // 每天
                startTime  = LocalTime.MIN,              // 00:00
                endTime    = LocalTime.MAX,              // 23:59:59…
                timezone   = ZoneId.of("UTC"),
            )
            val rule = buildRule(
                userId     = "user1",
                deviceId   = "device1",
                ops        = setOf(DeviceOperation.LOCK),
                timeWindow = alwaysOpen,
            )
            every { repository.getRules() } returns flowOf(listOf(rule))

            assertTrue(controller.canUserOperate("user1", "device1", DeviceOperation.LOCK))
        }

        @Test
        @DisplayName("時間窗口只限明天，今天不允許")
        fun `window excluding today denies access`() = runTest {
            // 取今天的星期並排除它，製造「今天不在窗口內」的條件
            val today    = java.time.LocalDate.now().dayOfWeek
            val notToday = DayOfWeek.entries.filter { it != today }.toSet()
            if (notToday.isEmpty()) return@runTest  // 邊界：7天都排了

            val restrictedWindow = TimeWindow(
                daysOfWeek = notToday,
                startTime  = LocalTime.MIN,
                endTime    = LocalTime.MAX,
                timezone   = ZoneId.systemDefault(),
            )
            val rule = buildRule(
                userId     = "user1",
                deviceId   = "device1",
                ops        = setOf(DeviceOperation.UNLOCK),
                timeWindow = restrictedWindow,
            )
            every { repository.getRules() } returns flowOf(listOf(rule))

            assertFalse(
                controller.canUserOperate("user1", "device1", DeviceOperation.UNLOCK),
                "時間窗口不含今天，應拒絕存取",
            )
        }
    }

    // ── 角色隔離 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("角色邊界")
    inner class RoleBoundaryTests {

        @Test
        @DisplayName("訪客無規則時無法操作任何設備")
        fun `guest without rules cannot operate any device`() = runTest {
            // guest 沒有任何對應規則
            val adminRule = buildRule(
                userId   = "admin",
                deviceId = "device1",
                ops      = setOf(DeviceOperation.LOCK),
            )
            every { repository.getRules() } returns flowOf(listOf(adminRule))

            assertFalse(controller.canUserOperate("guest", "device1", DeviceOperation.LOCK))
        }

        @Test
        @DisplayName("管理員有萬用字元規則可操作所有設備")
        fun `admin with wildcard rules can operate any device`() = runTest {
            val adminRule = buildRule(
                userId   = "admin",
                deviceId = "*",
                ops      = DeviceOperation.entries.toSet(),  // 所有操作
            )
            every { repository.getRules() } returns flowOf(listOf(adminRule))

            assertTrue(controller.canUserOperate("admin", "any_device",   DeviceOperation.LOCK))
            assertTrue(controller.canUserOperate("admin", "other_device", DeviceOperation.REBOOT))
        }
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun buildRule(
        id: String = java.util.UUID.randomUUID().toString(),
        userId: String,
        deviceId: String,
        ops: Set<DeviceOperation>,
        timeWindow: TimeWindow? = null,
        isEnabled: Boolean = true,
        expiresAt: Long? = null,
    ) = AccessRule(
        id                = id,
        userId            = userId,
        deviceId          = deviceId,
        allowedOperations = ops,
        timeWindow        = timeWindow,
        isEnabled         = isEnabled,
        expiresAt         = expiresAt,
    )
}
