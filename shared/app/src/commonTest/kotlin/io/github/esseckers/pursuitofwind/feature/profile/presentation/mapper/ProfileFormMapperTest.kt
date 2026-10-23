package io.github.esseckers.pursuitofwind.feature.profile.presentation.mapper

import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.engine.model.WindSpeed
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileCheck
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileError
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileField
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileForm
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileLimits
import io.github.esseckers.pursuitofwind.feature.profile.savedAlex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ProfileFormMapperTest {
    private val filled =
        ProfileForm(name = "Alex", weightKg = "80", level = SkillLevel.INTERMEDIATE, unit = WindUnit.KNOTS, kitesM2 = setOf(12.0, 7.0, 9.0))

    @Test
    fun `a saved profile fills the form and checks back to the same profile`() {
        val saved = savedAlex(minWind = WindSpeed.knots(14.0), maxWind = WindSpeed.knots(30.0))
        val form = ProfileFormMapper.formOf(saved)

        assertEquals("80", form.weightKg)
        assertEquals("14", form.minWind)
        assertEquals("30", form.maxWind)
        assertEquals(saved, (ProfileFormMapper.check(form) as ProfileCheck.Valid).profile)
    }

    @Test
    fun `a valid form becomes a profile with the name trimmed and the kites in order`() {
        val profile = (ProfileFormMapper.check(filled.copy(name = "  Alex ", weightKg = "62,5")) as ProfileCheck.Valid).profile

        assertEquals("Alex", profile.displayName)
        assertEquals(62.5, profile.rider.weightKg)
        assertEquals(listOf(7.0, 9.0, 12.0), profile.rider.quiverM2)
        assertEquals(120.minutes, profile.rider.minSession)
        assertEquals(180.minutes, profile.rider.maxDrive)
        assertEquals(null, profile.rider.minWind)
    }

    @Test
    fun `every field that needs fixing is reported at once`() {
        val check = ProfileFormMapper.check(ProfileForm(name = " ", weightKg = "", minWind = "25", maxWind = "15"))

        assertEquals(
            mapOf(
                ProfileField.NAME to ProfileError.NAME_MISSING,
                ProfileField.WEIGHT to ProfileError.WEIGHT_MISSING,
                ProfileField.MAX_WIND to ProfileError.WIND_NOT_ABOVE_MIN,
            ),
            (check as ProfileCheck.Invalid).errors,
        )
    }

    @Test
    fun `a weight outside 30 to 200 kg is out of range and the bounds are allowed`() {
        listOf("29.9" to false, "30" to true, "200" to true, "200.1" to false).forEach { (weight, valid) ->
            val check = ProfileFormMapper.check(filled.copy(weightKg = weight))
            assertEquals(valid, check is ProfileCheck.Valid, weight)
            if (!valid) assertEquals(ProfileError.WEIGHT_OUT_OF_RANGE, (check as ProfileCheck.Invalid).errors[ProfileField.WEIGHT])
        }
    }

    @Test
    fun `wind limits that are equal are upside down and a lone separator is not a number`() {
        val equal = ProfileFormMapper.check(filled.copy(minWind = "20", maxWind = "20"))
        val separator = ProfileFormMapper.check(filled.copy(minWind = "."))

        assertEquals(ProfileError.WIND_NOT_ABOVE_MIN, (equal as ProfileCheck.Invalid).errors[ProfileField.MAX_WIND])
        assertEquals(ProfileError.WIND_NOT_A_NUMBER, (separator as ProfileCheck.Invalid).errors[ProfileField.MIN_WIND])
    }

    @Test
    fun `numbers take a point or a comma and one decimal at most`() {
        listOf("", "8", "80", "62.5", "62,5", "62.", ".5").forEach { assertTrue(acceptsNumber(it), it) }
        listOf("1000", "62.55", "-5", "6e2", "62..5", "a").forEach { assertFalse(acceptsNumber(it), it) }
        assertEquals(62.0, parseNumber("62."))
        assertEquals(0.5, parseNumber(",5"))
        assertEquals(null, parseNumber("."))
    }

    @Test
    fun `names longer than the server takes are never typed`() {
        assertTrue(ProfileFormMapper.acceptsName("a".repeat(ProfileLimits.NAME_MAX_LENGTH)))
        assertFalse(ProfileFormMapper.acceptsName("a".repeat(ProfileLimits.NAME_MAX_LENGTH + 1)))
    }

    @Test
    fun `a new unit converts typed wind limits and leaves what is not a number`() {
        val knots = filled.copy(minWind = "14", maxWind = "3x")
        val metres = ProfileFormMapper.edit(knots, ProfileEdit.Units(WindUnit.METERS_PER_SECOND))

        assertEquals(WindUnit.METERS_PER_SECOND, metres.unit)
        assertEquals("7.2", metres.minWind)
        assertEquals("3x", metres.maxWind)
        assertEquals("14", ProfileFormMapper.edit(metres, ProfileEdit.Units(WindUnit.KNOTS)).minWind)
    }

    @Test
    fun `switching units back and forth never rounds a limit`() {
        val knots = filled.copy(minWind = "23", maxWind = "31")
        val back =
            ProfileFormMapper.edit(
                ProfileFormMapper.edit(knots, ProfileEdit.Units(WindUnit.METERS_PER_SECOND)),
                ProfileEdit.Units(WindUnit.KNOTS),
            )

        assertEquals("23" to "31", back.minWind to back.maxWind)
        assertEquals(WindSpeed.knots(23.0), (ProfileFormMapper.check(back) as ProfileCheck.Valid).profile.rider.minWind)
    }

    @Test
    fun `limits that round to the same number in another unit stay apart`() {
        val metres = ProfileFormMapper.edit(filled.copy(minWind = "20", maxWind = "20.1"), ProfileEdit.Units(WindUnit.METERS_PER_SECOND))

        assertEquals(metres.minWind, metres.maxWind)
        assertIs<ProfileCheck.Valid>(ProfileFormMapper.check(metres))
    }

    @Test
    fun `an untouched saved limit is saved exactly and typing replaces it`() {
        val saved = savedAlex(unit = WindUnit.KNOTS, maxWind = WindSpeed(17.5))
        val form = ProfileFormMapper.formOf(saved)
        val typed = ProfileFormMapper.edit(form, ProfileEdit.MaxWind("34"))

        assertEquals("34", form.maxWind)
        assertEquals(WindSpeed(17.5), (ProfileFormMapper.check(form) as ProfileCheck.Valid).profile.rider.maxWind)
        assertEquals(WindSpeed.knots(34.0), (ProfileFormMapper.check(typed) as ProfileCheck.Valid).profile.rider.maxWind)
    }

    @Test
    fun `wind limits stay in knots when wind shows in Beaufort`() {
        val beaufort = ProfileFormMapper.edit(filled.copy(minWind = "14"), ProfileEdit.Units(WindUnit.BEAUFORT))
        val profile = (ProfileFormMapper.check(beaufort) as ProfileCheck.Valid).profile

        assertEquals(WindUnit.KNOTS, limitUnit(WindUnit.BEAUFORT))
        assertEquals("14", beaufort.minWind)
        assertEquals(WindSpeed.knots(14.0), profile.rider.minWind)
        assertEquals(WindUnit.BEAUFORT, profile.windUnit)
    }

    @Test
    fun `the quiver holds ten kites and the others are off until one goes`() {
        val ten = ProfileLimits.KITE_SIZES_M2.take(ProfileLimits.MAX_KITES).toSet()
        val full = filled.copy(kitesM2 = ten)
        val unowned = ProfileLimits.KITE_SIZES_M2.last()

        assertEquals(full, ProfileFormMapper.edit(full, ProfileEdit.Kite(unowned, owned = true)))
        assertFalse(ProfileFormMapper.kiteChoices(full).single { it.sizeM2 == unowned }.enabled)
        assertTrue(ProfileFormMapper.kiteChoices(full).filter { it.owned }.all { it.enabled })

        val nine = ProfileFormMapper.edit(full, ProfileEdit.Kite(ten.first(), owned = false))
        assertTrue(ProfileFormMapper.kiteChoices(nine).all { it.enabled })
    }

    @Test
    fun `a kite the rider owns is offered even when it is not a common size`() {
        val choices = ProfileFormMapper.kiteChoices(filled.copy(kitesM2 = setOf(10.5)))

        assertTrue(choices.single { it.sizeM2 == 10.5 }.owned)
        assertEquals(choices.map { it.sizeM2 }.sorted(), choices.map { it.sizeM2 })
    }

    @Test
    fun `steppers stay within the limits of the server`() {
        val form = ProfileForm()

        assertEquals(30, ProfileFormMapper.edit(form, ProfileEdit.MinSession(0)).minSessionMinutes)
        assertEquals(720, ProfileFormMapper.edit(form, ProfileEdit.MinSession(750)).minSessionMinutes)
        assertEquals(0, ProfileFormMapper.edit(form, ProfileEdit.MaxDrive(-30)).maxDriveMinutes)
        assertEquals(720, ProfileFormMapper.edit(form, ProfileEdit.MaxDrive(750)).maxDriveMinutes)
        assertIs<ProfileCheck.Invalid>(ProfileFormMapper.check(form))
    }
}
