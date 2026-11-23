package io.github.esseckers.pursuitofwind.feature.alerts.domain.entity

/** How a background check ended, for the platform's scheduler. */
internal enum class AlertsCheckResultEntity {
    /** Nothing more to do until the next scheduled check. */
    DONE,

    /** The forecast could not be fetched or kept: worth another try before the next scheduled check. */
    RETRY,
}
