package io.github.esseckers.pursuitofwind.feature.spotmap.presentation.view

import io.github.esseckers.pursuitofwind.core.resources.Res
import io.github.esseckers.pursuitofwind.core.resources.water_e
import io.github.esseckers.pursuitofwind.core.resources.water_n
import io.github.esseckers.pursuitofwind.core.resources.water_ne
import io.github.esseckers.pursuitofwind.core.resources.water_nw
import io.github.esseckers.pursuitofwind.core.resources.water_s
import io.github.esseckers.pursuitofwind.core.resources.water_se
import io.github.esseckers.pursuitofwind.core.resources.water_sw
import io.github.esseckers.pursuitofwind.core.resources.water_w
import io.github.esseckers.pursuitofwind.core.ui.Compass
import org.jetbrains.compose.resources.StringResource

/** `Water to the west`: where a spot's water lies, from the pin. */
internal fun waterLabel(compass: Compass): StringResource =
    when (compass) {
        Compass.N -> Res.string.water_n
        Compass.NE -> Res.string.water_ne
        Compass.E -> Res.string.water_e
        Compass.SE -> Res.string.water_se
        Compass.S -> Res.string.water_s
        Compass.SW -> Res.string.water_sw
        Compass.W -> Res.string.water_w
        Compass.NW -> Res.string.water_nw
    }
