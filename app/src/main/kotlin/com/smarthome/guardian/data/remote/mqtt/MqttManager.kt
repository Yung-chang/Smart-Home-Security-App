package com.smarthome.guardian.data.remote.mqtt

import android.content.Context
import com.smarthome.guardian.domain.model.MqttMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import timber.log.Timber
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.math.min
import kotlin.math.pow

/**
 * Eclipse Paho MQTT 客戶端封裝，提供 Coroutine/Flow 友好的 API。
 *
 * ## 安全特性
 * - TLS 連線（port 8883）：`ssl://` 協定強制加密
 * - **Client Certificate 認證**：從 Android Keystore 載入裝置憑證，
 *   建立自訂 [SSLContext]，伺服器可驗證裝置身份（雙向 TLS）
 * - QoS 2（Exactly Once）：確保重要設備指令不遺失、不重複
 *
 * ## 自動重連
 * 與 WebSocketManager 相同的指數退避策略（1s → 2s → … 最大 60s）。
 *
 * ## OWASP M2 — 不安全的資料儲存
 * MQTT clientId 僅包含 UUID，不含用戶個資。
 *
 * @param context Application Context（用於 Keystore 存取）
 */
@Singleton
class MqttManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var client: MqttAsyncClient? = null
    @Volatile private var brokerUri   = ""
    @Volatile private var clientId    = ""
    @Volatile private var retryCount  = 0
    private var reconnectJob: Job? = null

    // ── 公開 API ──────────────────────────────────────────────────────────────

    /**
     * 建立至 MQTT Broker 的 TLS 連線。
     *
     * @param broker   broker URI，格式 `ssl://<host>:8883`
     * @param clientId MQTT Client ID（建議包含裝置 UUID）
     */
    fun connect(broker: String, clientId: String) {
        this.brokerUri = broker
        this.clientId  = clientId
        retryCount     = 0
        doConnect()
    }

    /** 斷開 MQTT 連線並取消背景重連。 */
    fun disconnect() {
        reconnectJob?.cancel()
        runCatching {
            client?.disconnect()
            client = null
        }
        Timber.d("MQTT disconnected")
    }

    /**
     * 訂閱指定 Topic，回傳接收到訊息的 [Flow]。
     *
     * @param topic MQTT Topic（支援 `#` 和 `+` 萬用字元）
     * @param qos   服務品質：0 = At Most Once, 1 = At Least Once, 2 = Exactly Once
     */
    fun subscribe(topic: String, qos: Int = QOS_EXACTLY_ONCE): Flow<MqttMessage> = callbackFlow {
        val mqttClient = client
        if (mqttClient == null || !mqttClient.isConnected) {
            Timber.w("MQTT subscribe failed: not connected (topic=$topic)")
            close()
            return@callbackFlow
        }

        mqttClient.subscribe(topic, qos) { receivedTopic, msg ->
            trySend(
                MqttMessage(
                    topic   = receivedTopic,
                    payload = msg.payload,
                    qos     = msg.qos,
                    retained= msg.isRetained,
                )
            )
        }

        awaitClose {
            runCatching { mqttClient.unsubscribe(topic) }
        }
    }

    /**
     * 發布 MQTT 訊息。
     *
     * @param topic   目標 Topic
     * @param payload 訊息內容（byte array）
     * @param qos     服務品質（預設 QoS 2 確保設備指令不遺失）
     * @param retained 是否保留最後一筆訊息給新訂閱者
     * @return 成功發布時 [Result.success]，失敗時 [Result.failure]
     */
    suspend fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int = QOS_EXACTLY_ONCE,
        retained: Boolean = false,
    ): Result<Unit> = runCatching {
        val mqttClient = client ?: throw MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED.toInt())
        if (!mqttClient.isConnected) throw MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED.toInt())

        mqttClient.publish(topic, payload, qos, retained)
        Timber.d("MQTT published: topic=$topic qos=$qos payload=${payload.size}B")
    }

    val isConnected: Boolean get() = client?.isConnected == true

    // ── 私有：連線邏輯 ────────────────────────────────────────────────────────

    private fun doConnect() {
        runCatching {
            val persistence = MemoryPersistence()
            val mqtt = MqttAsyncClient(brokerUri, clientId, persistence)
            client = mqtt

            val options = buildConnectOptions()
            mqtt.setCallback(createCallback())
            mqtt.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Timber.d("MQTT connected to $brokerUri")
                    retryCount = 0
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Timber.e(exception, "MQTT connect failed")
                    scheduleReconnect()
                }
            })
        }.onFailure { e ->
            Timber.e(e, "MQTT doConnect exception")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = calculateBackoff(retryCount)
            Timber.d("MQTT reconnecting in ${delayMs}ms (attempt=${retryCount + 1})")
            delay(delayMs)
            if (isActive) {
                retryCount++
                doConnect()
            }
        }
    }

    /**
     * 建立 MQTT 連線選項，含 TLS Client Certificate 認證。
     * 從 Android Keystore 載入裝置憑證，建立雙向 TLS（mTLS）。
     */
    private fun buildConnectOptions(): MqttConnectOptions = MqttConnectOptions().apply {
        isCleanSession      = false           // 保留 QoS 1/2 離線訊息佇列
        connectionTimeout   = CONNECT_TIMEOUT_S
        keepAliveInterval   = KEEPALIVE_S
        isAutomaticReconnect= false           // 自行實作退避重連

        // ── TLS + Client Certificate（雙向 mTLS）────────────────────────────
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            if (keyStore.containsAlias(CLIENT_CERT_ALIAS)) {
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(keyStore, null)
                val sslCtx = SSLContext.getInstance("TLS")
                sslCtx.init(kmf.keyManagers, null, null)
                socketFactory = sslCtx.socketFactory
                Timber.d("MQTT: client certificate loaded from Keystore")
            } else {
                Timber.w("MQTT: client cert alias '$CLIENT_CERT_ALIAS' not found — using server-only TLS")
            }
        }.onFailure { e ->
            Timber.e(e, "MQTT: TLS setup failed, falling back to unverified client")
        }
    }

    private fun createCallback() = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String) {
            Timber.d("MQTT connectComplete: reconnect=$reconnect uri=$serverURI")
        }
        override fun connectionLost(cause: Throwable?) {
            Timber.w(cause, "MQTT connection lost")
            scheduleReconnect()
        }
        override fun messageArrived(topic: String, message: org.eclipse.paho.client.mqttv3.MqttMessage) {
            // 由 callbackFlow 訂閱者在 subscribe() 處理
        }
        override fun deliveryComplete(token: IMqttDeliveryToken) {
            Timber.d("MQTT delivery complete: messageId=${token.messageId}")
        }
    }

    private fun calculateBackoff(attempt: Int): Long =
        min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * 2.0.pow(attempt).toLong())

    companion object {
        const val QOS_AT_MOST_ONCE  = 0
        const val QOS_AT_LEAST_ONCE = 1
        const val QOS_EXACTLY_ONCE  = 2

        private const val CONNECT_TIMEOUT_S = 15
        private const val KEEPALIVE_S       = 60
        private const val BASE_BACKOFF_MS   = 1_000L
        private const val MAX_BACKOFF_MS    = 60_000L
        private const val CLIENT_CERT_ALIAS = "smarthome_device_cert"
    }
}
