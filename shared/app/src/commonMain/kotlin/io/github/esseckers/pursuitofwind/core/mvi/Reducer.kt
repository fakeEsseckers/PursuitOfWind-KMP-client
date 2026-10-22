package io.github.esseckers.pursuitofwind.core.mvi

/**
 * The only place a screen's state changes: the next state from the current one and one result. Pure, with no I/O and
 * no clock, so it is tested with plain values.
 */
internal fun interface Reducer<State, Result> {
    fun reduce(
        state: State,
        result: Result,
    ): State
}
