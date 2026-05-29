# SmartHome Guardian — 完整 Android 原始碼
**技術棧：Kotlin + Jetpack Compose + Hilt + Room + MQTT**

---

## 📁 專案結構

```
SmartHomeGuardian/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/smarthome/guardian/
│           ├── GuardianApp.kt
│           ├── MainActivity.kt
│           ├── di/
│           │   ├── AppModule.kt
│           │   ├── NetworkModule.kt
│           │   └── DatabaseModule.kt
│           ├── security/
│           │   ├── CryptoManager.kt
│           │   ├── TokenManager.kt
│           │   ├── SecurityChecker.kt
│           │   ├── CertificatePinner.kt
│           │   └── HmacSigner.kt
│           ├── domain/model/
│           │   ├── Device.kt
│           │   ├── SecurityAlert.kt
│           │   ├── User.kt
│           │   ├── AccessRule.kt
│           │   └── AuditLog.kt
│           ├── data/
│           │   ├── local/AppDatabase.kt
│           │   ├── remote/ApiService.kt
│           │   ├── remote/WebSocketManager.kt
│           │   └── remote/MqttManager.kt
│           └── presentation/
│               ├── auth/LoginScreen.kt
│               ├── dashboard/DashboardScreen.kt
│               ├── devices/DeviceDetailScreen.kt
│               ├── security/SecurityMonitorScreen.kt
│               ├── access/AccessControlScreen.kt
│               └── audit/AuditLogScreen.kt
├── gradle/libs.versions.toml
└── build.gradle.kts
```

---

## 📄 gradle/libs.versions.toml

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
coreKtx = "1.13.1"
composeBom = "2024.06.00"
activityCompose = "1.9.0"
navigationCompose = "2.7.7"
lifecycleViewModel = "2.8.2"
hilt = "2.51.1"
hiltNavigation = "1.2.0"
ksp = "2.0.0-1.0.22"
retrofit = "2.11.0"
okhttp = "4.12.0"
room = "2.6.1"
sqlcipher = "4.5.6"
securityCrypto = "1.1.0-alpha06"
biometric = "1.2.0-alpha05"
rootbeer = "0.1.0"
firebaseBom = "33.1.0"
coil = "2.6.0"
coroutines = "1.8.1"
paging = "3.3.0"
timber = "5.0.1"
paho = "1.1.1"
junit5 = "5.10.2"
mockk = "1.13.11"
turbine = "1.1.0"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewModel" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigation" }
hilt-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
sqlcipher = { group = "net.zetetic", name = "android-database-sqlcipher", version.ref = "sqlcipher" }
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
biometric = { group = "androidx.biometric", name = "biometric", version.ref = "biometric" }
rootbeer = { group = "com.scottyab", name = "rootbeer-lib", version.ref = "rootbeer" }
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging-ktx" }
firebase-crashlytics = { group = "com.google.firebase", name = "firebase-crashlytics-ktx" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }
paho-mqtt-android = { group = "org.eclipse.paho", name = "org.eclipse.paho.android.service", version.ref = "paho" }
junit5 = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
google-services = { id = "com.google.gms.google-services", version = "4.4.2" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version = "3.0.1" }
```

---

## 📄 AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.USE_FINGERPRINT" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".GuardianApp"
        android:allowBackup="false"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.SmartHomeGuardian"
        android:networkSecurityConfig="@xml/network_security_config">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- FCM Service -->
        <service
            android:name=".notifications.GuardianFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

        <!-- Security Monitoring Foreground Service -->
        <service
            android:name=".service.SecurityMonitoringService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />

        <!-- Boot Receiver -->
        <receiver
            android:name=".receivers.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

---

## 📄 GuardianApp.kt

```kotlin
package com.smarthome.guardian

import android.app.Application
import com.smarthome.guardian.security.SecurityChecker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * SmartHome Guardian 應用程式入口點
 * 負責初始化 Hilt DI、Timber 日誌、及開機安全檢查
 */
@HiltAndroidApp
class GuardianApp : Application() {

    @Inject
    lateinit var securityChecker: SecurityChecker

