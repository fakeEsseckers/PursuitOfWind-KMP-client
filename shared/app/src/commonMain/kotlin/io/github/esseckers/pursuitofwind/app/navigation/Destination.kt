package io.github.esseckers.pursuitofwind.app.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * The app's screens as back stack keys. They are serializable, so the back stack survives process death; the values
 * are plain, so they also fit a deep link later.
 */
@Serializable
internal sealed interface Destination : NavKey {
    @Serializable
    data object Today : Destination

    /** The hourly forecast of the spot [spotId], opened at [date] (local to the spot) if given. */
    @Serializable
    data class SpotDetail(
        val spotId: String,
        val date: LocalDate? = null,
    ) : Destination
}

/**
 * How the back stack is saved. On iOS there is no reflection, so every [Destination] is registered here by hand; a new
 * screen that is missing fails to save on the first test that navigates to it.
 */
internal val navigationConfiguration: SavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Destination.Today::class, Destination.Today.serializer())
                    subclass(Destination.SpotDetail::class, Destination.SpotDetail.serializer())
                }
            }
    }
