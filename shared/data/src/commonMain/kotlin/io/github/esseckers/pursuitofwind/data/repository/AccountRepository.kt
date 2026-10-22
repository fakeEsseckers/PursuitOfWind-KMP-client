package io.github.esseckers.pursuitofwind.data.repository

import io.github.esseckers.pursuitofwind.data.internal.network.AuthSession
import io.github.esseckers.pursuitofwind.data.internal.network.outcome
import io.github.esseckers.pursuitofwind.data.model.Outcome
import kotlinx.coroutines.flow.StateFlow

/** The anonymous account of this installation. There is no sign-up: the first call signs in. */
public class AccountRepository internal constructor(
    private val session: AuthSession,
) {
    /** The signed-in user, null until the first sign-in; screens use it to tell "me" from other crew members. */
    public val userId: StateFlow<String?> = session.userId

    /** Signs in unless a session exists and returns the user id. Other calls sign in on their own when needed. */
    public suspend fun signIn(): Outcome<String> = outcome { session.tokens().userId }
}
