package io.github.esseckers.pursuitofwind.app

import androidx.compose.ui.window.ComposeUIViewController
import io.github.esseckers.pursuitofwind.app.di.startKoinOnce
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * Entry point for the iOS app: starts the dependency graph on first use and hosts [App] in a UIKit view controller
 * (see iosApp/iosApp/ContentView.swift).
 */
@Suppress("FunctionName", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController {
    startKoinOnce(platform = module { })
    return ComposeUIViewController { App() }
}
