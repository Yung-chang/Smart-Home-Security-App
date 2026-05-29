package com.smarthome.guardian.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 負責所有 AES-256-GCM 對稱加密及 RSA 非對稱金鑰對的管理。
 *
 * 金鑰全部存放於 Android Keystore，不可匯出至應用程式記憶體之外。
 * 每次加密皆產生隨機 IV，確保相同明文不會產生相同密文。
 *
 * ## 使用範例
 * ```kotlin
 * val encrypted = cryptoManager.encrypt("secret".toByteArray())
 * val plaintext = cryptoManager.decrypt(encrypted)
 * ```
 */
@Singleton
class CryptoManager @Inject constructor() {

    // ── 常數 ──────────────────────────────────────────────────────────────────

    private companion object {
        const val ANDROID_KEYSTORE   = "AndroidKeyStore"
        const val TRANSFORMATION     = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS      = 256
        const val GCM_TAG_LENGTH     = 128
        const val DEFAULT_KEY_ALIAS  = "smarthome_aes_master"
        const val EC_CURVE           = "P-256"
    }

    // ── 公開 API ──────────────────────────────────────────────────────────────

    /**
     * 使用 Android Keystore 中 [keyAlias] 對應的 AES-256-GCM 金鑰加密 [plaintext]。
     *
     * @param plaintext 欲加密的原始位元組
     * @param keyAlias  Keystore 金鑰別名，預設為 [DEFAULT_KEY_ALIAS]
     * @return [EncryptedData]，包含密文、IV 及金鑰別名
     * @throws EncryptionException 加密流程任何步驟失敗時
     */
    fun encrypt(
        plaintext: ByteArray,
        keyAlias: String = DEFAULT_KEY_ALIAS,
    ): EncryptedData = runCatching {
        val key    = getOrCreateSecretKey(keyAlias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        EncryptedData(ciphertext = ciphertext, iv = cipher.iv, keyAlias = keyAlias)
    }.getOrElse { cause ->
        Timber.e(cause, "Encryption failed for alias=$keyAlias")
        throw EncryptionException("Failed to encrypt data", cause)
    }

    /**
     * 解密 [encryptedData]，回傳原始明文位元組。
     *
     * @param encryptedData 先前由 [encrypt] 產生的加密資料
     * @return 解密後的原始位元組
     * @throws DecryptionException 解密失敗（資料竄改、金鑰不符、IV 錯誤等）
     */
    fun decrypt(encryptedData: EncryptedData): ByteArray = runCatching {
        val key    = getOrCreateSecretKey(encryptedData.keyAlias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec   = GCMParameterSpec(GCM_TAG_LENGTH, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        cipher.doFinal(encryptedData.ciphertext)
    }.getOrElse { cause ->
        Timber.e(cause, "Decryption failed for alias=${encryptedData.keyAlias}")
        throw DecryptionException("Failed to decrypt data — possible tampering", cause)
    }

    /**
     * 在 Android Keystore 中產生 EC P-256 金鑰對，用於裝置身份認證（TLS Client Auth / ECDSA 簽章）。
     *
     * 若 [alias] 已存在金鑰對，直接回傳現有的；需更換時請先呼叫 [deleteKey]。
     *
     * @param alias 金鑰對別名
     * @return [KeyPair]（公鑰可安全傳遞至伺服器；私鑰永不離開 Keystore）
     * @throws KeystoreException 產生金鑰失敗時
     */
    fun generateKeyPair(alias: String): KeyPair = runCatching {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(alias)) {
            val privateKey = keyStore.getKey(alias, null) as java.security.PrivateKey
            val publicKey  = keyStore.getCertificate(alias).publicKey
            return@runCatching KeyPair(publicKey, privateKey)
        }

        val keyGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        keyGen.initialize(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        keyGen.generateKeyPair()
    }.getOrElse { cause ->
        Timber.e(cause, "KeyPair generation failed for alias=$alias")
        throw KeystoreException("Failed to generate key pair for alias=$alias", cause)
    }

    /**
     * 從 Android Keystore 刪除指定別名的金鑰。
     * 刪除後以此金鑰加密的資料將**永久無法解密**，呼叫前請確認。
     */
    fun deleteKey(alias: String) {
        runCatching {
            loadKeyStore().deleteEntry(alias)
            Timber.d("Key deleted: alias=$alias")
        }.onFailure { cause ->
            Timber.e(cause, "Failed to delete key alias=$alias")
        }
    }

    /** 確認 Keystore 中是否存在指定別名的金鑰。 */
    fun keyExists(alias: String): Boolean =
        runCatching { loadKeyStore().containsAlias(alias) }.getOrDefault(false)

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private fun getOrCreateSecretKey(alias: String): SecretKey {
        val keyStore = loadKeyStore()

        if (keyStore.containsAlias(alias)) {
            return (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        }

        Timber.d("Creating new AES-256-GCM key: alias=$alias")
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true) // 強制使用隨機 IV
                .build()
        )
        return keyGen.generateKey()
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
}
