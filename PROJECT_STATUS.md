# P-Music — Project Status

## Current Sprint

**Fix: promote media service to foreground for background playback** — completed (on-device verified on Moto G32 / Android 13). Addresses the reported "song stops when played in the background". The manifest already declared FGS permission + `foregroundServiceType="mediaPlayback"` + `stopWithTask="false"`, but the service was never actually **promoted** to the foreground — it was only bound by the Media3 controller, `POST_NOTIFICATIONS` was never requested at runtime, and the system log showed `startForegroundService()` **DENIED** once backgrounded, then `Stopping service due to app idle`.

- `MainActivity`: runtime `POST_NOTIFICATIONS` request (Android 13+).
- `Media3PlaybackController.promoteServiceToForeground()`: `ContextCompat.startForegroundService` before `play()` in `playSong` / `playQueue` / `playExternalAudioNow` / `togglePlayPause` (resume) / `jumpToQueueIndex`.
- `PlaybackService`: `FGS_START` diagnostics in `onStartCommand`; `promoteToForegroundIfPlaying()` re-posts the media notification on widget/media-button play; `onForegroundServiceStartNotAllowedException` logs `FGS_START_DENIED`.
- **Verification (Moto G32):** permission granted, service enters `isForeground=true` with the media notification at play time, and backgrounded playback survives a full track (~206 s) past the previous idle-stop window with no freeze. `:app:assembleDebug` green.

**Fix: silent background renderer freeze in `MultiOutputAudioSink`** — completed (on-device verified). Resolves the reported "playback stops when the app is backgrounded / UI still shows playing / Play needs two presses".

- **Root cause (via diagnostics):** the background stop is a **silent renderer freeze** — the player froze at one position while still reporting `isPlaying=true`, and logged **no** playback event (no `playWhenReady`/`suppression`/command change). The fan-out `MultiOutputAudioSink` guarded every method with a single `synchronized(lock)` and executed blocking child calls (`DefaultAudioSink.configure`/`setPreferredDevice`/`setVolume`) inside that critical section on the main thread during output reconfiguration. If that reconfiguration stalled, the renderer's `handleBuffer` blocked on the same lock indefinitely — audio froze while player state stayed "playing". A bypass build (stock `DefaultAudioSink`) eliminated the freeze, confirming the wrapper as the source.
- **Fix:** child list is now an immutable `@Volatile` snapshot; the renderer hot path reads it **without any lock** and never waits on reconfiguration. Output reconfiguration runs on a **single dedicated config thread**, doing blocking child work outside the shared lock and swapping the published snapshot under a brief lock only. Release is idempotent (`AtomicBoolean` + `configExecutor.shutdown()`).
- **Diagnostics added:** `SUPPRESSION_CHANGED` (`PlaybackService.onEvents` on `EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED`) and `ROUTE_DEVICES_ADDED`/`ROUTE_DEVICES_REMOVED` (`MultiOutputEngine` audio device callbacks), both INFO-level.
- **Verification (0025865CN000478):** bypass + hardened builds both ran backgrounded with continuous advancement and clean track transitions. Hardened build: **4.5+ min backgrounded, zero stalls, still `PLAYING`** at the end — versus the previous build's 5-minute silence at one position. `:app:assembleDebug` green; APK installed.

**Persistent rotated playback diagnostics** — completed (on-device verified) and retained as the observability layer that led to the fix above. Playback background-stop diagnostic logging was added *without* pre-committing to a fix direction, so the failure was observable.

