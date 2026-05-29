package com.smarthome.guardian.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 從 Android Keystore 衍生 SQLCipher 資料庫通行密語（Passphrase）。
 *
 * 通行密語不直接儲存在任何可讀取的位置，而是由 Keystore 加密的隨機位元組
 * 透過 EncryptedSharedPreferences 儲存其密文，每次啟動解密後使用。
 *
 * ## 金鑰衍生流程
 * ```
 * 首次啟動：
 *   Random 32 bytes → AES-256-GCM 加密（Keystore MasterKey）→ 儲存密文
 *
 * 後續啟動：
 *   讀取密文 → AES-256-GCM 解密（Keystore MasterKey）→ 取得原始 32 bytes
 * ```
 *
 * 即使裝置遭 Root，攻擊者也無法在不通過 Keystore 驗證的情況下取得通行密語。
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val masterKey: MasterKey,
) {

    /**
     * 取得資料庫通行密語（CharArray 格式，符合 SQLCipher API）。
     *
     * 首次呼叫時自動產生並加密儲存；後續呼叫解密回傳。
     * 呼叫後建議儘快使用，使用完畢後以 [CharArray.fill] 覆蓋記憶體。
     *
     * @return 32 字元的 Base64 通行密語
     * @throws KeystoreException 無法存取 Keystore 或解密失敗時
     */
    fun getDatabasePassphrase(): CharArray {
        val encryptedPrefs = buildEncryptedPrefs()
        val existingPassphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)

        if (existingPassphrase != null) {
            Timber.d("Database passphrase loaded from secure storage")
            return existingPassphrase.toCharArray()
        }

        Timber.d("Generating new database passphrase")
        val random     = java.security.SecureRandom()
        val rawBytes   = ByteArray(32).also { random.nextBytes(it) }
        val passphrase = Base64.encodeToString(rawBytes, Base64.NO_WRAP)

        rawBytes.fill(0) // 立即清除記憶體中的原始位元組

        encryptedPrefs.edit().putString(KEY_DB_PASSPHRASE, passphrase).apply()
        return passphrase.toCharArray()
    }

    private fun buildEncryptedPrefs(): android.content.SharedPreferences =
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private companion object {
        const val PREFS_NAME      = "smarthome_db_key_prefs"
        const val KEY_DB_PASSPHRASE = "db_passphrase"
    }
}
