package io.github.esseckers.pursuitofwind.engine.model

import kotlinx.datetime.TimeZone
import kotlin.jvm.JvmInline
import kotlin.time.Duration

/** Stable identifier of a spot. Ordering is used only to break ties deterministically. */
@JvmInline
public value class SpotId(
    public val value: String,
) : Comparable<SpotId> {
    init {
        require(value.isNotBlank()) { "Spot id must not be blank" }
    }

    override fun compareTo(other: SpotId): Int = value.compareTo(other.value)

    override fun toString(): String = value
}

/**
 * A spot as the engine sees it.
 *
 * @property timeZone IANA zone of the spot. Day boundaries and local times are computed in it.
 * @property rideableSectors wind directions that work here. Must not be empty.
 * @property dangerSectors directions that are dangerous here (typically offshore). They win over rideable sectors.
 * @property drive one-way drive from the rider's home.
 */
public data class SpotConfig(
    public val id: SpotId,
    public val name: String,
    public val timeZone: TimeZone,
    public val rideableSectors: List<Sector>,
    public val dangerSectors: List<Sector> = emptyList(),
    public val drive: Duration,
) {
    init {
        require(rideableSectors.isNotEmpty()) { "Spot $id needs at least one rideable sector" }
        require(!drive.isNegative()) { "Drive to spot $id must not be negative, was $drive" }
    }
}
