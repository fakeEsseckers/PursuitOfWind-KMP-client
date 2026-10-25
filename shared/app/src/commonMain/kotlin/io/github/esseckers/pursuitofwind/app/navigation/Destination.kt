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
    /** The first screen of a new rider: what the app does, and the way into setting it up. */
    @Serializable
    data object Welcome : Destination

    /** Onboarding, step one: the rider's profile, with "Continue" to the spots. */
    @Serializable
    data object SetUpProfile : Destination

    /** Choosing favorite spots and drive times: onboarding step two, or opened from Today or the Spots tab. */
    @Serializable
    data object PickSpots : Destination

    /** Tab: the answer for today and the next days. The root of the app once the rider is set up. */
    @Serializable
    data object Today : Destination

    /** Tab: the spots the rider follows, changed in place. */
    @Serializable
    data object Spots : Destination

    /** Tab: the rider's crews, their rides and seats. */
    @Serializable
    data object Crew : Destination

    /** Tab: the rider's profile, saved in place. */
    @Serializable
    data object Profile : Destination

    /**
     * "I'm going": the form that offers the crew a ride to [spotId] for the window from [windowStart] to [windowEnd].
     * [departAt] is the time to leave, which may have passed; the form decides the departure. Times are epoch seconds,
     * so the key stays plain.
     */
    @Serializable
    data class OfferRide(
        val spotId: String,
        val windowStart: Long,
        val windowEnd: Long,
        val departAt: Long,
    ) : Destination

    /** The hourly forecast of the spot [spotId], opened at [date] (local to the spot) if given. */
    @Serializable
    data class SpotDetail(
        val spotId: String,
        val date: LocalDate? = null,
    ) : Destination
}

/** The app's tabs, in the bar's order, and the screen each one shows. Every tab's screen carries the tab bar. */
internal enum class AppTab(
    val destination: Destination,
) {
    TODAY(Destination.Today),
    SPOTS(Destination.Spots),
    CREW(Destination.Crew),
    PROFILE(Destination.Profile),
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
                    subclass(Destination.Welcome::class, Destination.Welcome.serializer())
                    subclass(Destination.SetUpProfile::class, Destination.SetUpProfile.serializer())
                    subclass(Destination.PickSpots::class, Destination.PickSpots.serializer())
                    subclass(Destination.Today::class, Destination.Today.serializer())
                    subclass(Destination.Spots::class, Destination.Spots.serializer())
                    subclass(Destination.Crew::class, Destination.Crew.serializer())
                    subclass(Destination.Profile::class, Destination.Profile.serializer())
                    subclass(Destination.OfferRide::class, Destination.OfferRide.serializer())
                    subclass(Destination.SpotDetail::class, Destination.SpotDetail.serializer())
                }
            }
    }
