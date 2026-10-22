package io.github.esseckers.pursuitofwind.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

/**
 * Values encrypted with AES-256-GCM under a key that never leaves the Android Keystore, stored in their own DataStore
 * file. Not `androidx.security:security-crypto`, which is deprecated. Keystore work runs off the caller's thread.
 *
 * A value reads as absent only when it can never be decrypted again: the key is gone, or the ciphertext does not match
 * it (a backup restored on another device). Other Keystore failures can be passing and throw, so the caller does not
 * mistake them for a missing installation id.
 */
internal class AndroidKeystoreTokenStore(
    context: Context,
    storageName: String,
) : SecureTokenStore,
    AutoCloseable {
    private val alias = "pursuitofwind.$storageName"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store =
        PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = {
                context.filesDir
                    .resolve("datastore/$storageName.secure.preferences_pb")
                    .absolutePath
                    .toPath()
            },
        )

    @Volatile
    private var cachedKey: SecretKey? = null

    override suspend fun read(key: String): String? =
        withContext(Dispatchers.IO) {
            store.data.first()[stringPreferencesKey(key)]?.let(::decrypt)
        }

    override suspend fun write(
        key: String,
        value: String,
    ) {
        withContext(Dispatchers.IO) {
            val encrypted = encrypt(value)
            store.edit { it[stringPreferencesKey(key)] = encrypted }
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { store.edit { it.remove(stringPreferencesKey(key)) } }
    }

    override fun close() {
        scope.cancel()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(create = true)) }
        return Base64.encode(cipher.iv + cipher.doFinal(value.encodeToByteArray()))
    }

    /** Null when the value can never be decrypted again; other failures throw. */
    private fun decrypt(encoded: String): String? {
        val secretKey = key(create = false)
        val bytes = runCatching { Base64.decode(encoded) }.getOrNull()?.takeIf { it.size > IV_BYTES }
        return if (secretKey == null || bytes == null) null else decrypt(secretKey, bytes)
    }

    private fun decrypt(
        secretKey: SecretKey,
        bytes: ByteArray,
    ): String? =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
        } catch (_: AEADBadTagException) {
            null
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        }

    @Synchronized
    private fun key(create: Boolean): SecretKey? {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        val key = existing ?: if (create) generateKey() else null
        cachedKey = key
        return key
    }

    private fun generateKey(): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
