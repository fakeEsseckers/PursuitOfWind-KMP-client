package io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel

import io.github.esseckers.pursuitofwind.data.model.Cached
import io.github.esseckers.pursuitofwind.data.model.MySpot
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotEdit
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsInputs
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState
import io.github.esseckers.pursuitofwind.feature.spots.presentation.model.SpotsState.Content
import io.github.esseckers.pursuitofwind.feature.spots.presentation.view.SpotsPreviewData.nordstrand
import io.github.esseckers.pursuitofwind.feature.spots.presentation.view.SpotsPreviewData.saltLagoon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SpotsReducerTest {
    private val fetchedAt = Instant.parse("2026-10-24T06:00:00Z")

    private fun data(vararg spots: MySpot) = SpotsResult.DataChanged(Cached(spots.toList(), fetchedAt))

    private fun reduce(
        vararg results: SpotsResult,
        from: SpotsState = SpotsState(),
    ): SpotsState = results.fold(from, SpotsReducer::reduce)

    private val loaded = reduce(data(nordstrand, saltLagoon), SpotsResult.RefreshFinished(null))

    private fun SpotsState.spot(name: String) = (content as Content.Ready).spots.single { it.name == name }

    private fun cache(vararg spots: MySpot) = Cached(spots.toList(), fetchedAt)

    @Test
    fun `a change shows at once and stays until its save ends with the cache read after it`() {
        val id = saltLagoon.spot.id
        val edited = reduce(SpotsResult.Edited(id, SpotEdit(driveMinutes = 160, favorite = true)), from = loaded)
        val stillOld = reduce(data(nordstrand, saltLagoon), from = edited)
        val cachedWhileSaving = reduce(data(nordstrand, saltLagoon.copy(favorite = true)), from = stillOld)
        val saved = reduce(SpotsResult.SaveFinished(id, cache(nordstrand, saltLagoon.copy(favorite = true))), from = cachedWhileSaving)

        assertTrue(edited.spot("Salt Lagoon").favorite)
        assertTrue(stillOld.spot("Salt Lagoon").favorite)
        assertTrue(stillOld.spot("Salt Lagoon").saving)
        assertTrue(cachedWhileSaving.spot("Salt Lagoon").saving)
        assertTrue(saved.spot("Salt Lagoon").favorite)
        assertFalse(saved.spot("Salt Lagoon").saving)
        assertTrue(saved.inputs.edits.isEmpty())
        assertTrue(saved.inputs.saving.isEmpty())
    }

    @Test
    fun `a change back to the cached value never shows the value of an earlier save on its way`() {
        val id = saltLagoon.spot.id
        val back =
            reduce(
                SpotsResult.Edited(id, SpotEdit(driveMinutes = 160, favorite = true)),
                SpotsResult.Edited(id, SpotEdit(driveMinutes = 160, favorite = false)),
                // Another change makes the cache emit, still with the old flag.
                data(nordstrand.copy(drive = 135.minutes), saltLagoon),
                from = loaded,
            )
        val earlierSaved = reduce(data(nordstrand, saltLagoon.copy(favorite = true)), from = back)
        val done = reduce(SpotsResult.SaveFinished(id, cache(nordstrand, saltLagoon)), from = earlierSaved)

        assertFalse(back.spot("Salt Lagoon").favorite)
        assertTrue(back.spot("Salt Lagoon").saving)
        assertFalse(earlierSaved.spot("Salt Lagoon").favorite)
        assertFalse(done.spot("Salt Lagoon").favorite)
        assertFalse(done.spot("Salt Lagoon").saving)
    }

    @Test
    fun `a write that ends while the tab is not watching the cache leaves nothing behind`() {
        val salt = saltLagoon.spot.id
        val nord = nordstrand.spot.id
        // No cache emission reaches the tab between the start and the end of either write.
        val ended =
            reduce(
                SpotsResult.Edited(salt, SpotEdit(135, true)),
                SpotsResult.RemoveStarted(nord),
                SpotsResult.SaveFinished(salt, cache(nordstrand, saltLagoon.copy(drive = 135.minutes, favorite = true))),
                SpotsResult.RemoveFinished(nord, cache(saltLagoon.copy(drive = 135.minutes, favorite = true))),
                from = loaded,
            )
        // Meanwhile the picker follows Nordstrand Bay again and changes Salt Lagoon.
        val picked = reduce(data(nordstrand.copy(drive = 60.minutes), saltLagoon.copy(drive = 150.minutes)), from = ended)

        assertEquals(SpotsInputs(cached = ended.inputs.cached, refresh = ended.inputs.refresh), ended.inputs)
        val shown = (picked.content as Content.Ready).spots
        assertEquals(listOf(60.minutes, 150.minutes), shown.map { it.drive })
        assertTrue(shown.none { it.saving })
    }

    @Test
    fun `a failed save shows the spot as cached again`() {
        val failed =
            reduce(SpotsResult.Edited(nordstrand.spot.id, SpotEdit(135, true)), SpotsResult.SaveFailed(nordstrand.spot.id), from = loaded)

        assertEquals(nordstrand.drive, failed.spot("Nordstrand Bay").drive)
        assertFalse(failed.spot("Nordstrand Bay").saving)
        assertTrue(failed.inputs.saving.isEmpty())
    }

    @Test
    fun `a removed spot drops its change and stays hidden until its removal ends`() {
        val id = nordstrand.spot.id
        val removing = reduce(SpotsResult.Edited(id, SpotEdit(135, false)), SpotsResult.RemoveStarted(id), from = loaded)
        // The save that was running ends first and writes the spot once more.
        val saveEnded = reduce(SpotsResult.SaveFinished(id, cache(nordstrand.copy(drive = 135.minutes), saltLagoon)), from = removing)
        val removed = reduce(SpotsResult.RemoveFinished(id, cache(saltLagoon)), from = saveEnded)

        assertTrue(removing.inputs.edits.isEmpty())
        assertEquals(listOf("Salt Lagoon"), (saveEnded.content as Content.Ready).spots.map { it.name })
        assertEquals(listOf("Salt Lagoon"), (removed.content as Content.Ready).spots.map { it.name })
        assertTrue(removed.inputs.removing.isEmpty())
        assertTrue(removed.inputs.saving.isEmpty())
    }

    @Test
    fun `a spot that cannot be removed comes back`() {
        val failed = reduce(SpotsResult.RemoveStarted(nordstrand.spot.id), SpotsResult.RemoveFailed(nordstrand.spot.id), from = loaded)

        assertEquals(listOf("Nordstrand Bay", "Salt Lagoon"), (failed.content as Content.Ready).spots.map { it.name })
    }

    @Test
    fun `equal content keeps its instance so unchanged cards skip recomposition`() {
        val refreshing = reduce(SpotsResult.RefreshStarted, from = loaded)

        assertSame(loaded.content, refreshing.content)
        assertTrue(refreshing.isRefreshing)
        assertEquals(160.minutes, refreshing.spot("Salt Lagoon").drive)
    }
}
