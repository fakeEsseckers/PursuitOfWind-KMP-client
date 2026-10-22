package io.github.esseckers.pursuitofwind.data.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Generic-password items in the Keychain, one per key, under [service]. Readable after the first unlock (background
 * refresh runs while the phone is locked) and never synced or restored to another device. Keychain calls are
 * synchronous, so they run off the caller's thread.
 *
 * Only `errSecItemNotFound` reads as absent. Any other status (for example `errSecInteractionNotAllowed` before the
 * first unlock) throws, so the caller does not mistake it for a missing installation id.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class KeychainTokenStore(
    private val service: String,
) : SecureTokenStore {
    override suspend fun read(key: String): String? =
        withContext(Dispatchers.IO) {
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status =
                    withQuery(key, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { query ->
                        SecItemCopyMatching(query, result.ptr)
                    }
                when (status) {
                    errSecSuccess -> {
                        (CFBridgingRelease(result.value) as? NSData)?.let {
                            NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString()
                        }
                    }

                    errSecItemNotFound -> {
                        null
                    }

                    else -> {
                        error("Keychain read failed with OSStatus $status")
                    }
                }
            }
        }

    override suspend fun write(
        key: String,
        value: String,
    ) {
        withContext(Dispatchers.IO) {
            val data = requireNotNull(NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding))
            val dataRef = CFBridgingRetain(data)
            try {
                val updated = withQuery(key) { query -> withAttributes(kSecValueData to dataRef) { SecItemUpdate(query, it) } }
                val status =
                    if (updated != errSecItemNotFound) {
                        updated
                    } else {
                        withQuery(
                            key,
                            kSecValueData to dataRef,
                            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        ) { query ->
                            SecItemAdd(query, null)
                        }
                    }
                check(status == errSecSuccess) { "Keychain write failed with OSStatus $status" }
            } finally {
                CFRelease(dataRef)
            }
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            val status = withQuery(key) { query -> SecItemDelete(query) }
            check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain delete failed with OSStatus $status" }
        }
    }

    /** Runs [block] with a query for [key] plus [extra] attributes, and releases everything it created afterwards. */
    private fun <T> withQuery(
        key: String,
        vararg extra: Pair<CFStringRef?, CFTypeRef?>,
        block: (CFDictionaryRef?) -> T,
    ): T {
        val serviceRef = CFBridgingRetain(NSString.create(string = service))
        val accountRef = CFBridgingRetain(NSString.create(string = key))
        try {
            return withAttributes(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
                kSecAttrAccount to accountRef,
                *extra,
            ) {
                block(it)
            }
        } finally {
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }

    /** Runs [block] with a dictionary of [attributes] (retained by the dictionary while it lives). */
    private fun <T> withAttributes(
        vararg attributes: Pair<CFStringRef?, CFTypeRef?>,
        block: (CFDictionaryRef?) -> T,
    ): T {
        val dictionary =
            CFDictionaryCreateMutable(
                null,
                attributes.size.convert(),
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            )
        attributes.forEach { (name, value) -> CFDictionaryAddValue(dictionary, name, value) }
        try {
            return block(dictionary)
        } finally {
            CFRelease(dictionary)
        }
    }
}
