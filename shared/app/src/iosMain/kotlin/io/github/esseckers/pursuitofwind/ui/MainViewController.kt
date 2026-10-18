package io.github.esseckers.pursuitofwind.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point for the iOS app: hosts [App] in a UIKit view controller (see iosApp/iosApp/ContentView.swift). */
@Suppress("FunctionName", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
