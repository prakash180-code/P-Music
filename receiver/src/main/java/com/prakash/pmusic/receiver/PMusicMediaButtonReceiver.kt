package com.prakash.pmusic.receiver

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver

/**
 * Media button receiver for P-Music.
 *
 * Media3's [MediaButtonReceiver] already routes ACTION_MEDIA_BUTTON intents
 * (wired headset buttons, Bluetooth AVRCP fallback) and the notification's
 * media button intent to the active [MediaSessionService]. Subclassing it
 * simply gives the manifest a stable app-specific component to reference.
 */
@OptIn(UnstableApi::class)
class PMusicMediaButtonReceiver : MediaButtonReceiver()
