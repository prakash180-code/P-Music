# P-Music — Project Status

## Current Sprint

**Sprint 11: Equalizer (`:features:equalizer`)** — completed.

## Completed Features

- **Sprint 1:** Multi-module Gradle project (21 modules), version catalog, Gradle wrapper (8.14.3), AGP 8.13.2, Kotlin 2.2.10, Compose BOM 2025.06.01, Hilt, Material 3 theme (dynamic colour + violet fallback), runnable `:app` shell, adaptive launcher icon.
- **Sprint 2:**
  - Domain models (`Song`, `Album`, `Artist`, `Genre`, `AppPreferences`, `ThemeMode`, `LibraryScanState`) and repository contracts (`LibraryRepository`, `PreferencesRepository`) in `:domain`.
  - Room database v1 with `songs` table, full index coverage, reactive DAO flows, and derived album/artist/genre groupings.
  - `UserPreferencesDataStore` (DataStore Preferences) with typed preference snapshots.
  - `MediaLibraryScanner` (MediaStore → Room) with chunked writes, safe stale-row deletion, genre resolution, album-art lookup, and favorites/play-count preservation across rescans.
  - Repository implementations bound via Hilt; mapper unit tests (5/5 passing).
  - Runtime media-permission flow, background scan on first launch, theme reactive to stored preferences.
- **Sprint 3:**
  - `PlaybackController` contract + `PlaybackState`/`RepeatMode` in `:domain`; `Song.artPath` for notification artwork.
  - `PlaybackMappers` in `:core:media` (Song ↔ MediaItem, repeat-mode maps) with unit tests (3/3 passing).
  - `PlaybackService` (Media3 `MediaSessionService` + ExoPlayer) with audio focus, becoming-noisy pause, and a media notification (transport category, small icon, play/pause/seek controls).
  - `Media3PlaybackController` (Hilt) on `MediaController`: non-blocking async connect, player-event state sync, 500 ms position ticker, full command surface.
  - `PMusicMediaButtonReceiver` in `:receiver` registered for hardware media buttons and notification taps.
  - MainActivity play/pause toggle wired to the controller (first tap plays the first indexed song).
  - Verified on device: playback starts, audio outputs, position advances, notification shows; no crashes.
- **Sprint 4:**
  - `:features:library` module with a Hilt `LibraryViewModel` exposing Room-backed flows (songs, albums, artists, genres, recently added, most played, favorites) and the shared `PlaybackController`.
  - `LibraryScreen`: Songs/Albums/Artists/Genres tabs, home sections (Recently Added / Most Played / Favorites) as horizontal artwork rows, All Songs list, live search filter, scan-state header with manual rescan, empty states.
  - `LibraryItems`: Coil-powered `LibraryArtwork` with fallback tile; `SongRow` (now-playing highlight + favorite toggle), `CompactSongCard`, `AlbumCard`, `ArtistRow`, `GenreRow`.
  - Tap-to-play everywhere: songs play with the current filtered list as queue; album/artist/genre taps queue their matching songs.
  - `AppRootScreen` bottom navigation shell (Library, Search, Playlists, Settings placeholders); `MainActivity` permission gate routes into it.
  - Fixed `observeAlbums` NULL `albumArtist` crash via `COALESCE(MAX(albumArtist), MAX(artist))`.
  - Verified on device: all tabs render, tap-to-play works (MediaSession STATE_PLAYING), search filters, favorites persist in Room and survive a rescan, no crashes.
- **Sprint 5:**
  - `:features:player` module: Hilt `PlayerViewModel` (live `PlaybackState` + transport action helpers), `TimeFormat`, `MiniPlayerBar` (artwork/title/artist/play-pause, tap-to-open), `NowPlayingScreen` (artwork, drag-commit seek bar with time labels, previous/play/next, shuffle/repeat/speed controls, scrollable queue with current-item highlight and row-tap jump).
  - Shared `AppArtwork` in `:core:ui`; `LibraryArtwork` refactored onto it.
  - `PlaybackController.jumpToQueueIndex` added (interface + Media3 impl via `seekTo(index)` + `play`).
  - Fixed `currentSong` never being set: the MediaItem tag is stripped across the media-session boundary, so the controller now resolves the song from its locally tracked queue by index.
  - `AppRootScreen` wires the player: mini player above the nav bar, full-screen Now Playing overlay, `PlayerViewModel` via Hilt.
  - Verified on device: mini player + Now Playing render; play/pause, next/previous, seek, shuffle, repeat, speed (session `speed=1.25`) and queue jumps all work; media notification still posts; no crashes.
