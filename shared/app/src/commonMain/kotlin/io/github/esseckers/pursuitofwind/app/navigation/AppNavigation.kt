package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.esseckers.pursuitofwind.app.language.AppLanguageScope
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.tab_crew
import io.github.esseckers.pursuitofwind.core.resources.tab_profile
import io.github.esseckers.pursuitofwind.core.resources.tab_spots
import io.github.esseckers.pursuitofwind.core.resources.tab_today
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.crew.presentation.navigation.CrewRoute
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileMode
import io.github.esseckers.pursuitofwind.feature.profile.presentation.navigation.ProfileRoute
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.entity.RideDraftEntity
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.navigation.RideOfferRoute
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.navigation.SpotDetailRoute
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.navigation.SpotPickerRoute
import io.github.esseckers.pursuitofwind.feature.spots.presentation.navigation.SpotsRoute
import io.github.esseckers.pursuitofwind.feature.today.presentation.navigation.TodayRoute
import io.github.esseckers.pursuitofwind.feature.welcome.presentation.view.WelcomeScreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * The app's screens and how they connect (Navigation 3). A rider without a profile starts at Welcome and goes through
 * onboarding (profile, then spots) to Today; everyone else starts at Today. Today, Spots, Crew and Profile are tabs
 * ([AppTab]): each tab's screen carries the tab bar, and moving between tabs fades. Everything shows in the rider's
 * language (`AppLanguage`).
 *
 * The back stack is a list of [Destination]s in saved state; every entry gets its own ViewModel store, so a screen's
 * ViewModel lives exactly as long as the screen is on the stack. Features do not know each other: this is the only
 * place where, say, Today's "open a spot" becomes the spot detail screen.
 */
@Composable
internal fun AppNavigation(modifier: Modifier = Modifier) {
    val start = rememberStartDestination()
    val language = rememberAppLanguage()
    // Reading the cached profile and language takes a moment; the theme's background shows meanwhile.
    if (start == null || language == null) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
    } else {
        AppNavigation(start = start, language = language, modifier = modifier)
    }
}

/** The navigation from [start] on a fresh launch, in [language]; after process death, the saved back stack wins. */
@Composable
internal fun AppNavigation(
    start: Destination,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(navigationConfiguration, start)
    val navigator = remember(backStack) { Navigator(backStack) }
    // Outside the language scope, so a new language keeps the back stack and every screen's ViewModel. Saved state of
    // the screens (scroll positions) starts over with the language: its holder cannot be shared across the switch.
    val viewModels = rememberViewModelStoreNavEntryDecorator<NavKey>()
    AppLanguageScope(language) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            // System back (gesture, button, iOS swipe) always means the screen on top.
            onBack = navigator::back,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), viewModels),
            entryProvider = entries(navigator),
        )
    }
}

/** Each destination's screen, with its navigation lambdas. */
private fun entries(navigator: Navigator): (NavKey) -> NavEntry<NavKey> =
    entryProvider {
        onboarding(navigator)
        tabs(navigator)
        screensOnTop(navigator)
    }

/** Welcome, the rider's profile and the spot picker, which Today and the Spots tab open too. */
private fun EntryProviderScope<NavKey>.onboarding(navigator: Navigator) {
    entry<Destination.Welcome> { WelcomeScreen(onGetStarted = { navigator.open(Destination.SetUpProfile) }) }
    entry<Destination.SetUpProfile> { key ->
        ProfileRoute(
            mode = ProfileMode.SET_UP,
            onContinue = { navigator.open(Destination.PickSpots) },
            onBack = { navigator.back(from = key) },
        )
    }
    entry<Destination.PickSpots> { key ->
        SpotPickerRoute(onFinished = navigator::finishPicking, onBack = { navigator.back(from = key) })
    }
}

