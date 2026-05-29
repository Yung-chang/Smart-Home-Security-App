package com.smarthome.guardian.domain.model

/**
 * MQTT 接收訊息的 domain 模型。
 *
 * @property topic    訊息的 MQTT Topic
 * @property payload  原始位元組內容
 * @property qos      服務品質（0/1/2）
 * @property retained 是否為 retained 訊息
 */
data class MqttMessage(
    val topic: String,
    val payload: ByteArray,
    val qos: Int = 0,
    val retained: Boolean = false,
) {
    /** 以 UTF-8 解析 payload 為字串。 */
    val payloadString: String get() = payload.toString(Charsets.UTF_8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MqttMessage) return false
        return topic == other.topic && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * topic.hashCode() + payload.contentHashCode()
}
