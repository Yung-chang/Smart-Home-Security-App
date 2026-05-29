package com.smarthome.guardian.data.repository

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.smarthome.guardian.data.local.database.AuditDao
import com.smarthome.guardian.data.local.database.entity.AuditLogEntity
import com.smarthome.guardian.domain.model.AuditFilter
import com.smarthome.guardian.domain.model.AuditLog
import com.smarthome.guardian.domain.model.ExportFormat
import com.smarthome.guardian.domain.model.IntegrityResult
import com.smarthome.guardian.domain.repository.AuditRepository
import com.smarthome.guardian.security.HmacSigner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AuditRepository] 的 Room 實作。
 *
 * ## Paging 3 策略
 * 以 Room [PagingSource] 作為資料來源，每頁 [PAGE_SIZE] 筆，
 * 確保數十萬筆日誌下捲動仍流暢。
 *
 * ## 匯出
 * - CSV：UTF-8 BOM，含所有欄位，Excel 可直接開啟
 * - PDF：使用 Android 原生 [PdfDocument] API，不需要額外相依套件
 */
@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val auditDao: AuditDao,
    private val hmacSigner: HmacSigner,
    @ApplicationContext private val context: Context,
) : AuditRepository {

    // ── 分頁查詢 ──────────────────────────────────────────────────────────────

    override fun getLogs(filter: AuditFilter): Flow<PagingData<AuditLog>> =
        Pager(
            config = PagingConfig(
                pageSize           = PAGE_SIZE,
                prefetchDistance   = PAGE_SIZE / 2,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                auditDao.getPagedFiltered(
                    userId   = filter.userId,
                    action   = filter.action?.name,
                    targetId = filter.targetId,
                    query    = filter.searchQuery.takeIf { it.isNotBlank() },
                    startMs  = filter.startMs,
                    endMs    = filter.endMs,
                )
            },
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    // ── 單筆寫入 ──────────────────────────────────────────────────────────────

    override suspend fun writeLog(log: AuditLog): Result<Unit> = runCatching {
        val signed = if (log.signature.isBlank()) {
            log.copy(signature = hmacSigner.sign(log.toSignableString()))
        } else {
            log
        }
        auditDao.insert(AuditLogEntity.fromDomain(signed))
    }

    // ── 完整性驗證 ────────────────────────────────────────────────────────────

    override suspend fun verifyLogIntegrity(logId: String): IntegrityResult {
        val entity = auditDao.getById(logId)
            ?: return IntegrityResult.Unverifiable("找不到日誌 id=$logId")

        if (entity.signature.isBlank()) {
            return IntegrityResult.Unverifiable("此日誌無簽章（可能為舊版資料）")
        }

        return runCatching {
            val log     = entity.toDomain()
            val isValid = hmacSigner.verify(log.toSignableString(), entity.signature)
            if (isValid) IntegrityResult.Valid
            else IntegrityResult.Tampered("HMAC 不符——日誌內容已被修改")
        }.getOrElse { e ->
            Timber.e(e, "Integrity check error logId=$logId")
            IntegrityResult.Unverifiable("驗證時發生錯誤：${e.message}")
        }
    }

    // ── 匯出 ──────────────────────────────────────────────────────────────────

    override suspend fun exportLogs(filter: AuditFilter, format: ExportFormat): Result<Uri> =
        runCatching {
            val logs = withContext(Dispatchers.IO) {
                auditDao.getAllForExport(filter.startMs, filter.endMs).map { it.toDomain() }
            }
            val fileName = "audit_${System.currentTimeMillis()}.${format.extension}"
            val file     = File(context.cacheDir, fileName)

            withContext(Dispatchers.IO) {
                when (format) {
                    ExportFormat.CSV -> writeCsv(file, logs)
                    ExportFormat.PDF -> writePdf(file, logs)
                }
            }
            Timber.d("Exported ${logs.size} audit logs → $fileName")

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private fun writeCsv(file: File, logs: List<AuditLog>) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        PrintWriter(file, "UTF-8").use { pw ->
            pw.print("﻿") // UTF-8 BOM（Excel 相容）
            pw.println("\"ID\",\"用戶ID\",\"動作\",\"目標ID\",\"時間\",\"IP\",\"裝置指紋\",\"簽章狀態\",\"操作前\",\"操作後\"")
            logs.forEach { log ->
                val row = listOf(
                    log.id,
                    log.userId,
                    log.action.displayName,
                    log.targetId.orEmpty(),
                    sdf.format(Date(log.timestamp)),
                    log.ipAddress,
                    log.deviceFingerprint.take(16),
                    if (log.signature.isNotBlank()) "已簽章" else "無簽章",
                    log.before?.replace("\"", "\"\"").orEmpty(),
                    log.after?.replace("\"", "\"\"").orEmpty(),
                )
                pw.println(row.joinToString(",") { "\"$it\"" })
            }
        }
    }

    private fun writePdf(file: File, logs: List<AuditLog>) {
        val sdf      = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 72dpi
        val page     = document.startPage(pageInfo)
        val canvas   = page.canvas

        val titlePaint = Paint().apply {
            textSize    = 14f
            color       = Color.BLACK
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            textSize    = 8f
            color       = Color.DKGRAY
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            textSize    = 9f
            color       = Color.BLACK
            isFakeBoldText = true
            isAntiAlias = true
        }

        var y = 40f
        val marginL = 30f

        canvas.drawText("SmartHome Guardian — 稽核日誌", marginL, y, titlePaint)
        y += 16f
        canvas.drawText("匯出時間：${sdf.format(Date())}  共 ${logs.size} 筆", marginL, y, bodyPaint)
        y += 18f

        // 表頭
        canvas.drawText(
            "%-20s %-20s %-16s %-20s".format("時間", "動作", "用戶ID", "目標ID"),
            marginL, y, headerPaint,
        )
        y += 4f
        canvas.drawLine(marginL, y, 565f, y, bodyPaint)
        y += 10f

        logs.take(MAX_PDF_ROWS).forEach { log ->
            if (y > 820f) return@forEach
            val line = "%-20s %-20s %-16s %-20s".format(
                sdf.format(Date(log.timestamp)).take(19),
                log.action.displayName.take(18),
                log.userId.take(14),
                log.targetId?.take(18).orEmpty(),
            )
            canvas.drawText(line, marginL, y, bodyPaint)
            y += 11f
        }

        if (logs.size > MAX_PDF_ROWS) {
            y += 6f
            canvas.drawText("（僅顯示前 $MAX_PDF_ROWS 筆，完整資料請使用 CSV 匯出）", marginL, y, bodyPaint)
        }

        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }

    companion object {
        private const val PAGE_SIZE    = 30
        private const val MAX_PDF_ROWS = 80
    }
}
