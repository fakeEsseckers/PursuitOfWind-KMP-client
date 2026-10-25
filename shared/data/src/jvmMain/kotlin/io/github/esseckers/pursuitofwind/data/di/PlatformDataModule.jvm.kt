package io.github.esseckers.pursuitofwind.data.di

import androidx.room3.Room
import androidx.room3.useWriterConnection
import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.data.auth.InMemorySecureTokenStore
import io.github.esseckers.pursuitofwind.data.auth.SecureTokenStore
import io.github.esseckers.pursuitofwind.data.internal.local.LocalSettings
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

// The JVM target exists for tests: everything lives in memory or in a temporary directory.

internal actual fun platformDataModule(storageName: String): Module =
    module {
        single<SecureTokenStore> { InMemorySecureTokenStore() }
        single { PursuitOfWindDatabase.build(Room.inMemoryDatabaseBuilder<PursuitOfWindDatabase>()) } withOptions
            { onClose { it?.closeWhenIdle() } }
        single { LocalSettings.open(Files.createTempDirectory(storageName).resolve("$storageName.preferences_pb").toString()) } withOptions
            { onClose { it?.close() } }
    }

internal actual fun remoteHttpEngine(): HttpClientEngine = OkHttp.create()

/** The contract knows Android and iOS only; JVM tests sign in as Android. */
internal actual val currentPlatform: PlatformDto = PlatformDto.ANDROID

/**
 * Closes the database once no query holds its connection, or after [IDLE_WAIT] (logged). Tests close the graph while
 * work they started may still be reading (the last query of a cancelled flow, on an IO thread), and closing the bundled
 * SQLite under a running statement can crash the JVM. This makes that unlikely, not impossible: a query can still start
 * between the wait and the close.
 */
private fun PursuitOfWindDatabase.closeWhenIdle() {
    val idle = runBlocking { withTimeoutOrNull(IDLE_WAIT) { useWriterConnection { } } }
    if (idle == null) Logger.withTag("data").w { "Closing the test database while a query still holds it" }
    close()
}

private val IDLE_WAIT = 5.seconds
