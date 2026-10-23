package io.github.esseckers.pursuitofwind.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageTest {
    @Test
    fun `a stored tag reads back as its language and anything else follows the phone`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of("en"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of("ru"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.of("de"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.of(null))
    }
}
