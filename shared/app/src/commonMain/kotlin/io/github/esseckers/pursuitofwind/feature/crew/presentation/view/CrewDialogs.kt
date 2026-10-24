package io.github.esseckers.pursuitofwind.feature.crew.presentation.view

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.esseckers.pursuitofwind.core.designsystem.theme.Sizes
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.action_cancel_ride
import io.github.esseckers.pursuitofwind.core.resources.action_close
import io.github.esseckers.pursuitofwind.core.resources.action_create
import io.github.esseckers.pursuitofwind.core.resources.action_join
import io.github.esseckers.pursuitofwind.core.resources.action_keep_ride
import io.github.esseckers.pursuitofwind.core.resources.crew_cancel_nobody
import io.github.esseckers.pursuitofwind.core.resources.crew_cancel_passengers
import io.github.esseckers.pursuitofwind.core.resources.crew_cancel_title
import io.github.esseckers.pursuitofwind.core.resources.crew_code_hint
import io.github.esseckers.pursuitofwind.core.resources.crew_code_label
import io.github.esseckers.pursuitofwind.core.resources.crew_create_title
import io.github.esseckers.pursuitofwind.core.resources.crew_join_title
import io.github.esseckers.pursuitofwind.core.resources.crew_name_label
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewDialog
import io.github.esseckers.pursuitofwind.feature.crew.presentation.model.CrewIntent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The dialog the state asks for, if any: naming a new crew, entering an invite code, or confirming a cancellation. */
@Composable
internal fun CrewDialogs(
    dialog: CrewDialog?,
    onIntent: (CrewIntent) -> Unit,
) {
    if (dialog == null) return
    val close = { onIntent(CrewIntent.CloseDialog) }
    when (dialog) {
        is CrewDialog.Create -> {
            TextDialog(
                title = Res.string.crew_create_title,
                label = Res.string.crew_name_label,
                hint = null,
                confirm = Res.string.action_create,
                sending = dialog.sending,
                error = dialog.error,
                maxLength = MAX_CREW_NAME,
                capitalization = KeyboardCapitalization.Words,
                tag = "crew.createDialog",
                onConfirm = { onIntent(CrewIntent.CreateCrew(it)) },
                onDismiss = close,
            )
        }

        is CrewDialog.Join -> {
            TextDialog(
                title = Res.string.crew_join_title,
                label = Res.string.crew_code_label,
                hint = Res.string.crew_code_hint,
                confirm = Res.string.action_join,
                sending = dialog.sending,
                error = dialog.error,
                maxLength = MAX_INVITE_CODE,
                capitalization = KeyboardCapitalization.Characters,
                tag = "crew.joinDialog",
                onConfirm = { onIntent(CrewIntent.JoinCrew(it)) },
                onDismiss = close,
            )
        }

        is CrewDialog.ConfirmCancel -> {
            ConfirmCancelDialog(dialog, onConfirm = { onIntent(CrewIntent.ConfirmCancel) }, onDismiss = close)
        }
    }
}

/**
 * One line of text and a confirm button, enabled once the text has a non-space character. While [sending], the button
 * shows progress and the dialog cannot be dismissed; [error] shows below the field and the text stays.
 */
@Suppress("LongParameterList") // Two call sites, each naming every value; a holder class would only rename them.
@Composable
private fun TextDialog(
    title: StringResource,
    label: StringResource,
    hint: StringResource?,
    confirm: StringResource,
    sending: Boolean,
    error: StringResource?,
    maxLength: Int,
    capitalization: KeyboardCapitalization,
    tag: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val canConfirm = text.isNotBlank() && !sending
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= maxLength) text = it },
                label = { Text(stringResource(label)) },
                placeholder = hint?.let { { Text(stringResource(it)) } },
                isError = error != null,
                supportingText = error?.let { { Text(stringResource(it)) } },
                singleLine = true,
                enabled = !sending,
                keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(text) }),
                modifier = Modifier.testTag("$tag.field"),
            )
        },
        confirmButton = {
            val confirmLabel = stringResource(confirm)
            // Named even while it only shows progress.
            TextButton(
                onClick = { onConfirm(text) },
                enabled = canConfirm,
                modifier = Modifier.semantics { contentDescription = confirmLabel }.testTag("$tag.confirm"),
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Sizes.iconSmall),
                        color = LocalContentColor.current,
                        strokeWidth = Sizes.progressStroke,
                    )
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending, modifier = Modifier.testTag("$tag.close")) {
                Text(stringResource(Res.string.action_close))
            }
        },
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun ConfirmCancelDialog(
    dialog: CrewDialog.ConfirmCancel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.crew_cancel_title, dialog.spotName)) },
        text = {
            val body =
                if (dialog.passengers == 0) {
                    stringResource(Res.string.crew_cancel_nobody)
                } else {
                    pluralStringResource(Res.plurals.crew_cancel_passengers, dialog.passengers, dialog.passengers)
                }
            Text(body)
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("crew.cancelDialog.confirm")) {
                Text(stringResource(Res.string.action_cancel_ride))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("crew.cancelDialog.keep")) {
                Text(stringResource(Res.string.action_keep_ride))
            }
        },
        modifier = Modifier.testTag("crew.cancelDialog"),
    )
}

/** The API's limit for crew names. */
private const val MAX_CREW_NAME = 40

/** Invite codes look like `WIND-4KQ7`; some room for spaces pasted along. */
private const val MAX_INVITE_CODE = 20
