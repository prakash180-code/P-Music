# Changelog

All notable changes to P-Music are documented here.

## [0.12.0] - 2026-08-07

### Sprint 12 — Lyrics (`:features:lyrics`)

**New `:features:lyrics` module:**
- `LyricsViewModel` (Hilt) — loads lyrics for the current song through `LyricsRepository` whenever the song changes (reload job keyed by song id, cancelled on change); re-exposes the live `PlaybackState` so the screen can highlight synced lines against the playhead.
- `ui/LyricsScreen.kt` — full-screen lyrics overlay opened from Now Playing (system back returns there): header with back button and title, loading spinner, a "No lyrics found for this track" fallback, and two rendering modes:
  - `SyncedLyrics` — a `LazyColumn` where the line whose timestamp is the latest at-or-before the playhead is highlighted (theme **primary**, bold) and auto-scrolled into view (`animateScrollToItem`); inactive lines use `onSurfaceVariant`.
  - `PlainLyrics` — unsynced text bodies rendered as a scrollable, paragraph-split column.

**Domain (`:domain`):**
- `LyricLine` (optional `timestampMs` + text) and `Lyrics` (list of lines); `LyricsRepository.loadLyrics(song)` contract.

**Parsing (`:data`):**
- `LrcParser` — standard `.lrc` files: `[mm:ss.xx]` timestamps (1–3 fractional digits), multiple timestamps per line, the `[offset:+/-ms]` tag (applied and clamped), and plain unsynced lines without brackets.
- `Id3LyricsParser` — parses the ID3 tag at the start of an MP3 (v2.2/2.3/2.4, extended headers, synchsafe sizes): **USLT** frames for plain lyrics (multi-frame bodies joined), **SYLT** frames for timestamped lines (ms format; MPEG-frame timestamps skipped). Handles all four text encodings; for UTF-16 it honors an explicit BOM and otherwise infers endianness from the first byte pair, since not every writer repeats the BOM before the lyric text.
- `LyricsRepositoryImpl` (@Singleton) — offline sources in order: an LRC sidecar file next to the audio (`<song>.lrc`), then embedded ID3 USLT/SYLT; both reads are bounded (the ID3 tag is parsed from the first 256 KB only) and run on the IO dispatcher.
- Tests: `LrcParserTest` (9 tests — timestamps, multiple tags, offset, plain lines) and `Id3LyricsParserTest` (10 tests — USLT/SYLT across encodings, incl. UTF-16 with BOM and a body that omits its own BOM).

**App shell (`:app`):**
- `NowPlayingTopBar` gained a **Lyrics** `IconButton` (top-right) that opens the overlay; `AppRootScreen` hosts `LyricsScreen` as a full-screen overlay when opened and resets it on nav-item taps; `:features:lyrics` dependency added; version bumped to 0.12.0 (versionCode 12).

**Verification (physical device, SDK 33, 1789-song library):** built + `lintDebug` + all unit tests green (26 in `:data`); two synthetic tracks were scanned into MediaStore and verified — `Lyrics ID3 Test.mp3` (ID3v2.3 USLT in UTF-16 with a BOM) and `Lyrics LRC Test.mp3` with a 4-line sidecar `.lrc` timed at 0:00 / 2.50 / 5.25 / 8.00. The Now Playing lyrics button opens the overlay for both; embedded UTF-16 USLT lyrics render correctly, special characters and angle brackets included (`& <text> <here>`); for the LRC track the active line is highlighted in the theme primary colour while the rest render in the inactive grey, and screenshot pixel sampling across two points in time confirms the highlight advances with the playhead (line 3 at ~5 s, line 4 at ~9 s). `adb push` alone left the new files unindexed (`is_music=NULL`), so they were force-scanned via `content call scan_file` before the app picked them up. Test files and the temporary "Rescan on launch" toggle were removed after verification; logcat clean, no crashes.

## [0.11.0] - 2026-08-07

### Sprint 11 — Equalizer (`:features:equalizer`)

