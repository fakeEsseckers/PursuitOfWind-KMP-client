package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ApiLimits
import io.github.esseckers.pursuitofwind.contract.CreateCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.CrewDto
import io.github.esseckers.pursuitofwind.contract.CrewFeedDto
import io.github.esseckers.pursuitofwind.contract.CrewListDto
import io.github.esseckers.pursuitofwind.contract.CrewMemberListDto
import io.github.esseckers.pursuitofwind.contract.JoinCrewRequestDto
import io.github.esseckers.pursuitofwind.contract.MemberRoleDto
import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.ktor.http.HttpStatusCode

/** Crews, members and the crew feed. */
internal class CrewRoutes(
    private val state: FakeState,
) {
    fun create(call: FakeCall): FakeResponse {
        val name = call.decode(CreateCrewRequestDto.serializer()).name
        Validation.requireCrewName(name)
        val crew = Crew(state.nextId("crw"), name, newInviteCode(), call.now)
        state.crews[crew.id] = crew
        state.addMember(crew, call.userId, MemberRoleDto.OWNER, call.now, announce = false)
        return json(CrewDto.serializer(), state.crewDto(crew), HttpStatusCode.Created)
    }

    fun mine(call: FakeCall): FakeResponse {
        val crews =
            state.crews.values
                .filter { crew -> crew.members.any { it.userId == call.userId } }
                .sortedBy { it.createdAt }
        return json(CrewListDto.serializer(), CrewListDto(crews.map(state::crewDto)))
    }

    /** Codes are case-insensitive and surrounding spaces are ignored. Joining twice is not an error. */
    fun join(call: FakeCall): FakeResponse {
        val code = call.decode(JoinCrewRequestDto.serializer()).inviteCode
        Validation.requireNotEmpty(code, "inviteCode")
        val crew =
            state.crews.values.firstOrNull { it.inviteCode.equals(code.trim(), ignoreCase = true) }
                ?: throw problem(HttpStatusCode.NotFound, ProblemCode.INVITE_INVALID, "Invite code is not valid")
        if (crew.members.none { it.userId == call.userId }) {
            state.addMember(crew, call.userId, MemberRoleDto.MEMBER, call.now, announce = true)
        }
        return json(CrewDto.serializer(), state.crewDto(crew))
    }

    fun members(call: FakeCall): FakeResponse {
        val crew = memberCrew(call)
        return json(CrewMemberListDto.serializer(), CrewMemberListDto(crew.members.map(state::memberDto)))
    }

    /**
     * Without `since`: the newest events. With `since`: the events after it, oldest first, [CrewFeedDto.hasMore] when
     * the page is full. An unknown `since` (for example after the server was reset) is `410 FEED_RESET`.
     */
    fun feed(call: FakeCall): FakeResponse {
        val crew = memberCrew(call)
        val limit = call.intQuery("limit", LIMITS, DEFAULT_LIMIT)
        val since = call.query["since"]
        val remaining =
            if (since == null) {
                crew.events.takeLast(limit)
            } else {
                val position = crew.events.indexOfFirst { it.id == since }
                if (position < 0) throw problem(HttpStatusCode.Gone, ProblemCode.FEED_RESET, "Feed position is no longer available")
                crew.events.drop(position + 1)
            }
        val page = remaining.take(limit)
        return json(CrewFeedDto.serializer(), CrewFeedDto(page, hasMore = remaining.size > limit, lastEventId = page.lastOrNull()?.id))
    }

    private fun memberCrew(call: FakeCall): Crew = state.crew(call.pathParameter("crewId")).also { state.requireMember(it, call.userId) }

    private fun newInviteCode(): String =
        generateSequence {
            "WIND-" +
                (1..INVITE_LENGTH).map { INVITE_ALPHABET[state.inviteCodes.nextInt(INVITE_ALPHABET.length)] }.joinToString("")
        }.first { code -> state.crews.values.none { it.inviteCode.equals(code, ignoreCase = true) } }

    private companion object {
        /** No 0/O or 1/I, so codes survive being read out loud. */
        const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val INVITE_LENGTH = 4
        const val DEFAULT_LIMIT = 50
        val LIMITS = 1..ApiLimits.Paging.LIMIT_MAX
    }
}