    override fun onCreate() {
        super.onCreate()

        // 初始化日誌（僅 Debug 模式輸出）
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // 生產模式：只記錄錯誤，不記錄敏感資訊
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority >= android.util.Log.ERROR) {
                        android.util.Log.e(tag, message, t)
                    }
                }
            })
        }

        // 執行開機安全檢查
        if (BuildConfig.ENABLE_SECURITY_CHECKS) {
            securityChecker.runChecksOnAppStart()
        }
    }
}
```

---

## 📄 security/CryptoManager.kt

```kotlin
package com.smarthome.guardian.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密管理器
 * 使用 Android Keystore 提供 AES-256-GCM 加密/解密
 * 金鑰儲存於硬體安全模組（若裝置支援），永遠不以明文導出
 */
@Singleton
class CryptoManager @Inject constructor() {

    companion object {
        private const val KEY_ALIAS = "smarthome_guardian_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    data class EncryptedData(
        val ciphertext: ByteArray,
        val iv: ByteArray
    )

    /**
     * 加密資料
     * @param plaintext 明文位元組陣列
     * @return EncryptedData（密文 + IV）
     * @throws CryptoException 加密失敗時拋出
     */
    fun encrypt(plaintext: ByteArray): EncryptedData {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(plaintext)
            EncryptedData(ciphertext, cipher.iv)
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            throw CryptoException("Encryption failed", e)
        }
    }

    /**
     * 解密資料
     * @param encryptedData 加密資料（密文 + IV）
     * @return 明文位元組陣列
     * @throws CryptoException 解密失敗時拋出
     */
    fun decrypt(encryptedData: EncryptedData): ByteArray {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            cipher.doFinal(encryptedData.ciphertext)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            throw CryptoException("Decryption failed", e)
        }
    }

    /** 取得或建立 Keystore 金鑰 */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // 可設為 true 要求生物辨識
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }
}

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

---

## 📄 security/TokenManager.kt

```kotlin
package com.smarthome.guardian.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import timber.log.Timber
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token 管理器
 * 使用 EncryptedSharedPreferences 安全儲存 JWT Token
 * 提供 Token 存取、驗證、及登出事件
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREF_FILE = "guardian_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
    }

    private val _logoutEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutEvent: SharedFlow<Unit> = _logoutEvent

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** 儲存 Token 對 */
    fun saveTokens(accessToken: String, refreshToken: String) {
        val expiry = extractExpiry(accessToken)
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiry)
            .apply()
    }

    /** 取得 Access Token */
    fun getAccessToken(): String? = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

    /** 取得 Refresh Token */
    fun getRefreshToken(): String? = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)

    /** 判斷 Token 是否過期（含 30 秒緩衝） */
    fun isTokenExpired(): Boolean {
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        return System.currentTimeMillis() >= (expiry * 1000) - 30_000
    }

    /** 清除所有 Token（登出） */
    fun clearTokens() {
        encryptedPrefs.edit().clear().apply()
        _logoutEvent.tryEmit(Unit)
    }

    /** 解析 JWT exp claim */
    private fun extractExpiry(token: String): Long {
        return try {
            val payload = token.split(".")[1]
            val decoded = Base64.getUrlDecoder().decode(payload)
            JSONObject(String(decoded)).getLong("exp")
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse token expiry")
            0L
        }
    }
}
```

---

## 📄 security/SecurityChecker.kt

