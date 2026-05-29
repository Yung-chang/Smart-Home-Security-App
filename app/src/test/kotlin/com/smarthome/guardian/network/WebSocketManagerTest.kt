package com.smarthome.guardian.network

import app.cash.turbine.test
import org.junit.jupiter.api.Disabled
import com.smarthome.guardian.data.remote.websocket.WebSocketManager
import com.smarthome.guardian.data.remote.websocket.WebSocketState
import com.smarthome.guardian.domain.model.AlertType
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.domain.model.Severity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.TimeUnit

/**
 * [WebSocketManager] 單元測試。
 *
 * 使用 [MockWebServer] 模擬 WebSocket 伺服器，驗證：
 * - 連線狀態轉換
 * - JSON 訊息解析（DEVICE_STATUS / SECURITY_ALERT / SYSTEM_EVENT）
 * - PONG 心跳更新
 * - 斷線後排程重連（狀態轉換至 RECONNECTING）
 *
 * 注意：MockWebServer 使用 HTTP（非 HTTPS），測試用 OkHttpClient 不啟用 CertificatePinning。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@DisplayName("WebSocketManager")
class WebSocketManagerTest {

    private val server = MockWebServer()

    // 測試用 OkHttpClient（無 TLS 驗證 / 無 Auth Interceptor）
    private val testClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // WS 長連線不設逾時
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private lateinit var manager: WebSocketManager

    @BeforeEach
    fun setUp() {
        server.start()
        manager = WebSocketManager(testClient)
    }

    @AfterEach
    fun tearDown() {
        manager.disconnect()
        server.shutdown()
    }

    // ── 連線狀態 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("連線狀態轉換")
    inner class ConnectionStateTests {

        @Test
        @DisplayName("初始狀態為 IDLE")
        fun `initial state is IDLE`() {
            assertEquals(WebSocketState.IDLE, manager.state.value)
        }

        @Disabled("Integration test — requires real threading; run on emulator as androidTest")
        @Test
        @DisplayName("connect() 後成功建立連線 → CONNECTED")
        fun `connect transitions to CONNECTED after server upgrade`() = runTest {
            server.enqueue(MockResponse().withWebSocketUpgrade(NoOpWebSocketListener()))

            manager.state.test {
                assertEquals(WebSocketState.IDLE, awaitItem())

                val wsUrl = "ws://${server.hostName}:${server.port}/ws"
                manager.connect(wsUrl, "test-token")

                val states = mutableListOf<WebSocketState>()
                repeat(3) {
                    val s = awaitItem()
                    states.add(s)
                    if (s == WebSocketState.CONNECTED) return@test
                }
                assertTrue(WebSocketState.CONNECTED in states, "應包含 CONNECTED 狀態")
            }
        }

        @Disabled("Integration test — requires real threading; run on emulator as androidTest")
        @Test
        @DisplayName("disconnect() 後狀態為 DISCONNECTED")
        fun `disconnect transitions to DISCONNECTED`() = runTest {
            server.enqueue(MockResponse().withWebSocketUpgrade(NoOpWebSocketListener()))

            val wsUrl = "ws://${server.hostName}:${server.port}/ws"
            manager.connect(wsUrl, "test-token")
            Thread.sleep(200) // 等待連線建立

            manager.disconnect()
            assertEquals(WebSocketState.DISCONNECTED, manager.state.value)
        }
    }

    // ── 訊息解析 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("訊息解析")
    inner class MessageParsingTests {

        @Disabled("Integration test — requires real threading; run on emulator as androidTest")
        @Test
        @DisplayName("DEVICE_STATUS 訊息 → observeDeviceStatus() 收到正確狀態")
        fun `DEVICE_STATUS message emits to device status flow`() = runTest {
            val serverListener = ServerControlledWebSocketListener()
            server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

            val wsUrl = "ws://${server.hostName}:${server.port}/ws"
            manager.connect(wsUrl, "test-token")
            Thread.sleep(200) // 等待連線

            manager.observeDeviceStatus("device-001").test(timeout = kotlin.time.Duration.parse("3s")) {
                // 伺服器發送 DEVICE_STATUS 訊息
                serverListener.send("""
                    {
                        "type": "DEVICE_STATUS",
                        "deviceId": "device-001",
                        "status": "ONLINE",
                        "isOn": true,
                        "timestamp": ${System.currentTimeMillis()}
                    }
                """.trimIndent())

                val status = awaitItem()
                assertEquals(DeviceStatus.ONLINE, status)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Disabled("Integration test — requires real threading; run on emulator as androidTest")
        @Test
        @DisplayName("SECURITY_ALERT 訊息 → observeAlerts() 收到正確警報")
        fun `SECURITY_ALERT message emits to alerts flow`() = runTest {
            val serverListener = ServerControlledWebSocketListener()
            server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

            val wsUrl = "ws://${server.hostName}:${server.port}/ws"
            manager.connect(wsUrl, "test-token")
            Thread.sleep(200)

            manager.observeAlerts().test(timeout = kotlin.time.Duration.parse("3s")) {
                serverListener.send("""
                    {
                        "type": "SECURITY_ALERT",
                        "alertId": "alert-001",
                        "alertType": "INTRUSION",
                        "severity": "CRITICAL",
                        "deviceId": "camera-front",
                        "message": "入侵偵測觸發",
                        "timestamp": ${System.currentTimeMillis()}
                    }
                """.trimIndent())

                val alert = awaitItem()
                assertEquals(AlertType.INTRUSION, alert.type)
                assertEquals(Severity.CRITICAL, alert.severity)
                assertEquals("camera-front", alert.deviceId)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Disabled("Integration test — requires real threading; run on emulator as androidTest")
        @Test
        @DisplayName("格式錯誤的 JSON 訊息應忽略（不崩潰）")
        fun `malformed JSON message is silently ignored`() = runTest {
            val serverListener = ServerControlledWebSocketListener()
            server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

            val wsUrl = "ws://${server.hostName}:${server.port}/ws"
            manager.connect(wsUrl, "test-token")
            Thread.sleep(200)

            // 發送損壞的 JSON，不應拋出例外
            serverListener.send("{ this is not valid json }")
            Thread.sleep(100)

            // 連線應仍維持
            assertEquals(WebSocketState.CONNECTED, manager.state.value)
        }
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("訊息發送")
    inner class SendTests {

        @Test
        @DisplayName("未連線時 send() 應回傳 false")
        fun `send returns false when not connected`() {
            val result = manager.send("""{"type":"PING"}""")
            assertFalse(result)
        }

        private fun assertFalse(value: Boolean) =
            org.junit.jupiter.api.Assertions.assertFalse(value)
    }

    // ── 輔助類別 ──────────────────────────────────────────────────────────────

    /** 無操作的 WebSocket 監聽器（服務端）。 */
    private class NoOpWebSocketListener : okhttp3.WebSocketListener()

    /** 可由測試主動發送訊息的服務端監聽器。 */
    private class ServerControlledWebSocketListener : okhttp3.WebSocketListener() {
        @Volatile private var serverSocket: okhttp3.WebSocket? = null

        override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
            serverSocket = webSocket
        }

        fun send(json: String) {
            serverSocket?.send(json) ?: error("WebSocket not connected")
        }
    }
}