- `:domain`: `PlaybackLogLevel` / `PlaybackLogger` contract + `DiagnosticsSnapshot`; `PlaybackController`/`PreferencesRepository` gained `diagnosticsState` (live `StateFlow`) and `setPlaybackDebugLogging`; `AppPreferences.playbackDebugLogging`.
- `:data`: `PlaybackLoggerImpl` writes app-private `filesDir/diagnostics/playback.log`, rotating `.1`/`.2` at 3 MB each (bounded), corrupt-tolerant, IO + `Mutex`, DEBUG gated by the toggle.
- `:service`/`:app`: entry format `timestamp | level | component | event | detail`; components SERVICE/CONTROLLER/EQUALIZER/MULTI_OUTPUT/LOGGER/APP; logs player/session/audio-session/equalizer lifecycle, every play/pause/toggle/seek/next/prev/jump/shuffle/repeat command + result, state transitions with isPlaying/playWhenReady/suppression/position/buffered, service lifecycle, and APP_FOREGROUND/APP_BACKGROUND via `ProcessLifecycleOwner`. `PLAYBACK_HEALTH` throttled ~1/s; `PLAYBACK_STALLED` on ≥10 s no-advance; errors with full stack traces.
- `:features:settings`: Playback Diagnostics screen (live service/session/player/focus/error/stall fields, DEBUG toggle, View/Export/Clear).
- **Background-repeat test (3×, on-device) findings (evidence only):** backgrounding a playing session causes audio-focus suppression (`isPlaying=false, playWhenReady=true, suppression=1`) — **intermittent**: Round 1 stop persisted, Round 2 auto-recovered ~1 s, Round 3 played to natural ENDED. On return the player often auto-resumes while the button still shows Play, reproducing the "second press / wrong icon" symptom.

## Resume Playback Completed (previous)

**Playback session persistence / "Resume playback"** — completed (on-device verified).

Playback state (current song, queue, position, repeat/shuffle/speed) is now persisted to Room so the app restores the last session on every launch instead of starting empty. Before this, nothing survived the process dying (app swipe-away, force-stop, service kill): the controller's in-memory queue was lost and `connect()` built into an empty service player, so `currentSong=null` and the Mini Player never reappeared. Media3 1.6.1 has no built-in playback-state persistence, so this was implemented manually.

- **Persistence layer (Room v4→v5):** new `SavedPlaybackState` domain model + `PlaybackStateRepository`; single-row `playback_state` table (`PlaybackStateEntity` + DAO), `MIGRATION_4_5`, exported schema `5.json`; `PlaybackStateCodec` (escaped string serializer) + `PlaybackStateMappers` + Room-backed `PlaybackStateRepositoryImpl`, bound via Hilt. 9 new unit tests (round-trip, empty, malformed/escaped input).
- **Controller (`:service`):** on `connect()` with no pending external URI and an empty queue it restores the last session — verifies the current file still exists (`ContentResolver`; if gone it clears the stale state), rebuilds the queue dropping missing files, re-derives the index, clamps the position, and applies `setMediaItems` + shuffle/repeat/speed + `prepare()` with **no** `play()` (restores paused). Saves on events (pause/seek/song-change/modes/speed/queue + every media-item transition), periodically every 3 s while playing, and via `flushPlaybackState()` from `PlaybackService.onDestroy`/`onTaskRemoved`. External `ACTION_VIEW` file playback is excluded from persistence.
- **Behavior:** next launch shows the Mini Player with the previous song and a paused state; pressing play resumes from the saved position; deleted files are skipped instead of leaving a dead player.

**Verification (A001T, Android 16 / SDK 36):** full `testDebugUnitTest` suite + `assembleDebug` green; Room v4→v5 migration ran cleanly (no exceptions, correct `playback_state` schema); force-stop then relaunch → `dumpsys media_session` shows `PAUSED(2)` at the exact saved position (e.g. 96576 ms) with the same item, the Mini Player bar renders the restored song with a Play (paused) state, and pressing play resumes the advancing playhead; external-URI playback writes no saved session; logcat clean, no crashes.

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
  - `:features:player` module: Hilt `PlayerViewModel` (live `PlaybackState` + transport action helpers), `TimeFormat`, `MiniPlayerBar` (artwork/title/artist/play-pause, thin playback-progress bar, tap-to-open), `NowPlayingScreen` (artwork, drag-commit seek bar with time labels, previous/play/next, shuffle/repeat/speed controls, scrollable queue with current-item highlight and row-tap jump).
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

