package com.prakash.pmusic.features.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.prakash.pmusic.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App Widget host for the playback widget.
 *
 * Broadcast receiver instances are recreated per delivery, so all state lives
 * in the process-wide [PlaybackWidgetManager] singleton: it holds the
 * [MediaController] bound to [PlaybackService], re-renders on every player
 * event, ticks the progress bar while playing, and decodes album art on a
 * background thread.
 */
class PlaybackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlaybackWidgetManager.onWidgetUpdate(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        PlaybackWidgetManager.onWidgetRemoved(context)
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        PlaybackWidgetManager.onWidgetRemoved(context)
        super.onDisabled(context)
    }
}

/** Process-wide widget state and rendering. See [PlaybackWidgetProvider]. */
object PlaybackWidgetManager {

    private const val TAG = "PMusicWidget"
    private const val PROGRESS_TICK_MS = 1000L
    private const val MAX_ARTWORK_PX = 256

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var appContext: Context? = null
    private var controller: MediaController? = null
    private var connecting = false
    private var lastArtworkUri: Uri? = null
    private var widgetCount = 0
    private var tickerScheduled = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            render()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            render()
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            tickerScheduled = false
            val player = controller
            if (player != null && player.isPlaying && widgetCount > 0) {
                render()
            }
        }
    }

    fun onWidgetUpdate(context: Context) {
        appContext = context.applicationContext
        widgetCount = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
            .size
        Log.d(TAG, "onUpdate: $widgetCount widget(s)")
        render()
        ensureConnected()
    }

    /**
     * Wakes the session connection whenever the app's own process starts.
     * Widget update broadcasts are not guaranteed after a package update or a
     * background kill, so this keeps the widget live while the app runs.
     */
    fun onAppStart(context: Context) {
        appContext = context.applicationContext
        if (widgetCount <= 0) {
            widgetCount = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
                .size
        }
        if (widgetCount <= 0) return
        Log.d(TAG, "app start: connecting to session")
        render()
        ensureConnected()
    }

    fun onWidgetRemoved(context: Context) {
        val remaining = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
            .size
        if (remaining == 0) {
            widgetCount = 0
            mainHandler.removeCallbacks(progressTicker)
            tickerScheduled = false
            controller?.removeListener(playerListener)
            controller?.release()
            controller = null
            lastArtworkUri = null
            Log.d(TAG, "released session controller (no widgets left)")
        } else {
            widgetCount = remaining
        }
    }

    private fun ensureConnected() {
        if (controller != null || connecting) return
        val context = appContext ?: return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        connecting = true
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                connecting = false
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                Log.d(TAG, "connected to session")
                connected.addListener(playerListener)
                controller = connected
                render()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun render() {
        val context = appContext ?: return
        if (widgetCount <= 0) return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
        if (ids.isEmpty()) {
            widgetCount = 0
            return
        }
        val player = controller
        val views = if (player != null && player.mediaItemCount > 0) {
            PlaybackWidget.build(context, player)
        } else {
            PlaybackWidget.buildIdle(context)
        }
        manager.updateAppWidget(ids, views)
        scheduleProgressTicker()
        loadArtwork()
    }

    private fun scheduleProgressTicker() {
        val player = controller ?: return
        if (!player.isPlaying || tickerScheduled) return
        tickerScheduled = true
        mainHandler.postDelayed(progressTicker, PROGRESS_TICK_MS)
    }

    private fun loadArtwork() {
        val context = appContext ?: return
        val uri = PlaybackWidget.artworkOf(controller)
        if (uri == lastArtworkUri) return
        lastArtworkUri = uri
        if (uri == null) return

        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
        scope.launch {
            val bitmap = decodeScaledBitmap(context, uri, MAX_ARTWORK_PX)
            if (bitmap == null) return@launch
            withContext(Dispatchers.Main) {
                val views = RemoteViews(context.packageName, R.layout.widget_playback)
                views.setImageViewBitmap(R.id.widget_artwork, bitmap)
                manager.updateAppWidget(ids, views)
            }
        }
    }

    /** Bounds-aware decode of a local artwork file so the widget stays light. */
    private fun decodeScaledBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
            sampleSize *= 2
        }
        val full = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, full)
        }
    }.getOrNull()
}
