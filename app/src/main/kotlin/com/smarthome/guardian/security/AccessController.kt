package com.smarthome.guardian.security

import com.smarthome.guardian.domain.model.AccessRule
import com.smarthome.guardian.domain.model.DeviceOperation
import com.smarthome.guardian.domain.repository.AccessRuleRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 存取控制決策引擎（Policy Decision Point）。
 *
 * ## 決策流程
 * 1. 從 [AccessRuleRepository] 取得所有啟用中且未過期的規則
 * 2. 篩選出匹配 (userId 或 "*") + (deviceId 或 "*") 的規則
 * 3. 過濾時間窗口（若規則有設定）
 * 4. **拒絕優先（Deny-overrides-Allow）**：
 *    - 若有任何匹配規則不包含此操作 → 拒絕
 *    - 若所有匹配規則都包含此操作 → 允許
 *    - 無匹配規則 → 預設拒絕（Fail-closed）
 * 5. 所有決策寫入稽核日誌（PROMPT 08 實作）
 *
 * ## OWASP M1 — 不當平台使用
 * 採用 Fail-closed 策略（無規則 = 拒絕），而非 Fail-open（無規則 = 允許），
 * 確保設備在規則設定缺失時不會意外開放存取。
 */
@Singleton
class AccessController @Inject constructor(
    private val accessRuleRepository: AccessRuleRepository,
) {

    /**
     * 判斷使用者是否可對指定設備執行指定操作。
     *
     * @param userId    操作使用者 ID
     * @param deviceId  目標設備 ID
     * @param operation 欲執行的操作
     * @return `true` 表示允許；`false` 表示拒絕
     */
    suspend fun canUserOperate(
        userId: String,
        deviceId: String,
        operation: DeviceOperation,
    ): Boolean {
        val allRules = accessRuleRepository.getRules().first()
        val now      = System.currentTimeMillis()

        // 1. 篩選：啟用中 + 未過期 + 適用此 userId/deviceId
        val matchingRules = allRules.filter { rule ->
            rule.isEnabled &&
            (rule.expiresAt == null || rule.expiresAt > now) &&
            (rule.userId   == userId   || rule.userId   == WILDCARD) &&
            (rule.deviceId == deviceId || rule.deviceId == WILDCARD)
        }

        // 無規則 → Fail-closed 預設拒絕
        if (matchingRules.isEmpty()) {
            Timber.d("ACL: no rules for userId=$userId deviceId=$deviceId op=$operation → DENY (fail-closed)")
            return false
        }

        // 2. 過濾時間窗口
        val timeValidRules = matchingRules.filter { rule ->
            rule.timeWindow == null || isWithinTimeWindow(rule)
        }

        if (timeValidRules.isEmpty()) {
            Timber.d("ACL: all rules outside time window → DENY")
            return false
        }

        // 3. 拒絕優先：若有任何有效規則不含此操作 → 拒絕
        val hasExplicitDeny = timeValidRules.any { operation !in it.allowedOperations }
        if (hasExplicitDeny) {
            Timber.d("ACL: deny-override triggered → DENY userId=$userId op=$operation")
            return false
        }

        // 4. 所有有效規則均允許 → 允許
        Timber.d("ACL: ALLOW userId=$userId deviceId=$deviceId op=$operation")
        // TODO PROMPT 08: auditLogger.log(DEVICE_CONTROL, deviceId, "ALLOW op=$operation")
        return true
    }

    /**
     * 批次判斷操作集合的允許情況。
     * 用於設備卡片渲染：預先知道哪些操作可顯示為啟用。
     *
     * @return 每個操作對應的允許狀態 Map
     */
    suspend fun getAllowedOperations(
        userId: String,
        deviceId: String,
    ): Map<DeviceOperation, Boolean> =
        DeviceOperation.values().associateWith { op ->
            canUserOperate(userId, deviceId, op)
        }

    // ── 時間窗口驗證 ──────────────────────────────────────────────────────────

    private fun isWithinTimeWindow(rule: AccessRule): Boolean {
        val window = rule.timeWindow ?: return true
        val now    = LocalDateTime.now(window.timezone)
        val today  = now.dayOfWeek

        if (today !in window.daysOfWeek) return false

        val current = now.toLocalTime()
        return current >= window.startTime && current < window.endTime
    }

    companion object {
        /** 通用萬用字元，匹配所有使用者或所有設備。 */
        const val WILDCARD = "*"
    }
}
