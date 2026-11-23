package io.github.esseckers.pursuitofwind.app

import androidx.compose.ui.window.ComposeUIViewController
import io.github.esseckers.pursuitofwind.app.di.startKoinOnce
import io.github.esseckers.pursuitofwind.app.language.AppLocale
import io.github.esseckers.pursuitofwind.app.language.IosAppLocale
import io.github.esseckers.pursuitofwind.core.map.MapLibreSpotMap
import io.github.esseckers.pursuitofwind.core.map.SpotMap
import io.github.esseckers.pursuitofwind.core.platform.BackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.core.platform.IosBackgroundRefreshScheduler
import io.github.esseckers.pursuitofwind.core.platform.IosShareLauncher
import io.github.esseckers.pursuitofwind.core.platform.IosTextClipboard
import io.github.esseckers.pursuitofwind.core.platform.NoPushToken
import io.github.esseckers.pursuitofwind.core.platform.PushTokenSource
import io.github.esseckers.pursuitofwind.core.platform.ShareLauncher
import io.github.esseckers.pursuitofwind.core.platform.TextClipboard
import io.github.esseckers.pursuitofwind.data.Environment
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/**
 * Entry point for the iOS app: starts the dependency graph on first use and hosts [App] in a UIKit view controller
 * (see iosApp/iosApp/ContentView.swift).
 */
@Suppress("FunctionName", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController {
    startKoinOnce(
        // Set per build with API_BASE_URL in Configuration/Config.xcconfig; empty runs the in-app fake backend.
        environment = Environment.of(NSBundle.mainBundle.objectForInfoDictionaryKey(API_BASE_URL_KEY) as? String),
        platform =
            module {
                single<ShareLauncher> { IosShareLauncher() }
                single<AppLocale> { IosAppLocale() }
                single<SpotMap> { MapLibreSpotMap() }
                single<TextClipboard> { IosTextClipboard() }
                single<BackgroundRefreshScheduler> { IosBackgroundRefreshScheduler() }
                single<PushTokenSource> { NoPushToken }
            },
    )
    return ComposeUIViewController { App() }
}

/** The key in `Info.plist` that holds the server's origin for this build. */
private const val API_BASE_URL_KEY = "ApiBaseUrl"
