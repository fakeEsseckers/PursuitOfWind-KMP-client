package io.github.esseckers.pursuitofwind.feature.welcome.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.esseckers.pursuitofwind.core.designsystem.component.Disclaimer
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.PrimaryButton
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_get_started
import io.github.esseckers.pursuitofwind.core.resources.app_name
import io.github.esseckers.pursuitofwind.core.resources.welcome_point_crew
import io.github.esseckers.pursuitofwind.core.resources.welcome_point_decision
import io.github.esseckers.pursuitofwind.core.resources.welcome_point_kite
import io.github.esseckers.pursuitofwind.core.resources.welcome_tagline
import org.jetbrains.compose.resources.stringResource

/**
 * The first screen of a new rider: what the app answers, and "Get started" into setting it up. It has no state to
 * keep, so it has no ViewModel; [onGetStarted] goes straight to the app's navigation.
 */
@Composable
internal fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = Sizes.contentMaxWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        // At least the screen's height, so the button can sit at the bottom; taller content scrolls.
                        .heightIn(min = maxHeight)
                        .padding(horizontal = Spacing.l, vertical = Spacing.xl)
                        .testTag("welcome"),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Icon(
                    imageVector = AppIcons.Kite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.iconLarge),
                )
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.welcome_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.s))
                IconLine(icon = AppIcons.Go, text = stringResource(Res.string.welcome_point_decision))
                IconLine(icon = AppIcons.Kite, text = stringResource(Res.string.welcome_point_kite))
                IconLine(icon = AppIcons.Car, text = stringResource(Res.string.welcome_point_crew))
                // Pushes the disclaimer and the button down on tall screens.
                Spacer(modifier = Modifier.weight(1f))
                Disclaimer()
                PrimaryButton(
                    text = stringResource(Res.string.action_get_started),
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth().testTag("welcome.start"),
                )
            }
        }
    }
}