- **Sprint 6:**
  - `:features:playlist` module: Hilt `PlaylistViewModel` (playlists/all-songs/playback/detail/picker flows, create/rename/delete/add/remove/reorder, reactive detail via `flatMapLatest`), `PlaylistsScreen` (list, create/rename/delete dialogs, empty states, detail with play-all + add-songs picker), `PlaylistItems` (playlist rows with menus, song rows with now-playing highlight, add-song rows).
  - Long-press drag-to-reorder (`detectDragGesturesAfterLongPress` + `animateItem`) plus Move up/down menu.
  - Domain `Playlist` model + `PlaylistRepository` contract; Room v1→v2 migration adding `playlists` and `playlist_songs` (composite PK, position, FK CASCADE, indices) with exported schema; `PlaylistDao` transactional helpers; `PlaylistRepositoryImpl` + mappers + `PlaylistMappersTest`.
  - `AppRootScreen` Playlists placeholder replaced by `PlaylistsScreen()`.
  - Verified on device (1789-song library, real migration path): migration preserved all data; create/rename/delete/add/remove/reorder (menu + drag) all work; play-all + row-tap highlight current song; playlist survives force-stop restart; empty states correct; no crashes.
- **Sprint 7:**
  - `:features:search` module: `SearchViewModel` (debounced in-memory query over songs/albums/artists/genres, session recent searches, tap-to-play with filtered queue), `SearchScreen` (query field, recent searches, grouped results), `FavoritesViewModel` + `FavoritesScreen` (reactive favorites, tap-to-play, empty state).
  - Album/Artist/Genre detail screens in `:features:library`: `LibrarySelection`/`LibraryDetail` state in the ViewModel and a shared `LibraryDetailScreen` (back + system-back, play-all, song list with now-playing highlight).
  - Shared `SongRow`/`CompactSongCard`/`AlbumCard`/`ArtistRow`/`GenreRow` moved to `:core:ui` (`component/MusicItems.kt`) and reused by Library, Search and Favorites.
  - `AppRootScreen`: new **Favorites** bottom-nav destination; Search placeholder replaced by the real screen.
  - Verified on device (1789-song library): 5-tab nav, live search filtering + play + recent searches, favorites add/remove/persist, all three detail screens with play-all and both back paths, no crashes.
- **Sprint 8:**
  - `:features:settings` module: Hilt `SettingsViewModel` (reactive `AppPreferences`, setters for theme/dynamic colour/default speed/rescan), `SettingsScreen` with Appearance / Playback / Library / About sections — Theme and Default playback speed picker dialogs, Dynamic colour + Rescan-on-launch switches (dynamic colour disabled in AMOLED mode), version row reading the installed app version.
  - `AppPreferences` gained `defaultPlaybackSpeed` (new `default_playback_speed` DataStore key) across model, repository, DataStore and `PreferencesRepositoryImpl`.
  - **AMOLED theme**: new `AmoledColorScheme` (Dark tonal palette with true-black `background`/`surface`) in `:core:ui`; `PmusicTheme` now takes `themeMode` instead of `darkTheme` and resolves SYSTEM/LIGHT/DARK/AMOLED (AMOLED takes priority over dynamic colour).
  - **Default playback speed**: `Media3PlaybackController` observes `PreferencesRepository` and applies the stored speed at every session start (`playSong`/`playQueue`/`jumpToQueueIndex`), so new playbacks start at the chosen speed while the Now Playing speed control still overrides the current session.
  - **Rescan on launch**: `MainActivity` now passes `rescanOnLaunch` into `scanLibrary(force = …)` once real preferences load (no redundant scans and no re-scan on later preference changes).
  - `AppRootScreen` Settings placeholder replaced by `SettingsScreen()`; version bumped to 0.8.0 (versionCode 8).
  - Verified on device (1789-song library): Settings renders all four sections; Theme dialog switches Light (light background) / AMOLED (background measured pure black 0,0,0) / System instantly; Dynamic colour toggle flips between the dynamic wallpaper palette and the static violet palette; Default speed set to 2x makes a fresh session play at 2.0x (Now Playing label) before and after a force-stop restart; both switches + speed + theme persist across restarts; disabled dynamic-colour switch in AMOLED mode; no crashes.

- **Sprint 9:**
  - `:features:statistics` module: Hilt `StatisticsViewModel` (combines reactive library into counts/total duration/play stats/top-5 most-played + recently-played, exposes playback + play/toggle helpers) and `StatisticsScreen` (back + title, Library tiles Songs/Albums/Artists/Genres/Favorites/Total duration, Listening tiles Total plays/Unplayed songs, Most played + Recently played song lists with now-playing highlight/favorite toggle/tap-to-play, empty states). Opened from a new Settings "Statistics" row (with chevron) wired in `AppRootScreen`; version bumped to 0.9.0 (versionCode 9).
  - **Play recording**: `SongDao.recordPlay` (playCount + 1, lastPlayedAt stamp) + `LibraryRepository.recordPlay`; `Media3PlaybackController` records a play once per song start (fires on first STATE_READY+playing per song in `onEvents`, resets on disconnect) so statistics reflect real listening.
  - Fixed a duplicate-LazyColumn-key crash (a song present in both Most played and Recently played shared the same item key) by namespacing the keys per list.
  - Verified on device (1789-song library): Statistics renders all counts; plays recorded exactly once per start from Library home, Favorites and Statistics itself (total plays 1→2→3→4, replay increments only that song); played song tops Most played and Recently played (lastPlayedAt DESC) with now-playing indicators; counts persist across force-stop restart and APK reinstall; logcat clean.

