package io.github.esseckers.pursuitofwind.core.ui

/** The eight main compass points: where the wind comes from, or where a spot's water lies. */
internal enum class Compass {
    N,
    NE,
    E,
    SE,
    S,
    SW,
    W,
    NW,
    ;

    companion object {
        private const val SECTOR_DEG = 45.0
        private const val FULL_CIRCLE_DEG = 360.0

        /** The compass point nearest to [bearingDeg]; each covers 45° around its direction. */
        fun of(bearingDeg: Double): Compass {
            val normalized = ((bearingDeg % FULL_CIRCLE_DEG) + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG
            return entries[((normalized + SECTOR_DEG / 2) / SECTOR_DEG).toInt() % entries.size]
        }
    }
}
