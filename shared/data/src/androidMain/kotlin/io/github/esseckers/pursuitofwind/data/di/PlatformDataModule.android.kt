package io.github.esseckers.pursuitofwind.data.di

import android.content.Context
import androidx.room3.Room
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.data.auth.AndroidKeystoreTokenStore
import io.github.esseckers.pursuitofwind.data.auth.SecureTokenStore
import io.github.esseckers.pursuitofwind.data.internal.local.LocalSettings
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

internal actual fun platformDataModule(storageName: String): Module =
    module {
        single<SecureTokenStore> { AndroidKeystoreTokenStore(get<Context>(), storageName) } withOptions
            { onClose { (it as? AutoCloseable)?.close() } }
        single {
            val context = get<Context>()
            PursuitOfWindDatabase.build(
                Room.databaseBuilder<PursuitOfWindDatabase>(context, context.getDatabasePath("$storageName.db").absolutePath),
            )
        } withOptions { onClose { it?.close() } }
        single { LocalSettings.open(get<Context>().filesDir.resolve("datastore/$storageName.preferences_pb").absolutePath) } withOptions
            { onClose { it?.close() } }
    }

internal actual fun remoteHttpEngine(): HttpClientEngine = OkHttp.create()

internal actual val currentPlatform: PlatformDto = PlatformDto.ANDROID
