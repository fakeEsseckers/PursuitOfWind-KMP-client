package io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel

import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.crew_invite_invalid
import io.github.esseckers.pursuitofwind.core.resources.crew_ride_was_cancelled
import io.github.esseckers.pursuitofwind.core.resources.crew_seat_taken
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.data.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals

class CrewFailureTest {
    @Test
    fun `failures that have a reason of their own say it`() {
        assertEquals(Res.string.crew_seat_taken, AppError.Conflict(ProblemCode.SEATS_FULL).crewFailure())
        assertEquals(Res.string.crew_ride_was_cancelled, AppError.Conflict(ProblemCode.RIDE_CANCELLED).crewFailure())
        assertEquals(Res.string.crew_invite_invalid, AppError.Rejected(404, ProblemCode.INVITE_INVALID).crewFailure())
        assertEquals(Res.string.error_network, AppError.Network.crewFailure())
    }
}
