package io.github.esseckers.pursuitofwind.feature.spots.presentation.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SpotDriveTest {
    @Test
    fun `a drive steps by a quarter hour up to a day trip and down to zero`() {
        assertEquals(135, SpotDrive.longer(120))
        assertEquals(720, SpotDrive.longer(710))
        assertEquals(720, SpotDrive.longer(720))
        assertEquals(900, SpotDrive.longer(900))
        assertEquals(105, SpotDrive.shorter(120))
        assertEquals(0, SpotDrive.shorter(10))
        assertEquals(885, SpotDrive.shorter(900))
    }
}
