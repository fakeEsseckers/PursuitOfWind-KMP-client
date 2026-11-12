package io.github.esseckers.pursuitofwind.core.platform

/**
 * Background checks on iOS need a `BGTaskScheduler` task registered by the app and listed in its `Info.plist`, which
 * the iOS app does not have yet. Until it does, this says so ([available] is false), and the profile does not offer
 * alerts it could not deliver.
 */
internal class IosBackgroundRefreshScheduler : BackgroundRefreshScheduler {
    override val available: Boolean = false

    override fun schedule() = Unit

    override fun cancel() = Unit
}