- **Sprint 13:**
  - `SmartPlaylistRule` sealed interface (`Favorites`, `MostPlayed`, `RecentlyAdded`, `RecentlyPlayed`, `NeverPlayed`, `Genre(name)`, `Artist(id, name)`) with a string codec (`encode`/`parse`, inverse, colon-safe) in `:domain`; `Playlist.rule` + `isSmart`; `PlaylistRepository.createPlaylist(name, rule?)`.
  - `:core:database` v3: `playlists.rule` column, `PlaylistDao.observeRule`, `SongDao` smart queries (`observeNeverPlayed` / `observeByGenre` / `observeByArtist`), `MIGRATION_2_3`.
  - `PlaylistRepositoryImpl` re-derives smart playlist contents and counts live (rule-keyed `flatMapLatest` + folded combine of count flows, `SMART_PLAYLIST_LIMIT = 100`).
  - `PlaylistsScreen` smart FAB + `SmartPlaylistDialog` (rule radios + Genre/Artist value dropdowns); smart rows show an AutoAwesome icon and `Label · N`; smart detail is read-only (no Add songs / drag / row options; overflow shows Rename/Delete only).
  - `SmartPlaylistRuleTest` (12 tests, `:domain`); version bumped to 0.13.0 (versionCode 13).
  - Verified on device (1789-song library, real v2→v3 migration): no Room errors on upgrade; `Favorites · 2` smart playlist live-updates 2→1→2 when favorites are toggled; Genre dropdown path works (`Genre · Unknown · 1789`); manual playlists keep their editable behavior; delete works for both types; rule persists across force-stop restart; logcat clean, no crashes.

- **Sprint 14:**
  - New `:features:filemanager` module (depends on `:core:ui` + `:domain` only): `FileDetailsViewModel` (reactive single-song observe + one-time on-demand enrichment), `FileDetailsScreen` (artwork/header, metadata card, red **Delete from device** button → in-app confirm → API 30+ `MediaStore.createDeleteRequest` system dialog via `StartIntentSenderForResult`, <30 `ContentResolver.delete` fallback, error dialog), pure `FileFormat` helpers (resolve/details/bytes/duration/bitrate/sample rate/channels/date) + 16 unit tests.
  - `:domain`: `AudioFileDetails` model; `LibraryRepository` gained `observeSong` / `readFileDetails` / `deleteSongsFromDatabase`.
  - `:data`: `AudioFileMetadataReader` (@Singleton `MediaExtractor` over the song content Uri — sample rate, channel count, bitrate, mime; null on failure) wired into `LibraryRepositoryImpl`.
  - Shared UI: `SongRow` and `PlaylistSongRow` gained an optional "File details" overflow menu (smart-playlist rows show it even though move/remove stay hidden).
  - `AppRootScreen` hosts the File details overlay and threads `onOpenFileDetails` through Library, Search, Favorites, Playlists and Statistics; version bumped to 0.14.0 (versionCode 14).
  - **Library count fix**: the header subtitle previously used the last scan's frozen `scanState.songCount`; it now uses the live `songs.size`, so the count reflects deletions immediately.
  - Verified on device (1789-song library): details render from Library, Favorites and a smart-playlist detail with real extractor values (48 kHz / Mono / 64 kbps; test WAV 86.2 KB / 00:01); end-to-end delete of a disposable test WAV — system confirmation → file gone from disk → Room purged → library 1790→1789 live, search results drop the row, favorites/smart playlist untouched; 72 unit tests + lint (0 errors) green; logcat clean, no crashes.
