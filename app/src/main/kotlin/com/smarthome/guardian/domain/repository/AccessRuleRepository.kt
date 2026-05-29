package com.smarthome.guardian.domain.repository

import com.smarthome.guardian.domain.model.AccessRule
import kotlinx.coroutines.flow.Flow

/**
 * 存取規則的 Repository 介面。
 */
interface AccessRuleRepository {

    /** 訂閱所有存取規則。 */
    fun getRules(): Flow<List<AccessRule>>

    /** 依使用者 ID 篩選規則（含通用規則 userId="*"）。 */
    fun getRulesForUser(userId: String): Flow<List<AccessRule>>

    /** 依設備 ID 篩選規則（含通用規則 deviceId="*"）。 */
    fun getRulesForDevice(deviceId: String): Flow<List<AccessRule>>

    /** 新增規則。 */
    suspend fun addRule(rule: AccessRule): Result<Unit>

    /** 更新規則設定。 */
    suspend fun updateRule(rule: AccessRule): Result<Unit>

    /** 刪除規則。 */
    suspend fun deleteRule(ruleId: String): Result<Unit>

    /** 切換規則啟用/停用狀態。 */
    suspend fun toggleRule(ruleId: String, enabled: Boolean): Result<Unit>

    /**
     * 偵測規則衝突（兩條規則針對同一 userId/deviceId 但 allowedOperations 互相矛盾）。
     * @return 衝突規則對清單
     */
    fun detectConflicts(rules: List<AccessRule>): List<Pair<AccessRule, AccessRule>>
}