**New `:features:equalizer` module:**
- `EqualizerViewModel` (Hilt) — a thin pass-through: observes the reactive `EqualizerState` from the playback controller and forwards enable / band-gain / preset / reset gestures to it.
- `ui/EqualizerScreen.kt` — opened from Settings (header back + system back): an on/off switch, a preset picker dialog (device presets, radio-selected), a **Reset to flat** action, and one slider per band (60 Hz–14 kHz on the test device) with a `+/- dB` label. Sliders commit on gesture end (thumb follows the finger locally; the value is applied and persisted only when the drag finishes, so DataStore is not flooded). Renders an "This device does not expose an audio equalizer" fallback when `EqualizerState.supported` is false.

**Domain (`:domain`):**
- `EqualizerState` (supported, enabled, min/max gain in millibels, default gain, `EqualizerBand`s, preset names, selected preset index) and `EqualizerBand` (center frequency Hz + gain in millibels).
- `PlaybackController` gained `equalizerState: StateFlow<EqualizerState>` plus `setEqualizerEnabled`, `setEqualizerBandGain`, `selectEqualizerPreset` and `resetEqualizer`.

**Effect engine (`:service`):**
- `AudioFxEqualizerEngine` (@Singleton) — single owner of the curve. Probes the device band/preset layout on the global output session (probe effect released immediately), then binds the real `android.media.audiofx.Equalizer` to the player's live audio session and applies the persisted curve the moment it binds. The UI can render and accept changes before audio ever plays; they are applied when the effect attaches.

**Persistence (`:core:datastore` / `:data`):**
- Three new DataStore keys — `equalizer_enabled`, `equalizer_band_gains`, `equalizer_preset_index` — with `EqualizerGainsCodec` (string-encoding the band-gain list), plumbed through `AppPreferences` and `PreferencesRepository`.

**App shell (`:app`):**
- New **Equalizer** row (with chevron) under the Settings "Playback" section; `AppRootScreen` hosts `EqualizerScreen` as an overlay when opened; version bumped to 0.11.0 (versionCode 11).

**Critical audio-session fix (`:service`):**
- The effect can only attach to the player's real audio session. `PlaybackService` originally forced a hard-coded session id onto the ExoPlayer, but Media3 built the AudioTrack on its own derived session — so when the engine created `Equalizer(0, staleId)` during playback, AudioFlinger threw **"Cannot create AudioTrack"** and the effect never registered (verified via `dumpsys media.audio_flinger`, which showed no effect chain on the session).
- Fix: `PlaybackService.onCreate` now generates a valid id via `AudioManager.generateAudioSessionId()`, assigns it to the ExoPlayer (`setAudioSessionId`) and reports the same id to the engine before playback starts; `Player.Listener.onAudioSessionIdChanged` remains as a safety net.

**Verification (physical device, SDK 33, 1789-song library):** built + lint + all unit tests green; Settings shows Version 0.11.0 and the Equalizer row; the screen renders the device's 5 bands / 10 presets; logcat confirms `ensureEffect() bound to session <id>` at service start and `dumpsys media.audio_flinger` shows the Equalizer **registered on the playing session with 1 active track**; the toggle flips the effect to `Enabled=y`; band gains and presets persist across restarts; previously a hard-coded session id broke binding entirely — now the effect chain is live on the player's session and there is no `Cannot create AudioTrack`; no crashes.

## [0.10.0] - 2026-08-07

### Sprint 10 — Playback Widget (`:features:widgets`)

**New `:features:widgets` module:**
- `PlaybackWidgetProvider` + process-wide `PlaybackWidgetManager` — a home-screen 4x1 playback widget. The manager holds a `MediaController` bound to `PlaybackService`, re-renders on every player event (`Player.Listener`), ticks the progress bar every second while playing, and decodes album art off the main thread (bounds-aware, max 256px).
- `PlaybackWidget` — `RemoteViews` builder: artwork, title/artist, progress bar + time label, and previous / play-pause / next buttons; idle state ("P-Music" / "No music playing") when nothing is loaded. Artwork/title tap opens the app.
- `WidgetFormat` + `WidgetFormatTest` (8 unit tests) — pure helpers for `m:ss` time formatting, progress fraction (clamped, unknown duration handled) and the play/pause icon switch.
- `res/xml/playback_widget_info.xml` (4x1, resizable, no polling), `widget_playback.xml`, rounded `widget_background`, Material vector icons (`ic_widget_play/pause/prev/next/music`) and widget strings.

