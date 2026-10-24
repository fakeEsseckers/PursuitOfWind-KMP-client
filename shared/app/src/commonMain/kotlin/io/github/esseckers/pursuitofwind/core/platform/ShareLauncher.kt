package io.github.esseckers.pursuitofwind.core.platform

/** Opens the system share sheet. Each platform binds its own in its Koin platform module. */
internal interface ShareLauncher {
    /** Offers [text] to the apps that take text: messengers, mail, notes. */
    fun share(text: String)
}
