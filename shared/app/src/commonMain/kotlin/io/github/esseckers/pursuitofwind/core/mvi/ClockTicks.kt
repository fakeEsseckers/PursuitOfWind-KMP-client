package io.github.esseckers.pursuitofwind.core.mvi

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** The clock's time now and after every [period]. Repeated readings are dropped, so a stopped clock costs nothing. */
internal fun Clock.ticks(period: Duration): Flow<Instant> =
    flow {
        while (true) {
            emit(now())
            delay(period)
        }
    }.distinctUntilChanged()