**Transport controls (`:service`):**
- The widget's buttons are **service PendingIntents** (`PendingIntent.getService`) targeting `PlaybackService`, which now handles `ACTION_PLAY_PAUSE` / `ACTION_NEXT` / `ACTION_PREVIOUS` in `onStartCommand` on the session player. Rationale: Media3 1.6 removed `MediaButtonReceiver.buildMediaButtonPendingIntent`, and a `BroadcastReceiver` cannot bind to a service on modern Android (`ReceiverCallNotAllowedException`) — verified live on device, where the first implementation crashed on tap.

**App shell (`:app`):**
- `:features:widgets` wired in; `PMusicApplication` wakes the widget's session connection on process start so the widget stays live after package updates/background kills (update broadcasts are not guaranteed); version bumped to 0.10.0 (versionCode 10).

**Verification (physical device, SDK 33, Microsoft Launcher):** built + all unit tests green; Settings shows Version 0.10.0; widget added to the home screen via the launcher widget picker (system "Create widget and allow access?" dialog accepted); idle state renders; while playing, the widget shows the current title/artist, an advancing `m:ss / total` time label and a live progress bar; play/pause toggles playback (position freezes while paused, resumes after), next/prev step through a multi-item queue (verified with the 2-song Favorites queue), tapping artwork opens the app, and tapping the transport button from a cold app state starts the session without crashing; the whole flow survives an APK reinstall (widget stays placed, manager reconnects from `onAppStart`); a duplicate `MediaController` bind seen in early testing was eliminated with a `connecting` guard; logcat clean (no FATAL).

## [0.9.0] - 2026-08-07

### Sprint 9 — Statistics & Play Tracking (`:features:statistics`)

**New `:features:statistics` module:**
- `StatisticsViewModel` (Hilt): combines the reactive library into a `StatisticsState` (song/album/artist/genre/favorite counts, total duration, total plays, unplayed count, top-5 most played and recently played); also exposes playback state and `playSong`/`playQueue`/`toggleFavorite` so the top lists act as playback entry points.
- `ui/StatisticsScreen.kt` — dashboard opened from Settings with back handling: **Library** tiles (Songs/Albums, Artists/Genres, Favorites/Total duration), **Listening** tiles (Total plays/Unplayed songs), **Most played** and **Recently played** song lists (now-playing highlight, favorite toggle, tap-to-play with the list as queue), empty states for both top lists.

**Play recording (`:core:database` / `:domain` / `:data` / `:service`):**
- `SongDao.recordPlay(songId, playedAt)` increments `playCount` and stamps `lastPlayedAt`; `LibraryRepository.recordPlay(songId)` (impl uses `System.currentTimeMillis()`).
- `Media3PlaybackController` now observes `LibraryRepository` and records a play exactly once per song (fired from `playerListener.onEvents` on the first `STATE_READY`+playing transition for each newly started song; the tracking id resets on `disconnect()`), so the statistics reflect real listening.

**App shell (`:app`):**
- New **Statistics** row (with a chevron) under the Settings "Library" section; `AppRootScreen` shows `StatisticsScreen` when opened from Settings and resets the overlay on nav-item taps; version bumped to 0.9.0 (versionCode 9).

