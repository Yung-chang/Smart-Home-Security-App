package com.smarthome.guardian.presentation.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.smarthome.guardian.domain.model.AuditFilter
import com.smarthome.guardian.domain.model.ExportFormat
import com.smarthome.guardian.domain.model.IntegrityResult
import com.smarthome.guardian.domain.repository.AuditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 稽核日誌畫面的 ViewModel。
 *
 * 主要職責：
 * 1. 從 [AuditRepository] 取得 Paging 3 日誌串流並以 [cachedIn] 快取
 * 2. 管理搜尋列、篩選 BottomSheet 的顯示狀態
 * 3. 觸發 HMAC 完整性驗證（結果快取於 [AuditUiState.integrityResults]）
 * 4. 執行 CSV / PDF 匯出
 */
@HiltViewModel
class AuditViewModel @Inject constructor(
    private val auditRepository: AuditRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditUiState())
    val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

    init {
        loadLogs(AuditFilter.NONE)
    }

    // ── 篩選 ──────────────────────────────────────────────────────────────────

    /**
     * 套用篩選條件並重新載入分頁資料。
     * 每次呼叫都會建立新的 Pager，確保 Room PagingSource 重新查詢。
     */
    fun applyFilter(filter: AuditFilter) {
        loadLogs(filter)
        _uiState.update { it.copy(filter = filter, showFilterSheet = false) }
    }

    fun clearFilter() = applyFilter(AuditFilter.NONE)

    fun updateSearchQuery(query: String) {
        val newFilter = _uiState.value.filter.copy(searchQuery = query)
        loadLogs(newFilter)
        _uiState.update { it.copy(filter = newFilter) }
    }

    fun toggleFilterSheet() = _uiState.update { it.copy(showFilterSheet = !it.showFilterSheet) }

    fun toggleSearchBar() = _uiState.update { state ->
        val visible = !state.searchBarVisible
        // 隱藏搜尋列時清除搜尋字串
        if (!visible && state.filter.searchQuery.isNotBlank()) {
            val cleared = state.filter.copy(searchQuery = "")
            loadLogs(cleared)
            state.copy(searchBarVisible = false, filter = cleared)
        } else {
            state.copy(searchBarVisible = visible)
        }
    }

    // ── 展開/收合詳情 ─────────────────────────────────────────────────────────

    fun toggleExpand(logId: String) {
        _uiState.update { state ->
            val newSet = if (logId in state.expandedLogIds)
                state.expandedLogIds - logId
            else
                state.expandedLogIds + logId
            state.copy(expandedLogIds = newSet)
        }
    }

    // ── 完整性驗證 ────────────────────────────────────────────────────────────

    /**
     * 驗證指定日誌的 HMAC 簽章。
     * 結果快取於 [AuditUiState.integrityResults]，避免重複驗證。
     */
    fun verifyIntegrity(logId: String) {
        if (logId in _uiState.value.integrityResults) return
        viewModelScope.launch {
            val result = auditRepository.verifyLogIntegrity(logId)
            _uiState.update { state ->
                state.copy(integrityResults = state.integrityResults + (logId to result))
            }
            if (result is IntegrityResult.Tampered) {
                Timber.w("TAMPERED log detected: $logId — ${result.reason}")
            }
        }
    }

    // ── 匯出 ──────────────────────────────────────────────────────────────────

    /**
     * 匯出當前篩選結果為 CSV 或 PDF。
     * 匯出完成後 [AuditUiState.exportUri] 會更新，UI 收到後可觸發 ShareSheet。
     */
    fun exportLogs(format: ExportFormat) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            auditRepository.exportLogs(_uiState.value.filter, format)
                .onSuccess { uri ->
                    _uiState.update { it.copy(isExporting = false, exportUri = uri) }
                    Timber.d("Export complete: $uri")
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isExporting = false, error = e.message) }
                    Timber.e(e, "Export failed")
                }
        }
    }

    // ── 清除事件 ──────────────────────────────────────────────────────────────

    fun clearExportUri() = _uiState.update { it.copy(exportUri = null) }
    fun clearError()     = _uiState.update { it.copy(error = null) }

    // ── 私有 ──────────────────────────────────────────────────────────────────

    private fun loadLogs(filter: AuditFilter) {
        val paged = auditRepository.getLogs(filter).cachedIn(viewModelScope)
        _uiState.update { it.copy(logs = paged, filter = filter) }
    }
}
