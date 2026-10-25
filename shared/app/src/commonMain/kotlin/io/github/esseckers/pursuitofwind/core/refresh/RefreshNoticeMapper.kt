package io.github.esseckers.pursuitofwind.core.refresh

import io.github.esseckers.pursuitofwind.core.ui.Freshness
import io.github.esseckers.pursuitofwind.data.model.AppError
import kotlin.time.Duration

/** Which [RefreshNotice] a screen shows. */
internal object RefreshNoticeMapper {
    /**
     * The notice for a refresh that ended with [error]: offline for a connection problem, failed for any other one. With
     * no error, stale once what was fetched is older than [Freshness.STALE_AFTER] ([age], for screens that show it).
     */
    fun notice(
        error: AppError?,
        age: Duration? = null,
    ): RefreshNotice? =
        when {
            error == AppError.Network -> RefreshNotice.OFFLINE
            error != null -> RefreshNotice.REFRESH_FAILED
            age != null && age > Freshness.STALE_AFTER -> RefreshNotice.STALE
            else -> null
        }
}
