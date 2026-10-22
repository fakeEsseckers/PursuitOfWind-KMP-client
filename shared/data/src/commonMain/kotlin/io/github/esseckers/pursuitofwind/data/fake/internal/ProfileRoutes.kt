package io.github.esseckers.pursuitofwind.data.fake.internal

import io.github.esseckers.pursuitofwind.contract.ProblemCode
import io.github.esseckers.pursuitofwind.contract.PushTokenRequestDto
import io.github.esseckers.pursuitofwind.contract.RiderProfileDto
import io.ktor.http.HttpStatusCode

/** The rider's own data: profile and push token. */
internal class ProfileRoutes(
    private val state: FakeState,
) {
    fun get(call: FakeCall): FakeResponse {
        val profile =
            state.profiles[call.userId]
                ?: throw problem(HttpStatusCode.NotFound, ProblemCode.NOT_FOUND, "Profile not found", "Save a profile first.")
        return json(RiderProfileDto.serializer(), profile)
    }

    fun put(call: FakeCall): FakeResponse {
        val profile = call.decode(RiderProfileDto.serializer())
        Validation.profile(profile)
        state.profiles[call.userId] = profile
        return json(RiderProfileDto.serializer(), profile)
    }

    fun putPushToken(call: FakeCall): FakeResponse {
        val request = call.decode(PushTokenRequestDto.serializer())
        Validation.requireNotEmpty(request.token, "token")
        state.pushTokens[call.userId] = request
        return noContent()
    }
}
