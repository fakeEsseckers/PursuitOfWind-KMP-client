package io.github.esseckers.pursuitofwind.data.fake

import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.CrewFeedDto
import io.github.esseckers.pursuitofwind.contract.JoinCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.MemberJoinedEventDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.RideCreatedEventDto
import io.github.esseckers.pursuitofwind.contract.SeatReleasedEventDto
import io.github.esseckers.pursuitofwind.contract.SeatTakenEventDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeedTest {
    private suspend fun FakeApiDriver.feed(
        token: String,
        vararg query: Pair<String, String>,
    ): CrewFeedDto = get(ApiRoute.GET_CREW_FEED, "crw_weekend", token = token, query = query.toMap()).decode(CrewFeedDto.serializer())

    @Test
    fun `the seeded crew has a history and new actions append to it`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn()
            driver.send(
                ApiRoute.JOIN_CREW,
                token = alex,
                value = JoinCrewRequestDto("WIND-4KQ7"),
            )
            driver.call(ApiRoute.TAKE_SEAT, "rid_7f3c", token = alex)
            driver.call(ApiRoute.RELEASE_SEAT, "rid_7f3c", token = alex)

            val events = driver.feed(alex).items

            assertEquals(
                listOf("MemberJoined", "MemberJoined", "RideCreated", "SeatTaken", "MemberJoined", "SeatTaken", "SeatReleased"),
                events.map { it::class.simpleName!!.removeSuffix("EventDto") },
            )
            assertIs<RideCreatedEventDto>(events[2])
            assertTrue((events[4] as MemberJoinedEventDto).actor.displayName.startsWith("Rider "))
            assertEquals(1, (events[5] as SeatTakenEventDto).seatsFree)
            assertEquals(2, (events.last() as SeatReleasedEventDto).seatsFree)
            assertEquals(events.map { it.id }.sorted(), events.map { it.id })
        }

    @Test
    fun `pages follow since and hasMore until caught up`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn()
            driver.send(
                ApiRoute.JOIN_CREW,
                token = alex,
                value = JoinCrewRequestDto("WIND-4KQ7"),
            )
            val all = driver.feed(alex).items

            val first = driver.feed(alex, "since" to all.first().id, "limit" to "2")
            val second = driver.feed(alex, "since" to first.lastEventId!!, "limit" to "2")
            val caughtUp = driver.feed(alex, "since" to all.last().id)

            assertEquals(all.subList(1, 3), first.items)
            assertTrue(first.hasMore)
            assertEquals(all.subList(3, 5), second.items)
            assertFalse(second.hasMore)
            assertEquals(emptyList(), caughtUp.items)
            assertEquals(null, caughtUp.lastEventId)
        }

    @Test
    fun `an unknown position asks the client to reload`() =
        runTest {
            val driver = fakeDriver()
            val alex = driver.signIn()
            driver.send(
                ApiRoute.JOIN_CREW,
                token = alex,
                value = JoinCrewRequestDto("WIND-4KQ7"),
            )

            val reset = driver.get(ApiRoute.GET_CREW_FEED, "crw_weekend", token = alex, query = mapOf("since" to "evt_999999"))

            assertEquals(410, reset.status)
            assertEquals(ProblemCode.FEED_RESET, reset.problem().code)
        }
}
