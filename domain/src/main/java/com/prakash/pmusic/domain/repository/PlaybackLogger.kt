package com.prakash.pmusic.domain.repository

import com.prakash.pmusic.domain.model.PlaybackLogLevel
import java.io.File

/**
 * Contract for the persistent, rotated playback diagnostic log.
 *
 * The implementation writes to app-private storage (no storage permission, no
 * root) and survives activity/player/service recreation and backgrounding. It
 * is the single source of truth for the "what actually happened" evidence we
 * use to diagnose background playback stops.
 *
 * Every entry carries: timestamp | level | component | event | detail.
 */
interface PlaybackLogger {

    /** Current minimum level. DEBUG can be enabled by the user. */
    val level: PlaybackLogLevel

    /** Enables or disables DEBUG-level verbosity (INFO is the default floor). */
    fun setDebugEnabled(enabled: Boolean)

    /** Logs at [level] from [component] describing [event] with [detail]. */
    fun log(
        level: PlaybackLogLevel,
        component: String,
        event: String,
        detail: String = ""
    )

    fun debug(component: String, event: String, detail: String = "") =
        log(PlaybackLogLevel.DEBUG, component, event, detail)

    fun info(component: String, event: String, detail: String = "") =
        log(PlaybackLogLevel.INFO, component, event, detail)

    fun warn(component: String, event: String, detail: String = "") =
        log(PlaybackLogLevel.WARN, component, event, detail)

    fun error(component: String, event: String, detail: String = "") =
        log(PlaybackLogLevel.ERROR, component, event, detail)

    /** Logs [th] with its full stack trace at ERROR level. */
    fun error(component: String, event: String, th: Throwable)

    /** Returns the current log content for on-screen viewing. */
    fun read(): String

    /** Merges all rotated files into a single export-friendly string. */
    fun export(): String

    /** Deletes the log contents (all rotated files). */
    fun clear()
}
