package io.github.esseckers.pursuitofwind.core.platform

import platform.UIKit.UIPasteboard

/** The iOS general pasteboard. iOS says nothing when an app copies, so the app does. */
internal class IosTextClipboard : TextClipboard {
    override fun copy(
        label: String,
        text: String,
    ): Boolean {
        UIPasteboard.generalPasteboard.string = text
        return false
    }
}
