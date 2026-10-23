package io.github.esseckers.pursuitofwind.feature.profile.presentation.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileIntent
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileMode
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileState
import io.github.esseckers.pursuitofwind.testing.PHONE_DENSITY
import io.github.esseckers.pursuitofwind.testing.PHONE_SIZE
import io.github.esseckers.pursuitofwind.testing.checkTexts
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The profile form on the desktop runtime: what each state shows, what its fields and buttons send, and how it reads. */
@OptIn(ExperimentalTestApi::class)
class ProfileScreenTest {
    private val intents = mutableListOf<ProfileIntent>()
    private var backs = 0
    private val systemLocale = Locale.getDefault()

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    private fun ComposeUiTest.show(
        state: ProfileState,
        mode: ProfileMode = ProfileMode.TAB,
        fontScale: Float = 1f,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PursuitOfWindTheme(darkTheme = false) {
                    ProfileScreen(state = state, mode = mode, onIntent = { intents += it }, onBack = { backs++ })
                }
            }
        }
    }

    private fun edits(): List<ProfileEdit> = intents.filterIsInstance<ProfileIntent.Edit>().map { it.edit }

    @Test
    fun `onboarding asks about the rider and every choice and continue reach the view model`() =
        runComposeUiTest {
            show(ProfilePreviewData.empty, mode = ProfileMode.SET_UP)

            onNode(isHeading() and hasText("About you")).assertIsDisplayed()
            onNodeWithTag("profile.name").performTextInput("Mira")
            onNodeWithTag("profile.level.ADVANCED").performScrollTo().performClick()
            onNodeWithTag("profile.kite.9").performScrollTo().performClick()
            onNodeWithTag("profile.unit.METERS_PER_SECOND").performScrollTo().performClick()
            onNodeWithTag("profile.save").performScrollTo().assert(hasText("Continue")).performClick()
            onNodeWithContentDescription("Back").performClick()

            assertEquals(
                listOf(
                    ProfileEdit.Name("Mira"),
                    ProfileEdit.Level(SkillLevel.ADVANCED),
                    ProfileEdit.Kite(9.0, owned = true),
                    ProfileEdit.Units(WindUnit.METERS_PER_SECOND),
                ),
                edits(),
            )
            assertEquals(ProfileIntent.Save, intents.last())
            assertEquals(1, backs)
        }

    @Test
    fun `the weight takes a number with a comma and nothing else`() =
        runComposeUiTest {
            show(ProfilePreviewData.empty, mode = ProfileMode.SET_UP)

            onNodeWithTag("profile.weight").performTextInput("8a")
            onNodeWithTag("profile.weight").performTextInput("62,5")

            assertEquals(listOf(ProfileEdit.Weight("62,5")), edits())
            onNodeWithTag("profile.weight").assert(hasText("62,5"))
        }

    @Test
    fun `errors show under their fields and hidden settings open to show theirs`() =
        runComposeUiTest {
            show(ProfilePreviewData.invalid, mode = ProfileMode.SET_UP)

            onNodeWithText("Enter your name").assertExists()
            onNodeWithText("Between 30 and 200 kg").assertExists()
            onNodeWithText("Must be above your lowest wind").performScrollTo().assertIsDisplayed()
            // Next to the button too, which may be far from the first field with an error.
            onNodeWithTag("profile.fixFields").performScrollTo().assert(hasText("Check the fields marked above."))
        }

    @Test
    fun `more settings stay open once their error is fixed`() =
        runComposeUiTest {
            var state by mutableStateOf(ProfilePreviewData.invalid)
            setContent { PursuitOfWindTheme(darkTheme = false) { ProfileScreen(state = state, mode = ProfileMode.SET_UP, onIntent = {}) } }

            onNodeWithTag("profile.maxWind").performScrollTo().assertIsDisplayed()
            state = state.copy(errors = emptyMap())
            onNodeWithTag("profile.maxWind").performScrollTo().assertIsDisplayed()
            onNodeWithTag("profile.fixFields").assertDoesNotExist()
        }

    @Test
    fun `level and unit are one choice each and kites are a set`() =
        runComposeUiTest {
            show(ProfilePreviewData.saved)

            onNodeWithTag("profile.level.INTERMEDIATE").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            onNodeWithTag(
                "profile.unit.KNOTS",
            ).performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            onNodeWithTag("profile.kite.9").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        }

    @Test
    fun `the tab saves only after a change and says why a save failed`() =
        runComposeUiTest {
            var state by mutableStateOf(ProfilePreviewData.saved)
            setContent { PursuitOfWindTheme(darkTheme = false) { ProfileScreen(state = state, mode = ProfileMode.TAB, onIntent = {}) } }

            onNode(isHeading() and hasText("Profile")).assertIsDisplayed()
            onNodeWithTag("profile.save").performScrollTo().assert(hasText("Save")).assertIsNotEnabled()
            state = ProfilePreviewData.edited
            onNodeWithTag("profile.save").assertIsEnabled()
            state = ProfilePreviewData.saveFailed
            onNodeWithTag("profile.saveError").performScrollTo().assert(hasText("No connection. Check your internet and try again."))
        }

    @Test
    fun `more settings open and their steppers send whole steps`() =
        runComposeUiTest {
            show(ProfilePreviewData.saved)

            onNodeWithTag("profile.more").performScrollTo().assert(hasText("More settings")).performClick()
            onNodeWithContentDescription("Longer session").performScrollTo().performClick()
            onNodeWithContentDescription("Shorter drive").performScrollTo().performClick()

            assertEquals(listOf(ProfileEdit.MinSession(150), ProfileEdit.MaxDrive(150)), edits())
            onNodeWithTag("profile.minSession").assert(hasAnyDescendant(hasText("2 h")))
        }

    @Test
    fun `text fields start over when the form is rewritten from outside`() =
        runComposeUiTest {
            var state by mutableStateOf(ProfilePreviewData.empty)
            setContent { PursuitOfWindTheme(darkTheme = false) { ProfileScreen(state = state, mode = ProfileMode.SET_UP, onIntent = {}) } }

            onNodeWithTag("profile.name").performTextInput("Al")
            onNodeWithTag("profile.name").assert(hasText("Al"))
            // Same version: typing is kept even though this state does not have it yet.
            state = state.copy(form = state.form.copy(name = ""))
            onNodeWithTag("profile.name").assert(hasText("Al"))
            state = ProfilePreviewData.saved.copy(formVersion = state.formVersion + 1)
            onNodeWithTag("profile.name").assert(hasText("Alex"))
        }

    @Test
    fun `every language is offered by its own name and a tap picks it`() =
        runComposeUiTest {
            show(ProfilePreviewData.saved)

            onNodeWithTag("profile.language.SYSTEM")
                .performScrollTo()
                .assert(hasText("As on the phone"))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertIsSelected()
            onNodeWithTag("profile.language.ENGLISH").assert(hasText("English"))
            onNodeWithTag("profile.language.RUSSIAN").assert(hasText("Русский")).performClick()

            assertEquals(listOf<ProfileIntent>(ProfileIntent.ChooseLanguage(AppLanguage.RUSSIAN)), intents)
        }

    @Test
    fun `russian speakers get the form in russian`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            show(ProfilePreviewData.empty, mode = ProfileMode.SET_UP)

            onNode(isHeading() and hasText("О тебе")).assertIsDisplayed()
            onNodeWithTag("profile.level.INTERMEDIATE").performScrollTo().assert(hasText("Средний"))
            onNodeWithTag("profile.kite.9").performScrollTo().assert(hasText("9 м²"))
            onNodeWithTag("profile.save").performScrollTo().assert(hasText("Продолжить"))
        }

    @Test
    fun `no text is cut off at twice the font size on a phone`() =
        runSkikoComposeUiTest(size = PHONE_SIZE, density = PHONE_DENSITY) {
            show(ProfilePreviewData.invalid, mode = ProfileMode.SET_UP, fontScale = 2f)

            val fit = checkTexts()

            assertTrue("Must be above your lowest wind" in fit.checked, "The bottom of the form was never measured: ${fit.checked}")
            assertEquals(emptySet(), fit.cutOff)
        }
}