```kotlin
package com.smarthome.guardian.security

import android.content.Context
import android.os.Build
import com.scottyab.rootbeer.RootBeer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全環境檢查器
 * 偵測 Root、模擬器、除錯模式等不安全環境
 */
@Singleton
class SecurityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class SecurityCheckResult(
        val isRooted: Boolean = false,
        val isEmulator: Boolean = false,
        val isDebuggable: Boolean = false,
        val isAdbEnabled: Boolean = false
    ) {
        val isSafe: Boolean get() = !isRooted && !isEmulator
        val issues: List<String> get() = buildList {
            if (isRooted) add("裝置已 Root")
            if (isEmulator) add("執行於模擬器")
            if (isDebuggable) add("除錯模式已啟用")
            if (isAdbEnabled) add("ADB 偵錯已啟用")
        }
    }

    /** 應用程式啟動時執行的完整安全檢查 */
    fun runChecksOnAppStart(): SecurityCheckResult {
        val result = runSecurityChecks()
        if (!result.isSafe) {
            Timber.w("Security issues detected: ${result.issues}")
        }
        return result
    }

    /** 執行所有安全檢查 */
    fun runSecurityChecks(): SecurityCheckResult {
        return SecurityCheckResult(
            isRooted = checkRoot(),
            isEmulator = checkEmulator(),
            isDebuggable = checkDebuggable(),
            isAdbEnabled = checkAdbEnabled()
        )
    }

    private fun checkRoot(): Boolean {
        return try {
            RootBeer(context).isRooted
        } catch (e: Exception) {
            Timber.e(e, "Root check failed")
            false
        }
    }

    private fun checkEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") ||
                Build.DEVICE.startsWith("generic"))
    }

    private fun checkDebuggable(): Boolean {
        return (context.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun checkAdbEnabled(): Boolean {
        return android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.ADB_ENABLED, 0
        ) == 1
    }
}
```

---

## 📄 domain/model/Device.kt

```kotlin
package com.smarthome.guardian.domain.model

/**
 * 家庭設備領域模型
 */
data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val roomId: String,
    val roomName: String,
    val status: DeviceStatus,
    val isOn: Boolean,
    val isLocked: Boolean,          // 管理員鎖定（禁止控制）
    val lastSeenMillis: Long,
    val firmware: String,
    val macAddress: String,
    val signalStrength: Int,        // 0-100
    val batteryLevel: Int?,         // null 表示有線供電
    val properties: Map<String, Any> = emptyMap() // 設備自訂屬性
)

enum class DeviceType(val displayName: String, val iconName: String) {
    LIGHT("燈光", "lightbulb"),
    DOOR_LOCK("門鎖", "lock"),
    CAMERA("攝影機", "videocam"),
    SENSOR_MOTION("動作感應器", "sensors"),
    SENSOR_DOOR("門窗感應器", "door_front"),
    OUTLET("智慧插座", "outlet"),
    THERMOSTAT("溫控器", "thermostat"),
    ALARM("警報器", "alarm")
}

enum class DeviceStatus {
    ONLINE,     // 正常在線
    OFFLINE,    // 離線
    ERROR,      // 錯誤狀態
    UPDATING    // 韌體更新中
}

/**
 * 設備操作指令
 */
data class DeviceCommand(
    val deviceId: String,
    val action: String,             // "SET_ON", "SET_OFF", "SET_BRIGHTNESS" 等
    val payload: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    var signature: String = ""      // HMAC 簽章（傳送前填入）
)
```

---

## 📄 domain/model/SecurityAlert.kt

```kotlin
package com.smarthome.guardian.domain.model

/**
 * 安全警報領域模型
 */
data class SecurityAlert(
    val id: String,
    val type: AlertType,
    val severity: Severity,
    val deviceId: String?,
    val deviceName: String?,
    val message: String,
    val detail: String?,
    val timestamp: Long,
    val isAcknowledged: Boolean,
    val acknowledgedBy: String?,
    val acknowledgedAt: Long?,
    val actionTaken: String?
)

enum class AlertType(val displayName: String) {
    INTRUSION("入侵偵測"),
    DEVICE_OFFLINE("設備離線"),
    AUTH_FAILURE("認證失敗"),
    ANOMALY("異常行為"),
    BRUTE_FORCE("暴力破解嘗試"),
    TAMPER("設備遭竄改"),
    SYSTEM("系統警告")
}

enum class Severity(val level: Int, val displayName: String) {
    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高"),
    CRITICAL(4, "緊急")
}
```

---

## 📄 domain/model/AuditLog.kt

