package io.github.esseckers.pursuitofwind.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.esseckers.pursuitofwind.engine.model.Verdict
import io.github.esseckers.pursuitofwind.ui.generated.resources.Res
import io.github.esseckers.pursuitofwind.ui.generated.resources.verdict_go
import io.github.esseckers.pursuitofwind.ui.generated.resources.verdict_marginal
import io.github.esseckers.pursuitofwind.ui.generated.resources.verdict_no_go
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The colors of [verdict] in the current theme. */
@Composable
@ReadOnlyComposable
internal fun verdictPalette(verdict: Verdict): VerdictPalette =
    when (verdict) {
        Verdict.GO -> PursuitOfWindTheme.colors.go
        Verdict.MARGINAL -> PursuitOfWindTheme.colors.marginal
        Verdict.NO_GO -> PursuitOfWindTheme.colors.noGo
    }

/** Icon of [verdict]: a tick, a warning triangle or a cross, so the verdict never depends on color alone. */
internal fun verdictIcon(verdict: Verdict): ImageVector =
    when (verdict) {
        Verdict.GO -> AppIcons.Go
        Verdict.MARGINAL -> AppIcons.Marginal
        Verdict.NO_GO -> AppIcons.NoGo
    }

/** Short label of [verdict]: "GO", "MARGINAL", "NO-GO". */
internal fun verdictLabel(verdict: Verdict): StringResource =
    when (verdict) {
        Verdict.GO -> Res.string.verdict_go
        Verdict.MARGINAL -> Res.string.verdict_marginal
        Verdict.NO_GO -> Res.string.verdict_no_go
    }

/** A pill with the icon, label and color of [verdict]. */
@Composable
internal fun VerdictBadge(
    verdict: Verdict,
    modifier: Modifier = Modifier,
) {
    val palette = verdictPalette(verdict)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = palette.accent,
        contentColor = palette.onAccent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m - Spacing.xs, vertical = Spacing.xs + Spacing.xs / 2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xs / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = verdictIcon(verdict), contentDescription = null, modifier = Modifier.size(Sizes.icon))
            Text(text = stringResource(verdictLabel(verdict)), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A card tinted with the colors of [verdict], for one decision. */
@Composable
internal fun VerdictCard(
    verdict: Verdict,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = verdictPalette(verdict)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = palette.container,
        contentColor = palette.onContainer,
        border = BorderStroke(Sizes.border, palette.accent.copy(alpha = BORDER_ALPHA)),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
            content = content,
        )
    }
}

private const val BORDER_ALPHA = 0.4f
