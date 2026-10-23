package io.github.esseckers.pursuitofwind.data.di

import io.github.esseckers.pursuitofwind.data.Environment
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.internal.local.UserCache
import io.github.esseckers.pursuitofwind.data.internal.network.AuthSession
import io.github.esseckers.pursuitofwind.data.internal.network.HttpClients
import io.github.esseckers.pursuitofwind.data.internal.network.PursuitOfWindApi
import io.github.esseckers.pursuitofwind.data.internal.network.RetryPolicy
import io.github.esseckers.pursuitofwind.data.internal.sync.CrewFeedSync
import io.github.esseckers.pursuitofwind.data.internal.sync.CrewStore
import io.github.esseckers.pursuitofwind.data.repository.AccountRepository
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.ForecastRepository
import io.github.esseckers.pursuitofwind.data.repository.PreferencesRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.RideRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * The data layer for [environment]: repositories backed by a cache of their own (database, settings and secure store
 * named after [Environment.storageName]) and an HTTP client talking to the fake backend or a real server.
 *
 * On Android the graph must also contain the application `Context` (Koin `androidContext`).
 *
 * @param clock time for the fake backend, token expiry and "updated" stamps; tests pass a controllable clock.
 */
public fun dataModule(
    environment: Environment,
    clock: Clock = Clock.System,
): Module =
    module {
        includes(platformDataModule(environment.storageName))
        single<Clock> { clock }
        when (environment) {
            Environment.Fake -> {
                single { FakePursuitOfWindServer(get()) }
                single<HttpClientEngine> { get<FakePursuitOfWindServer>().engine }
            }

            is Environment.Remote -> {
                single<HttpClientEngine> { remoteHttpEngine() }
            }
        }
        single {
            HttpClients(
                engine = get(),
                baseUrl = environment.baseUrl(),
                retry = RetryPolicy(),
                session = { get() },
            )
        }
        single {
            val cache = get<UserCache>()
            AuthSession(client = { get<HttpClients>().auth }, store = get(), platform = currentPlatform, onSignedIn = cache::claimFor)
        }
        single { PursuitOfWindApi(get<HttpClients>().api) }
        singleOf(::UserCache)
        singleOf(::CrewStore)
        single { CrewFeedSync(get(), get(), get(), get()) }
        singleOf(::AccountRepository)
        singleOf(::ProfileRepository)
        singleOf(::PreferencesRepository)
        singleOf(::SpotRepository)
        singleOf(::ForecastRepository)
        singleOf(::CrewRepository)
        singleOf(::RideRepository)
    }

private fun Environment.baseUrl(): String =
    when (this) {
        Environment.Fake -> FakePursuitOfWindServer.BASE_URL
        is Environment.Remote -> baseUrl
    }
