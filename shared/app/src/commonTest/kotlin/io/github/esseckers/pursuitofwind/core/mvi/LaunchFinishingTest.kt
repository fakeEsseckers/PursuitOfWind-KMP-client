package io.github.esseckers.pursuitofwind.core.mvi

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchFinishingTest {
    @Test
    fun `work that finishes hands its result to onFinish once`() =
        runTest {
            val finished = mutableListOf<String>()

            launchFinishing(unfinished = "unfinished", work = { "saved" }) { finished += it }.join()

            assertEquals(listOf("saved"), finished)
        }

    @Test
    fun `work that is cancelled hands the unfinished value to onFinish`() =
        runTest {
            val finished = mutableListOf<String>()

            val job = launchFinishing(unfinished = "unfinished", work = { awaitCancellation() }) { finished += it }
            runCurrent()
            job.cancel()
            job.join()

            assertEquals(listOf("unfinished"), finished)
        }

    @Test
    fun `work that throws hands the unfinished value to onFinish and the scope still gets the exception`() =
        runTest {
            val finished = mutableListOf<String>()
            val caught = mutableListOf<Throwable>()
            val scope =
                CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> caught += e })

            scope.launchFinishing(unfinished = "unfinished", work = { error("no network") }) { finished += it }.join()

            assertEquals(listOf("unfinished"), finished)
            assertIs<IllegalStateException>(caught.single())
        }
}
