package com.prakash.pmusic.features.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.prakash.pmusic.features.widgets.WidgetFormat.formatPosition
import com.prakash.pmusic.features.widgets.WidgetFormat.playPauseIcon
import com.prakash.pmusic.features.widgets.WidgetFormat.progressFraction
import com.prakash.pmusic.service.PlaybackService

/**
 * Builds the playback widget's [RemoteViews] from a connected [Player] and
 * the PendingIntents that drive it.
 *
 * Tap targets:
 * - Artwork/title open the app (launch intent).
 * - The transport buttons are service PendingIntents that [PlaybackService]
 *   handles in onStartCommand, so the widget works whether or not the app UI
 *   is in the foreground.
 */
object PlaybackWidget {

    private const val REQUEST_CODE_OPEN = 100
    private const val REQUEST_CODE_PLAY_PAUSE = 101
    private const val REQUEST_CODE_NEXT = 102
    private const val REQUEST_CODE_PREVIOUS = 103

    /** The artwork URI of the current song, or null when nothing is loaded. */
    fun artworkOf(player: Player?): Uri? = player?.currentMediaItem?.mediaMetadata?.artworkUri

    /** Views for the "no music" state. */
    fun buildIdle(context: Context): RemoteViews {
        val title = context.getString(R.string.widget_default_title)
        val subtitle = context.getString(R.string.widget_default_subtitle)
        return build(context, title, subtitle, timeText = "", isPlaying = false, durationMs = 0L)
    }

    /** Views reflecting the connected player's current state. */
    fun build(context: Context, player: Player): RemoteViews {
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_default_title)
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_default_subtitle)
        val duration = player.duration.coerceAtLeast(0L)
        val timeText = formatPosition(player.currentPosition.coerceAtLeast(0L), duration)
        return build(context, title, artist, timeText, player.isPlaying, duration)
    }

    private fun build(
        context: Context,
        title: String,
        subtitle: String,
        timeText: String,
        isPlaying: Boolean,
        durationMs: Long
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_playback)

        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, subtitle)
        views.setTextViewText(R.id.widget_time, timeText)
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon(isPlaying))
        views.setProgressBar(R.id.widget_progress, 1000, progressFraction(0L, durationMs), false)
        views.setInt(
            R.id.widget_progress,
            "setVisibility",
            if (durationMs > 0) View.VISIBLE else View.GONE
        )

        val openApp = openAppPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_artwork, openApp)
        views.setOnClickPendingIntent(R.id.widget_title, openApp)

        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            transportPendingIntent(context, PlaybackService.ACTION_PLAY_PAUSE, REQUEST_CODE_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            transportPendingIntent(context, PlaybackService.ACTION_NEXT, REQUEST_CODE_NEXT)
        )
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            transportPendingIntent(context, PlaybackService.ACTION_PREVIOUS, REQUEST_CODE_PREVIOUS)
        )

        return views
    }

    /** Service PendingIntent that tells [PlaybackService] to run a transport command. */
    private fun transportPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Activity PendingIntent that brings P-Music to the foreground. */
    private fun openAppPendingIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setClassName(context, context.packageName + ".MainActivity")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
