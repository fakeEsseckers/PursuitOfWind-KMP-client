package io.github.esseckers.pursuitofwind.data.internal.network

import io.github.esseckers.pursuitofwind.contract.AnonymousSignInRequestDto
import io.github.esseckers.pursuitofwind.contract.ApiRoute
import io.github.esseckers.pursuitofwind.contract.AuthTokensDto
import io.github.esseckers.pursuitofwind.contract.ContractJson
import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.github.esseckers.pursuitofwind.contract.RefreshTokensRequestDto
import io.github.esseckers.pursuitofwind.data.auth.SecureTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * The anonymous session: signs in on first use, refreshes on `401`, and signs in again with the same installation id
 * when a refresh is refused (the server then returns the same user). Every step runs under one [Mutex], so parallel
 * requests that hit `401` together cause one refresh; the others pick up its result.
 *
 * @param onSignedIn called after every sign-in, before the tokens are stored, with the user id and whether the server
 *   created that user just now (`201`); caches of another user, or of a server that forgot its users, are dropped.
 */
internal class AuthSession(
    private val client: () -> HttpClient,
    private val store: SecureTokenStore,
    private val platform: PlatformDto,
    private val onSignedIn: suspend (userId: String, newUser: Boolean) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private val signedInUser = MutableStateFlow<String?>(null)

    /** The signed-in user, null until the first sign-in or restore. */
    val userId: StateFlow<String?> = signedInUser.asStateFlow()

    /** Stored tokens, or a fresh sign-in when there are none. */
    suspend fun tokens(): AuthTokensDto = mutex.withLock { stored() ?: signIn() }

    /**
     * Tokens to retry with after [staleAccessToken] was refused. If another request refreshed meanwhile, its tokens are
     * returned without asking the server again.
     */
    suspend fun refresh(staleAccessToken: String?): AuthTokensDto =
        mutex.withLock {
            val current = stored()
            when {
                current == null -> signIn()
                current.accessToken != staleAccessToken -> current
                else -> refreshed(current) ?: signIn()
            }
        }

    /** Forgets the tokens; the installation id stays, so the next sign-in returns the same user. */
    suspend fun clear() {
        mutex.withLock {
            store.delete(TOKENS)
            signedInUser.value = null
        }
    }

    private suspend fun stored(): AuthTokensDto? =
        store
            .read(TOKENS)
            ?.let { runCatching { ContractJson.decodeFromString(AuthTokensDto.serializer(), it) }.getOrNull() }
            ?.also { signedInUser.value = it.userId }

    private suspend fun refreshed(current: AuthTokensDto): AuthTokensDto? {
        val response =
            client().request(ApiRoute.REFRESH_TOKENS.path) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(RefreshTokensRequestDto(current.refreshToken))
            }
        return when {
            response.status.isSuccess() -> save(response.body())
            response.status.value.isRefusal() -> null
            else -> throw ApiException.from(response)
        }
    }

    private suspend fun signIn(): AuthTokensDto {
        val response =
            client().request(ApiRoute.SIGN_IN_ANONYMOUSLY.path) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(AnonymousSignInRequestDto(installationId(), platform))
            }
        if (!response.status.isSuccess()) throw ApiException.from(response)
        val tokens = response.body<AuthTokensDto>()
        onSignedIn(tokens.userId, response.status.value == CREATED)
        return save(tokens)
    }

    /**
     * The installation id is the account: it is created once, when the store says there is none. A store that fails to
     * read throws instead of answering null, so a passing failure never replaces it.
     */
    private suspend fun installationId(): String =
        store.read(INSTALLATION_ID) ?: Uuid.random().toString().also { store.write(INSTALLATION_ID, it) }

    private suspend fun save(tokens: AuthTokensDto): AuthTokensDto {
        store.write(TOKENS, ContractJson.encodeToString(AuthTokensDto.serializer(), tokens))
        signedInUser.value = tokens.userId
        return tokens
    }

    private companion object {
        const val TOKENS = "session"
        const val INSTALLATION_ID = "installation_id"
        const val CREATED = 201

        /** A `4xx` answer to a refresh means the refresh token is no good: sign in again. Not a timeout or rate limit. */
        fun Int.isRefusal(): Boolean = this in CLIENT_ERRORS && this != REQUEST_TIMEOUT && this != TOO_MANY_REQUESTS

        val CLIENT_ERRORS = 400..499

        const val REQUEST_TIMEOUT = 408
        const val TOO_MANY_REQUESTS = 429
    }
}
