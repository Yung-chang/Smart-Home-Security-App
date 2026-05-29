package com.smarthome.guardian.presentation.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BiometricPrompt API 的封裝層。
 *
 * 支援 CLASS_3（強生物辨識：指紋/臉部/虹膜），並可降級至裝置 PIN/密碼備援。
 * 呼叫端透過 lambda 接收非同步結果，不阻塞主執行緒。
 *
 * ## OWASP M4 對應
 * 生物辨識僅用於「解鎖已存在的 Session」，不單獨用於身份驗證；
 * 首次登入仍需 Email + 密碼確認伺服器端身份。
 */
@Singleton
class BiometricHelper @Inject constructor() {

    /**
     * 啟動生物辨識認證對話框。
     *
     * @param activity   宿主 Activity（BiometricPrompt 需要 FragmentActivity）
     * @param title      對話框標題
     * @param subtitle   對話框副標題
     * @param onSuccess  認證成功的回呼
     * @param onFailure  單次識別失敗（指紋不符，仍可重試）
     * @param onError    不可恢復的錯誤（鎖定、取消、無硬體等）
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "SmartHome Guardian",
        subtitle: String = "請使用生物辨識解鎖",
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
        onError: (errorCode: Int, message: String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Timber.d("Biometric auth succeeded")
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                Timber.d("Biometric auth failed (single attempt)")
                onFailure()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Timber.w("Biometric auth error [$errorCode]: $errString")
                onError(errorCode, errString.toString())
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            // CLASS_3 = 強生物辨識；DEVICE_CREDENTIAL 允許 PIN 備援
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }

    /**
     * 檢查裝置生物辨識可用性。
     *
     * @param context 應用程式 Context
     * @return [BiometricAvailability] 枚舉，描述目前狀態
     */
    fun isBiometricAvailable(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS             -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE   -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            else                                           -> BiometricAvailability.UNAVAILABLE
        }
    }
}

/** 裝置生物辨識感應器的可用狀態。 */
enum class BiometricAvailability {
    /** 感應器可用且已有已登記的生物特徵。 */
    AVAILABLE,

    /** 裝置無生物辨識感應器。 */
    NO_HARDWARE,

    /** 感應器暫時無法使用（例如過熱）。 */
    HARDWARE_UNAVAILABLE,

    /** 感應器存在但用戶尚未登記（未設定指紋/臉部）。 */
    NOT_ENROLLED,

    /** 其他原因不可用。 */
    UNAVAILABLE,
}
