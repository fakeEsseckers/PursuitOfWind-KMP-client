package io.github.esseckers.pursuitofwind.data.auth

/**
 * Small secrets kept encrypted at rest: the session tokens and the installation id. Implemented with an AES-GCM key
 * from the Android Keystore plus DataStore on Android, and with the Keychain on iOS. Values are opaque strings.
 */
internal interface SecureTokenStore {
    /**
     * The value, or null only when there is none (or it can never be decrypted again). Any other failure throws: the
     * caller must not mistake "could not read now" for "does not exist".
     */
    suspend fun read(key: String): String?

    suspend fun write(
        key: String,
        value: String,
    )

    suspend fun delete(key: String)
}

/** For tests, the JVM and demo sessions that must not outlive the process. */
internal class InMemorySecureTokenStore : SecureTokenStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }
}