```kotlin
package com.smarthome.guardian.domain.model

/**
 * 稽核日誌領域模型（防竄改）
 */
data class AuditLog(
    val id: String,
    val userId: String,
    val userDisplayName: String,
    val action: AuditAction,
    val targetId: String?,
    val targetName: String?,
    val description: String,
    val beforeState: String?,   // JSON 格式的操作前狀態
    val afterState: String?,    // JSON 格式的操作後狀態
    val ipAddress: String,
    val deviceFingerprint: String,
    val timestamp: Long,
    val signature: String,          // HMAC-SHA256 防竄改簽章
    val isSignatureValid: Boolean?  // null = 未驗證
)

enum class AuditAction(val displayName: String) {
    LOGIN_SUCCESS("登入成功"),
    LOGIN_FAILED("登入失敗"),
    LOGOUT("登出"),
    DEVICE_CONTROL("控制設備"),
    DEVICE_SETTINGS_CHANGED("修改設備設定"),
    ACCESS_RULE_CREATED("建立存取規則"),
    ACCESS_RULE_DELETED("刪除存取規則"),
    USER_INVITED("邀請用戶"),
    USER_ROLE_CHANGED("修改用戶角色"),
    USER_REVOKED("撤銷用戶存取"),
    ALERT_ACKNOWLEDGED("確認安全警報"),
    SYSTEM_SETTING_CHANGED("修改系統設定"),
    EXPORT_LOGS("匯出日誌"),
    TOKEN_REFRESH("Token 更新"),
    AUTH_BYPASS_ATTEMPT("嘗試繞過認證")
}
```

---

## 📄 presentation/theme/Theme.kt

```kotlin
package com.smarthome.guardian.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 主色調：科技青藍
val PrimaryColor = Color(0xFF00D4FF)
val SecondaryColor = Color(0xFF7B2FBE)
val BackgroundColor = Color(0xFF0A0E1A)
val SurfaceColor = Color(0xFF121827)
val ErrorColor = Color(0xFFFF4444)
val SuccessColor = Color(0xFF00C853)
val WarningColor = Color(0xFFFFB300)

// 設備狀態色
val OnlineColor = Color(0xFF00C853)
val OfflineColor = Color(0xFF546E7A)
val AlertColor = Color(0xFFFF4444)

// 安全等級色
val SecureGreen = Color(0xFF00C853)
val WarnAmber = Color(0xFFFFB300)
val CriticalRed = Color(0xFFFF4444)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    background = BackgroundColor,
    surface = SurfaceColor,
    error = ErrorColor,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.White
)

/**
 * SmartHome Guardian 應用程式主題
 * 僅支援深色模式（資安 APP 最佳實踐）
 */
@Composable
fun SmartHomeGuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = GuardianTypography,
        shapes = GuardianShapes,
        content = content
    )
}
```

---

## 📄 presentation/auth/LoginScreen.kt

```kotlin
package com.smarthome.guardian.presentation.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smarthome.guardian.presentation.theme.*

/**
 * 登入畫面
 * 支援帳號/密碼、生物辨識、PIN 碼三種登入方式
 * 啟用 FLAG_SECURE 防止截圖
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val passwordFocusRequester = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }

    // FLAG_SECURE：禁止截圖/螢幕錄影
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // 監聽狀態變化
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.Authenticated -> onLoginSuccess()
            is AuthUiState.Error -> {
                snackbarMessage = state.message
                showSnackbar = true
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo 區域
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "SmartHome Guardian",
                tint = PrimaryColor,
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = "SmartHome Guardian",
                style = MaterialTheme.typography.headlineMedium,
                color = PrimaryColor
            )
            Text(
                text = "智慧家庭資安管理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Email 輸入
            OutlinedTextField(
                value = viewModel.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("電子郵件") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 密碼輸入
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("密碼") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "隱藏密碼" else "顯示密碼"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.loginWithCredentials() }
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester)
            )

            // 登入按鈕
            Button(
                onClick = { viewModel.loginWithCredentials() },
                enabled = uiState !is AuthUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("登入", style = MaterialTheme.typography.titleMedium)
                }
            }

            // 分隔線
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "  或  ",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // 生物辨識登入
            OutlinedButton(
                onClick = { viewModel.loginWithBiometric(context as Activity) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("使用生物辨識登入")
            }
        }

        // 錯誤 Snackbar
        if (showSnackbar) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { showSnackbar = false }) {
                        Text("關閉", color = PrimaryColor)
                    }
                },
                containerColor = ErrorColor
            ) {
                Text(snackbarMessage, color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}
```