**Verification (physical device, SDK 33, 1789-song library):** built + all unit tests green; Settings shows the Statistics row and Version 0.9.0; Statistics renders 1789 Songs / 6 Albums / 2 Artists / 1 Genre / 2 Favorites / 62h 17m / 0 plays / 1789 unplayed before listening; playing songs records plays exactly once per start from multiple entry points (Library home row, Favorites, Statistics "Most played") — total plays advanced 1 → 2 → 3 → 4 with replaying a song incrementing it alone; the played song moved to #1 Most played and appeared top of Recently played (lastPlayedAt DESC) with a now-playing indicator in both lists; counts and rankings survive a force-stop restart and an APK reinstall (Room persists play data); a duplicate-LazyColumn-key crash (same song in both top lists) surfaced during testing and was fixed by namespacing the item keys (`most_played_<id>` / `recently_played_<id>`); logcat clean after the fix.

## [0.8.0] - 2026-08-07

### Sprint 8 — Settings (`:features:settings`)

**New `:features:settings` module:**
- `SettingsViewModel` (Hilt): exposes the reactive `AppPreferences` snapshot and forwards each change to the DataStore-backed `PreferencesRepository`.
- `ui/SettingsScreen.kt` — four sections:
  - **Appearance**: Theme row (dialog with System default / Light / Dark / AMOLED (pure black)) and a Dynamic colour switch (disabled while AMOLED is selected, since pure black wins over wallpaper colours).
  - **Playback**: Default playback speed row (dialog with the 0.5x–2.0x presets matching the Now Playing cycle).
  - **Library**: Rescan on launch switch.
  - **About**: app name + installed version (read from `PackageManager`).

**Preferences (`:domain` / `:core:datastore` / `:data`):**
- `AppPreferences` gained `defaultPlaybackSpeed` with a new `default_playback_speed` DataStore key, plumbed through the repository contract and `PreferencesRepositoryImpl`.

**AMOLED theme (`:core:ui`):**
- New `AmoledColorScheme` — the Dark tonal palette with true-black `background`/`surface` so OLED pixels stay off.
- `PmusicTheme` now takes `themeMode: ThemeMode` instead of `darkTheme` and resolves SYSTEM/LIGHT/DARK/AMOLED; AMOLED is always dark and takes priority over dynamic colour.

**Default playback speed (`:service`):**
- `Media3PlaybackController` observes `PreferencesRepository` and applies the stored speed whenever a new playback session starts (`playSong`/`playQueue`/`jumpToQueueIndex`), so playbacks begin at the chosen speed while the Now Playing speed control still overrides the active session.

**Rescan on launch (`:app`):**
- `MainActivity` reads `rescanOnLaunch` from the loaded preferences and passes it as `force` to `scanLibrary` once (a `scanStarted` guard avoids re-scanning on later preference changes).

**App shell (`:app`):**
- The Settings placeholder tab now hosts `SettingsScreen()`; dependency on `:features:settings` added; version bumped to 0.8.0 (versionCode 8).

**Verification (physical device, SDK 33, 1789-song library):** built + all unit tests green (10/10); Settings renders every section; Theme dialog switches instantly — Light (light background), AMOLED (background measured pure black 0,0,0 via screenshot pixel sampling), System; Dynamic colour toggles between the wallpaper palette (242,251,255) and the static violet palette (253,247,255); Default speed set to 2x plays a fresh session at 2.0x (Now Playing label) and still applies after a force-stop restart; theme/speed/switches all survive a force-stop restart; dynamic-colour switch correctly disabled in AMOLED mode; no crashes or exceptions in logcat.

## [0.7.0] - 2026-08-07

### Sprint 7 — Search, Favorites & Detail Screens (`:features:search` + `:features:library`)

**New `:features:search` module:**
- `SearchViewModel` (Hilt): debounced query matched in memory against the reactive library lists (songs/albums/artists/genres), session-scoped recent searches (committed on the IME search action or result taps, capped at 8, deduped, clearable), tap-to-play with the filtered songs as the queue.
- `ui/SearchScreen.kt` — large query field with IME Search action; recent-searches list (history icon, tap to re-run, per-item remove, Clear) or a browse hint while empty; grouped results (Songs / Albums / Artists / Genres) with section counts, song rows with now-playing highlight + favorite toggle, and an empty state.
- `FavoritesViewModel` + `ui/FavoritesScreen.kt` — reactive favorite list (unfavoriting removes a row instantly), tap-to-play with the favorites list as the queue, empty state.

