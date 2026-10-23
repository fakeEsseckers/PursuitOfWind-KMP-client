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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.tab_profile
import io.github.esseckers.pursuitofwind.core.resources.tab_today
import io.github.esseckers.pursuitofwind.data.repository.ProfileRepository
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileMode
import io.github.esseckers.pursuitofwind.feature.profile.presentation.view.ProfileRoute
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view.SpotDetailRoute
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.view.SpotPickerRoute
import io.github.esseckers.pursuitofwind.feature.today.presentation.view.TodayRoute
import io.github.esseckers.pursuitofwind.feature.welcome.presentation.view.WelcomeScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The app's screens and how they connect (Navigation 3). A rider without a profile starts at Welcome and goes through
 * onboarding (profile, then spots) to Today; everyone else starts at Today. Today and Profile are tabs in a bottom bar,
 * shown while one of them is on top.
 *
 * The back stack is a list of [Destination]s in saved state; every entry gets its own ViewModel store, so a screen's
 * ViewModel lives exactly as long as the screen is on the stack. Features do not know each other: this is the only
 * place where, say, Today's "open a spot" becomes the spot detail screen.
 */
@Composable
internal fun AppNavigation(modifier: Modifier = Modifier) {
    val start = rememberStartDestination()
    // Reading the cached profile takes a moment; the theme's background shows meanwhile.
    if (start == null) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
    } else {
        AppNavigation(start = start, modifier = modifier)
    }
}

/** The navigation from [start] on a fresh launch; after process death, the saved back stack wins. */
@Composable
internal fun AppNavigation(
    start: Destination,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(navigationConfiguration, start)
    val navigator = remember(backStack) { Navigator(backStack) }
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
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider =
                entryProvider {
                    entry<Destination.Welcome> { WelcomeScreen(onGetStarted = { navigator.open(Destination.SetUpProfile) }) }
                    entry<Destination.SetUpProfile> {
                        ProfileRoute(
                            mode = ProfileMode.SET_UP,
                            onContinue = { navigator.open(Destination.PickSpots) },
                            onBack = navigator::back,
                        )
                    }
                    entry<Destination.PickSpots> { SpotPickerRoute(onFinished = navigator::finishSetup, onBack = navigator::back) }
                    entry<Destination.Today> {
                        TodayRoute(
                            onOpenSpot = { spotId, date -> navigator.open(Destination.SpotDetail(spotId.value, date)) },
                            onOpenProfile = { navigator.selectTab(Destination.Profile) },
                            onPickSpots = { navigator.open(Destination.PickSpots) },
                        )
                    }
                    entry<Destination.Profile> { ProfileRoute(mode = ProfileMode.TAB) }
                    entry<Destination.SpotDetail> { key ->
                        SpotDetailRoute(spotId = SpotId(key.spotId), date = key.date, onBack = navigator::back)
                    }
                },
        )
    }
}

/**
 * Where a fresh launch starts, once the cached profile is read; null until then. The answer is saved with the screen,
 * so after a configuration change or process death the restored back stack shows at once, without reading again. A
 * profile that cannot be read starts at Welcome: setting it up again replaces it.
 */
@Composable
private fun rememberStartDestination(profiles: ProfileRepository = koinInject()): Destination? {
    var welcome by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(profiles) {
        if (welcome == null) welcome = readStart(profiles) == Destination.Welcome
    }
    return welcome?.let { if (it) Destination.Welcome else Destination.Today }
}

private suspend fun readStart(profiles: ProfileRepository): Destination =
    try {
        startDestination(profiles.profile.first().value)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (
        // Any storage failure: the rider sets the profile up again.
        @Suppress("TooGenericExceptionCaught")
        failure: Exception,
    ) {
        Logger.withTag("AppNavigation").e(failure) { "Reading the profile failed" }
        Destination.Welcome
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
        Destination.Profile -> AppIcons.Person to Res.string.tab_profile
        else -> AppIcons.Wind to Res.string.tab_today
    }

private fun tabName(tab: Destination): String = if (tab == Destination.Profile) "profile" else "today"
