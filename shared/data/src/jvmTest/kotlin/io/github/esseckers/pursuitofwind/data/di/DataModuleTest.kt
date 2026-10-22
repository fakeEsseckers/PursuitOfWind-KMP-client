package io.github.esseckers.pursuitofwind.data.di

import androidx.datastore.core.DataStore
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.data.Environment
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.THURSDAY_MORNING
import io.github.esseckers.pursuitofwind.data.internal.network.RetryPolicy
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.data.repository.AccountRepository
import io.github.esseckers.pursuitofwind.data.repository.CrewRepository
import io.github.esseckers.pursuitofwind.data.repository.ForecastRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.data.repository.RideRepository
import io.github.esseckers.pursuitofwind.data.repository.SpotRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DataModuleTest {
    /**
     * Koin checks the constructor parameters of every definition. The ones listed here are passed by hand in
     * `dataModule` (values, lambdas, the DataStore built by the platform module), not resolved from the graph.
     */
    private val builtByHand =
        listOf(
            DataStore::class,
            HttpClient::class,
            RetryPolicy::class,
            PlatformDto::class,
            Function0::class,
        )

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `every definition of the data module can be created`() {
        dataModule(Environment.Fake).verify(extraTypes = builtByHand)
        dataModule(Environment.Remote("https://api.example.invalid")).verify(extraTypes = builtByHand)
    }

    @Test
    fun `the fake environment works end to end through Koin`() =
        runTest {
            val koin = koinApplication { modules(dataModule(Environment.Fake, ControllableClock(THURSDAY_MORNING))) }.koin
            try {
                listOf(
                    ProfileRepository::class,
                    SpotRepository::class,
                    ForecastRepository::class,
                    RideRepository::class,
                ).forEach { koin.get<Any>(it) }
                val crews = koin.get<CrewRepository>()

                val user = koin.get<AccountRepository>().signIn()
                val joined = crews.join("WIND-4KQ7")

                assertEquals(Outcome.Success("usr_0001"), user)
                assertIs<Outcome.Success<*>>(joined)
                assertEquals(
                    listOf("Weekend Crew"),
                    crews.crews
                        .first()
                        .value
                        .map { it.name },
                )
            } finally {
                koin.close()
            }
        }
}
