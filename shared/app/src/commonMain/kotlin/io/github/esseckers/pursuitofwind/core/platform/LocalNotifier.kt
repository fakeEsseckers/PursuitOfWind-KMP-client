package io.github.esseckers.pursuitofwind.core.platform

/**
 * A notification the app shows by itself, without a server.
 *
 * @property id stable for what the notification is about: showing another one with the same id replaces the first.
 * @property link what a tap opens, as a link the app reads (`AppLink`); null opens the app where it was.
 */
internal data class LocalNotification(
    val id: String,
    val title: String,
    val text: String,
    val link: String? = null,
)

/** Shows notifications in the system's notification area. Each platform binds its own in its Koin platform module. */
internal interface LocalNotifier {
    /** Shows [notification]. Without the rider's permission nothing shows and nothing fails. */
    suspend fun show(notification: LocalNotification)
}
