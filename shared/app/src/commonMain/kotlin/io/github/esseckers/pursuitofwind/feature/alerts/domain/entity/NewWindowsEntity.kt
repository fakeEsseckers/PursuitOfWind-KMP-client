package io.github.esseckers.pursuitofwind.feature.alerts.domain.entity

import io.github.esseckers.pursuitofwind.data.model.NotifiedWindow
import io.github.esseckers.pursuitofwind.engine.model.DayDecision

/**
 * What a check found: the GO days whose window the rider has not been told about ([fresh]), and the windows to keep as
 * announced once those are shown ([notified]).
 */
internal data class NewWindowsEntity(
    val fresh: List<DayDecision.Go>,
    val notified: List<NotifiedWindow>,
)