---

## 📄 presentation/dashboard/DashboardScreen.kt

```kotlin
package com.smarthome.guardian.presentation.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smarthome.guardian.domain.model.*
import com.smarthome.guardian.presentation.theme.*

/**
 * 主儀表板畫面
 * 顯示所有設備狀態、安全等級、及最近警報
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onDeviceClick: (String) -> Unit,
    onAlertsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedRoom by remember { mutableStateOf("全部") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SmartHome Guardian", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "歡迎回來",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    // 警報通知鈴
                    BadgedBox(
                        badge = {
                            if (uiState.unreadAlertCount > 0) {
                                Badge { Text(uiState.unreadAlertCount.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = onAlertsClick) {
                            Icon(Icons.Default.Notifications, "通知")
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "設定")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceColor
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { /* 新增設備 */ },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("新增設備") },
                containerColor = PrimaryColor,
                contentColor = Color.Black
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColor)
                .padding(paddingValues)
        ) {
            // 安全狀態橫幅
            SecurityStatusBanner(
                securityLevel = uiState.securityLevel,
                alertCount = uiState.unreadAlertCount,
                onBannerClick = onAlertsClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 房間篩選器
            RoomFilterChips(
                rooms = uiState.rooms,
                selectedRoom = selectedRoom,
                onRoomSelected = { selectedRoom = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 設備卡片格
            val filteredDevices = if (selectedRoom == "全部") uiState.devices
                                  else uiState.devices.filter { it.roomName == selectedRoom }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredDevices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        onCardClick = { onDeviceClick(device.id) },
                        onToggle = { viewModel.toggleDevice(device.id, it) }
                    )
                }
            }
        }
    }
}

/**
 * 安全狀態橫幅元件
 */
@Composable
fun SecurityStatusBanner(
    securityLevel: SecurityLevel,
    alertCount: Int,
    onBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, icon, message) = when (securityLevel) {
        SecurityLevel.SECURE -> Triple(
            SecureGreen.copy(alpha = 0.15f),
            Icons.Default.CheckCircle,
            "系統安全 · 所有設備正常"
        )
        SecurityLevel.WARNING -> Triple(
            WarnAmber.copy(alpha = 0.15f),
            Icons.Default.Warning,
            "$alertCount 個警報待處理"
        )
        SecurityLevel.CRITICAL -> Triple(
            CriticalRed.copy(alpha = 0.15f),
            Icons.Default.Error,
            "系統警告！立即處理"
        )
    }

    val borderColor = when (securityLevel) {
        SecurityLevel.SECURE -> SecureGreen
        SecurityLevel.WARNING -> WarnAmber
        SecurityLevel.CRITICAL -> CriticalRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBannerClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = borderColor, modifier = Modifier.size(28.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = borderColor,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = borderColor.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 設備卡片元件
 */
@Composable
fun DeviceCard(
    device: Device,
    onCardClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val statusColor = when (device.status) {
        DeviceStatus.ONLINE -> OnlineColor
        DeviceStatus.OFFLINE -> OfflineColor
        DeviceStatus.ERROR -> AlertColor
        DeviceStatus.UPDATING -> WarnAmber
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (device.status == DeviceStatus.OFFLINE)
                SurfaceColor.copy(alpha = 0.5f) else SurfaceColor
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 設備類型圖示
                Icon(
                    imageVector = getDeviceIcon(device.type),
                    contentDescription = device.type.displayName,
                    tint = if (device.isOn && device.status == DeviceStatus.ONLINE)
                        PrimaryColor else OfflineColor,
                    modifier = Modifier.size(32.dp)
                )
                // 狀態指示燈
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
                )
            }

            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )

            Text(
                text = device.roomName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            // 開關（僅 LIGHT, OUTLET 等可切換設備）
            if (device.type in listOf(DeviceType.LIGHT, DeviceType.OUTLET, DeviceType.THERMOSTAT)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (device.isOn) "開啟" else "關閉",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (device.isOn) PrimaryColor else OfflineColor
                    )
                    Switch(
                        checked = device.isOn,
                        onCheckedChange = { if (!device.isLocked) onToggle(it) },
                        enabled = device.status == DeviceStatus.ONLINE && !device.isLocked,
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }
    }
}

private fun getDeviceIcon(type: DeviceType) = when (type) {
    DeviceType.LIGHT -> Icons.Default.LightMode
    DeviceType.DOOR_LOCK -> Icons.Default.Lock
    DeviceType.CAMERA -> Icons.Default.Videocam
    DeviceType.SENSOR_MOTION -> Icons.Default.Sensors
    DeviceType.SENSOR_DOOR -> Icons.Default.DoorFront
    DeviceType.OUTLET -> Icons.Default.Outlet
    DeviceType.THERMOSTAT -> Icons.Default.Thermostat
    DeviceType.ALARM -> Icons.Default.NotificationsActive
}

private fun androidx.compose.ui.Modifier.scale(scale: Float) = this

enum class SecurityLevel { SECURE, WARNING, CRITICAL }
```

