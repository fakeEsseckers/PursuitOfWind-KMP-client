package io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.core.ui.StepperUi
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.AlertsUi
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.AlertsWish
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileError
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileField
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileForm
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileLimits
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileState
import io.github.esseckers.pursuitofwind.feature.profile.savedAlex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileReducerTest {
    private fun reduce(
        vararg results: ProfileResult,
        from: ProfileState = ProfileState(),
    ): ProfileState = results.fold(from, ProfileReducer::reduce)

    @Test
    fun `the first saved profile fills the form and tells the text fields`() {
        val state = reduce(ProfileResult.Loaded(savedAlex()))

        assertFalse(state.loading)
        assertEquals("Alex", state.form.name)
        assertEquals(1, state.formVersion)
        assertFalse(state.hasChanges)
    }

    @Test
    fun `the form is a profile to save only while every field is right`() {
        val loaded = reduce(ProfileResult.Loaded(savedAlex()))
        val noWeight = reduce(ProfileResult.Edited(ProfileEdit.Weight("")), from = loaded)

        assertEquals(savedAlex(), loaded.validProfile)
        assertNull(noWeight.validProfile)
        // Errors wait for the first try to save.
        assertTrue(noWeight.errors.isEmpty())
    }

    @Test
    fun `a rider without a profile gets an empty form with something to save`() {
        val state = reduce(ProfileResult.Loaded(null))

        assertEquals(ProfileForm(), state.form)
        assertTrue(state.hasChanges)
        assertEquals(ProfileLimits.KITE_SIZES_M2, state.kites.map { it.sizeM2 })
    }

    @Test
    fun `a later profile never overwrites the draft but is what the draft is compared with`() {
        val edited = reduce(ProfileResult.Loaded(savedAlex()), ProfileResult.Edited(ProfileEdit.Weight("78")))
        val savedElsewhere = reduce(ProfileResult.Loaded(savedAlex().copy(displayName = "Alexandra")), from = edited)

        assertEquals("Alex", savedElsewhere.form.name)
        assertEquals("78", savedElsewhere.form.weightKg)
        assertEquals(edited.formVersion, savedElsewhere.formVersion)
        assertTrue(savedElsewhere.hasChanges)
    }

    @Test
    fun `the saved profile typed differently is no change`() {
        val comma = reduce(ProfileResult.Loaded(savedAlex()), ProfileResult.Edited(ProfileEdit.Weight("80,0")))
        val heavier = reduce(ProfileResult.Edited(ProfileEdit.Weight("80,5")), from = comma)

        assertFalse(comma.hasChanges)
        assertTrue(heavier.hasChanges)
    }

    @Test
    fun `typing changes the form but not its version so the cursor stays`() {
        val state = reduce(ProfileResult.Loaded(null), ProfileResult.Edited(ProfileEdit.Name("Al")))

        assertEquals("Al", state.form.name)
        assertEquals(1, state.formVersion)
    }

    @Test
    fun `a new unit that converts the wind limits tells the text fields`() {
        val typed = reduce(ProfileResult.Loaded(null), ProfileResult.Edited(ProfileEdit.MinWind("14")))
        val converted = reduce(ProfileResult.Edited(ProfileEdit.Units(WindUnit.METERS_PER_SECOND)), from = typed)
        val nothingToConvert = reduce(ProfileResult.Loaded(null), ProfileResult.Edited(ProfileEdit.Units(WindUnit.METERS_PER_SECOND)))

        assertEquals(typed.formVersion + 1, converted.formVersion)
        assertEquals("7.2", converted.form.minWind)
        assertEquals(1, nothingToConvert.formVersion)
    }

    @Test
    fun `errors show only once the rider has tried to save and go as they are fixed`() {
        val empty = reduce(ProfileResult.Loaded(null))
        val submitted = reduce(ProfileResult.Submitted, from = empty)
        val named = reduce(ProfileResult.Edited(ProfileEdit.Name("Alex")), from = submitted)

        assertTrue(empty.errors.isEmpty())
        assertEquals(ProfileError.NAME_MISSING, submitted.errors[ProfileField.NAME])
        assertEquals(setOf(ProfileField.WEIGHT), named.errors.keys)
    }

    @Test
    fun `a failed save says why and the next one clears it`() {
        val failed =
            reduce(
                ProfileResult.Loaded(savedAlex()),
                ProfileResult.SaveStarted,
                ProfileResult.SaveFinished(AppError.Network),
            )
        val again = reduce(ProfileResult.SaveStarted, from = failed)

        assertFalse(failed.saving)
        assertEquals(Res.string.error_network, failed.saveError)
        assertTrue(again.saving)
        assertNull(again.saveError)
    }

    @Test
    fun `a saved profile counts at once so save is not offered again before the cache catches up`() {
        val edited = reduce(ProfileResult.Loaded(savedAlex()), ProfileResult.Edited(ProfileEdit.Weight("78")), ProfileResult.SaveStarted)
        val stored = savedAlex().copy(rider = savedAlex().rider.copy(weightKg = 78.0))
        val saved = reduce(ProfileResult.SaveFinished(error = null, saved = stored), from = edited)

        assertTrue(edited.hasChanges)
        assertFalse(saved.hasChanges)
        assertEquals(stored, saved.inputs.saved)
    }

    @Test
    fun `the language is shown as stored and is not part of the draft`() {
        val loaded = reduce(ProfileResult.Loaded(savedAlex()))
        val russian = reduce(ProfileResult.LanguageChanged(AppLanguage.RUSSIAN), from = loaded)

        assertEquals(AppLanguage.SYSTEM, loaded.language)
        assertEquals(AppLanguage.RUSSIAN, russian.language)
        assertFalse(russian.hasChanges)
    }

    @Test
    fun `the steppers show the form and offer only the ways it can still move`() {
        val loaded = reduce(ProfileResult.Loaded(savedAlex()))
        val shortest = reduce(*Array(4) { ProfileResult.Edited(ProfileEdit.MinSession(Step.DOWN)) }, from = loaded)

        assertEquals(StepperUi(120, canDecrease = true, canIncrease = true), loaded.minSession)
        assertEquals(StepperUi(180, canDecrease = true, canIncrease = true), loaded.maxDrive)
        assertEquals(StepperUi(30, canDecrease = false, canIncrease = true), shortest.minSession)
        assertEquals(30, shortest.form.minSessionMinutes)
    }

    @Test
    fun `the riding hour steppers keep an hour between the start and the end`() {
        val loaded = reduce(ProfileResult.Loaded(savedAlex()))
        val lateStart = reduce(*Array(11) { ProfileResult.Edited(ProfileEdit.RidingFrom(Step.UP)) }, from = loaded)

        assertEquals(StepperUi(9, canDecrease = true, canIncrease = true), loaded.ridingFrom)
        assertEquals(StepperUi(21, canDecrease = true, canIncrease = true), loaded.ridingTo)
        assertEquals(StepperUi(20, canDecrease = true, canIncrease = false), lateStart.ridingFrom)
        assertEquals(StepperUi(21, canDecrease = false, canIncrease = true), lateStart.ridingTo)
        assertTrue(lateStart.hasChanges)
    }

    @Test
    fun `the kite chips keep their instance while the quiver is the same`() {
        val loaded = reduce(ProfileResult.Loaded(savedAlex()))
        val renamed = reduce(ProfileResult.Edited(ProfileEdit.Name("Alexandra")), from = loaded)

        assertSame(loaded.kites, renamed.kites)
    }

    @Test
    fun `the switch shows the stored choice before the system has been asked about notifications`() {
        val chosen = reduce(ProfileResult.AlertsChosen(true))

        // Nothing is known about the system yet, so nothing is called blocked.
        assertEquals(AlertsUi(on = true), chosen.alerts)
        assertEquals(AlertsUi(on = true), reduce(ProfileResult.NotificationsChecked(allowed = true), from = chosen).alerts)
        assertEquals(AlertsUi(), reduce(ProfileResult.NotificationsChecked(allowed = true)).alerts)
    }

    @Test
    fun `chosen alerts the system does not show stay on and say they are blocked`() {
        val blocked = reduce(ProfileResult.AlertsChosen(true), ProfileResult.NotificationsChecked(allowed = false))

        assertEquals(AlertsUi(on = true, blocked = true), blocked.alerts)
        assertEquals(AlertsUi(), reduce(ProfileResult.AlertsChosen(false), from = blocked).alerts)
    }

    @Test
    fun `a wish shows as on while the system is asked and as blocked once it says no`() {
        val denied = reduce(ProfileResult.NotificationsChecked(allowed = false))
        val asking = reduce(ProfileResult.AlertsWished(AlertsWish.ASKING), from = denied)
        val refused = reduce(ProfileResult.AlertsWished(AlertsWish.REFUSED), from = asking)

        // While its dialog is up the system has not answered: the earlier no is not held against the wish.
        assertEquals(AlertsUi(on = true), asking.alerts)
        assertEquals(AlertsUi(on = true, blocked = true), refused.alerts)
        assertEquals(AlertsUi(), reduce(ProfileResult.AlertsWished(AlertsWish.NONE), from = refused).alerts)
    }

    @Test
    fun `a wish ends when it becomes the stored choice and not when the settings file emits again`() {
        val refused = reduce(ProfileResult.AlertsWished(AlertsWish.REFUSED), ProfileResult.NotificationsChecked(allowed = false))
        // Saving the profile or the language makes the store emit the same choice again.
        val emittedAgain = reduce(ProfileResult.AlertsChosen(false), from = refused)
        val stored = reduce(ProfileResult.NotificationsChecked(allowed = true), ProfileResult.AlertsChosen(true), from = refused)

        assertEquals(AlertsWish.REFUSED, emittedAgain.inputs.alertsWish)
        assertEquals(AlertsUi(on = true, blocked = true), emittedAgain.alerts)
        assertEquals(AlertsWish.NONE, stored.inputs.alertsWish)
        assertEquals(AlertsUi(on = true), stored.alerts)
    }

    @Test
    fun `notifications the system does not show are no problem while the rider never asked for alerts`() {
        val state = reduce(ProfileResult.AlertsChosen(false), ProfileResult.NotificationsChecked(allowed = false))

        assertEquals(AlertsUi(), state.alerts)
    }
}
