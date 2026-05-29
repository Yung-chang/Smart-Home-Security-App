package com.smarthome.guardian.security

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 封裝 OkHttp [okhttp3.CertificatePinner] 的設定，
 * 提供多 Domain 的 SHA-256 憑證 Pin 及備援 Pin 機制。
 *
 * ## 取得憑證 Pin 的方式
 * ```bash
 * openssl s_client -connect api.smarthome.local:443 -servername api.smarthome.local \
 *   | openssl x509 -pubkey -noout \
 *   | openssl rsa -pubin -outform der \
 *   | openssl dgst -sha256 -binary \
 *   | base64
 * ```
 *
 * ## 備援 Pin 策略
 * 每個 Domain 設定 2 個 Pin（主憑證 + 中繼 CA），
 * 在輪換憑證前部署新 Pin，確保不中斷服務。
 *
 * ## OWASP M3 — 不安全通訊對應
 * CertificatePinning 可防止中間人攻擊，即使攻擊者安裝了偽造的系統 CA
 * 也無法攔截應用程式的 TLS 流量。
 */
@Singleton
class CertificatePinner @Inject constructor() {

    /**
     * 建立並回傳已設定所有 Domain Pin 的 [okhttp3.CertificatePinner]。
     *
     * **重要**：在正式部署前，必須將 [PLACEHOLDER_PIN] 替換為
     * 伺服器實際憑證的 SHA-256 public key pin。
     *
     * @return 設定完成的 [okhttp3.CertificatePinner]
     */
    fun build(): okhttp3.CertificatePinner {
        Timber.d("Building CertificatePinner for ${PINNED_DOMAINS.size} domains")
        return okhttp3.CertificatePinner.Builder()
            .apply {
                PINNED_DOMAINS.forEach { (domain, pins) ->
                    pins.forEach { pin -> add(domain, pin) }
                }
            }
            .build()
    }

    companion object {
        /**
         * 憑證 Pin 設定表。
         *
         * Key   = Domain pattern（`**` 匹配所有子 Domain）
         * Value = SHA-256 public key pin 清單（至少需 1 主 Pin + 1 備援 Pin）
         *
         * 格式：`"sha256/<base64-encoded-spki-hash>"`
         *
         * TODO: 部署前替換為真實憑證的 SHA-256 Pin。
         */
        private val PINNED_DOMAINS: Map<String, List<String>> = mapOf(
            // REST API + WebSocket
            "api.smarthome.local" to listOf(
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // 主憑證 (leaf)
                "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=", // 備援 (intermediate CA)
            ),
            // MQTT Broker
            "mqtt.smarthome.local" to listOf(
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // 主憑證 (leaf)
                "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=", // 備援 (intermediate CA)
            ),
        )

        private const val PLACEHOLDER_PIN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}
