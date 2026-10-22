package io.github.esseckers.pursuitofwind.data.di

import io.github.esseckers.pursuitofwind.contract.PlatformDto
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module

/**
 * Per platform: the secure token store, the database file and the settings file, all named after [storageName], so
 * each environment keeps its own.
 */
internal expect fun platformDataModule(storageName: String): Module

/** The engine for a real server: OkHttp on Android, Darwin (`NSURLSession`) on iOS. */
internal expect fun remoteHttpEngine(): HttpClientEngine

/** Sent with the anonymous sign-in. */
internal expect val currentPlatform: PlatformDto
