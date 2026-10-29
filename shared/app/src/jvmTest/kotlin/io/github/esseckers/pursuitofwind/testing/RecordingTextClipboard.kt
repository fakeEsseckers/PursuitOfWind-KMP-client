package io.github.esseckers.pursuitofwind.testing

import io.github.esseckers.pursuitofwind.core.platform.TextClipboard

/**
 * The clipboard in tests: keeps what was copied, and answers whether the system shows it ([systemShows], as Android 13
 * and later do) or says nothing, as iOS does.
 */
internal class RecordingTextClipboard : TextClipboard {
    val copied = mutableListOf<Pair<String, String>>()
    var systemShows = false

    override fun copy(
        label: String,
        text: String,
    ): Boolean {
        copied += label to text
        return systemShows
    }
}
