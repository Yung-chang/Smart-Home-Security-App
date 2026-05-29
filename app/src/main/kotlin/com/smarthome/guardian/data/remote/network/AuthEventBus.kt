package com.smarthome.guardian.data.remote.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全應用程式的認證事件匯流排。
 *
 * 用於解耦 [com.smarthome.guardian.di.AuthInterceptor]（發送者）
 * 與 [com.smarthome.guardian.presentation.auth.AuthViewModel]（接收者）：
 * 當 Token 刷新失敗時，攔截器發出 [forceLogout] 事件，
 * ViewModel 收到後強制登出並導航至登入畫面。
 *
 * 使用 `extraBufferCapacity = 1` 確保在無訂閱者時事件不丟失。
 */
@Singleton
class AuthEventBus @Inject constructor() {

    private val _forceLogout = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 訂閱強制登出事件，值為登出原因描述。 */
    val forceLogout: SharedFlow<String> = _forceLogout.asSharedFlow()

    /**
     * 發出強制登出事件。
     * 可從非 Coroutine 環境呼叫（[MutableSharedFlow.tryEmit]）。
     *
     * @param reason 登出原因（用於日誌與 UI 提示）
     */
    fun emitLogout(reason: String = "Session expired") {
        _forceLogout.tryEmit(reason)
    }
}
