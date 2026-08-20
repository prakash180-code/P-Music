package com.prakash.pmusic.domain.model

/**
 * Intent action used by the playback notification's content intent.
 *
 * The notification targets [MainActivity] through this action (declared as an
 * intent-filter in the app manifest) so the Now Playing screen opens when the
 * user taps the notification, without the `:service` module depending on the
 * `:app` module.
 */
const val ACTION_OPEN_NOW_PLAYING = "com.prakash.pmusic.action.OPEN_NOW_PLAYING"
