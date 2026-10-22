package io.github.esseckers.pursuitofwind.data.di

import androidx.room3.Room
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.data.auth.KeychainTokenStore
import io.github.esseckers.pursuitofwind.data.auth.SecureTokenStore
import io.github.esseckers.pursuitofwind.data.internal.local.LocalSettings
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

internal actual fun platformDataModule(storageName: String): Module =
    module {
        single<SecureTokenStore> { KeychainTokenStore(service = "io.github.esseckers.pursuitofwind.$storageName") }
        single {
            PursuitOfWindDatabase.build(
                Room.databaseBuilder<PursuitOfWindDatabase>("${cacheDirectory()}/$storageName.db"),
            )
        } withOptions
            { onClose { it?.close() } }
        single { LocalSettings.open("${cacheDirectory()}/$storageName.preferences_pb") } withOptions { onClose { it?.close() } }
    }

internal actual fun remoteHttpEngine(): HttpClientEngine = Darwin.create()

internal actual val currentPlatform: PlatformDto = PlatformDto.IOS

/**
 * `Application Support/PursuitOfWind`: kept across launches, unlike Caches, which iOS may purge, and excluded from
 * iCloud backup, because everything in it can be fetched again.
 */
@OptIn(ExperimentalForeignApi::class)
private fun cacheDirectory(): String {
    val support =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    val directory = requireNotNull(support?.URLByAppendingPathComponent("PursuitOfWind")) { "Application Support is not available" }
    NSFileManager.defaultManager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)
    directory.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return requireNotNull(directory.path)
}
