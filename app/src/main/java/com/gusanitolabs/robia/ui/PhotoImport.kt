package com.gusanitolabs.robia.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Copies in flight at once during a batch import: enough to hide storage latency, few enough not to thrash the disk. */
internal const val MAX_PARALLEL_PHOTO_COPIES = 4

/**
 * Copies the photos of a multi-pick, at most [MAX_PARALLEL_PHOTO_COPIES] at a time. The result keeps the picked order and
 * leaves out a photo whose copy fails. [onProgress] reports `(done, total)` after each photo, copied or failed.
 *
 * If the caller is cancelled, the copies already made are deleted through [deleteCopies] and the cancellation is rethrown.
 * [coroutineScope] waits for the copies already running, so the cleanup sees every copy that was made.
 */
internal suspend fun <Picked> copyPickedPhotos(
    picked: List<Picked>,
    copy: suspend (Picked) -> String,
    deleteCopies: (List<String>) -> Unit,
    onProgress: (done: Int, total: Int) -> Unit,
): List<String> {
    val copies = arrayOfNulls<String>(picked.size)
    val progressLock = Any()
    var done = 0
    try {
        coroutineScope {
            val slots = Semaphore(MAX_PARALLEL_PHOTO_COPIES)
            picked.forEachIndexed { index, photo ->
                launch {
                    slots.withPermit {
                        copies[index] = try {
                            copy(photo)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: Exception) {
                            null
                        }
                    }
                    synchronized(progressLock) { onProgress(++done, picked.size) }
                }
            }
        }
    } catch (thrown: Throwable) {
        deleteCopies(copies.filterNotNull())
        throw thrown
    }
    return copies.filterNotNull()
}
