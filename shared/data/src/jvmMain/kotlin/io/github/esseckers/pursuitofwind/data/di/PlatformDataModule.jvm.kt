package io.github.esseckers.pursuitofwind.data.di

import androidx.room3.Room
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.data.auth.InMemorySecureTokenStore
import io.github.esseckers.pursuitofwind.data.auth.SecureTokenStore
import io.github.esseckers.pursuitofwind.data.internal.local.LocalSettings
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import java.nio.file.Files

// The JVM target exists for tests: everything lives in memory or in a temporary directory.

internal actual fun platformDataModule(storageName: String): Module =
    module {
        single<SecureTokenStore> { InMemorySecureTokenStore() }
        single { PursuitOfWindDatabase.build(Room.inMemoryDatabaseBuilder<PursuitOfWindDatabase>()) } withOptions
            { onClose { it?.close() } }
        single { LocalSettings.open(Files.createTempDirectory(storageName).resolve("$storageName.preferences_pb").toString()) } withOptions
            { onClose { it?.close() } }
    }

internal actual fun remoteHttpEngine(): HttpClientEngine = OkHttp.create()

/** The contract knows Android and iOS only; JVM tests sign in as Android. */
internal actual val currentPlatform: PlatformDto = PlatformDto.ANDROID
