package io.github.esseckers.pursuitofwind.data.internal.local

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
