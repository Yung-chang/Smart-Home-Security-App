package com.smarthome.guardian.data.repository

import com.smarthome.guardian.domain.model.AccessRule
import com.smarthome.guardian.domain.model.DeviceOperation
import com.smarthome.guardian.domain.repository.AccessRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AccessRuleRepository] 的記憶體實作。
 * PROMPT 09 完成後接入 Room DB + REST API 同步。
 */
@Singleton
class AccessRuleRepositoryImpl @Inject constructor() : AccessRuleRepository {

    private val _rules = MutableStateFlow<List<AccessRule>>(emptyList())

    override fun getRules(): Flow<List<AccessRule>> = _rules

    override fun getRulesForUser(userId: String): Flow<List<AccessRule>> =
        _rules.map { list -> list.filter { it.userId == userId || it.userId == "*" } }

    override fun getRulesForDevice(deviceId: String): Flow<List<AccessRule>> =
        _rules.map { list -> list.filter { it.deviceId == deviceId || it.deviceId == "*" } }

    override suspend fun addRule(rule: AccessRule): Result<Unit> = runCatching {
        _rules.value = _rules.value + rule
        Timber.d("Rule added: ${rule.id}")
    }

    override suspend fun updateRule(rule: AccessRule): Result<Unit> = runCatching {
        _rules.value = _rules.value.map { if (it.id == rule.id) rule else it }
    }

    override suspend fun deleteRule(ruleId: String): Result<Unit> = runCatching {
        _rules.value = _rules.value.filter { it.id != ruleId }
        Timber.d("Rule deleted: $ruleId")
    }

    override suspend fun toggleRule(ruleId: String, enabled: Boolean): Result<Unit> = runCatching {
        _rules.value = _rules.value.map {
            if (it.id == ruleId) it.copy(isEnabled = enabled) else it
        }
    }

    /**
     * 衝突偵測：找出針對相同 userId+deviceId 但操作集合互相矛盾的規則對。
     *
     * 衝突定義：兩條規則 A、B，當：
     * - 適用相同 userId（或一方為 "*"）
     * - 適用相同 deviceId（或一方為 "*"）
     * - 但 allowedOperations 不完全相同（一條允許而另一條不允許某操作）
     */
    override fun detectConflicts(rules: List<AccessRule>): List<Pair<AccessRule, AccessRule>> {
        val active   = rules.filter { it.isEnabled }
        val conflicts = mutableListOf<Pair<AccessRule, AccessRule>>()

        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val a = active[i]
                val b = active[j]
                if (isConflicting(a, b)) conflicts += Pair(a, b)
            }
        }
        return conflicts
    }

    private fun isConflicting(a: AccessRule, b: AccessRule): Boolean {
        val userMatch   = a.userId == b.userId || a.userId == "*" || b.userId == "*"
        val deviceMatch = a.deviceId == b.deviceId || a.deviceId == "*" || b.deviceId == "*"
        if (!userMatch || !deviceMatch) return false
        // 操作集合有差異即為潛在衝突
        return a.allowedOperations != b.allowedOperations &&
               (a.allowedOperations.intersect(b.allowedOperations).size <
                maxOf(a.allowedOperations.size, b.allowedOperations.size))
    }
}
