package io.github.esseckers.pursuitofwind.data.internal.local

import co.touchlab.kermit.Logger
import io.github.esseckers.pursuitofwind.data.internal.network.toAppError
import io.github.esseckers.pursuitofwind.data.model.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * A failure to read or write what this device keeps (DataStore, the Keychain, the Android Keystore). DataStore fails
 * with the same `IOException` as the network does; wrapped in this, it becomes `AppError.Storage`, not "no connection".
 */
internal class StorageException(
    cause: Throwable,
) : Exception(cause.message, cause)

/** Runs [block], which reads or writes local storage; any failure but cancellation comes out as a [StorageException]. */
@Suppress("TooGenericExceptionCaught") // Whatever the platform's store throws, it is a storage failure.
internal suspend inline fun <T> storage(block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: StorageException) {
        throw e
    } catch (e: Exception) {
        throw StorageException(e)
    }

/** This flow of stored values, with a failure to read them as a [StorageException]. */
internal fun <T> Flow<T>.storageErrors(): Flow<T> =
    catch { failure -> throw if (failure is CancellationException || failure is StorageException) failure else StorageException(failure) }

/**
 * Writes the server's answer to a change into the cache with [write]. The server has made the change already, so this
 * device failing to keep the answer does not fail the change: reported as a failure, it would invite a retry that makes
 * the change again (a second ride or crew). The failure is logged and the next sync caches what the server holds. Any
 * other failure is a bug and is thrown.
 */
@Suppress("TooGenericExceptionCaught") // Only a storage failure is let through; everything else is rethrown.
internal suspend inline fun cacheAnswer(write: () -> Unit) {
    try {
        write()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e.toAppError() != AppError.Storage) throw e
        Logger.withTag("Cache").w(e) { "The server's answer to a change is not cached" }
    }
}
