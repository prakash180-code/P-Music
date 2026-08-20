package com.prakash.pmusic.service

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList

/**
 * Media3 notification provider that appends a favorite toggle to the playback
 * notification.
 *
 * The heart icon is resolved when the notification is built ([isFavorite]), so
 * it always reflects the current library state. Tapping it delivers the
 * [PlaybackService.ACTION_TOGGLE_FAVORITE] session command, which the service
 * callback handles.
 */
@OptIn(UnstableApi::class)
class FavoriteNotificationProvider(
    context: Context,
    private val isFavorite: () -> Boolean
) : DefaultMediaNotificationProvider(context) {

    override fun getMediaButtons(
        mediaSession: MediaSession,
        playerCommands: Player.Commands,
        commandButtons: ImmutableList<CommandButton>,
        showSeekBarInCompactView: Boolean
    ): ImmutableList<CommandButton> {
        val base = super.getMediaButtons(
            mediaSession,
            playerCommands,
            commandButtons,
            showSeekBarInCompactView
        ).filterNot { it.sessionCommand?.customAction == PlaybackService.ACTION_TOGGLE_FAVORITE }

        val favoriteButton = CommandButton.Builder(
            if (isFavorite()) {
                CommandButton.ICON_HEART_FILLED
            } else {
                CommandButton.ICON_HEART_UNFILLED
            }
        )
            .setSessionCommand(SessionCommand(PlaybackService.ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
            .setDisplayName(if (isFavorite()) "Remove from favorites" else "Add to favorites")
            .build()

        return ImmutableList.builder<CommandButton>()
            .addAll(base)
            .add(favoriteButton)
            .build()
    }
}
