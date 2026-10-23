package io.github.esseckers.pursuitofwind.app.navigation

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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.esseckers.pursuitofwind.app.language.AppLanguageScope
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.tab_profile
import io.github.esseckers.pursuitofwind.core.resources.tab_spots
import io.github.esseckers.pursuitofwind.core.resources.tab_today
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileMode
import io.github.esseckers.pursuitofwind.feature.profile.presentation.view.ProfileRoute
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view.SpotDetailRoute
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerRoute
import io.github.esseckers.pursuitofwind.feature.spots.presentation.view.SpotsRoute
import io.github.esseckers.pursuitofwind.feature.today.presentation.view.TodayRoute
import io.github.esseckers.pursuitofwind.feature.welcome.presentation.view.WelcomeScreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The app's screens and how they connect (Navigation 3). A rider without a profile starts at Welcome and goes through
 * onboarding (profile, then spots) to Today; everyone else starts at Today. Today, Spots and Profile are tabs in a
 * bottom bar, shown while one of them is on top. Everything shows in the rider's language (`AppLanguage`).
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
        val top = backStack.lastOrNull()
        Scaffold(
            modifier = modifier,
            bottomBar = { if (top in tabDestinations) TabBar(selected = top, onSelect = navigator::selectTab) },
        ) { padding ->
            NavDisplay(
                backStack = backStack,
                // The bar's height is taken here, so screens lay out above it and do not add the system bars twice.
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                onBack = navigator::back,
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), viewModels),
                entryProvider = entries(navigator),
            )
        }
    }
}

/** Each destination's screen, with its navigation lambdas. */
private fun entries(navigator: Navigator): (NavKey) -> NavEntry<NavKey> =
    entryProvider {
        entry<Destination.Welcome> { WelcomeScreen(onGetStarted = { navigator.open(Destination.SetUpProfile) }) }
        entry<Destination.SetUpProfile> {
            ProfileRoute(
                mode = ProfileMode.SET_UP,
                onContinue = { navigator.open(Destination.PickSpots) },
                onBack = navigator::back,
            )
        }
        entry<Destination.PickSpots> { SpotPickerRoute(onFinished = navigator::finishPicking, onBack = navigator::back) }
        entry<Destination.Today> {
            TodayRoute(
                onOpenSpot = { spotId, date -> navigator.open(Destination.SpotDetail(spotId.value, date)) },
                onOpenProfile = { navigator.selectTab(Destination.Profile) },
                onPickSpots = { navigator.open(Destination.PickSpots) },
            )
        }
        entry<Destination.Spots> {
            SpotsRoute(
                onOpenSpot = { spotId -> navigator.open(Destination.SpotDetail(spotId.value)) },
                onAddSpots = { navigator.open(Destination.PickSpots) },
            )
        }
        entry<Destination.Profile> { ProfileRoute(mode = ProfileMode.TAB) }
        entry<Destination.SpotDetail> { key ->
            SpotDetailRoute(spotId = SpotId(key.spotId), date = key.date, onBack = navigator::back)
        }
    }

@Composable
private fun TabBar(
    selected: NavKey?,
    onSelect: (Destination) -> Unit,
) {
    NavigationBar(modifier = Modifier.testTag("tabs")) {
        tabDestinations.forEach { tab ->
            val (icon, label) = tabLook(tab)
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
                label = { Text(text = stringResource(label)) },
                modifier = Modifier.testTag("tab.${tabName(tab)}"),
            )
        }
    }
}

private fun tabLook(tab: Destination): Pair<ImageVector, StringResource> =
    when (tab) {
        Destination.Spots -> AppIcons.Pin to Res.string.tab_spots
        Destination.Profile -> AppIcons.Person to Res.string.tab_profile
        else -> AppIcons.Wind to Res.string.tab_today
    }

private fun tabName(tab: Destination): String =
    when (tab) {
        Destination.Spots -> "spots"
        Destination.Profile -> "profile"
        else -> "today"
    }
