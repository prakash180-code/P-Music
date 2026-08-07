package com.prakash.pmusic

import android.app.Application
import com.prakash.pmusic.features.widgets.PlaybackWidgetManager
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point of P-Music.
 *
 * Marked with [HiltAndroidApp] so the Hilt graph is generated at the app
 * level and every annotated component across all modules is available
 * application-wide.
 *
 * Decisions:
 * - Wakes the playback widget's session connection on process start. Widget
 *   update broadcasts are not guaranteed to arrive after a package update or
 *   a background kill, so the app connects the widget's [MediaController]
 *   whenever its own process is alive (which it is during playback).
 */
@HiltAndroidApp
class PMusicApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        PlaybackWidgetManager.onAppStart(this)
    }
}