---

## 📄 data/remote/WebSocketManager.kt

```kotlin
package com.smarthome.guardian.data.remote

import com.google.gson.Gson
import com.smarthome.guardian.domain.model.DeviceStatus
import com.smarthome.guardian.domain.model.SecurityAlert
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * WebSocket 連線管理器
 * 管理即時設備狀態更新與安全警報推播
 * 包含自動重連（指數退避）與心跳機制
 */
@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _alertFlow = MutableSharedFlow<SecurityAlert>(replay = 0, extraBufferCapacity = 64)
    val alertFlow: SharedFlow<SecurityAlert> = _alertFlow

    private val deviceStatusFlows = mutableMapOf<String, MutableSharedFlow<DeviceStatus>>()
    private var reconnectAttempts = 0
    private val maxReconnectDelay = 60_000L

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    /** 建立 WebSocket 連線 */
    fun connect(wsUrl: String, token: String) {
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, createListener())
        startHeartbeat()
        Timber.d("WebSocket connecting to $wsUrl")
    }

    /** 斷開連線 */
    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        Timber.d("WebSocket disconnected")
    }

    /** 訂閱特定設備狀態更新 */
    fun observeDeviceStatus(deviceId: String): Flow<DeviceStatus> {
        return deviceStatusFlows.getOrPut(deviceId) {
            MutableSharedFlow(replay = 1, extraBufferCapacity = 16)
        }
    }

    /** 訂閱安全警報 */
    fun observeAlerts(): Flow<SecurityAlert> = alertFlow

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts = 0
            _connectionState.value = ConnectionState.Connected
            Timber.d("WebSocket connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            parseMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = ConnectionState.Disconnected
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "WebSocket failure")
            _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")
            scheduleReconnect()
        }
    }

    private fun parseMessage(text: String) {
        try {
            val json = gson.fromJson(text, Map::class.java)
            when (json["type"] as? String) {
                "device.status" -> {
                    val deviceId = json["deviceId"] as? String ?: return
                    val status = DeviceStatus.valueOf(json["status"] as? String ?: return)
                    deviceStatusFlows[deviceId]?.tryEmit(status)
                }
                "security.alert" -> {
                    val alert = gson.fromJson(text, SecurityAlert::class.java)
                    _alertFlow.tryEmit(alert)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WebSocket message")
        }
    }

    /** 指數退避重連 */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delay = min(1000L * (1 shl reconnectAttempts), maxReconnectDelay)
            reconnectAttempts++
            Timber.d("Reconnecting in ${delay}ms (attempt #$reconnectAttempts)")
            delay(delay)
            // 重新連線邏輯（需要從外部注入 URL 和 Token）
        }
    }

    /** 心跳機制 */
    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                webSocket?.send("{\"type\":\"ping\"}")
            }
        }
    }

    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}
```