- **Sprint 15:**
  - New `:features:folders` module (depends on `:core:ui` + `:domain` only): `FolderManagerViewModel` (single `FolderManagerUiState` `MutableStateFlow`, reactive cards via `combine(observeFolders, observeSongs)`, per-folder stats from `FolderStats.forFolder`, scan-switch toggles that create/remove EXCLUDED rules + reconciliation rescan, suggestions refreshed after every completed scan), `FolderManagerScreen` (search/sort, folder cards with stats + scan switch + "Excluded" chip + Refresh/Open folder/Statistics actions, stats dialog, suggestion cards, empty state, Add folder → SAF picker → adds as EXCLUDED), and the first-run `FolderWizardHost` (Exclude recommended / Review / Skip, once-only via `folder_wizard_shown`, auto-hides after the scan). `TreePathResolver` (SAF tree Uri → absolute path) + 7 tests.
  - `:domain`: `LibraryFolder` + `LibraryFolderType`, `LibraryFolderRepository` contract, `FolderRules` + `FolderRulesMatcher` (case-insensitive recursive containment; excluded always wins; empty included ⇒ whole device) + 13 tests.
  - `:core:database` v4: `library_folders` table + DAO, `MIGRATION_3_4`, exported schema `4.json`.
  - `:data`: `LibraryFolderRepositoryImpl` (purges newly-hidden songs + reconciliation rescan on rule changes), `MediaLibraryScanner` folder-rule filtering (two-pass: allowed-id collection, then enriched upsert of allowed rows only), `NonMusicFolderDetector` + `NonMusicFolderClassifier` (name + short-clip heuristics; recursive **music-container guard**; 18 tests), `MediaStoreWatcher` (@Singleton, debounced content observer, idempotent start).
  - `:app`: Settings gained a Folder Manager row (`onOpenFolderManager`); `AppRootScreen` hosts the folder-manager screen + wizard overlays; `MainActivity` starts `MediaStoreWatcher`; version bumped to 0.15.0 (versionCode 15). Also fixed pre-existing lint `@OptIn(UnstableApi::class)` on `PlaybackService`/`PMusicMediaButtonReceiver` and the API-33 `getPackageInfo` guard in the About row.
  - Verified on device (1789-song library): the detector initially suggested excluding the SD card's music container (`…/Recordings (1)`, which recursively holds 1,775 songs) — excluding it wiped the library to 14 songs, which exposed the false positive. The recursive music-container guard fixed it: the wizard now suggests only the genuine recording folders (12 internal + 4 SD card files), **Exclude recommended** purged exactly those 16 (1789 → **1773**, all music intact), Add folder / card switch / actions menu / rules + library persistence across force-stop all verified, playback still starts after the lint fixes (STATE_PLAYING, position advancing), 114 unit tests + lint (0 errors) green, logcat clean, no crashes.
- **Playback session persistence / "Resume playback":**
  - `:domain`: `SavedPlaybackState` model (queue, current index, media id/uri, title/artist/album/artwork, position/duration, speed, repeat, shuffle, wasPlaying, savedAtNanos, `isEmpty`) + `PlaybackStateRepository` contract.
  - `:core:database` v5: `PlaybackStateEntity` + `PlaybackStateDao` (get/upsert/clear), `MIGRATION_4_5`, exported schema `5.json`, DAO binding in `DatabaseModule`.
  - `:data`: `PlaybackStateCodec` (string serializer escaping `\n`/field-separator/escape chars) + `PlaybackStateMappers` + Room-backed `PlaybackStateRepositoryImpl` (IO dispatcher, skips empty saves) + 9 unit tests, bound in `RepositoryModule`.
  - `:service`: `Media3PlaybackController` restores the last session on `connect()` (empty queue, no external URI) — `mediaExists()` check drops disappeared files, index re-derivation, position clamp, `setMediaItems` + shuffle/repeat/speed + `prepare()` (no auto-play, stays paused); saves via `saveOnEvent` / `maybePeriodicSave` (3 s while playing) / `flushPlaybackState()`; `PlaybackService.onDestroy`/`onTaskRemoved` flush. `:core:media` `Song.contentUri()` reused for save/restore lookups. External `ACTION_VIEW` playback excluded from all save paths.
  - Verified on device: force-stop then relaunch restores PAUSED at the exact saved position with the correct song/queue, the Mini Player reappears with the restored track, and Play resumes; migration clean; no crashes.

## Pending Features

- Optional future modules (Wi-Fi sync, online search) — designed as pluggable, not built.

## Known Issues

- Genre metadata is frequently absent from MediaStore on real devices, so songs fall back to "Unknown" (handled gracefully). No crashes.
- Bitrate / sample rate / channel count are not reliably indexed by MediaStore; the File details screen enriches them on demand via `MediaExtractor` (falling back to the indexed values when the file cannot be read). No crashes.
- Noted during Sprint 3 device testing: scripted `adb input tap` sequences occasionally deliver duplicate taps; single physical taps are handled correctly (no app-side defect).
- The A001T audio policy does not expose simultaneous output routing (see the multi-output diagnostic report), so Multi-Output Audio is correctly reported as UNSUPPORTED there; single-output playback continues to work normally.
- Playback sessions always restore **paused** on launch (by design — no auto-resume, no new setting). Only library playback (a queue loaded through the app) is persisted; opening an external file via `ACTION_VIEW` is not persisted.

## Next Sprint

Next roadmap item from Pending Features (optional future modules — Wi-Fi sync, online search).
