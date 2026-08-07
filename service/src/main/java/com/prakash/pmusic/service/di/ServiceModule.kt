package com.prakash.pmusic.service.di

import com.prakash.pmusic.domain.repository.PlaybackController
import com.prakash.pmusic.service.Media3PlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the Media3-backed playback controller to the domain contract so the
 * UI only ever depends on [PlaybackController].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: Media3PlaybackController): PlaybackController
}