**Album / Artist / Genre detail screens (`:features:library`):**
- `LibraryViewModel` gained a `LibrarySelection` state (`None`/`Album`/`Artist`/`Genre`) and a derived `detail` flow (combines selection + reactive lists into `LibraryDetail(title, songs)`).
- Tapping an album/artist/genre row now opens a shared `LibraryDetailScreen` (back button, title, song count, play-all, song list with now-playing highlight) instead of immediately queueing; system back (`BackHandler`) and the header back button both return to the browser.

**Shared UI (`:core:ui`):**
- `SongRow`, `CompactSongCard`, `AlbumCard`, `ArtistRow` and `GenreRow` moved from `:features:library` into a new `component/MusicItems.kt` so Search/Favorites reuse them (library now imports from there). `:core:ui` gained a `:domain` dependency for the models.

**App shell (`:app`):**
- Added a **Favorites** destination to the bottom navigation (Library / Search / Favorites / Playlists / Settings) and replaced the Search placeholder with the real `SearchScreen`; `:features:search` dependency added.

**Verification (physical device, SDK 33, 1789-song library):** built + `testDebugUnitTest` green; 5-tab nav renders; Search filters instantly ("wfh" → 2 song results), result tap plays with the filtered queue, IME search records recent searches and Clear empties them; Favorites tab lists favorited songs (including one persisted from prior sprints), unfavoriting removes the row live, favorites survive a force-stop restart; album/artist/genre rows open their detail screens, play-all and row-tap play with the collection as queue, and both back paths (header + system back) return to the browser; no crashes or exceptions in logcat.

## [0.6.0] - 2026-08-07

### Sprint 6 — Playlists (`:features:playlist`)

**New feature module `:features:playlist`:**
- `PlaylistViewModel` (Hilt): exposes playlists, all songs, playback state, selected playlist detail and song picker state as `StateFlow`s; create/rename/delete/add/remove/reorder actions all touch `updatedAt`; `flatMapLatest` keeps the detail reactive; auto-closes the detail when its playlist is deleted.
- `ui/PlaylistsScreen.kt` — list with create dialog (FAB), rename/delete menus, empty state; detail with play-all, add-songs full-screen picker, empty state, per-song now-playing highlight.
- `ui/PlaylistItems.kt` — `PlaylistRow` (cover, name, song count, rename/delete menu), `PlaylistSongRow` (drag handle + move up/down/remove menu + now-playing highlight), `AddSongRow` (CheckCircle/AddCircle state).
- Long-press drag reorder via `detectDragGesturesAfterLongPress` + `offset`/`zIndex`/`Modifier.animateItem()`; Move up/down menu for accessibility.

**Domain (`:domain`):**
- `Playlist` model (`id`, `name`, `createdAt`, `updatedAt`, `songCount`) and `PlaylistRepository` contract (reactive observe/…/reorderSongs; create/rename/delete/addSong/removeSong).

**Database (`:core:database`):**
- **Room v1 → v2 migration** (`Migrations.MIGRATION_1_2`): adds `playlists` (autoincrement id, timestamps) and `playlist_songs` (composite PK, `position`, FK CASCADE both ways, index), exported schema, and the `PlaylistDao` with reactive reads (LEFT-JOIN song counts) and transactional helpers (`addSongIfAbsent`, `removeAndRenumber`, `replaceOrder`).
- `DatabaseModule` now registers the migration; schema export enabled (`room.schemaLocation`).

**Data (`:data`):**
- `PlaylistRepositoryImpl` (@Singleton) with projection→domain mappers; `PlaylistMappersTest` added.

**App shell (`:app`):**
- The Playlists placeholder tab now hosts `PlaylistsScreen()`; dependency on `:features:playlist` added.

