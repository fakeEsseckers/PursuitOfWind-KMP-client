package io.github.esseckers.pursuitofwind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.esseckers.pursuitofwind.ui.design.PursuitOfWindTheme
import io.github.esseckers.pursuitofwind.ui.design.Spacing
import io.github.esseckers.pursuitofwind.ui.generated.resources.Res
import io.github.esseckers.pursuitofwind.ui.generated.resources.app_name
import io.github.esseckers.pursuitofwind.ui.generated.resources.app_tagline
import io.github.esseckers.pursuitofwind.ui.generated.resources.bootstrap_status
import org.jetbrains.compose.resources.stringResource

/** Root composable shared by the Android and iOS apps. */
@Composable
fun App() {
    PursuitOfWindTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BootstrapScreen()
        }
    }
}

/** Placeholder shown until the Today screen lands (Issue #7). */
@Composable
private fun BootstrapScreen(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.app_tagline),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.bootstrap_status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun AppPreview() {
    App()
}
