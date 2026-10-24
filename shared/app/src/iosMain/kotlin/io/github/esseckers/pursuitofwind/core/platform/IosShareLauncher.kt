package io.github.esseckers.pursuitofwind.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/** The iOS share sheet ([UIActivityViewController]), presented over whatever the key window shows. */
internal class IosShareLauncher : ShareLauncher {
    @OptIn(ExperimentalForeignApi::class)
    override fun share(text: String) {
        val top = topViewController() ?: return
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        // On iPad the sheet is a popover: anchor it at the middle of the screen, without an arrow.
        sheet.popoverPresentationController?.let { popover ->
            popover.sourceView = top.view
            popover.sourceRect = top.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
            popover.permittedArrowDirections = 0u
        }
        top.presentViewController(sheet, animated = true, completion = null)
    }

    /** The controller on top of the key window. */
    private fun topViewController(): UIViewController? {
        val window =
            UIApplication.sharedApplication.connectedScenes
                .filterIsInstance<UIWindowScene>()
                .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
                .firstOrNull { it.isKeyWindow() }
        var top = window?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}
