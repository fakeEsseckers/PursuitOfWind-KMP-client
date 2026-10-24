package io.github.esseckers.pursuitofwind.testing

import io.github.esseckers.pursuitofwind.core.platform.ShareLauncher

/** The share sheet in tests: keeps what would have been shared. */
internal class RecordingShareLauncher : ShareLauncher {
    val shared = mutableListOf<String>()

    override fun share(text: String) {
        shared += text
    }
}