**Verification (physical device, SDK 33, real 1789-song library):** built + `testDebugUnitTest` green (incl. new mapper tests); installed; the v1→v2 migration ran in place on the existing library (1789 songs preserved; `playlists`/`playlist_songs` tables confirmed in the DB, `room_master_table` identity hash present); create / rename / delete (with confirmation) / add-songs picker / remove / reorder-via-menu / long-press-drag-reorder all verified; tap-to-play + Play all highlight the current song and drive the mini player; playlist name + contents survive a force-stop restart; empty state restores after deleting the last playlist; no crashes or exceptions in logcat.

## [0.5.0] - 2026-08-07

### Sprint 5 — Now Playing & Mini Player (`:features:player`)

**New feature module `:features:player`:**
- `PlayerViewModel` (Hilt) observes the shared `PlaybackController` singleton and translates high-level transport actions (toggle shuffle, cycle repeat OFF→ALL→ONE→OFF, cycle speed through 0.5x–2.0x presets) into controller calls; exposes the live `PlaybackState` as a `StateFlow`.
- `TimeFormat.kt` — position/duration formatting (`mm:ss`, `h:mm:ss` past the hour).
- `ui/MiniPlayerBar.kt` — compact bar rendered above the bottom navigation whenever a song is loaded: artwork, title/artist, play/pause toggle; tapping the body opens Now Playing.
- `ui/NowPlayingScreen.kt` — full-screen surface (stateless, all controls delegate to callbacks):
  - Large centred artwork, title/artist/album, fixed top bar with a dismiss action and safe-area insets.
  - Seek slider with local drag state (thumb follows the finger, seeks on release) and current/total time labels.
  - Previous / play-pause / next transport row plus shuffle, repeat and playback-speed controls (active modes tint primary).
  - Scrollable queue list with the current item highlighted and a "now playing" indicator; tapping any row jumps to it.

**Shared UI (`:core:ui`):**
- New `AppArtwork` component (Coil `AsyncImage` over the MediaStore art URI with a fallback tile); `LibraryArtwork` refactored to delegate to it so mini player, now playing and library all render artwork identically.

**Playback (`:domain` + `:service`):**
- `PlaybackController.jumpToQueueIndex(index)` added to the contract and implemented via `seekTo(index, 0L)` + `play()`.
- **Bug fix:** `Media3PlaybackController.syncState` recovered the current song from the MediaItem tag, which is not reliably delivered through the Media3 session boundary, so `PlaybackState.currentSong` was always `null` and the mini player never appeared. The song is now resolved from the locally tracked queue by index (tag only as fallback).

**App shell (`:app`):**
- `AppRootScreen` now hosts a `PlayerViewModel`; the mini player sits between the content and the `NavigationBar`, and a full-screen Now Playing overlay covers the app when opened (dismiss restores the underlying shell).
- Dependencies added: `:features:player`, androidx.hilt:hilt-navigation-compose.

**Verification (physical device, SDK 33):** built + `testDebugUnitTest` green; mini player appears on playback with correct title/artist and play/pause; tapping it opens Now Playing; artwork, seek (drag + tap-to-jump both update position), next/previous advance the queue, shuffle/repeat/speed cycle and persist (speed confirmed applied: session `speed=1.25`), queue lists all loaded songs with the current row marked, queue-row tap jumps and plays, closing Now Playing leaves the mini player showing the current song; media notification still posts with transport controls; no crashes.

## [0.4.0] - 2026-08-07

### Sprint 4 — Library UI (`:features:library`)

**New feature module `:features:library`:**
- `LibraryViewModel` (Hilt) exposes the Room-backed `LibraryRepository` flows as `StateFlow`s: all songs, albums, artists, genres, recently-added (30), most-played (30) and favorites; forwards playback to the shared `PlaybackController` singleton; provides `toggleFavorite` and `refresh` (force rescan).
- `ui/LibraryScreen.kt` — tabbed browser (Songs / Albums / Artists / Genres) with:
  - Home sections on the Songs tab (Recently Added, Most Played, Favorites) as horizontal artwork rows, then the full All Songs list.
  - Live search field that filters the active tab by title/artist/album/name and hides home sections while searching.
  - Header showing the live scan state / song count with a manual rescan action and a spinner while scanning.
  - Empty-library state with a "Scan your music" action; "no results" and empty-tab hints.
