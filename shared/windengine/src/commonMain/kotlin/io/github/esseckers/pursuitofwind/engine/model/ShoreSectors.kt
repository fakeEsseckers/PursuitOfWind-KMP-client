package io.github.esseckers.pursuitofwind.engine.model

/**
 * The wind sectors of a spot on a shore, worked out from the direction of its open water
 * ([io.github.esseckers.pursuitofwind.engine.DecisionEngine.sectorsFacing]).
 *
 * @property rideable wind from the water and along the shore: onshore, side-onshore and side-shore.
 * @property danger wind from the land, which blows a rider away from the shore: offshore and side-offshore.
 */
public data class ShoreSectors(
    public val rideable: Sector,
    public val danger: Sector,
)
