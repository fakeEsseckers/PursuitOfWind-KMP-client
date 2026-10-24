package io.github.esseckers.pursuitofwind.feature.today.domain.repository

import io.github.esseckers.pursuitofwind.feature.today.domain.entity.RefreshResultEntity
import io.github.esseckers.pursuitofwind.feature.today.domain.entity.TodaySnapshotEntity
import kotlinx.coroutines.flow.Flow

/** The data Today needs, and the refresh that updates it. Cached data is the source of truth. */
internal interface TodayRepository {
    /** The cached snapshot; emits again whenever any part of it changes. */
    val snapshot: Flow<TodaySnapshotEntity>

    /** Fetches everything Today shows from the server. Never throws, apart from cancellation. */
    suspend fun refresh(): RefreshResultEntity
}
