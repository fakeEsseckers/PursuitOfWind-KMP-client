package io.github.esseckers.pursuitofwind.core.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/** The Android clipboard. From Android 13 the system shows what was copied, so the app says nothing then. */
internal class AndroidTextClipboard(
    private val context: Context,
) : TextClipboard {
    override fun copy(
        label: String,
        text: String,
    ): Boolean {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
