package io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.data.fake.ControllableClock
import io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer
import io.github.esseckers.pursuitofwind.data.fake.NetworkMode
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.data.repository.PreferencesRepository
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.engine.model.RidingHours
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEffect
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileError
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileField
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileForm
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileIntent
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileState
import io.github.esseckers.pursuitofwind.feature.spotdetail.SATURDAY
import io.github.esseckers.pursuitofwind.feature.spotdetail.alex
import io.github.esseckers.pursuitofwind.feature.spotdetail.at
import io.github.esseckers.pursuitofwind.testing.releaseMainDispatcher
import io.github.esseckers.pursuitofwind.testing.testGraph
import io.github.esseckers.pursuitofwind.testing.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** The profile form's view model on the real data layer: the in-app fake backend over HTTP and DataStore. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = ControllableClock(at(SATURDAY, 8))
    private val koin: Koin = testGraph(clock).koin
    private val server get() = koin.get<FakePursuitOfWindServer>()
    private val profiles get() = koin.get<ProfileRepository>()
    private val viewModels = ViewModelStore()

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModels.clear()
        koin.close()
        releaseMainDispatcher()
    }

    private fun viewModel(): ProfileViewModel =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { ProfileViewModel(repository = koin.get()) } })[
            ProfileViewModel::class,
        ]

    private fun ProfileViewModel.edit(vararg edits: ProfileEdit) = edits.forEach { onIntent(ProfileIntent.Edit(it)) }

    @Test
    fun `a new rider fills in the form and saving stores the profile`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(ProfileForm(), awaitState { !it.loading }.form)
                viewModel.edit(
                    ProfileEdit.Name("Mira"),
                    ProfileEdit.Weight("62,5"),
                    ProfileEdit.Level(SkillLevel.ADVANCED),
                    ProfileEdit.Kite(8.0, owned = true),
                    ProfileEdit.Units(WindUnit.METERS_PER_SECOND),
                    ProfileEdit.RidingFrom(Step.DOWN),
                    ProfileEdit.RidingTo(Step.UP),
                )
                viewModel.onIntent(ProfileIntent.Save)
                awaitState { !it.saving && !it.hasChanges }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(ProfileEffect.Saved, awaitItem()) }

            val stored = profiles.profile.first().value
            assertEquals("Mira", stored?.displayName)
            assertEquals(62.5, stored?.rider?.weightKg)
            assertEquals(SkillLevel.ADVANCED, stored?.rider?.level)
            assertEquals(listOf(8.0), stored?.rider?.quiverM2)
            assertEquals(WindUnit.METERS_PER_SECOND, stored?.windUnit)
            assertEquals(RidingHours(8, 22), stored?.rider?.ridingHours)
        }

    @Test
    fun `an incomplete form shows what to fix and saves nothing`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { !it.loading }
                viewModel.edit(ProfileEdit.Weight("250"))
                viewModel.onIntent(ProfileIntent.Save)
                val invalid = awaitState { it.errors.isNotEmpty() }

                assertEquals(
                    mapOf(ProfileField.NAME to ProfileError.NAME_MISSING, ProfileField.WEIGHT to ProfileError.WEIGHT_OUT_OF_RANGE),
                    invalid.errors,
                )
                assertFalse(invalid.saving)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { expectNoEvents() }
            assertNull(profiles.profile.first().value)
        }

    @Test
    fun `a saved profile fills the form and saving it unchanged only confirms`() =
        runTest(dispatcher) {
            profiles.save(alex()).value()
            // Any request would fail now, so the confirmation proves nothing was sent.
            server.networkMode = NetworkMode.OFFLINE
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                val loaded = awaitState { !it.loading }
                assertEquals("Alex", loaded.form.name)
                assertEquals(setOf(7.0, 9.0, 12.0), loaded.form.kitesM2)
                assertFalse(loaded.hasChanges)
                viewModel.onIntent(ProfileIntent.Save)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) { assertEquals(ProfileEffect.Saved, awaitItem()) }
        }

    @Test
    fun `saving offline says so and keeps the draft and a retry saves it`() =
        runTest(dispatcher) {
            profiles.save(alex()).value()
            server.networkMode = NetworkMode.OFFLINE
            val viewModel = viewModel()

            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { !it.loading }
                viewModel.edit(ProfileEdit.Weight("78"))
                viewModel.onIntent(ProfileIntent.Save)
                val failed = awaitState { it.saveError != null }
                assertEquals(Res.string.error_network, failed.saveError)
                assertEquals("78", failed.form.weightKg)

                server.networkMode = NetworkMode.NORMAL
                viewModel.onIntent(ProfileIntent.Save)
                awaitState { !it.saving && !it.hasChanges }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test(timeout = TURBINE_TIMEOUT) {
                assertEquals(ProfileEffect.Saved, awaitItem())
                expectNoEvents()
            }
            assertEquals(
                78.0,
                profiles.profile
                    .first()
                    .value
                    ?.rider
                    ?.weightKg,
            )
        }

    @Test
    fun `a language picked is stored at once and shown without saving the form`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.state.test(timeout = TURBINE_TIMEOUT) {
                awaitState { !it.loading }
                viewModel.onIntent(ProfileIntent.ChooseLanguage(AppLanguage.RUSSIAN))
                val russian = awaitState { it.language == AppLanguage.RUSSIAN }

                assertFalse(russian.saving)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(AppLanguage.RUSSIAN, koin.get<PreferencesRepository>().language.first())
            assertNull(profiles.profile.first().value)
        }

    @Test
    fun `the app graph creates the view model of the profile form`() =
        runTest(dispatcher) {
            val fromGraph = ViewModelProvider.create(viewModels, viewModelFactory { initializer { koin.get<ProfileViewModel>() } })

            assertIs<ProfileViewModel>(fromGraph[ProfileViewModel::class])
        }

    private suspend fun ReceiveTurbine<ProfileState>.awaitState(predicate: (ProfileState) -> Boolean): ProfileState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private companion object {
        /** Room, DataStore and the HTTP engine run on real threads here; leave room for a cold JVM on CI. */
        val TURBINE_TIMEOUT = 10.seconds
    }
}
