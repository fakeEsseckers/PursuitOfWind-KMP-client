package io.github.esseckers.pursuitofwind.feature.spots.presentation.mapper

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.error_network
import io.github.esseckers.pursuitofwind.data.model.AppError
import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.RefreshStatus
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotDrive
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotEdit
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsInputs
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Content
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Notice
import io.github.esseckers.pursuitofwind.feature.spots.presentation.view.SpotsPreviewData.lakeWeiss
import io.github.esseckers.pursuitofwind.feature.spots.presentation.view.SpotsPreviewData.nordstrand
import io.github.esseckers.pursuitofwind.feature.spots.presentation.view.SpotsPreviewData.saltLagoon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SpotsStateMapperTest {
    private val fetchedAt = Instant.parse("2026-10-24T06:00:00Z")

    private fun inputs(
        vararg spots: MySpot,
        fetched: Boolean = true,
    ) = SpotsInputs(cached = Cached(spots.toList(), if (fetched) fetchedAt else null))

    private fun SpotsInputs.spots() = (SpotsStateMapper.map(this).content as Content.Ready).spots

    @Test
    fun `followed spots show as cached with their favorite flag drive and sector check`() {
        val spots = inputs(lakeWeiss, nordstrand, saltLagoon).spots()

        assertEquals(listOf("Lake Weiss", "Nordstrand Bay", "Salt Lagoon"), spots.map { it.name })
        assertEquals(listOf(true, true, false), spots.map { it.favorite })
        assertEquals(listOf(45.minutes, 2.hours, 160.minutes), spots.map { it.drive })
        assertEquals(listOf(false, true, true), spots.map { it.verified })
        assertTrue(spots.none { it.saving })
    }

    @Test
    fun `nothing cached loads until the first refresh ends and then says why`() {
        assertEquals(Content.Loading, SpotsStateMapper.map(SpotsInputs()).content)
        assertEquals(Content.Loading, SpotsStateMapper.map(inputs(fetched = false)).content)
        assertEquals(
            Content.Error(Res.string.error_network),
            SpotsStateMapper.map(inputs(fetched = false).copy(refresh = RefreshStatus(finished = true, error = AppError.Network))).content,
        )
        assertEquals(Content.Empty, SpotsStateMapper.map(inputs(fetched = false).copy(refresh = RefreshStatus(finished = true))).content)
        assertEquals(Content.Empty, SpotsStateMapper.map(inputs()).content)
    }

    @Test
    fun `cached spots win over a failed refresh with a notice above them`() {
        val offline = SpotsStateMapper.map(inputs(nordstrand).copy(refresh = RefreshStatus(finished = true, error = AppError.Network)))
        val failed = SpotsStateMapper.map(inputs(nordstrand).copy(refresh = RefreshStatus(finished = true, error = AppError.Server(503))))

        assertIs<Content.Ready>(offline.content)
        assertEquals(Notice.OFFLINE, offline.notice)
        assertEquals(Notice.REFRESH_FAILED, failed.notice)
    }

    @Test
    fun `an edit shows on top of the cache as saving and a removed spot is hidden`() {
        val edited = inputs(nordstrand, saltLagoon).copy(edits = mapOf(saltLagoon.spot.id to SpotEdit(90, favorite = true)))
        val removing = inputs(nordstrand, saltLagoon).copy(removing = setOf(nordstrand.spot.id))

        val salt = edited.spots().single { it.name == "Salt Lagoon" }
        assertEquals(90.minutes, salt.drive)
        assertTrue(salt.favorite)
        assertTrue(salt.saving)
        assertEquals(listOf("Salt Lagoon"), removing.spots().map { it.name })
    }

    @Test
    fun `the last spot removed leaves the empty state at once`() {
        assertEquals(Content.Empty, SpotsStateMapper.map(inputs(nordstrand).copy(removing = setOf(nordstrand.spot.id))).content)
    }

    @Test
    fun `the plus button stops at a day trip and minus at zero but a longer drive can still step down`() {
        val bounds =
            inputs(nordstrand, saltLagoon, lakeWeiss)
                .copy(
                    edits =
                        mapOf(
                            nordstrand.spot.id to SpotEdit(0, true),
                            saltLagoon.spot.id to SpotEdit(SpotDrive.MAX_STEPPED_MINUTES, true),
                            lakeWeiss.spot.id to SpotEdit(900, true),
                        ),
                ).spots()
                .associateBy { it.name }

        assertFalse(bounds.getValue("Nordstrand Bay").canShorten)
        assertFalse(bounds.getValue("Salt Lagoon").canLengthen)
        assertTrue(bounds.getValue("Lake Weiss").canShorten)
        assertFalse(bounds.getValue("Lake Weiss").canLengthen)
    }
}
