package io.github.esseckers.pursuitofwind.app

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.app.navigation.PendingLinks
import org.koin.mp.KoinPlatform

/**
 * Hands the app a link it was opened with (`pursuitofwind://invite/…`, `pursuitofwind://spot/…`), from the platform's
 * entry point: an activity's intent on Android, a scene's URL on iOS. The app opens it once it can, which for a new
 * rider is after setting up. Anything else is ignored: links come from outside the app.
 *
 * @return whether [uri] is a link the app knows.
 */
fun openLink(uri: String): Boolean {
    val taken = KoinPlatform.getKoinOrNull()?.get<PendingLinks>()?.open(uri) ?: false
    // The text of a link the app does not know is someone else's: it stays out of the log.
    if (!taken) Logger.withTag("Links").i { "A link the app does not know was ignored" }
    return taken
}
