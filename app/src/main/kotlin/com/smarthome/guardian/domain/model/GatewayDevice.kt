package com.smarthome.guardian.domain.model

/**
 * 透過 mDNS/NSD 在區域網路中發現的 Home Gateway。
 *
 * @property name        服務名稱（mDNS 廣播的名稱）
 * @property host        主機名稱或 IP 位址
 * @property port        服務監聽埠號
 * @property type        閘道器協定類型
 * @property properties  TXT 記錄附加屬性（如 id、version、model）
 * @property isReachable 最後一次 ping 是否可達
 */
data class GatewayDevice(
    val name: String,
    val host: String,
    val port: Int,
    val type: GatewayType,
    val properties: Map<String, String> = emptyMap(),
    val isReachable: Boolean = true,
) {
    /** 用於 WebSocket 連線的 WSS URL。 */
    val wsUrl: String get() = "wss://$host:$port/ws"

    /** 用於 MQTT 連線的 broker URI。 */
    val mqttUrl: String get() = "ssl://$host:8883"
}

enum class GatewayType(val serviceType: String, val displayName: String) {
    SMARTHOME("_smarthome._tcp", "SmartHome Guardian"),
    HOMEKIT("_hap._tcp",        "Apple HomeKit"),
    MATTER("_matter._tcp",      "Matter"),
}
