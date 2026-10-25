package io.github.esseckers.pursuitofwind.feature.profile.presentation.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.core.designsystem.component.ChoiceChip
import io.github.esseckers.pursuitofwind.core.designsystem.component.DraftTextField
import io.github.esseckers.pursuitofwind.core.designsystem.component.SectionTitle
import io.github.esseckers.pursuitofwind.core.designsystem.component.Stepper
import io.github.esseckers.pursuitofwind.core.designsystem.icon.AppIcons
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Spacing
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.decimal_separator
import io.github.esseckers.pursuitofwind.core.resources.kite_size
import io.github.esseckers.pursuitofwind.core.resources.profile_kites
import io.github.esseckers.pursuitofwind.core.resources.profile_kites_hint
import io.github.esseckers.pursuitofwind.core.resources.profile_language
import io.github.esseckers.pursuitofwind.core.resources.profile_level
import io.github.esseckers.pursuitofwind.core.resources.profile_level_hint
import io.github.esseckers.pursuitofwind.core.resources.profile_max_drive
import io.github.esseckers.pursuitofwind.core.resources.profile_max_drive_longer
import io.github.esseckers.pursuitofwind.core.resources.profile_max_drive_shorter
import io.github.esseckers.pursuitofwind.core.resources.profile_max_wind
import io.github.esseckers.pursuitofwind.core.resources.profile_min_session
import io.github.esseckers.pursuitofwind.core.resources.profile_min_session_longer
import io.github.esseckers.pursuitofwind.core.resources.profile_min_session_shorter
import io.github.esseckers.pursuitofwind.core.resources.profile_min_wind
import io.github.esseckers.pursuitofwind.core.resources.profile_more
import io.github.esseckers.pursuitofwind.core.resources.profile_name
import io.github.esseckers.pursuitofwind.core.resources.profile_unit
import io.github.esseckers.pursuitofwind.core.resources.profile_weight
import io.github.esseckers.pursuitofwind.core.resources.profile_wind_range
import io.github.esseckers.pursuitofwind.core.resources.profile_wind_range_hint
import io.github.esseckers.pursuitofwind.core.resources.state_collapsed
import io.github.esseckers.pursuitofwind.core.resources.state_expanded
import io.github.esseckers.pursuitofwind.core.resources.unit_kg
import io.github.esseckers.pursuitofwind.core.ui.Step
import io.github.esseckers.pursuitofwind.core.ui.StepperUi
import io.github.esseckers.pursuitofwind.core.ui.durationText
import io.github.esseckers.pursuitofwind.core.ui.formatKiteSize
import io.github.esseckers.pursuitofwind.core.ui.windUnitLabel
import io.github.esseckers.pursuitofwind.data.model.AppLanguage
import io.github.esseckers.pursuitofwind.data.model.WindUnit
import io.github.esseckers.pursuitofwind.engine.model.SkillLevel
import io.github.esseckers.pursuitofwind.feature.profile.presentation.mapper.acceptsNumber
import io.github.esseckers.pursuitofwind.feature.profile.presentation.mapper.limitUnit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.KiteChoice
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileEdit
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileError
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileField
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileForm
import io.github.esseckers.pursuitofwind.feature.profile.presentation.model.ProfileLimits
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.minutes

// The groups of the profile form. Each one sends edits; none keeps state of its own apart from what is open.

