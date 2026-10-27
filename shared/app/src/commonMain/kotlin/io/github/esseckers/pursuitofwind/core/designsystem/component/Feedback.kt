package io.github.esseckers.pursuitofwind.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.refresh.RefreshNotice
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_refresh
import io.github.esseckers.pursuitofwind.core.resources.action_retry
import io.github.esseckers.pursuitofwind.core.resources.banner_stale
import io.github.esseckers.pursuitofwind.core.resources.disclaimer
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

/** A small icon followed by text, for facts such as the wind range or the departure time. Decorative icon. */
@Composable
internal fun IconLine(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    iconTint: Color = LocalContentColor.current,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(Sizes.icon))
        Text(text = text, style = style)
    }
}

/**
 * A full-width notice above the content: data is old, the device is offline, a refresh failed. Announced politely to
 * screen readers when it appears. [action] is typically a retry button.
 */
@Composable
internal fun StatusBanner(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        // One announcement for icon and text; an action button stays focusable on its own.
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.m, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Sizes.icon))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}

/**
 * Why what a screen shows may not be current, with a button that refreshes: [offline] or [failed] for a refresh that did
 * not work, and "getting old" with a refresh button for [RefreshNotice.STALE]. Tagged `<tagPrefix>.notice`, its button
 * `<tagPrefix>.retry`.
 */
@Composable
internal fun RefreshNoticeBanner(
    notice: RefreshNotice,
    offline: StringResource,
    failed: StringResource,
    tagPrefix: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stale = notice == RefreshNotice.STALE
    val text =
        when (notice) {
            RefreshNotice.OFFLINE -> offline
            RefreshNotice.REFRESH_FAILED -> failed
            RefreshNotice.STALE -> Res.string.banner_stale
        }
    StatusBanner(
        icon = if (stale) AppIcons.Clock else AppIcons.CloudOff,
        text = stringResource(text),
        modifier = modifier.testTag("$tagPrefix.notice"),
        action = {
            ActionButton(
                text = stringResource(if (stale) Res.string.action_refresh else Res.string.action_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("$tagPrefix.retry"),
            )
        },
    )
}

/**
 * A centered message instead of content: nothing to show yet, or something went wrong. Announced to screen readers
 * when it replaces other content; a placeholder that is simply there ([announced] false) is not.
 */
@Composable
internal fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    announced: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.xl)
                .semantics { if (announced) liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/** A text button with the minimum touch target, for "Retry" and similar actions. */
@Composable
internal fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** The decision-support disclaimer. It stays visible on Today and in Settings. */
@Composable
internal fun Disclaimer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            imageVector = AppIcons.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.iconSmall),
        )
        Text(
            text = stringResource(Res.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The screen's snackbars, each shown for [SNACKBAR_TIME] at most, one with an action too, unless the rider's
 * accessibility settings ask for more time (Android's "Time to take action", or a screen reader with an action to reach).
 */
@Composable
internal fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val accessibility = LocalAccessibilityManager.current
    SnackbarHost(hostState, modifier) { data ->
        LaunchedEffect(data) {
            val time = SNACKBAR_TIME.inWholeMilliseconds
            val visuals = data.visuals
            delay(
                accessibility?.calculateRecommendedTimeoutMillis(
                    time,
                    containsIcons = visuals.withDismissAction,
                    containsText = true,
                    containsControls = visuals.actionLabel != null,
                ) ?: time,
            )
            data.dismiss()
        }
        Snackbar(data)
    }
}

/** How long a snackbar stays. */
internal val SNACKBAR_TIME = 3.seconds

/** A gently pulsing placeholder block while content loads. Size it with [modifier]; screen readers skip it. */
@Composable
internal fun Skeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = SKELETON_PULSE_MILLIS), RepeatMode.Reverse),
        label = "skeleton alpha",
    )
    Box(
        modifier =
            modifier
                // Read in the draw phase, so the pulse does not recompose every frame.
                .graphicsLayer { this.alpha = alpha }
                .background(PursuitOfWindTheme.colors.skeleton, MaterialTheme.shapes.medium),
    )
}

private const val SKELETON_MIN_ALPHA = 0.4f
private const val SKELETON_PULSE_MILLIS = 900
