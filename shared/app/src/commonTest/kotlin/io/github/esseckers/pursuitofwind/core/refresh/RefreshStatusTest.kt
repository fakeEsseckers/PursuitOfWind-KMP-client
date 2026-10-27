package io.github.esseckers.pursuitofwind.core.refresh

import io.github.esseckers.pursuitofwind.data.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class RefreshStatusTest {
    private val eight = Instant.parse("2026-10-22T08:00:00Z")

    @Test
    fun `a refresh runs from its start to its end and keeps when it started`() {
        val started = RefreshStatus().started(at = eight)
        val finished = started.finished(error = null)

        assertEquals(RefreshStatus(refreshing = true, startedAt = eight), started)
        assertEquals(RefreshStatus(finished = true, startedAt = eight), finished)
    }

    @Test
    fun `the last error shows only while no other refresh runs`() {
        val offline = RefreshStatus().started().finished(AppError.Network)
        val retrying = offline.started()

        assertEquals(AppError.Network, offline.shownError)
        assertEquals(AppError.Network, retrying.error)
        assertNull(retrying.shownError)
    }
}
