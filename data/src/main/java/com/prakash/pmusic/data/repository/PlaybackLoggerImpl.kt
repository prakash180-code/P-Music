package com.prakash.pmusic.data.repository

import android.content.Context
import com.prakash.pmusic.domain.model.PlaybackLogLevel
import com.prakash.pmusic.domain.repository.PlaybackLogger
import com.prakash.pmusic.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistent, rotated playback log stored in app-private storage.
 *
 * Writes to `filesDir/diagnostics/playback.log` (no storage permission, no
 * root). When the active file grows past [MAX_FILE_BYTES] it is rotated to
 * `playback.log.1` (previous `.1` → `.2`, dropping the oldest), so total disk
 * use is bounded (roughly 3 × [MAX_FILE_BYTES]). Logs survive activity/player/
 * service recreation and backgrounding because they live on disk.
 *
 * All writes are funneled through a single [Mutex] on the IO dispatcher so the
 * operator side never blocks the main thread and calls can be made from any
 * thread. Severe levels (WARN/ERROR) are always logged; DEBUG is gated by
 * [setDebugEnabled] (default off, matching the INFO default).
 */
@Singleton
class PlaybackLoggerImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val preferencesRepository: PreferencesRepository
) : PlaybackLogger {

    private val dir = File(context.filesDir, "diagnostics")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    init {
        // Respect the persisted "Playback Debug Logging" toggle: DEBUG is off
        // by default and only enabled when the user turns it on in Diagnostics.
        scope.launch {
            preferencesRepository.preferences.collect { prefs ->
                val target = if (prefs.playbackDebugLogging) PlaybackLogLevel.DEBUG else PlaybackLogLevel.INFO
                if (target != level) {
                    level = target
                    info("LOGGER", "LEVEL_APPLIED", "level=${target.label} source=preference")
                }
            }
        }
    }

    @Volatile
    override var level: PlaybackLogLevel = PlaybackLogLevel.INFO
        private set

    private val activeFile = File(dir, "playback.log")
    private val rotated1 = File(dir, "playback.log.1")
    private val rotated2 = File(dir, "playback.log.2")

    override fun setDebugEnabled(enabled: Boolean) {
        level = if (enabled) PlaybackLogLevel.DEBUG else PlaybackLogLevel.INFO
        info("LOGGER", "LEVEL_CHANGED", "level=${level.label}")
    }

    override fun log(level: PlaybackLogLevel, component: String, event: String, detail: String) {
        if (!level.isEnabledFor(this.level)) return
        val line = format(level, component, event, detail)
        scope.launch {
            mutex.withLock {
                runCatching {
                    ensureDir()
                    appendLine(line)
                    rotateIfNeeded()
                }
            }
        }
    }

    override fun error(component: String, event: String, th: Throwable) {
        val sb = StringBuilder()
        val name = th.javaClass.simpleName
        val message = th.message?.take(500).orEmpty()
        sb.append("$name | $message")
        th.stackTrace.take(20).forEach { sb.append("\n    at $it") }
        th.cause?.let { c ->
            sb.append("\n  caused by ${c.javaClass.simpleName}: ${c.message?.take(300).orEmpty()}")
            c.stackTrace.take(8).forEach { sb.append("\n    at $it") }
        }
        log(PlaybackLogLevel.ERROR, component, event, sb.toString())
    }

    override fun read(): String = runBlockingRead { activeFile.readText().trim() }

    override fun export(): String = runBlockingRead { mergeForExport() }

    override fun clear() {
        scope.launch {
            mutex.withLock {
                runCatching { activeFile.delete() }
                runCatching { rotated1.delete() }
                runCatching { rotated2.delete() }
            }
            info("LOGGER", "LOGS_CLEARED", "dir=${dir.path}")
        }
    }

    // ----- internals -----

    private fun ensureDir() {
        if (!dir.exists()) dir.mkdirs()
    }

    private fun appendLine(line: String) {
        if (activeFile.length() > 0) {
            activeFile.appendText("\n$line")
        } else {
            activeFile.writeText(line)
        }
    }

    private fun rotateIfNeeded() {
        if (activeFile.length() <= MAX_FILE_BYTES) return
        // Shift .2 -> (delete), .1 -> .2, active -> .1
        if (rotated2.exists()) rotated2.delete()
        if (rotated1.exists()) rotated1.renameTo(rotated2)
        val rotated = File(dir, "playback.log.rotated")
        if (activeFile.renameTo(rotated)) {
            rotated.renameTo(rotated1)
        } else {
            // Fallback: copy + truncate
            activeFile.copyTo(rotated1, overwrite = true)
            activeFile.delete()
        }
        activeFile.writeText("LOG_ROTATED at ${timestamp()}")
    }

    private fun mergeForExport(): String {
        val parts = mutableListOf<String>()
        if (rotated2.exists()) parts += rotated2.readText()
        if (rotated1.exists()) parts += rotated1.readText()
        if (activeFile.exists()) parts += activeFile.readText()
        return parts.joinToString("\n").trim()
    }

    private inline fun runBlockingRead(crossinline loader: () -> String): String =
        kotlinx.coroutines.runBlocking {
            mutex.withLock { runCatching { loader() }.getOrDefault("") }
        }

    private fun format(level: PlaybackLogLevel, component: String, event: String, detail: String): String {
        val stamp = timestamp()
        val d = detail.trim()
        return if (d.isEmpty()) {
            "$stamp | $level | $component | $event"
        } else {
            "$stamp | $level | $component | $event | $d"
        }
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private companion object {
        /** Rotation threshold per file (keep 2–5 MB). */
        const val MAX_FILE_BYTES = 3_000_000L
    }
}
