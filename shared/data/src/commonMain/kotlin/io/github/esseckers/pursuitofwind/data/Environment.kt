package io.github.esseckers.pursuitofwind.data

/**
 * Where the app's data comes from, chosen at the entry point (`androidApp`, `iosApp`) and passed to the Koin graph.
 *
 * Each environment keeps its own database, settings and credentials ([storageName]), so data from the fake backend
 * never mixes with a real account.
 */
public sealed interface Environment {
    /** File-name stem of this environment's database, settings and secure store. */
    public val storageName: String

    /** The in-app fake backend ([io.github.esseckers.pursuitofwind.data.fake.FakePursuitOfWindServer]); no network. */
    public data object Fake : Environment {
        override val storageName: String = "pursuitofwind-fake"
    }

    /**
     * A real server implementing `api/openapi.yaml`. [baseUrl] is the origin only (`https://api.example.com`): request
     * paths come from the contract and start with `/v1/`, so a path in [baseUrl] would be replaced.
     */
    public data class Remote(
        val baseUrl: String,
    ) : Environment {
        override val storageName: String = "pursuitofwind"
    }
}