@Composable
internal fun AboutYouFields(
    form: ProfileForm,
    version: Int,
    errors: Map<ProfileField, ProfileError>,
    onEdit: (ProfileEdit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        DraftTextField(
            value = form.name,
            version = version,
            onValueChange = { onEdit(ProfileEdit.Name(it)) },
            label = stringResource(Res.string.profile_name),
            // Longer names than the server allows are never typed.
            accepts = { ApiLimits.lengthOf(it) <= ProfileLimits.NAME_MAX_LENGTH },
            error = errors[ProfileField.NAME]?.let { stringResource(errorText(it)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.testTag("profile.name"),
        )
        DraftTextField(
            value = form.weightKg,
            version = version,
            onValueChange = { onEdit(ProfileEdit.Weight(it)) },
            label = stringResource(Res.string.profile_weight),
            accepts = ::acceptsNumber,
            error = errors[ProfileField.WEIGHT]?.let { stringResource(errorText(it)) },
            suffix = stringResource(Res.string.unit_kg),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.testTag("profile.weight"),
        )
    }
}

@Composable
internal fun LevelChoice(
    level: SkillLevel,
    onEdit: (ProfileEdit) -> Unit,
) {
    ChoiceGroup(
        title = stringResource(Res.string.profile_level),
        hint = stringResource(Res.string.profile_level_hint),
        singleChoice = true,
    ) {
        SkillLevel.entries.forEach { choice ->
            ChoiceChip(
                label = stringResource(levelLabel(choice)),
                selected = choice == level,
                onClick = { onEdit(ProfileEdit.Level(choice)) },
                singleChoice = true,
                modifier = Modifier.testTag("profile.level.${choice.name}"),
            )
        }
    }
}

@Composable
internal fun KiteChoices(
    kites: List<KiteChoice>,
    onEdit: (ProfileEdit) -> Unit,
) {
    val separator = stringResource(Res.string.decimal_separator).first()
    ChoiceGroup(
        title = stringResource(Res.string.profile_kites),
        hint = stringResource(Res.string.profile_kites_hint, ProfileLimits.MAX_KITES),
    ) {
        kites.forEach { kite ->
            ChoiceChip(
                label = stringResource(Res.string.kite_size, formatKiteSize(kite.sizeM2, separator)),
                selected = kite.owned,
                enabled = kite.enabled,
                onClick = { onEdit(ProfileEdit.Kite(kite.sizeM2, owned = !kite.owned)) },
                modifier = Modifier.testTag("profile.kite.${formatKiteSize(kite.sizeM2)}"),
            )
        }
    }
}

@Composable
internal fun UnitChoice(
    unit: WindUnit,
    onEdit: (ProfileEdit) -> Unit,
) {
    ChoiceGroup(title = stringResource(Res.string.profile_unit), singleChoice = true) {
        WindUnit.entries.forEach { choice ->
            ChoiceChip(
                label = stringResource(unitName(choice)),
                selected = choice == unit,
                onClick = { onEdit(ProfileEdit.Units(choice)) },
                singleChoice = true,
                modifier = Modifier.testTag("profile.unit.${choice.name}"),
            )
        }
    }
}

/** The app's language: the phone's, or one the app is translated into. It applies at once, without saving. */
@Composable
internal fun LanguageChoice(
    language: AppLanguage,
    onChoose: (AppLanguage) -> Unit,
) {
    ChoiceGroup(title = stringResource(Res.string.profile_language), singleChoice = true) {
        AppLanguage.entries.forEach { choice ->
            ChoiceChip(
                label = stringResource(languageLabel(choice)),
                selected = choice == language,
                onClick = { onChoose(choice) },
                singleChoice = true,
                modifier = Modifier.testTag("profile.language.${choice.name}"),
            )
        }
    }
}

/** Session, drive and personal wind limits: folded away, because the defaults suit most riders. */
@Composable
internal fun MoreSettings(
    form: ProfileForm,
    minSession: StepperUi,
    maxDrive: StepperUi,
    version: Int,
    errors: Map<ProfileField, ProfileError>,
    onEdit: (ProfileEdit) -> Unit,
) {
    // Opens by itself when a hidden field needs fixing, so an error is never out of sight.
    val hasErrors = ProfileField.MIN_WIND in errors || ProfileField.MAX_WIND in errors
    var expanded by rememberSaveable { mutableStateOf(hasErrors) }
    // It stays open once fixed, so the field being typed in does not vanish with the error.
    LaunchedEffect(hasErrors) { if (hasErrors) expanded = true }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        MoreSettingsHeader(expanded = expanded, onToggle = { expanded = !expanded })
        if (expanded) {
            Stepper(
                label = stringResource(Res.string.profile_min_session),
                value = durationText(minSession.value.minutes),
                decreaseLabel = stringResource(Res.string.profile_min_session_shorter),
                increaseLabel = stringResource(Res.string.profile_min_session_longer),
                onDecrease = { onEdit(ProfileEdit.MinSession(Step.DOWN)) },
                onIncrease = { onEdit(ProfileEdit.MinSession(Step.UP)) },
                canDecrease = minSession.canDecrease,
                canIncrease = minSession.canIncrease,
                modifier = Modifier.testTag("profile.minSession"),
            )
            Stepper(
                label = stringResource(Res.string.profile_max_drive),
                value = durationText(maxDrive.value.minutes),
                decreaseLabel = stringResource(Res.string.profile_max_drive_shorter),
                increaseLabel = stringResource(Res.string.profile_max_drive_longer),
                onDecrease = { onEdit(ProfileEdit.MaxDrive(Step.DOWN)) },
                onIncrease = { onEdit(ProfileEdit.MaxDrive(Step.UP)) },
                canDecrease = maxDrive.canDecrease,
                canIncrease = maxDrive.canIncrease,
                modifier = Modifier.testTag("profile.maxDrive"),
            )
            WindLimitFields(form = form, version = version, errors = errors, onEdit = onEdit)
        }
    }
}

@Composable
private fun MoreSettingsHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val state = stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTarget)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = state
                }.testTag("profile.more"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(Res.string.profile_more), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

@Composable
private fun WindLimitFields(
    form: ProfileForm,
    version: Int,
    errors: Map<ProfileField, ProfileError>,
    onEdit: (ProfileEdit) -> Unit,
) {
    val unit = stringResource(windUnitLabel(limitUnit(form.unit)))
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle(text = stringResource(Res.string.profile_wind_range))
        Hint(text = stringResource(Res.string.profile_wind_range_hint))
        DraftTextField(
            value = form.minWind,
            version = version,
            onValueChange = { onEdit(ProfileEdit.MinWind(it)) },
            label = stringResource(Res.string.profile_min_wind),
            accepts = ::acceptsNumber,
            error = errors[ProfileField.MIN_WIND]?.let { stringResource(errorText(it)) },
            suffix = unit,
            keyboardOptions = keyboard,
            modifier = Modifier.testTag("profile.minWind"),
        )
        DraftTextField(
            value = form.maxWind,
            version = version,
            onValueChange = { onEdit(ProfileEdit.MaxWind(it)) },
            label = stringResource(Res.string.profile_max_wind),
            accepts = ::acceptsNumber,
            error = errors[ProfileField.MAX_WIND]?.let { stringResource(errorText(it)) },
            suffix = unit,
            keyboardOptions = keyboard.copy(imeAction = ImeAction.Done),
            modifier = Modifier.testTag("profile.maxWind"),
        )
    }
}

/** A titled group of chips that wraps onto more lines, so long labels and large fonts fit. */
@Composable
private fun ChoiceGroup(
    title: String,
    hint: String? = null,
    singleChoice: Boolean = false,
    chips: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle(text = title)
        hint?.let { Hint(text = it) }
        FlowRow(
            modifier = if (singleChoice) Modifier.selectableGroup() else Modifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) { chips() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
