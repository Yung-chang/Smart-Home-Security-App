package com.smarthome.guardian.domain.repository

import android.net.Uri
import androidx.paging.PagingData
import com.smarthome.guardian.domain.model.AuditFilter
import com.smarthome.guardian.domain.model.AuditLog
import com.smarthome.guardian.domain.model.ExportFormat
import com.smarthome.guardian.domain.model.IntegrityResult
import kotlinx.coroutines.flow.Flow

/**
 * 稽核日誌的 Repository 介面（Domain 層）。
 *
 * 實作位於 [com.smarthome.guardian.data.repository.AuditRepositoryImpl]，
 * 使用 Room + SQLCipher 儲存，並以 Paging 3 支援大量記錄的分頁載入。
 */
interface AuditRepository {

    /**
     * 取得符合 [filter] 條件的分頁稽核日誌。
     *
     * 回傳 [PagingData] Flow；ViewModel 透過 `cachedIn(viewModelScope)` 快取，
     * 避免螢幕旋轉時重複請求 DB。
     *
     * @param filter 篩選條件，[AuditFilter.NONE] 表示取得全部記錄
     */
    fun getLogs(filter: AuditFilter): Flow<PagingData<AuditLog>>

    /**
     * 手動寫入一筆稽核日誌。
     *
     * 若 [AuditLog.signature] 為空，自動計算 HMAC-SHA256 簽章後再寫入。
     * 一般情況下建議透過 [com.smarthome.guardian.data.logger.AuditLogger.log] 批次寫入，
     * 此函數適用於需要立即確認寫入結果的場景。
     *
     * @param log 欲寫入的日誌物件
     * @return 成功時 [Result.success]，DB 寫入失敗時 [Result.failure]
     */
    suspend fun writeLog(log: AuditLog): Result<Unit>

    /**
     * 驗證指定日誌的 HMAC-SHA256 簽章完整性。
     *
     * @param logId 目標日誌 ID
     * @return [IntegrityResult.Valid] — 未被竄改
     *         [IntegrityResult.Tampered] — 簽章不符，資料已被修改
     *         [IntegrityResult.Unverifiable] — 金鑰遺失或 signature 欄位為空
     */
    suspend fun verifyLogIntegrity(logId: String): IntegrityResult

    /**
     * 批次匯出稽核日誌為 [ExportFormat.CSV] 或 [ExportFormat.PDF]。
     *
     * 匯出結果寫至 APP cache 目錄，透過 FileProvider 回傳可分享的 [Uri]。
     * CSV 含 UTF-8 BOM 以確保 Excel 相容性。
     *
     * @param filter 篩選條件（決定匯出範圍）
     * @param format 目標格式
     * @return 成功時回傳暫存檔案的 [Uri]，失敗時 [Result.failure]
     */
    suspend fun exportLogs(filter: AuditFilter, format: ExportFormat): Result<Uri>
}
