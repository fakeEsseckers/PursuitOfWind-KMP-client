package io.github.esseckers.pursuitofwind.app

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.app.navigation.PendingLinks
import io.github.esseckers.pursuitofwind.core.link.AppLink
import org.koin.mp.KoinPlatform

/**
 * Hands the app a link it was opened with (`pursuitofwind://invite/…`, `pursuitofwind://spot/…`), from the platform's
 * entry point: an activity's intent on Android, a scene's URL on iOS. The app opens it once it can, which for a new
 * rider is after setting up. Anything else is ignored: links come from outside the app.
 *
 * @return whether [uri] is a link the app knows.
 */
fun openLink(uri: String): Boolean {
    val koin = KoinPlatform.getKoinOrNull()
    val taken = if (koin != null) koin.get<PendingLinks>().open(uri) else EarlyLink.keep(uri)
    // The text of a link the app does not know is someone else's: it stays out of the log.
    if (!taken) Logger.withTag("Links").i { "A link the app does not know was ignored" }
    return taken
}

/**
 * A link handed over before the app's graph exists. On iOS a URL can arrive before the first screen starts the graph;
 * it is kept here, checked like any other, and passed on when the graph starts.
 */
internal object EarlyLink {
    private var uri: String? = null

    /** Keeps [uri] when it is a link the app knows, and says whether it did. */
    fun keep(uri: String): Boolean {
        val known = AppLink.parse(uri) != null
        if (known) this.uri = uri
        return known
    }

    /** The link that was kept, once. */
    fun take(): String? = uri.also { uri = null }
}
