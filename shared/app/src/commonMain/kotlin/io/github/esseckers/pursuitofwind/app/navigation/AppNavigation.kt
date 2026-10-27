package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
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
 * ([AppTab]): each tab's screen carries the tabs (a bar, or a rail on a wide window), and moving between tabs fades.
 * Everything shows in the rider's language (`AppLanguage`).
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

/**
 * The navigation from [start] on a fresh launch, in [language]; after process death, the saved back stack wins. With
 * room for two panes, a spot opened from the Spots list shows beside it (the list-detail scene); everything else shows
 * one screen at a time.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
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
    // Back pops one entry, as everywhere: from a forecast beside the list, back leaves the list with its placeholder.
    // The default pops until the panes change, and a placeholder in the detail pane counts as no change.
    val listDetail =
        rememberListDetailSceneStrategy<NavKey>(
            backNavigationBehavior = BackNavigationBehavior.PopLatest,
            directive = paneDirective(),
        )
    AppLanguageScope(language) {
        NavDisplay(
            backStack = backStack,
            // Behind the screens: the gap between two panes shows the theme's background, not the window's.
            modifier = modifier.background(MaterialTheme.colorScheme.background),
            // System back (gesture, button, iOS swipe) always means the screen on top.
            onBack = navigator::back,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), viewModels),
            sceneStrategies = listOf(listDetail),
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

/** The tabs, each with the tab bar or rail beside it. */
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
    entry<Destination.Spots>(metadata = tabTransitions + spotListPane) {
        PaneInsets(away = WindowInsetsSides.End) {
            TabScreen(AppTab.SPOTS, navigator) {
                SpotsRoute(
                    onOpenSpot = { spotId -> navigator.openBesideList(Destination.SpotDetail(spotId.value, besideList = true)) },
                    onAddSpots = { navigator.open(Destination.PickSpots) },
                )
            }
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
    entry<Destination.SpotDetail>(metadata = { key -> if (key.besideList) spotDetailPane else emptyMap() }) { key ->
        PaneInsets(away = WindowInsetsSides.Start) {
            SpotDetailRoute(spotId = SpotId(key.spotId), date = key.date, onBack = { navigator.back(from = key) })
        }
    }
}

/**
 * Moving between tabs fades, and the background of the tabs each tab carries stays solid: a tab that opens fades in
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
 * [tab]'s screen with the tabs: a bar under it on a phone, a rail at its start on a wider window (a tablet, a foldable
 * open, a desktop window), as [tabsLayout] picks for the window. The tabs are part of the tab's screen, so they come
 * and go with it: a screen opened from the tab covers them, and back (also a predictive back) shows them again with the
 * tab.
 *
 * Under the tab is the bar, or beside the rail the navigation bar. The tab consumes the bar's whole height: the
 * scaffold only consumes the navigation bar under it, and a form's keyboard padding would count the bar itself too.
 * Beside the rail nothing covers the navigation bar, so the tab ends above it, as it ends above the bar on a phone.
 * Both are measured sizes, not positions, so they hold while the tab slides in or out. Every other inset is the tab's
 * own.
 */
@Composable
private fun TabScreen(
    tab: AppTab,
    navigator: Navigator,
    content: @Composable () -> Unit,
) {
    val layout = tabsLayout()
    var scaffoldHeight by remember { mutableIntStateOf(0) }
    NavigationSuiteScaffold(
        navigationItems = {
            AppTab.entries.forEach { item ->
                val (icon, label) = tabLook(item)
                NavigationSuiteItem(
                    selected = item == tab,
                    onClick = { navigator.selectTab(item) },
                    icon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
                    label = { Text(text = stringResource(label)) },
                    navigationSuiteType = layout,
                    modifier = Modifier.testTag("tab.${item.name.lowercase()}"),
                )
            }
        },
        modifier = Modifier.onSizeChanged { scaffoldHeight = it.height },
        navigationSuiteType = layout,
    ) {
        var tabHeight by remember { mutableIntStateOf(0) }
        // The bar's height, from the navigation bar up: what lies under the tab. Zero beside the rail.
        val underTab = WindowInsets(bottom = (scaffoldHeight - tabHeight).coerceAtLeast(0))
        val aboveNavigationBar =
            if (layout.isRail) Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)) else Modifier
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { tabHeight = it.height }
                    .consumeWindowInsets(underTab)
                    .then(aboveNavigationBar),
        ) { content() }
    }
}

/** The tabs for this window: Material's choice, a bar at a compact width or a short window, a rail otherwise. */
@Composable
internal fun tabsLayout(): NavigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())

internal val NavigationSuiteType.isRail: Boolean
    get() =
        this == NavigationSuiteType.WideNavigationRailCollapsed ||
            this == NavigationSuiteType.WideNavigationRailExpanded ||
            this == NavigationSuiteType.NavigationRail

private fun tabLook(tab: AppTab): Pair<ImageVector, StringResource> =
    when (tab) {
        AppTab.TODAY -> AppIcons.Wind to Res.string.tab_today
        AppTab.SPOTS -> AppIcons.Pin to Res.string.tab_spots
        AppTab.CREW -> AppIcons.Group to Res.string.tab_crew
        AppTab.PROFILE -> AppIcons.Person to Res.string.tab_profile
    }

/** As long as Material's medium fade. */
private const val TAB_FADE_MILLIS = 250
