package com.smarthome.guardian.domain.model

/**
 * 稽核日誌篩選條件。
 *
 * 所有欄位均為可選：預設值代表「不限制」（全選）。
 * 使用 [isActive] 判斷是否有任何篩選條件已啟用。
 *
 * @property searchQuery  全文搜尋（用戶 ID / 動作名稱 / 目標 ID）
 * @property startMs      起始時間 epoch 毫秒（含）
 * @property endMs        結束時間 epoch 毫秒（含）
 * @property userId       限制特定用戶 ID
 * @property action       限制特定動作類型
 * @property targetId     限制特定目標 ID
 * @property categories   限制動作分類集合
 * @property onlyTampered 僅顯示疑似遭竄改的記錄
 */
data class AuditFilter(
    val searchQuery: String             = "",
    val startMs: Long?                  = null,
    val endMs: Long?                    = null,
    val userId: String?                 = null,
    val action: AuditAction?            = null,
    val targetId: String?               = null,
    val categories: Set<AuditCategory>  = emptySet(),
    val onlyTampered: Boolean           = false,
) {
    val isActive: Boolean
        get() = searchQuery.isNotBlank() || startMs != null || endMs != null ||
                userId != null || action != null || targetId != null ||
                categories.isNotEmpty() || onlyTampered

    companion object {
        val NONE = AuditFilter()
    }
}
