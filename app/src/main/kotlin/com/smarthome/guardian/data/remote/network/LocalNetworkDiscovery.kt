package com.smarthome.guardian.data.remote.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.smarthome.guardian.domain.model.GatewayDevice
import com.smarthome.guardian.domain.model.GatewayType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 使用 Android NSD（Network Service Discovery / mDNS）自動發現區域網路內的 Home Gateway。
 *
 * ## 支援協定
 * - `_smarthome._tcp`：SmartHome Guardian 自有 Gateway
 * - `_hap._tcp`：Apple HomeKit（HAP 協定）
 * - `_matter._tcp`：Matter（CSA 標準）
 *
 * ## 使用方式
 * ```kotlin
 * localNetworkDiscovery.discoverGateways().collect { gateways ->
 *     // 更新 UI 或自動連線至第一個可用 Gateway
 * }
 * ```
 *
 * ## 注意事項
 * - 需要 `ACCESS_NETWORK_STATE` 權限（已在 Manifest 宣告）
 * - Android 12+ 在 Discovery 過程中不保證能立即解析 IP；流程為先 onServiceFound → 再 resolveService
 * - 每個 [GatewayType] 獨立啟動一組 DiscoveryListener；Flow 取消時自動停止所有探索
 *
 * @param context ApplicationContext
 */
@Singleton
class LocalNetworkDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nsdManager: NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * 開始探索所有支援的 Gateway 類型，回傳已發現設備的累積列表。
     *
     * 每發現（或失去）一個設備，Flow 就會 emit 更新後的完整列表。
     * Flow 生命週期結束時自動停止所有 NSD 探索，釋放資源。
     */
    fun discoverGateways(): Flow<List<GatewayDevice>> = callbackFlow {
        val discovered = mutableMapOf<String, GatewayDevice>() // key = serviceInfo.serviceName

        fun notifyUpdate() = trySend(discovered.values.toList())

        // 為每個 GatewayType 建立獨立的探索監聽器
        val listeners = GatewayType.entries.map { gatewayType ->
            val listener = buildDiscoveryListener(
                gatewayType = gatewayType,
                onFound     = { device ->
                    discovered[device.name] = device
                    notifyUpdate()
                },
                onLost      = { serviceName ->
                    discovered.remove(serviceName)
                    notifyUpdate()
                },
            )
            try {
                nsdManager.discoverServices(
                    gatewayType.serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener,
                )
                Timber.d("NSD: started discovery for ${gatewayType.serviceType}")
            } catch (e: Exception) {
                Timber.e(e, "NSD: failed to start discovery for ${gatewayType.serviceType}")
            }
            listener
        }

        // 初始 emit（空列表）
        notifyUpdate()

        awaitClose {
            listeners.forEach { listener ->
                runCatching { nsdManager.stopServiceDiscovery(listener) }
                    .onFailure { e -> Timber.w(e, "NSD: error stopping discovery") }
            }
            Timber.d("NSD: all discoveries stopped")
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private fun buildDiscoveryListener(
        gatewayType: GatewayType,
        onFound: (GatewayDevice) -> Unit,
        onLost: (String) -> Unit,
    ): NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {

        override fun onDiscoveryStarted(regType: String) {
            Timber.d("NSD: discovery started ($regType)")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Timber.d("NSD: service found: ${serviceInfo.serviceName}")
            // 必須 resolve 才能取得 host + port
            resolveService(serviceInfo, gatewayType, onFound)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Timber.d("NSD: service lost: ${serviceInfo.serviceName}")
            onLost(serviceInfo.serviceName)
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Timber.d("NSD: discovery stopped ($serviceType)")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Timber.e("NSD: start discovery failed ($serviceType) code=$errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Timber.e("NSD: stop discovery failed ($serviceType) code=$errorCode")
        }
    }

    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        gatewayType: GatewayType,
        onResolved: (GatewayDevice) -> Unit,
    ) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Timber.w("NSD: resolve failed for ${info.serviceName} code=$errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress ?: return
                    val properties = buildMap {
                        info.attributes?.forEach { (k, v) ->
                            put(k, v?.toString(Charsets.UTF_8) ?: "")
                        }
                    }
                    val device = GatewayDevice(
                        name       = info.serviceName,
                        host       = host,
                        port       = info.port,
                        type       = gatewayType,
                        properties = properties,
                    )
                    Timber.d("NSD: resolved ${device.name} @ ${device.host}:${device.port}")
                    onResolved(device)
                }
            },
        )
    }
}
