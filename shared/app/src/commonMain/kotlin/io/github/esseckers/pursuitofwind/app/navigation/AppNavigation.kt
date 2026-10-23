package io.github.esseckers.pursuitofwind.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.view.SpotDetailRoute
import io.github.esseckers.pursuitofwind.feature.today.presentation.view.TodayRoute

/**
 * The app's screens and how they connect (Navigation 3). The back stack is a list of [Destination]s in saved state;
 * every entry gets its own ViewModel store, so a screen's ViewModel lives exactly as long as the screen is on the stack.
 * Features do not know each other: this is the only place where Today's "open a spot" becomes the spot detail screen.
 */
@Composable
internal fun AppNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(navigationConfiguration, Destination.Today)
    val navigator = remember(backStack) { Navigator(backStack) }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = navigator::back,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider =
            entryProvider {
                entry<Destination.Today> {
                    TodayRoute(onOpenSpot = { spotId, date -> navigator.open(Destination.SpotDetail(spotId.value, date)) })
                }
                entry<Destination.SpotDetail> { key ->
                    SpotDetailRoute(spotId = SpotId(key.spotId), date = key.date, onBack = navigator::back)
                }
            },
    )
}