- `ui/LibraryItems.kt` — presentation-only items: `SongRow` (now-playing highlight + favorite toggle), `CompactSongCard`, `AlbumCard`, `ArtistRow`, `GenreRow`, and `LibraryArtwork` (Coil `AsyncImage` over the MediaStore art URI with a music-note fallback tile).
- Interactivity: tapping a song plays it with its current filtered list as the queue; tapping an album/artist/genre queues every matching song from the in-memory list.

**App shell (`:app`):**
- `AppRootScreen` — `Scaffold` with a Material 3 bottom `NavigationBar` (Library, Search, Playlists, Settings); Library hosts the real feature, the rest are placeholders.
- `MainActivity` — permission gate (prompt screen until media access is granted), then routes to `AppRootScreen` and triggers the idempotent first scan.

**Bug fix (`:core:database`):**
- `SongDao.observeAlbums` used `MAX(albumArtist) AS artist`, which is `NULL` when no track carries album-artist metadata (all 1789 rows on the test device) and crashed Room's non-null `Album.artist` mapping. Fixed with `COALESCE(MAX(albumArtist), MAX(artist))`.

**Dependencies added:** Coil 2.7.0 (coil-compose), androidx.hilt:hilt-navigation-compose 1.2.0, androidx.lifecycle:lifecycle-viewmodel-compose.

**Verification (physical device, SDK 33):** built + installed; library renders 1789 songs with artwork placeholders; Songs/Albums/Artists/Genres tabs all render; song tap starts playback (MediaSession `STATE_PLAYING`, position advancing); album tap queues and plays the album; search filters to matching songs; favorite toggle persists to Room (`isFavorite=1`) and survives a force rescan; bottom-nav placeholders switch correctly; no crashes.

## [0.3.0] - 2026-08-07

### Sprint 3 — Media Playback Engine (Media3)

**Domain layer (`:domain`):**
- `PlaybackState` snapshot (current song, queue, index, position/duration, isPlaying/isBuffering, shuffle, repeat, speed, connection flag) and `RepeatMode` enum (OFF/ONE/ALL).
- `PlaybackController` contract: `playbackState` as `StateFlow`, plus connect/disconnect, playSong, playQueue, pause, toggle, seek, next/previous, shuffle, repeat, speed. Features depend only on this interface.
- `Song` gained `artPath` for album-art loading in the notification.

**Media mappings (`:core:media`):**
- `PlaybackMappers`: `Song.toMediaItem()` (MediaStore content URI, domain song attached as tag, populated `MediaMetadata` incl. artwork URI), `MediaItem.toSong()`, and both repeat-mode mappers.
- `PlaybackMappersTest` — 3 unit tests for the repeat-mode round trip (3/3 passing).

**Playback host (`:service`):**
- `PlaybackService` — `MediaSessionService` owning the `ExoPlayer` (audio focus + becoming-noisy handling) and `MediaSession`; `DefaultMediaNotificationProvider` with the notification small icon; `onTaskRemoved` stops the service when nothing is playing.
- `Media3PlaybackController` — Hilt-provided `PlaybackController` built on `MediaController`; connects asynchronously via the future callback (no main-thread blocking), syncs state from player events, and runs a 500 ms position ticker while playing.
- `ServiceModule` binds the controller to the contract.
- `ic_notification` vector drawable.

**Media button handling (`:receiver`):**
- `PMusicMediaButtonReceiver` (extends Media3 `MediaButtonReceiver`) declared with `MEDIA_BUTTON` intent-filter; used by the media session and notification controls.

**App wiring (`:app`):**
- MainActivity injects `PlaybackController`, connects on start, and the placeholder screen gains a play/pause toggle (first tap plays the first indexed song).
- `material-icons-extended` added for the `Pause` icon.

