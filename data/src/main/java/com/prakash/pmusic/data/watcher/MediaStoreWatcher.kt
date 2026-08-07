package com.prakash.pmusic.data.watcher

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.prakash.pmusic.domain.repository.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Watches the MediaStore audio catalogue for changes and re-scans the library
 * automatically, so songs that are added or removed on the device appear or
 * disappear without any manual refresh.
 *
 * Notifications are debounced (MediaStore fires one per touched row during a
 * bulk copy) and the scan mutex inside [LibraryRepository.scanLibrary] makes
 * a concurrent pass a no-op, so bursts collapse into a single scan.
 */
@Singleton
class MediaStoreWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository
) {

    private val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // A new scan is scheduled by the collection below; this call only
            // pushes an event and never blocks the main thread.
            changeEvents.tryEmit(Unit)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null

    /**
     * Registers the observer and starts the debounced collector. Safe to call
     * more than once (idempotent); the observer lives for the process.
     */
    fun start() {
        if (collectJob != null) return
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        collectJob = scope.launch {
            changeEvents
                .debounce(DEBOUNCE_MILLIS)
                .collect {
                    libraryRepository.scanLibrary(force = true)
                }
        }
    }

    /** Unregisters the observer and cancels the collector (process teardown). */
    fun stop() {
        collectJob?.cancel()
        collectJob = null
        context.contentResolver.unregisterContentObserver(observer)
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 2_000L
    }
}
