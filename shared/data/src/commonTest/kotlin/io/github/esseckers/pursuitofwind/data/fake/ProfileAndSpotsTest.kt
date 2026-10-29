package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.MySpotListDto
import io.github.esseckers.pursuitofwind.contract.MySpotUpdateDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import io.github.esseckers.pursuitofwind.contract.SpotPageDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileAndSpotsTest {
    @Test
    fun `a profile is stored and returned and invalid ones are rejected with a reason`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()

            val missing = driver.call(ApiRoute.GET_PROFILE, token = token)
            val duplicateKite =
                driver.send(
                    ApiRoute.PUT_PROFILE,
                    token = token,
                    value = ALEX_PROFILE.copy(quiverM2 = listOf(9.0, 9.0)),
                )
            driver.send(ApiRoute.PUT_PROFILE, token = token, value = ALEX_PROFILE)
            val stored = driver.call(ApiRoute.GET_PROFILE, token = token)

            assertEquals(404, missing.status)
            assertEquals(422, duplicateKite.status)
            assertTrue(duplicateKite.problem().detail!!.contains("twice"))
            assertEquals(ALEX_PROFILE, stored.decode(RiderProfileDto.serializer()))
        }

    @Test
    fun `riding hours default to 9 to 21 and lie within the day and start before they end`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()
            val withoutHours =
                JsonObject(
                    ContractJson.encodeToJsonElement(RiderProfileDto.serializer(), ALEX_PROFILE).jsonObject - "ridingFromHour" -
                        "ridingToHour",
                ).toString()

            val reversed =
                driver.send(
                    ApiRoute.PUT_PROFILE,
                    token = token,
                    value = ALEX_PROFILE.copy(ridingFromHour = 21, ridingToHour = 9),
                )
            val noHours = driver.call(ApiRoute.PUT_PROFILE, token = token, body = withoutHours)
            val stored = driver.call(ApiRoute.GET_PROFILE, token = token)
            val outOfRange =
                listOf(-1 to 21, 24 to 25, 9 to 0, 9 to 25).map { (from, to) ->
                    driver
                        .send(
                            ApiRoute.PUT_PROFILE,
                            token = token,
                            value = ALEX_PROFILE.copy(ridingFromHour = from, ridingToHour = to),
                        ).status
                }
            driver.send(ApiRoute.PUT_PROFILE, token = token, value = ALEX_PROFILE.copy(ridingFromHour = 0, ridingToHour = 24))
            val wholeDay = driver.call(ApiRoute.GET_PROFILE, token = token).decode(RiderProfileDto.serializer())

            assertEquals(422, reversed.status)
            assertEquals("ridingFromHour must be below ridingToHour", reversed.problem().detail)
            assertEquals(listOf(422, 422, 422, 422), outOfRange)
            assertEquals(200, noHours.status)
            assertTrue("\"ridingFromHour\":9" in stored.body && "\"ridingToHour\":21" in stored.body, stored.body)
            assertEquals(0 to 24, wholeDay.ridingFromHour to wholeDay.ridingToHour)
        }

    @Test
    fun `the catalog pages through every spot exactly once`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()
            val ids = mutableListOf<String>()
            var cursor: String? = null

            do {
                val query = mapOf("limit" to "2") + listOfNotNull(cursor?.let { "cursor" to it })
                val page = driver.get(ApiRoute.LIST_SPOT_CATALOG, token = token, query = query).decode(SpotPageDto.serializer())
                ids += page.items.map { it.id }
                cursor = page.nextCursor
            } while (cursor != null)

            assertEquals(listOf("dune-point", "harbor-flats", "lake-weiss", "nordstrand-bay", "salt-lagoon"), ids)
        }

    @Test
    fun `following a spot stores drive time and favorite and unfollowing twice is fine`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()

            driver.send(
                ApiRoute.PUT_MY_SPOT,
                "lake-weiss",
                token = token,
                value = MySpotUpdateDto(45, true),
            )
            val followed =
                driver
                    .call(ApiRoute.LIST_MY_SPOTS, token = token)
                    .decode(MySpotListDto.serializer())
                    .items
                    .single()
            val first = driver.call(ApiRoute.DELETE_MY_SPOT, "lake-weiss", token = token)
            val second = driver.call(ApiRoute.DELETE_MY_SPOT, "lake-weiss", token = token)

            assertEquals("lake-weiss", followed.spot.id)
            assertEquals(45, followed.driveMinutes)
            assertEquals(listOf(204, 204), listOf(first.status, second.status))
            assertTrue(
                driver
                    .call(ApiRoute.LIST_MY_SPOTS, token = token)
                    .decode(MySpotListDto.serializer())
                    .items
                    .isEmpty(),
            )
        }

    @Test
    fun `unknown spots are 404 and out-of-range drive times are 422`() =
        runTest {
            val driver = fakeDriver()
            val token = driver.signIn()

            val unknown =
                driver.send(
                    ApiRoute.PUT_MY_SPOT,
                    "atlantis",
                    token = token,
                    value = MySpotUpdateDto(45, true),
                )
            val tooFar =
                driver.send(
                    ApiRoute.PUT_MY_SPOT,
                    "lake-weiss",
                    token = token,
                    value = MySpotUpdateDto(5000, true),
                )

            assertEquals(ProblemCode.NOT_FOUND, unknown.problem().code)
            assertEquals(422, tooFar.status)
            assertNull(driver.call(ApiRoute.LIST_SPOT_CATALOG, token = token).decode(SpotPageDto.serializer()).nextCursor)
        }
}
