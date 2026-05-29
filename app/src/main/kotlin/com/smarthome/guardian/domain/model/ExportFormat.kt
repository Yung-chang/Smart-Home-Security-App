package com.smarthome.guardian.domain.model

/** 稽核日誌匯出格式。 */
enum class ExportFormat(val mimeType: String, val extension: String) {
    CSV("text/csv", "csv"),
    PDF("application/pdf", "pdf"),
}
