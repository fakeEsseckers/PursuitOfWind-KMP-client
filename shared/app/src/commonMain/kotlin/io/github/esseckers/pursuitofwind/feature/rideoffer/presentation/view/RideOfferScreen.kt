package io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.core.designsystem.component.ChoiceChip
import io.github.esseckers.pursuitofwind.core.designsystem.component.DraftTextField
import io.github.esseckers.pursuitofwind.core.designsystem.component.IconLine
import io.github.esseckers.pursuitofwind.core.designsystem.component.MessageState
import io.github.esseckers.pursuitofwind.core.designsystem.component.NavigationBarScrim
import io.github.esseckers.pursuitofwind.core.designsystem.component.PrimaryButton
import io.github.esseckers.pursuitofwind.core.designsystem.component.ScreenTopBar
import io.github.esseckers.pursuitofwind.core.designsystem.component.SectionTitle
import io.github.esseckers.pursuitofwind.core.designsystem.component.Stepper
import io.github.esseckers.pursuitofwind.core.designsystem.component.contentEndInsets
import io.github.esseckers.pursuitofwind.core.designsystem.component.screenInsets
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_back
import io.github.esseckers.pursuitofwind.core.resources.action_offer_ride
import io.github.esseckers.pursuitofwind.core.resources.offer_crew
import io.github.esseckers.pursuitofwind.core.resources.offer_failed
import io.github.esseckers.pursuitofwind.core.resources.offer_fewer_seats
import io.github.esseckers.pursuitofwind.core.resources.offer_leave
import io.github.esseckers.pursuitofwind.core.resources.offer_meeting
import io.github.esseckers.pursuitofwind.core.resources.offer_meeting_hint
import io.github.esseckers.pursuitofwind.core.resources.offer_more_seats
import io.github.esseckers.pursuitofwind.core.resources.offer_no_crew_body
import io.github.esseckers.pursuitofwind.core.resources.offer_no_crew_title
import io.github.esseckers.pursuitofwind.core.resources.offer_seats
import io.github.esseckers.pursuitofwind.core.resources.offer_title
import io.github.esseckers.pursuitofwind.core.resources.offer_window
import io.github.esseckers.pursuitofwind.core.ui.dayLabel
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.OfferSummary
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferInputs
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferIntent
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.model.RideOfferState
import org.jetbrains.compose.resources.stringResource

/**
 * "I'm going": the window the ride is for, the crew it goes to, the free seats and where to meet, and "Offer the
 * ride". Stateless; `RideOfferRoute` connects it to its ViewModel. [onBack] leaves without offering.
 */
@Composable
internal fun RideOfferScreen(
    state: RideOfferState,
    onIntent: (RideOfferIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            ScreenTopBar(
                title = stringResource(Res.string.offer_title),
                backLabel = stringResource(Res.string.action_back),
                onBack = onBack,
            )
        },
        contentWindowInsets = screenInsets(),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.noCrew) {
                MessageState(
                    icon = AppIcons.Car,
                    title = stringResource(Res.string.offer_no_crew_title),
                    body = stringResource(Res.string.offer_no_crew_body),
                    modifier = Modifier.padding(padding).testTag("offer.noCrew"),
                )
            } else {
                OfferForm(state, onIntent, Modifier.padding(padding))
            }
            NavigationBarScrim(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun OfferForm(
    state: RideOfferState,
    onIntent: (RideOfferIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                // Above the keyboard, so the meeting point and "Offer the ride" can be scrolled into view while typing.
                .imePadding()
                .verticalScroll(rememberScrollState())
                // Scrolls behind the navigation bar and ends clear of it, or of the keyboard while it is open.
                .windowInsetsPadding(contentEndInsets())
                .padding(Spacing.m)
                .testTag("offer.form"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            state.summary?.let { Summary(it) }
            CrewChoice(state, onIntent)
            Stepper(
                label = stringResource(Res.string.offer_seats),
                value = state.seats.toString(),
                decreaseLabel = stringResource(Res.string.offer_fewer_seats),
                increaseLabel = stringResource(Res.string.offer_more_seats),
                onDecrease = { onIntent(RideOfferIntent.FewerSeats) },
                onIncrease = { onIntent(RideOfferIntent.MoreSeats) },
                canDecrease = state.canFewer,
                canIncrease = state.canMore,
                modifier = Modifier.testTag("offer.seats"),
            )
            DraftTextField(
                value = state.meetingPoint,
                version = 0,
                onValueChange = { onIntent(RideOfferIntent.ChangeMeetingPoint(it)) },
                label = stringResource(Res.string.offer_meeting),
                // What the field shows is what is sent: no longer than the API takes.
                accepts = { ApiLimits.lengthOf(it) <= RideOfferInputs.MAX_MEETING_POINT },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.testTag("offer.meeting"),
            )
            Text(
                text = stringResource(Res.string.offer_meeting_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Submit(state, onIntent)
        }
    }
}

/** The crews to offer the ride to, one picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrewChoice(
    state: RideOfferState,
    onIntent: (RideOfferIntent) -> Unit,
) {
    SectionTitle(text = stringResource(Res.string.offer_crew))
    FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        state.crews.forEach { crew ->
            ChoiceChip(
                label = crew.name,
                selected = crew.selected,
                onClick = { onIntent(RideOfferIntent.ChooseCrew(crew.id)) },
                singleChoice = true,
                modifier = Modifier.testTag("offer.crew.${crew.id}"),
            )
        }
    }
}

/** Why the last try failed, if it did, and the button that offers the ride. */
@Composable
private fun Submit(
    state: RideOfferState,
    onIntent: (RideOfferIntent) -> Unit,
) {
    state.error?.let { reason ->
        Text(
            text = "${stringResource(Res.string.offer_failed)} ${stringResource(reason)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("offer.error"),
        )
    }
    PrimaryButton(
        text = stringResource(Res.string.action_offer_ride),
        onClick = { onIntent(RideOfferIntent.Submit) },
        enabled = state.canSubmit,
        busy = state.sending,
        modifier = Modifier.testTag("offer.submit"),
    )
}

/** The spot as a heading, the day and window local to the spot, and when the car leaves. */
@Composable
private fun Summary(summary: OfferSummary) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("offer.summary")) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = summary.spotName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            IconLine(
                icon = AppIcons.Clock,
                text = stringResource(Res.string.offer_window, dayLabel(summary.day), summary.window.toString()),
            )
            IconLine(icon = AppIcons.Car, text = stringResource(Res.string.offer_leave, summary.departAt))
        }
    }
}
