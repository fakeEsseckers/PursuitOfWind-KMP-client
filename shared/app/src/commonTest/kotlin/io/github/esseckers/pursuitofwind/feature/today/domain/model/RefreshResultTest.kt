package io.github.esseckers.pursuitofwind.feature.today.domain.model

import io.github.esseckers.pursuitofwind.data.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RefreshResultTest {
    @Test
    fun `a connection problem is the error to show because it explains the others`() {
        assertEquals(AppError.Network, mainError(listOf(AppError.Server(503), AppError.Network)))
        assertEquals(AppError.Server(503), mainError(listOf(AppError.Server(503), AppError.Unauthorized)))
        assertNull(mainError(emptyList()))
    }
}