---

## 📄 data/local/AppDatabase.kt

```kotlin
package com.smarthome.guardian.data.local

import androidx.room.*
import com.smarthome.guardian.data.local.entity.DeviceEntity
import com.smarthome.guardian.data.local.entity.AlertEntity
import com.smarthome.guardian.data.local.entity.AuditLogEntity
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Room 加密資料庫
 * 使用 SQLCipher 提供 AES-256 資料庫加密
 */
@Database(
    entities = [DeviceEntity::class, AlertEntity::class, AuditLogEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun alertDao(): AlertDao
    abstract fun auditDao(): AuditLogDao

    companion object {
        /**
         * 建立加密資料庫實例
         * @param context 應用程式 Context
         * @param databaseKey 資料庫加密金鑰（由 CryptoManager 管理）
         */
        fun create(
            context: android.content.Context,
            databaseKey: ByteArray
        ): AppDatabase {
            val passphrase = SQLiteDatabase.getBytes(
                android.util.Base64.encodeToString(databaseKey, android.util.Base64.NO_WRAP).toCharArray()
            )
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "guardian_encrypted.db"
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

class Converters {
    @TypeConverter
    fun fromString(value: String?): List<String> =
        value?.split(",") ?: emptyList()

    @TypeConverter
    fun toString(list: List<String>): String = list.joinToString(",")
}
```

---

## 📄 di/NetworkModule.kt

```kotlin
package com.smarthome.guardian.di

import com.smarthome.guardian.BuildConfig
import com.smarthome.guardian.security.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 網路層 Hilt 模組
 * 提供 OkHttpClient、Retrofit、Certificate Pinning
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.smarthome-guardian.local/"

    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner {
        return CertificatePinner.Builder()
            .add("api.smarthome-guardian.local",
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") // 替換為實際 pin
            .add("api.smarthome-guardian.local",
                "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // 備用 pin
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): Interceptor {
        return Interceptor { chain ->
            val token = tokenManager.getAccessToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        certificatePinner: CertificatePinner,
        authInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor { msg ->
                        // 不記錄 Authorization header
                        if (!msg.contains("Authorization")) Timber.d(msg)
                    }.apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
```

---

## 📄 presentation/security/SecurityMonitorScreen.kt

```kotlin
package com.smarthome.guardian.presentation.security

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smarthome.guardian.domain.model.*
import com.smarthome.guardian.presentation.theme.*

/**
 * 安全監控畫面
 * 即時顯示安全評分、威脅事件串流、及警報歷史
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityMonitorScreen(
    onBackClick: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentAlerts by viewModel.recentAlerts.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全監控") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.acknowledgeAll() }) {
                        Text("全部確認", color = PrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColor)
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 安全評分
            item {
                SecurityScoreCard(score = uiState.securityScore)
            }

            // 即時事件串流
            item {
                Text(
                    "即時事件",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryColor
                )
            }

            items(
                items = recentAlerts,
                key = { it.id }
            ) { alert ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically() + fadeIn()
                ) {
                    AlertListItem(
                        alert = alert,
                        onAcknowledge = { viewModel.acknowledgeAlert(alert.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SecurityScoreCard(score: Int) {
    val scoreColor = when {
        score >= 80 -> SecureGreen
        score >= 50 -> WarnAmber
        else -> CriticalRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("安全評分", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 8.dp,
                    color = scoreColor,
                    trackColor = SurfaceColor
                )
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.displaySmall,
                    color = scoreColor
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    score >= 80 -> "系統安全"
                    score >= 50 -> "需要注意"
                    else -> "高風險"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = scoreColor
            )
        }
    }
}

@Composable
fun AlertListItem(
    alert: SecurityAlert,
    onAcknowledge: () -> Unit
) {
    val severityColor = when (alert.severity) {
        Severity.CRITICAL -> CriticalRed
        Severity.HIGH -> Color(0xFFFF6B35)
        Severity.MEDIUM -> WarnAmber
        Severity.LOW -> OfflineColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = when (alert.severity) {
                    Severity.CRITICAL -> Icons.Default.Error
                    Severity.HIGH -> Icons.Default.Warning
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = severityColor,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        alert.type.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = severityColor
                    )
                    Text(
                        formatTimestamp(alert.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Text(
                    alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                alert.deviceName?.let {
                    Text(
                        "設備：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (!alert.isAcknowledged) {
                TextButton(
                    onClick = onAcknowledge,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("確認", color = PrimaryColor, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Icon(
                    Icons.Default.CheckCircle,
                    "已確認",
                    tint = SecureGreen.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
```

