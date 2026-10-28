package io.github.esseckers.pursuitofwind.app.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.descriptors.elementDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The back stack is saved with [navigationConfiguration]. On iOS there is no reflection, so a destination that is not
 * registered there cannot be saved. Runs on the JVM: saved state on Android is a `Bundle`, which host tests do not have.
 */
class DestinationSavedStateTest {
    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `every destination is registered for saving`() {
        // The sealed serializer lists every subclass, so a new destination is checked without editing this test.
        val destinations =
            Destination
                .serializer()
                .descriptor
                .getElementDescriptor(1)
                .elementDescriptors
                .map { it.serialName }

        assertTrue(destinations.isNotEmpty())
        destinations.forEach { name ->
            assertNotNull(navigationConfiguration.serializersModule.getPolymorphic(NavKey::class, name), "$name is not registered")
        }
    }

    @Test
    fun `a back stack survives saving and restoring`() {
        val serializer = NavBackStackSerializer(PolymorphicSerializer(NavKey::class))
        val stacks =
            listOf(
                NavBackStack<NavKey>(
                    Destination.Today,
                    Destination.SpotDetail("nordstrand-bay", LocalDate(2026, 10, 24)),
                    Destination.SpotDetail("salt-lagoon"),
                ),
                NavBackStack<NavKey>(Destination.Welcome, Destination.SetUpProfile, Destination.PickSpots, Destination.SpotMap()),
                NavBackStack<NavKey>(Destination.Today, Destination.Spots, Destination.SpotMap(editing = "spt_0001")),
                NavBackStack<NavKey>(Destination.Today, Destination.Profile),
            )

        stacks.forEach { stack ->
            val saved = encodeToSavedState(serializer, stack, navigationConfiguration)
            val restored = decodeFromSavedState(serializer, saved, navigationConfiguration)

            assertEquals(stack.toList(), restored.toList())
        }
    }
}
