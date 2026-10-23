package io.github.esseckers.pursuitofwind.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain

/**
 * Ends a test that ran view models on the real data layer, instead of `Dispatchers.resetMain()`.
 *
 * Room and DataStore work on real IO threads. A query that finishes after the test still resumes its cancelled caller
 * through [Dispatchers.Main]. On a device Main always exists and that resume does nothing; reset to "missing" on the
 * JVM, it throws on the IO thread, and kotlinx-coroutines-test fails whichever test starts next. So Main stays a working
 * dispatcher, [Dispatchers.Unconfined], until the next test sets its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun releaseMainDispatcher() {
    Dispatchers.setMain(Dispatchers.Unconfined)
}