---

## 📄 presentation/audit/AuditLogScreen.kt

```kotlin
package com.smarthome.guardian.presentation.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smarthome.guardian.domain.model.AuditLog
import com.smarthome.guardian.domain.model.AuditAction
import com.smarthome.guardian.presentation.theme.*

/**
 * 稽核日誌畫面
 * 顯示所有操作記錄，包含 HMAC 簽章完整性驗證
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    onBackClick: () -> Unit,
    viewModel: AuditViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("稽核日誌") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportLogs() }) {
                        Icon(Icons.Default.Download, "匯出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColor)
                .padding(paddingValues)
        ) {
            // 搜尋欄
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.search(it)
                },
                placeholder = { Text("搜尋操作、用戶、設備...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )

            // 日誌列表
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    AuditLogItem(log = log)
                }
            }
        }
    }
}

@Composable
fun AuditLogItem(log: AuditLog) {
    var expanded by remember { mutableStateOf(false) }

    val (actionColor, actionIcon) = when (log.action) {
        AuditAction.LOGIN_SUCCESS -> PrimaryColor to Icons.Default.Login
        AuditAction.LOGIN_FAILED, AuditAction.AUTH_BYPASS_ATTEMPT ->
            CriticalRed to Icons.Default.NoEncryption
        AuditAction.LOGOUT -> OfflineColor to Icons.Default.Logout
        AuditAction.DEVICE_CONTROL -> WarnAmber to Icons.Default.Devices
        AuditAction.ACCESS_RULE_CREATED, AuditAction.ACCESS_RULE_DELETED ->
            SecondaryColor to Icons.Default.AdminPanelSettings
        else -> MaterialTheme.colorScheme.onSurface to Icons.Default.Info
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(actionIcon, null, tint = actionColor, modifier = Modifier.size(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        log.action.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = actionColor
                    )
                    Text(
                        log.userDisplayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // HMAC 完整性狀態
                val (sigIcon, sigColor) = when (log.isSignatureValid) {
                    true -> Icons.Default.VerifiedUser to SecureGreen
                    false -> Icons.Default.GppBad to CriticalRed
                    null -> Icons.Default.Help to OfflineColor
                }
                Icon(sigIcon, "簽章狀態", tint = sigColor, modifier = Modifier.size(16.dp))

                Text(
                    formatTime(log.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // 展開詳情
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(Modifier.height(8.dp))

                Text(log.description, style = MaterialTheme.typography.bodySmall)

                log.targetName?.let {
                    Text(
                        "目標：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Text(
                    "IP：${log.ipAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                if (log.isSignatureValid == false) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = CriticalRed, modifier = Modifier.size(14.dp))
                        Text(
                            "⚠ 此日誌簽章無效，可能已遭竄改！",
                            style = MaterialTheme.typography.labelSmall,
                            color = CriticalRed
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
```

---

## 📄 proguard-rules.pro

```proguard
# 保留 Kotlin 序列化
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Gson（資料模型）
-keep class com.smarthome.guardian.domain.model.** { *; }
-keep class com.smarthome.guardian.data.remote.dto.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Paho MQTT
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# 移除日誌（生產環境）
-assumenosideeffects class timber.log.Timber {
    public static void d(...);
    public static void v(...);
    public static void i(...);
}

# 防止類別名稱被混淆（必要的反射使用）
-keepnames class com.smarthome.guardian.security.** { *; }
```
