package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TourTest {
    @Test
    fun `the tour reaches every route of the contract`() =
        runTest {
            val driver = fakeDriver()

            driver.tourEveryRoute()

            assertEquals(ApiRoute.entries.toSet(), driver.exchanges.map { it.route }.toSet())
        }
}
