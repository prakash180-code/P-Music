package com.prakash.pmusic.domain.model

/**
 * Current state of the background library scan, exposed to the UI so it can
 * show progress and the resulting library size.
 */
sealed interface LibraryScanState {
    /** No scan has run yet in this process. */
    data object Idle : LibraryScanState

    /** A scan is currently in progress. */
    data object Scanning : LibraryScanState

    /** The app does not have media-read permission. */
    data object NoPermission : LibraryScanState

    /** Scan finished; [songCount] songs were indexed, [removedCount] were dropped. */
    data class Complete(val songCount: Int, val removedCount: Int) : LibraryScanState

    /** Scan failed with a human-readable [message]. */
    data class Failed(val message: String) : LibraryScanState
}
