package io.github.esseckers.pursuitofwind.feature.alerts.domain.repository

import io.github.esseckers.pursuitofwind.data.model.NotifiedWindow
import io.github.esseckers.pursuitofwind.data.model.Outcome
import io.github.esseckers.pursuitofwind.feature.alerts.domain.entity.AlertsSnapshotEntity
import kotlinx.coroutines.flow.Flow

/** What the background check for new session windows reads and keeps. No call throws, apart from cancellation. */
internal interface AlertsRepository {
    /** The rider's choice as stored and whenever it changes. While it cannot be read, reading is tried again. */
    val choice: Flow<Boolean>

    /** Whether the rider asked to be told about new windows; false when that cannot be read. */
    suspend fun enabled(): Boolean

    /**
     * Fetches the forecasts of the rider's favorite spots and returns them with the profile, or null when there is
     * nothing to decide on: no profile, or no favorite spot. A favorite whose forecast cannot be fetched is left out
     * and reported in the snapshot: the check goes on with the others, and never decides on a forecast it could not
     * bring up to date. Only a cache that cannot be read fails the call.
     */
    suspend fun fetch(): Outcome<AlertsSnapshotEntity?>

    /** The windows announced so far. */
    suspend fun notified(): Outcome<List<NotifiedWindow>>

    suspend fun saveNotified(windows: List<NotifiedWindow>): Outcome<Unit>
}
