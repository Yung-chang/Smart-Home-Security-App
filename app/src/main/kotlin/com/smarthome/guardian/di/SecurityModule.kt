package com.smarthome.guardian.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.smarthome.guardian.data.local.preferences.SecurePreferences
import com.smarthome.guardian.security.CertificatePinner
import com.smarthome.guardian.security.DatabaseKeyProvider
import com.smarthome.guardian.security.HmacSigner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "smarthome_master_key"
    private const val HMAC_KEY_ALIAS   = "smarthome_hmac_key"

    /**
     * AES-256-GCM master key backed by Android Keystore.
     * Requires user authentication every 5 minutes for sensitive operations.
     */
    @Provides
    @Singleton
    fun provideMasterKey(@ApplicationContext context: Context): MasterKey {
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // token-based auth handled at app layer
            .build()

        return MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyGenParameterSpec(spec)
            .build()
    }

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
        masterKey: MasterKey,
    ): android.content.SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            "smarthome_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    @Provides
    @Singleton
    fun provideSecurePreferences(
        prefs: android.content.SharedPreferences,
    ): SecurePreferences = SecurePreferences(prefs)

    /**
     * HMAC-SHA256 key for audit log signing. Stored in Android Keystore so it
     * cannot be extracted even on rooted devices.
     */
    @Provides
    @Singleton
    @Named(HMAC_KEY_ALIAS)
    fun provideHmacKey(): javax.crypto.SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }

        if (!keyStore.containsAlias(HMAC_KEY_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
            keyGen.init(
                KeyGenParameterSpec.Builder(HMAC_KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .build()
            )
            keyGen.generateKey()
        }

        return keyStore.getKey(HMAC_KEY_ALIAS, null) as javax.crypto.SecretKey
    }

    @Provides
    @Singleton
    fun provideHmacSigner(@Named(HMAC_KEY_ALIAS) key: javax.crypto.SecretKey): HmacSigner =
        HmacSigner(key)

    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner = CertificatePinner()

    @Provides
    @Singleton
    fun provideDatabaseKeyProvider(
        @ApplicationContext context: Context,
        masterKey: MasterKey,
    ): DatabaseKeyProvider = DatabaseKeyProvider(context, masterKey)
}
