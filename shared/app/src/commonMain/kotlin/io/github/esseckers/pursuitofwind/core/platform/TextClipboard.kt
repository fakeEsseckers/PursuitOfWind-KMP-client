package io.github.esseckers.pursuitofwind.core.platform

/** Puts text on the system clipboard. Each platform binds its own in its Koin platform module. */
internal interface TextClipboard {
    /**
     * Copies [text], under [label] where the system shows one. True when the system itself shows the rider what was
     * copied (Android 13 and later), so the app need not say so too.
     */
    fun copy(
        label: String,
        text: String,
    ): Boolean
}
