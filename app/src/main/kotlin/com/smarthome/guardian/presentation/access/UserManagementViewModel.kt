package com.smarthome.guardian.presentation.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarthome.guardian.domain.model.AccessRule
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole
import com.smarthome.guardian.domain.repository.AccessRuleRepository
import com.smarthome.guardian.domain.repository.DeviceRepository
import com.smarthome.guardian.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 存取控制管理的 ViewModel。
 *
 * 合併使用者清單、規則清單、QR Code 清單三個資料流，
 * 同時執行規則衝突偵測。
 */
@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val accessRuleRepository: AccessRuleRepository,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    init {
        observeUsers()
        observeRules()
        observeQrCodes()
        observeDevices()
    }

    // ── Tab 切換 ──────────────────────────────────────────────────────────────

    fun selectTab(index: Int) = _uiState.update { it.copy(selectedTab = index) }

    // ── 用戶管理 ──────────────────────────────────────────────────────────────

    fun selectUser(user: User?)   = _uiState.update { it.copy(selectedUser = user) }
    fun showInviteDialog()        = _uiState.update { it.copy(showInviteDialog = true) }
    fun dismissInviteDialog()     = _uiState.update { it.copy(showInviteDialog = false) }

    /**
     * 邀請新使用者。
     * @param email 受邀 Email
     * @param role  授予角色
     */
    fun inviteUser(email: String, role: UserRole) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showInviteDialog = false) }
            userRepository.inviteUser(email, role)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, successMessage = "邀請已發送至 $email") }
                    Timber.d("User invited: $email role=$role")
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /**
     * 更新使用者角色。
     */
    fun updateUserRole(userId: String, role: UserRole) {
        viewModelScope.launch {
            userRepository.updateUserRole(userId, role)
                .onSuccess { _uiState.update { it.copy(successMessage = "角色已更新", selectedUser = null) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * 撤銷使用者存取（管理員操作）。
     */
    fun revokeUserAccess(userId: String) {
        viewModelScope.launch {
            userRepository.revokeUserAccess(userId)
                .onSuccess { _uiState.update { it.copy(successMessage = "已撤銷存取權限", selectedUser = null) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ── 存取規則 ──────────────────────────────────────────────────────────────

    fun showRuleWizard()   = _uiState.update { it.copy(showRuleWizard = true) }
    fun dismissRuleWizard() = _uiState.update { it.copy(showRuleWizard = false) }

    /**
     * 新增存取規則（精靈完成後呼叫）。
     */
    fun addRule(rule: AccessRule) {
        viewModelScope.launch {
            accessRuleRepository.addRule(rule)
                .onSuccess {
                    _uiState.update { it.copy(showRuleWizard = false, successMessage = "規則已新增") }
                    Timber.d("Rule added: ${rule.id}")
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * 切換規則啟用/停用。
     */
    fun toggleRule(ruleId: String, enabled: Boolean) {
        viewModelScope.launch {
            accessRuleRepository.toggleRule(ruleId, enabled)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * 刪除存取規則。
     */
    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            accessRuleRepository.deleteRule(ruleId)
                .onSuccess { _uiState.update { it.copy(successMessage = "規則已刪除") } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ── 臨時 QR Code ──────────────────────────────────────────────────────────

    /**
     * 生成訪客 QR Code。
     * @param deviceIds  允許存取的設備清單
     * @param expiresAt  到期時間（epoch 毫秒）
     * @param guestEmail 受邀訪客 Email（可選）
     */
    fun generateGuestQrCode(deviceIds: List<String>, expiresAt: Long, guestEmail: String? = null) {
        viewModelScope.launch {
            userRepository.generateGuestQrCode(deviceIds, expiresAt, guestEmail)
                .onSuccess { _uiState.update { it.copy(successMessage = "QR Code 已生成") } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * 撤銷 QR Code（立即失效）。
     */
    fun revokeQrCode(code: String) {
        viewModelScope.launch {
            userRepository.revokeQrCode(code)
                .onSuccess { _uiState.update { it.copy(successMessage = "QR Code 已撤銷") } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ── 清除訊息 ──────────────────────────────────────────────────────────────

    fun clearError()   = _uiState.update { it.copy(error = null) }
    fun clearSuccess() = _uiState.update { it.copy(successMessage = null) }

    // ── 私有訂閱 ──────────────────────────────────────────────────────────────

    private fun observeUsers() {
        viewModelScope.launch {
            userRepository.getUsers()
                .catch { e -> Timber.e(e) }
                .collect { users -> _uiState.update { it.copy(users = users, isLoading = false) } }
        }
    }

    private fun observeDevices() {
        viewModelScope.launch {
            deviceRepository.getDevices()
                .catch { e -> Timber.e(e, "Failed to load devices for rule wizard") }
                .collect { devices -> _uiState.update { it.copy(devices = devices) } }
        }
    }

    private fun observeRules() {
        viewModelScope.launch {
            accessRuleRepository.getRules()
                .catch { e -> Timber.e(e) }
                .collect { rules ->
                    val conflicts = accessRuleRepository.detectConflicts(rules)
                    _uiState.update { it.copy(rules = rules, conflictPairs = conflicts) }
                }
        }
    }

    private fun observeQrCodes() {
        viewModelScope.launch {
            userRepository.getQrCodes()
                .catch { e -> Timber.e(e) }
                .collect { codes -> _uiState.update { it.copy(qrCodes = codes) } }
        }
    }
}
