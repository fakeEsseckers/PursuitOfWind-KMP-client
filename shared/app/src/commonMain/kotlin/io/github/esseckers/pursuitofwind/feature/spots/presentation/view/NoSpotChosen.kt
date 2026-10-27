package io.github.esseckers.pursuitofwind.feature.spots.presentation.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.esseckers.pursuitofwind.core.designsystem.component.MessageState
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.my_spots_pick_body
import io.github.esseckers.pursuitofwind.core.resources.my_spots_pick_title
import org.jetbrains.compose.resources.stringResource

/**
 * What shows beside the Spots list on a wide window until a spot is opened: where its forecast will be. It is a pane of
 * its own, outside any screen's scaffold, so it brings its background and content colors. Screen readers find it, but
 * it is not announced each time the tab opens.
 */
@Composable
internal fun NoSpotChosen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize().testTag("myspots.noSpot"), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            MessageState(
                icon = AppIcons.Pin,
                title = stringResource(Res.string.my_spots_pick_title),
                body = stringResource(Res.string.my_spots_pick_body),
                announced = false,
            )
        }
    }
}
