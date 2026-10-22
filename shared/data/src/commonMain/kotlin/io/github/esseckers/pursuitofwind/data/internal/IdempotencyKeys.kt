package io.github.esseckers.pursuitofwind.data.internal

import kotlin.uuid.Uuid

/** One key per user action: retries of that action reuse it, so the server applies the action once. */
internal fun newIdempotencyKey(): String = Uuid.random().toString()