**Verification (physical device, SDK 33):** built + installed; session registered with the media-button receiver; tap Play → song loads, audio starts (AudioTrack active), position advances, media notification posted with `transport` category; no crashes.

**Dependencies added:** Media3 1.6.1 (exoplayer, session, common), Compose Material Icons Extended.

## [0.2.0] - 2026-08-07

### Sprint 2 — Domain Model, Room Database, Preferences & Media Scanning

**Domain layer (`:domain`):**
- Pure models: `Song`, `Album`, `Artist`, `Genre`, `AppPreferences`, `ThemeMode`, `LibraryScanState`.
- Repository contracts: `LibraryRepository`, `PreferencesRepository` (features depend only on these interfaces).

**Persistence (`:core:database`):**
- Room database (`PMusicDatabase`, v1) with the `songs` table and indexes for all hot query paths.
- `SongDao` with reactive flows: all songs, favorites, recently added/played, most played, and derived album/artist/genre groupings (single source of truth — no duplicated tables).
- `DatabaseModule` (Hilt) providing the singleton database + DAO.

**Preferences (`:core:datastore`):**
- `UserPreferencesDataStore` wrapping DataStore Preferences, exposing typed `Flow<AppPreferences>` with corruption tolerance.

**Data layer (`:data`):**
- `MediaLibraryScanner` — MediaStore scan with chunked upserts, bounded stale-row deletion (SQLite param safety), genre resolution via per-genre members queries, album-art lookup, and carry-over of favorites/play-counts across rescans.
- `LibraryRepositoryImpl` / `PreferencesRepositoryImpl` mapped to domain models.
- `RepositoryModule` (Hilt) binding implementations to contracts.
- Unit tests for all persistence→domain mappers (5/5 passing).

**App wiring:**
- Runtime media-permission request (`READ_MEDIA_AUDIO` on API 33+).
- Background library scan triggered on first launch; placeholder screen shows scan status.
- Theme now reads preferences from DataStore (reactive to theme mode + dynamic color).

**Verification (physical device, SDK 33):** app installed, permission granted, scan indexed **1,789 songs / 6 albums / 2 artists**; `assembleDebug` + `testDebugUnitTest` green.

**Dependencies added:** Room 2.7.2, DataStore Preferences 1.1.7, kotlinx-coroutines 1.9.0.

## [0.1.0] - 2026-08-06

### Sprint 1 — Project Scaffolding & Architecture Foundation

**Architecture setup:**
- Multi-module Gradle project with 21 modules following the planned Clean Architecture layout.
- Central version catalog (`gradle/libs.versions.toml`) for all dependencies and plugins.
- Gradle wrapper pinned to 8.14.3 (matching the locally cached distribution).
- `:app` module wired with Hilt (KSP), Kotlin Compose plugin, and Material 3.

**Modules created:**
- `:app` — Hilt Application, single-activity Compose host, adaptive launcher icon.
- `:core:common`, `:core:utils`, `:core:database`, `:core:datastore`, `:core:media`, `:core:ui` (shells; `:core:ui` already ships the Material 3 theme).
- `:domain`, `:data` (Clean Architecture layers).
- `:navigation`, `:service`, `:receiver` (app infrastructure).
- `:features:library|player|playlist|search|settings|lyrics|equalizer|statistics|widgets` (feature shells).

**Theming:**
- Material 3 dynamic-color theme with a violet brand seed and curated light/dark palettes.
- App typography scale in `Type.kt`.

**Verification:**
- `assembleDebug` succeeds; `app-debug.apk` produced (~10.5 MB).

**Dependencies introduced (Sprint 1 only):**
- AGP 8.13.2, Kotlin 2.2.10, KSP 2.2.10-2.0.2
- Compose BOM 2025.06.01, core-ktx 1.16.0, activity-compose 1.10.1, lifecycle 2.9.1
- Hilt 2.56.2, JUnit 4.13.2, androidx.test.ext:junit 1.2.1, espresso-core 3.6.1
