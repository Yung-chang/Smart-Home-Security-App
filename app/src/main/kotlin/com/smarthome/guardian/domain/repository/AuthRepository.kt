package com.smarthome.guardian.domain.repository

import com.smarthome.guardian.domain.model.User

/**
 * 驗證功能的 Repository 介面（Domain 層契約）。
 *
 * ViewModel 只依賴此介面，不直接接觸 Retrofit/API 實作，
 * 便於替換實作（測試時注入 Fake）。
 */
interface AuthRepository {

    /**
     * 使用 Email + 密碼登入。成功時 Token 已自動儲存至 [TokenManager]。
     *
     * @return [Result.success] 含已登入的 [User]；失敗時 [Result.failure] 含例外
     */
    suspend fun login(email: String, password: String): Result<User>

    /**
     * 使用伺服器端 PIN 驗證（本地 PIN 驗證在 [AuthViewModel] 直接處理）。
     */
    suspend fun verifyPin(email: String, pin: String): Result<User>

    /**
     * 使用現有的 Refresh Token 靜默更新 Access Token。
     * Refresh Token 過期時回傳 [Result.failure]，應跳至完整登入流程。
     */
    suspend fun refreshToken(): Result<User>

    /** 登出：撤銷伺服器端 Token 並清除本地儲存。 */
    suspend fun logout(): Result<Unit>

    /** 取得目前已快取的登入使用者資訊（不發出網路請求）。 */
    fun getCachedUser(): User?
}
