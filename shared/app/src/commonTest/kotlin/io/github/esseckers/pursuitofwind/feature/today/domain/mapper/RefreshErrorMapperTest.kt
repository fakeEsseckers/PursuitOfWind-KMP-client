package io.github.esseckers.pursuitofwind.feature.today.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RefreshErrorMapperTest {
    @Test
    fun `a connection problem is the error to show because it explains the others`() {
        assertEquals(AppError.Network, RefreshErrorMapper.mainError(listOf(AppError.Server(503), AppError.Network)))
    }

    @Test
    fun `without a connection problem the first error is shown`() {
        assertEquals(AppError.Server(503), RefreshErrorMapper.mainError(listOf(AppError.Server(503), AppError.Unauthorized)))
    }

    @Test
    fun `nothing failed means nothing to show`() {
        assertNull(RefreshErrorMapper.mainError(emptyList()))
    }
}
