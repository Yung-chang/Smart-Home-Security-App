package com.smarthome.guardian.presentation.audit

import android.net.Uri
import androidx.paging.PagingData
import com.smarthome.guardian.domain.model.AuditAction
import com.smarthome.guardian.domain.model.AuditCategory
import com.smarthome.guardian.domain.model.AuditFilter
import com.smarthome.guardian.domain.model.AuditLog
import com.smarthome.guardian.domain.model.IntegrityResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 稽核日誌畫面的整合 UI 狀態。
 *
 * @property logs             Paging 3 Flow（由 ViewModel 提供，含日誌列表）
 * @property filter           當前篩選條件
 * @property integrityResults 已驗證日誌的完整性結果快取（logId → 結果）
 * @property expandedLogIds   已展開詳情的日誌 ID 集合
 * @property isExporting      匯出操作進行中
 * @property exportUri        匯出完成的檔案 Uri（消費後應清除）
 * @property error            錯誤訊息（消費後應清除）
 * @property showFilterSheet  是否顯示篩選 BottomSheet
 * @property searchBarVisible 是否顯示搜尋列
 */
data class AuditUiState(
    val logs: Flow<PagingData<AuditLog>>             = emptyFlow(),
    val filter: AuditFilter                          = AuditFilter.NONE,
    val integrityResults: Map<String, IntegrityResult> = emptyMap(),
    val expandedLogIds: Set<String>                  = emptySet(),
    val isExporting: Boolean                         = false,
    val exportUri: Uri?                              = null,
    val error: String?                               = null,
    val showFilterSheet: Boolean                     = false,
    val searchBarVisible: Boolean                    = false,
)