- **Sprint 10:**
  - `:features:widgets` module: `PlaybackWidgetProvider` + process-wide `PlaybackWidgetManager` (binds a `MediaController` to `PlaybackService`, re-renders on player events, 1 s progress ticker, off-main-thread bounds-aware artwork decode), `PlaybackWidget` `RemoteViews` builder (artwork, title/artist, progress + time, prev/play-pause/next, idle state, artwork/title opens the app), `WidgetFormat` helpers + 8 unit tests, 4x1 `playback_widget_info`, layout, rounded background, vector icons, strings.
  - **Transport via service intents**: the widget buttons are `PendingIntent.getService` targets that `PlaybackService.onStartCommand` turns into play/pause/next/previous on the session player (`ACTION_PLAY_PAUSE`/`ACTION_NEXT`/`ACTION_PREVIOUS`). This replaced the original broadcast-receiver design, which crashed with `ReceiverCallNotAllowedException` (receivers cannot bind to services on modern Android; Media3 1.6 removed `buildMediaButtonPendingIntent`).
  - **App shell**: `:features:widgets` wired into `:app`; `PMusicApplication` connects the widget manager on process start (widget stays live after reinstalls/background kills); version bumped to 0.10.0 (versionCode 10).
  - Verified on device (1789-song library, Microsoft Launcher): widget placed on the home screen, idle + live states render (title/artist/progress/time advance while playing), play/pause toggles playback, next/prev step a 2-song queue, artwork tap opens the app, widget controls work from a cold app state, whole flow survives an APK reinstall, duplicate-bind guard, logcat clean.

- **Sprint 11:**
  - `:features:equalizer` module: `EqualizerViewModel` (thin pass-through to the shared `PlaybackController`) and `EqualizerScreen` — enable switch, device-preset picker dialog, Reset to flat, one gain slider per band with `+/- dB` labels, back handling, and an "unsupported device" fallback when the audio stack exposes no equalizer effect.
  - `:domain`: `EqualizerState`/`EqualizerBand` models; `PlaybackController` gained `equalizerState` + `setEqualizerEnabled`/`setEqualizerBandGain`/`selectEqualizerPreset`/`resetEqualizer`.
  - `:service`: `AudioFxEqualizerEngine` (@Singleton) owns the curve, probes the device band/preset layout on the global output session, binds the real `android.media.audiofx.Equalizer` to the player's live session, and re-applies the persisted curve on bind.
  - `:core:datastore` + `:data`: `equalizer_enabled` / `equalizer_band_gains` (`EqualizerGainsCodec`) / `equalizer_preset_index` keys plumbed through `AppPreferences` and `PreferencesRepository`.
  - Settings gained an Equalizer row (with chevron) hosted by `AppRootScreen`; version bumped to 0.11.0 (versionCode 11).
  - **Audio-session fix**: forcing a hard-coded session id onto the ExoPlayer made Media3 derive a different AudioTrack session, so the effect's `Equalizer(0, staleId)` threw "Cannot create AudioTrack" and never registered. `PlaybackService` now generates a valid id (`AudioManager.generateAudioSessionId()`), assigns it to the player, and hands it to the engine before playback starts (player `onAudioSessionIdChanged` kept as a safety net).
  - Verified on device (1789-song library): the effect binds at service start, `dumpsys media.audio_flinger` shows the Equalizer registered on the playing session with 1 active track, the toggle flips `Enabled`, band gains/presets persist across restarts, no crashes.

## Pending Features

- Smart playlists, Lyrics.
- File management.
- Optional future modules (Wi-Fi sync, online search) — designed as pluggable, not built.

## Known Issues

- Genre metadata is frequently absent from MediaStore on real devices, so songs fall back to "Unknown" (handled gracefully). No crashes.
- Bitrate is indexed from MediaStore; sample rate / channel count are not MediaStore columns and remain 0 (deferred to on-demand metadata enrichment).
- Noted during Sprint 3 device testing: scripted `adb input tap` sequences occasionally deliver duplicate taps; single physical taps are handled correctly (no app-side defect).

## Next Sprint

**Sprint 12: Lyrics** (first remaining item from Pending Features).