/** The tabs, each with the tab bar under it. */
private fun EntryProviderScope<NavKey>.tabs(navigator: Navigator) {
    entry<Destination.Today>(metadata = tabTransitions) {
        TabScreen(AppTab.TODAY, navigator) {
            TodayRoute(
                onOpenSpot = { spotId, date -> navigator.open(Destination.SpotDetail(spotId.value, date)) },
                onOpenProfile = { navigator.selectTab(AppTab.PROFILE) },
                onPickSpots = { navigator.open(Destination.PickSpots) },
                onOfferRide = { offer ->
                    val key =
                        Destination.OfferRide(
                            spotId = offer.spotId.value,
                            windowStart = offer.windowStart.epochSeconds,
                            windowEnd = offer.windowEnd.epochSeconds,
                            departAt = offer.departAt.epochSeconds,
                        )
                    navigator.open(key)
                },
            )
        }
    }
    entry<Destination.Spots>(metadata = tabTransitions) {
        TabScreen(AppTab.SPOTS, navigator) {
            SpotsRoute(
                onOpenSpot = { spotId -> navigator.open(Destination.SpotDetail(spotId.value)) },
                onAddSpots = { navigator.open(Destination.PickSpots) },
            )
        }
    }
    entry<Destination.Crew>(metadata = tabTransitions) {
        TabScreen(AppTab.CREW, navigator) {
            CrewRoute(onOpenSpot = { spotId, date -> navigator.open(Destination.SpotDetail(spotId.value, date)) })
        }
    }
    entry<Destination.Profile>(metadata = tabTransitions) {
        TabScreen(AppTab.PROFILE, navigator) { ProfileRoute(mode = ProfileMode.TAB) }
    }
}

/** Screens opened on top of a tab. */
private fun EntryProviderScope<NavKey>.screensOnTop(navigator: Navigator) {
    entry<Destination.OfferRide> { key ->
        val draft =
            RideDraftEntity(
                spotId = SpotId(key.spotId),
                windowStart = Instant.fromEpochSeconds(key.windowStart),
                windowEnd = Instant.fromEpochSeconds(key.windowEnd),
                departAt = Instant.fromEpochSeconds(key.departAt),
            )
        RideOfferRoute(draft = draft, onDone = { navigator.back(from = key) }, onBack = { navigator.back(from = key) })
    }
    entry<Destination.SpotDetail> { key ->
        SpotDetailRoute(spotId = SpotId(key.spotId), date = key.date, onBack = { navigator.back(from = key) })
    }
}

/**
 * Moving between tabs fades, and the background of the tab bar each tab carries stays solid: a tab that opens fades in
 * over the one before (a forward move takes the entering scene's transition), and on back the tab on top fades out over
 * the one below (a pop takes the leaving one's). Today fades in at the end of onboarding too, which replaces the stack.
 * Screens opened from a tab keep the platform's transitions.
 */
private val tabTransitions: Map<String, Any> =
    metadata {
        put(NavDisplay.TransitionKey) { fadeIn(tween(TAB_FADE_MILLIS)) togetherWith ExitTransition.KeepUntilTransitionsFinished }
        put(NavDisplay.PopTransitionKey) { EnterTransition.None togetherWith fadeOut(tween(TAB_FADE_MILLIS)) }
        put(NavDisplay.PredictivePopTransitionKey) { _ -> EnterTransition.None togetherWith fadeOut(tween(TAB_FADE_MILLIS)) }
    }

/**
 * [tab]'s screen with the tab bar under it. The bar is part of the tab, so it comes and goes with it: a screen opened
 * from the tab covers it, and back (also a predictive back) shows it again with the tab. The bar's height is taken here,
 * so the tab lays out above it and does not add the navigation bar again; it keeps clear of the other bars itself.
 */
@Composable
private fun TabScreen(
    tab: AppTab,
    navigator: Navigator,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = { TabBar(selected = tab, onSelect = navigator::selectTab) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

@Composable
private fun TabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    NavigationBar(modifier = Modifier.testTag("tabs")) {
        AppTab.entries.forEach { tab ->
            val (icon, label) = tabLook(tab)
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
                label = { Text(text = stringResource(label)) },
                modifier = Modifier.testTag("tab.${tab.name.lowercase()}"),
            )
        }
    }
}

private fun tabLook(tab: AppTab): Pair<ImageVector, StringResource> =
    when (tab) {
        AppTab.TODAY -> AppIcons.Wind to Res.string.tab_today
        AppTab.SPOTS -> AppIcons.Pin to Res.string.tab_spots
        AppTab.CREW -> AppIcons.Group to Res.string.tab_crew
        AppTab.PROFILE -> AppIcons.Person to Res.string.tab_profile
    }

/** As long as Material's medium fade. */
private const val TAB_FADE_MILLIS = 250
