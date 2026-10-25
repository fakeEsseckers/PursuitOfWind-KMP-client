package io.github.esseckers.pursuitofwind.feature.crew.domain.mapper

import io.github.esseckers.pursuitofwind.data.model.Crew
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CrewChoiceMapperTest {
    private val weekend =
        Crew("crw_weekend", "Weekend Crew", "WIND-4KQ7", memberCount = 4, createdAt = Instant.parse("2026-09-01T10:00:00Z"))
    private val owls = Crew("crw_owls", "Night Owls", "WIND-7HPX", memberCount = 2, createdAt = Instant.parse("2026-10-20T10:00:00Z"))

    @Test
    fun `the chosen crew is shown`() {
        assertEquals(owls, CrewChoiceMapper.shownCrew(listOf(weekend, owls), chosen = owls.id))
    }

    @Test
    fun `without a choice the first crew is shown`() {
        assertEquals(weekend, CrewChoiceMapper.shownCrew(listOf(weekend, owls), chosen = null))
    }

    @Test
    fun `a chosen crew the rider has left gives way to the first`() {
        assertEquals(weekend, CrewChoiceMapper.shownCrew(listOf(weekend, owls), chosen = "crw_left"))
    }

    @Test
    fun `without crews nothing is shown`() {
        assertNull(CrewChoiceMapper.shownCrew(emptyList(), chosen = owls.id))
    }
}
