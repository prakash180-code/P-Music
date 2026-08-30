# Changelog

All notable changes to P-Music are documented here.

## [Diagnostics instrumentation] - 2026-08-30

### Persistent rotated playback diagnostics (`:domain`, `:data`, `:core:datastore`, `:service`, `:app`, `:features:settings`)

**Goal:** make the reported "playback stops when the app is backgrounded (UI may still show playing, Play sometimes needs two presses)" failure observable before fixing it. No playback-fix logic was changed — this adds logging only.

**Logging contract (`:domain`):**
- `PlaybackLogLevel` (DEBUG/INFO/WARN/ERROR with `label`/`isEnabledFor`) and `PlaybackLogger` interface (`level`, `setDebugEnabled`, `log`, `debug`/`info`/`warn`/`error`, `error(component,event,th)`, `read`/`export`/`clear`) plus a `DiagnosticsSnapshot` data model.
- `PlaybackController` / `PreferencesRepository` gained a live `diagnosticsState: StateFlow<DiagnosticsSnapshot>` and `setPlaybackDebugLogging`; `AppPreferences` gained `playbackDebugLogging`.

**Logger implementation (`:data`):** `PlaybackLoggerImpl` (`@Singleton`) writes to app-private `filesDir/diagnostics/playback.log` (no permissions, survives activity/player/service recreation + background), rotating to `playback.log.1` / `playback.log.2` at 3 MB/file (~2-5 MB bounded), `Mutex` + `Dispatchers.IO`, corrupt-tolerant, and gates the DEBUG level on the `playbackDebugLogging` toggle.

**Instrumentation (`:service` / `:app`):** every entry is `timestamp | level | component | event | detail`, with component tags SERVICE / CONTROLLER / EQUALIZER / MULTI_OUTPUT / LOGGER / APP. Logged: player/session create & release (identity via `System.identityHashCode`), audio-session id + changes, equalizer attach/detach/rebind/probe failures, media-item transitions + song changes, state transitions (IDLE/BUFFERING/READY/ENDED) with `isPlaying`/`playWhenReady`/`suppression`/`position`/`buffered`, **every** PLAY/PAUSE/TOGGLE/SEEK/NEXT/PREV/JUMP/SHUFFLE/REPEAT command + result (before/after state), service lifecycle (ON_CREATE/ON_DESTROY/ON_TASK_REMOVED/PLAYER_CREATED/PLAYER_RELEASED/MEDIA_SESSION_CREATED), and **APP_FOREGROUND/APP_BACKGROUND** via `ProcessLifecycleOwner` (`AppLifecycleObserver`, registered from `PMusicApplication`). `PLAYBACK_HEALTH` is throttled to ~1/s and `PLAYBACK_STALLED` fires when `isPlaying` but position does not advance ≥ 10 s. All player errors log full stack traces.

**Diagnostics screen (`:features:settings`):** a Settings→Playback Diagnostics row (hosted by `AppRootScreen`) showing live Playback Service / MediaSession / Player / state / position / audio-session / focus / last error / last event / last stall, a "Playback Debug Logging" DEBUG toggle, and View Logs / Export / Clear actions (export as `p_music_playback_log.txt` via the share sheet). Diagnostics only — no personal/account/audio data (song title/artist OK).

**Verification (device 0025865CN000478, Android 16 / SDK 36, Media3 1.6.1):** full `:data`/`:domain`/`:core:datastore`/`:service` compile + `:app:assembleDebug` + `testDebugUnitTest` green; APK installed; on device the log is created in app-private storage and records service lifecycle, audio session 251961, equalizer attach, controller connect, session restore, song change and READY transitions; `ProcessLifecycleOwner` logs every foreground/background transition.

**Background-repeat test (3×, song "Aalapikkey Ummak" ~188 s) findings — evidence only, no fix:**
- Backgrounded while playing then observed via the log: the player loses audio focus and is paused with `isPlaying=false, playWhenReady=true, suppression=1`.
- Behavior is **intermittent**: Round 1 the stop persisted until foreground; Round 2 it auto-recovered in ~0.8 s; Round 3 the song played through to a natural `ENDED` with no stop.
- On returning to the app, playback often **auto-resumes** (because `playWhenReady=true` is retained) while the mini-player button still renders **Play** — that mismatched state reproduces the reported "wrong icon / second press" symptom.

## [0.15.1] - 2026-08-30

### Playback session persistence / "Resume playback" (`:service`, `:data`, `:core:database`, `:domain`)

**Problem:** nothing about the playback session was persisted. When the process died (app swipe-away, force-stop, service kill) the controller's in-memory queue was lost and on the next open `connect()` built a controller into an empty service player — `currentSong=null`, so the Mini Player never reappeared and playback always started from scratch. Media3 1.6.1 offers no built-in playback-state persistence, so this had to be implemented manually.

**Persistence layer (Room-backed, DB v4→v5):**
- `SavedPlaybackState` (`:domain`) — queue (list of `Song`s), `currentQueueIndex`, `mediaId`, `mediaUri`, title/artist/album/artwork, `positionMs`, `durationMs`, `playbackSpeed`, `repeatMode`, `shuffleEnabled`, `wasPlaying`, `savedAtNanos`, and an `isEmpty` helper for "nothing to save".
- `PlaybackStateRepository` (`:domain`) contract — `save` / `load` / `clear`.
- `PlaybackStateEntity` + `PlaybackStateDao` + `MIGRATION_4_5` (+ registered in `DatabaseModule`), `PMusicDatabase` bumped to version 5 with the new single-row `playback_state` table and an exported schema `5.json`; `PlaybackStateCodec` (pure string serializer with escaped `\n`/field-separator/escape chars), `PlaybackStateMappers`, `PlaybackStateRepositoryImpl` (IO dispatcher, skips empty saves), binding in `RepositoryModule`.
- 9 new unit tests in `:data` (`PlaybackStateCodecTest`) covering round-trips, empty state, malformed lines, and embedded newlines/escape chars.

**Service / controller (`:service`):**
- `Media3PlaybackController` — injects `PlaybackStateRepository`; on `connect()` with no pending external URI and an empty queue it calls `restoreLastSession(player)`; `restore()` verifies the current file still exists (`mediaExists` via `ContentResolver` — if gone it clears the saved state), rebuilds the queue dropping any missing files, re-derives the index, clamps the position to `0..duration`, and applies `setMediaItems(items, index, position)` + shuffle/repeat/speed + `prepare()` — deliberately **no** `play()`, so the session always comes back paused.
- Saves: `saveOnEvent` (on pause/seek/song-change/mode/speed/queue actions and every `EVENT_MEDIA_ITEM_TRANSITION`), `maybePeriodicSave` (throttled every 3 s while playing), `flushPlaybackState` (final flush).
- `PlaybackService` — calls `flushPlaybackState()` from `onDestroy` / `onTaskRemoved` so the last known-good song/position survive an app or service kill.
- External-file playback (`ACTION_VIEW` of a URI) is deliberately excluded from all save paths (`pendingExternalUri` / `externalPlayback` guards).

**Behavior:** on the next launch the previous song, queue, position, repeat/shuffle/speed are restored into a paused player; the Mini Player reappears with the restored song and pressing Play resumes from the saved position. A song whose file has been deleted is skipped and the index corrected rather than leaving a dead player.

**Verification (physical device A001T, Android 16 / SDK 36, Media3 1.6.1):** full `testDebugUnitTest` suite green + `assembleDebug` OK; Room v4→v5 migration ran cleanly (no exceptions, `playback_state` table created with correct schema); on device after force-stop + relaunch `dumpsys media_session` shows `PAUSED(2)` at the exact saved position (e.g. 96576 ms) with the same item; pressing play resumes and the playhead advances; the Mini Player bar renders above the nav bar with the restored song and a Play (paused) state; external-URI playback does not write a saved session.

## [0.15.0 follow-up] - 2026-08-28

### Playback / audio architecture fixes (`:service`) — on-device verified

**1. Audio stopped after ~10 s while the UI kept showing "playing"**
- Root cause: `MultiOutputAudioSink.setListener()` was a complete no-op — it never stored the `AudioSink.Listener`, so every callback (underruns, `AudioSinkError`, position discontinuities) was silently swallowed. The renderer kept reporting a healthy PLAYING state while the underlying audio was already dead, and the listener that would have let the service re-sync or surface a sink error never fired.
- Fix (`MultiOutputAudioSink.kt`): added a `listener: AudioSink.Listener?` field; `setListener()` now stores it; `createChildLocked()` wires an anonymous child listener that forwards `onPositionDiscontinuity`, `onUnderrun(bufferSize, elapsedTimeSinceFirstFeedUs, delaySinceStartOfPlay)`, `onSkipSilenceEnabledChanged` and `onAudioSinkError` to the primary controller (guard: only the primary child — `children[0].sink === sink` — forwards, so multi-output fans don't double-report).

**2. Enabling the equalizer caused a volume drop**
- Root cause: `AudioFxEqualizerEngine.probeLayoutIfNeeded()` initialized every band gain from the global output session (session 0) via `readGains(probe)`. That session can carry non-zero system EQ / Dolby levels, so the "default" curve its bands reported was not neutral — the moment the equalizer attached, it applied those non-zero gains and audibly changed (dropped) the volume.
- Fix (`AudioFxEqualizerEngine.kt`): each band now initializes to the neutral midpoint `range[0] + (range[1] - range[0]) / 2` instead of `readGains(probe)`. With a flat 0 dB curve the effect is transparent and no longer alters volume when enabled. A user's own persisted custom curve still applies on top (correct override behaviour).

**3. Playback stuttered / stopped when switching apps**
- Root causes: (a) `Media3PlaybackController.connect()` advanced past a stale/dead `MediaController` (created by a previous session in a now-restarted process) and kept using it; (b) the service never acquired a wake lock, so the CPU could sleep under a dark screen and pause/glitch audio.
- Fix (a) (`Media3PlaybackController.kt`): `connect()` now detects a stale controller (`controller != null && !controller.isConnected`), logs a warning, removes the listener, releases and nulls it, then rebuilds a fresh controller.
- Fix (b) (`PlaybackService.kt`): the ExoPlayer now calls `setWakeMode(C.WAKE_MODE_NETWORK)` (plus `import androidx.media3.common.C`), keeping the CPU awake with the screen off.

**Verification (physical device A001T, Android 16 / SDK 36, Media3 1.6.1):**
- Built + installed (`assembleDebug`), `:service:testDebugUnitTest` and the full `testDebugUnitTest` suite all green.
- **Bug 1:** `dumpsys media_session` shows `state=PLAYING(3)`, `error=null`; the playhead continuously advances well past the old ~10 s failure point (observed 59 s → 76 s → 121 s).
- **Bug 3:** with the app backgrounded (Home) and with the **screen off** (`KEYCODE_SLEEP`), the playhead keeps advancing (e.g. 112684 ms → 121699 ms with the screen off) — playback continues and the CPU stays awake.
- **Bug 2:** "Reset to flat" yields **0 dB on all 5 bands**; re-enabling the equalizer with the flat curve leaves playback smooth (`error=null`, position advancing) — no volume drop.
- **Regression:** the Multi-Output capability probe still runs cleanly (`capabilities probed: … level=UNSUPPORTED … availableDevices=[A001T speaker]`) with the sink-listener change in place; no crashes.

## [0.15.0] - 2026-08-07

### Sprint 15 — Library Folder Manager (`:features:folders`)

**New `:features:folders` module:**
- `FolderManagerViewModel` (Hilt) — combines the persisted folder rules with the live song table so every card shows real per-folder statistics. State is a single `FolderManagerUiState` `MutableStateFlow` (cards, restricted mode, search query, sort, non-music suggestions, stats dialog, busy + message); cards re-derive via `combine(observeFolders, observeSongs)` → `deriveFolders`/`applySortAndFilter` (sort by name/songs/modified/size) with a `lastFolderUis` cache for instant search/sort; every card has a scan switch that creates or removes an EXCLUDED rule (recursive) and triggers a reconciliation rescan; `refreshSuggestions` re-runs after every completed scan so new recordings appear and excluded ones disappear.
- `ui/FolderManagerScreen.kt` — header + mode banner ("Scanning the whole device except excluded folders." / restricted-mode text), search field, sort menu, folder cards (icon, name, resolved path, `N songs · size`, a scan switch, an "Excluded" chip when off, "Folder actions" menu with Refresh / Open folder / Statistics), a per-folder statistics dialog, the non-music suggestion cards ("These folders don't appear to contain music" with Exclude / Dismiss), an empty state ("No folders configured…"), and an **Add folder** action that opens the SAF folder picker → system persistable-grant dialog → adds the folder as EXCLUDED.
- `ui/FolderWizardDialog.kt` — first-run wizard ("We found folders that usually don't contain music…") with **Exclude recommended** / **Review** / **Skip**, shown once (`folder_wizard_shown` preference) and auto-dismissed after a scan completes.
- `TreePathResolver.kt` — pure, unit-tested mapping of a SAF tree Uri to an absolute path (`primary` → `/storage/emulated/0`, `XXXX-XXXX` → `/storage/<id>`, otherwise null); 7 tests.
- `build.gradle.kts` (depends on `:core:ui` + `:domain` only) + `AndroidManifest.xml`, registered in `settings.gradle.kts`.

**Domain (`:domain`):**
- `LibraryFolder` (`id`, `folderPath`, `displayName`, `type`, `enabled`, `recursive`, `lastScanned`, `songCount`, `dateAdded`) + `LibraryFolderType` (INCLUDED/EXCLUDED); `LibraryFolderRepository` contract (`observeFolders`, `addFolder`, `setEnabled`, `setType`, `removeFolder`, `discoverNonMusicFolders`).
- `FolderRules` + `FolderRulesMatcher` — pure path rules shared by the scanner/repository/UI: `normalize` (trim, flip separators, drop trailing slash), `isUnder` (recursive, case-insensitive containment), `isAllowed` (excluded always wins; empty included ⇒ whole device minus excluded; else only under included). 13 tests.

**Core database (`:core:database`):**
- `library_folders` table (`LibraryFolderEntity`, `LibraryFolderDao` with reactive `observeAll` + mutation DAOs), `MIGRATION_3_4`, `PMusicDatabase` bumped to version 4, exported schema `4.json`.

**Data (`:data`):**
- `LibraryFolderRepositoryImpl` (@Singleton) — persists rules and resolves suggested paths; on every rule mutation it purges songs that the new rules hide and triggers a reconciliation rescan so newly-included files come back.
- `MediaLibraryScanner` now applies the folder rules **before** any metadata work: pass 1 collects the allowed MediaStore ids via `FolderRulesMatcher.isAllowed`, pass 2 upserts only those rows, stale rows are removed — so excluded songs are never inserted, never genre/art-enriched, and their Room rows disappear.
- `NonMusicFolderDetector` + `NonMusicFolderClassifier` — MediaStore pass grouped by parent folder; a folder is suggested when its name matches well-known non-music locations (call/voice recordings, messengers, notifications, ringtones, alarms) or it holds mostly short clips (`< 15 s`, at least 3 files). The classifier gained a **recursive music-container guard** (see the fix below). 18 tests.
- `MediaStoreWatcher` (@Singleton, Hilt) — `registerContentObserver` on the MediaStore audio URI, debounced, idempotent `start()`, triggers a forced rescan on change.

**App shell (`:app`):**
- `AppRootScreen` hosts `FolderManagerScreen` (Settings → Library → Folder Manager) and the wizard as overlays (`showFolderManager` state, reset on nav taps); `SettingsScreen` gained `onOpenFolderManager` and a **Folder Manager** row in the Library section (above Statistics); `MainActivity` starts `MediaStoreWatcher` after playback connects; version bumped to 0.15.0 (versionCode 15).

**Detection fix found during on-device verification:**
- The detector suggested excluding `/storage/7FDE-1813/Recordings (1)` purely because its name matched "recordings" — but on this device that folder recursively held **1,775 of the 1,789 songs** (a "Call till 21.11.24" subfolder is the real music library). Excluding it wiped the library to 14 songs. Fix: `NonMusicFolderClassifier` now treats a folder with ≥ 20 song-shaped files (1–10 min each) as a **music container** that must never be suggested; the detector counts song-shaped files across each folder's whole subtree before proposing anything. Also added `records` to the known non-music names (this device's meeting recordings live in `Music/Recorder/records`).

**Verification (physical device, SDK 33, 1789-song library):** built + `lintDebug` (0 errors) + all unit tests green (**114**, incl. 42 new: `FolderRulesTest` 13, `NonMusicFolderClassifierTest` 18, `LibraryFolderMappersTest` 4, `TreePathResolverTest` 7). On device: first-run wizard suggests only genuine recording folders (12 internal + 4 SD card files); **Exclude recommended** purged exactly those 16 and the library went 1789 → **1773** with all music intact (SD library `…/Recordings (1)/Call till 21.11.24` fully preserved); the two EXCLUDED rules persist with correct resolved paths and `0 songs`; Add folder works end-to-end (SAF picker → system "Allow P-Music to access folder?" → EXCLUDED card appears); the card switch adds/removes the EXCLUDED rule and the Refresh / Open folder / Statistics actions work; rules + library survive a force-stop restart (1773 songs, no wizard re-show); playback still starts from the Library after the lint-driven `@OptIn(UnstableApi)` fixes on `PlaybackService`/`PMusicMediaButtonReceiver` (session `STATE_PLAYING`, position advancing, media button receiver restored); logcat clean, no crashes.

**Sprint 15 follow-up (cleanup + polish):**
- Removed dead repository API: `renameFolder`, `refreshFolderStats` and `rulesSnapshot` had no callers left after the screen reconciliation; `purgeFolder` dropped from the contract (now private to the impl); the now-unused `LibraryFolderDao.rename` was deleted too. Build + tests still green.
- `MiniPlayerBar` gained a thin playback-progress bar along its top edge (primary colour over the track, driven by `PlaybackState.positionMs`/`durationMs`).
- Folder Manager empty-state copy corrected to match the exclusion-only flow (no longer claims folders can "include only selected ones"), and a "No folders match your search" hint shows when a query has no hits.
- Statistics empty-state icon sized consistently (`size(64.dp)` instead of height-only).
- Verified on device (moto g32, 2-song library): folder-manager round-trip — toggle off → purge → **0 songs** in library, exclusion survives a force-stop, toggle on → rescan restores **2 songs**; mini player renders with the toggle and live progress while playing; `lintDebug` (0 errors) + all unit tests green.

## [0.14.0] - 2026-08-07

### Sprint 14 — File Management (`:features:filemanager`)

**New `:features:filemanager` module:**
- `FileDetailsViewModel` (Hilt) — observes the selected song reactively from Room (`LibraryRepository.observeSong` via `flatMapLatest`, so the row disappears the moment it is deleted) and enriches it once on open with the container details MediaStore does not index; exposes `deleting` / `deleted` state and drives the Room purge after the system deletion is confirmed.
- `ui/FileDetailsScreen.kt` — full-screen overlay (header back + system back): artwork + title/artist header, a metadata card (`DetailRow` label/value rows), and a red **Delete from device** button. The delete flow is: in-app confirmation dialog → **API 30+** `MediaStore.createDeleteRequest` system confirmation launched via `StartIntentSenderForResult` (Room is purged only on `RESULT_OK`, cancelled otherwise); **below API 30** it falls back to a direct `ContentResolver.delete`; failures show a "Could not delete" dialog.
- `FileFormat.kt` — pure, unit-tested helpers: `resolveFileDetails` (prefers freshly extracted values, falls back to indexed ones so zeros never hide real data), `detailRows` (Format/Size/Duration/Bitrate/Sample rate/Channels/Year/Genre/Track/Plays/Date added/Date modified/Path), `formatBytes`, `formatDuration`, `formatBitrate`, `formatSampleRate`, `formatChannelCount`, `formatDate`.
- `FileFormatTest` — 16 tests (bytes/duration/bitrate/sample rate/channels/date formatting, extraction-vs-fallback resolution, row rendering incl. unknown-row suppression).
- `AndroidManifest.xml` + `build.gradle.kts` (depends on `:core:ui` and `:domain` only; no `:data` dependency — the delete request is built in the feature from the song's content Uri) and registration in `settings.gradle.kts`.

**Domain (`:domain`):**
- `AudioFileDetails` (sampleRateHz, channelCount, bitrate, mimeType); `LibraryRepository` gained `observeSong(songId): Flow<Song?>`, `readFileDetails(song)` and `deleteSongsFromDatabase(songs)`.

**Data (`:data`):**
- `AudioFileMetadataReader` (@Singleton) — `MediaExtractor` over the song's content Uri, reading the first audio track's `KEY_SAMPLE_RATE` / `KEY_CHANNEL_COUNT` / `KEY_BIT_RATE` / `KEY_MIME`; returns null on any failure so callers fall back.
- `LibraryRepositoryImpl` now injects the reader; `readFileDetails` runs on `Dispatchers.IO`, `deleteSongsFromDatabase` deletes by id (playlist membership cascades via FK).

**Shared UI (`:core:ui`):**
- `SongRow` gained an optional `onFileDetails` callback: when set, a MoreVert overflow menu ("File details") appears next to the favorite toggle; when null (existing callers) nothing changes.
- `PlaylistSongRow` gained `onFileDetails`; its overflow now renders "File details" for read-only smart playlists too (move/remove stay hidden there).

**App shell (`:app`):**
- `AppRootScreen` hosts `FileDetailsScreen` as a full-screen overlay (`showFileDetails: Long?` state, reset on nav taps and after a successful delete) and threads `onOpenFileDetails = { showFileDetails = it.id }` through Library, Search, Favorites, Playlists and Statistics.
- Version bumped to 0.14.0 (versionCode 14).

**Library count fix (`:features:library`):**
- The header subtitle used `scanState.songCount` — the last scan's snapshot, which went stale as soon as the library changed afterwards (deleting a song exposed this: the list updated but the count did not). It now always uses the live `songs.size`; the empty-state helper no longer prints a stale positive count.

**Verification (physical device, SDK 33, 1789-song library):** built + `lintDebug` (0 errors) + all unit tests green (72, incl. 16 in `:features:filemanager`). File details opens from the Library All Songs list, Favorites, and a smart-playlist detail (read-only rows show only "File details"), rendering path/size/duration/bitrate/sample rate/channels via the extractor (e.g. a recorded call: 48 kHz / Mono / 64 kbps, and a generated test WAV: 86.2 KB / 00:01) with unknown rows hidden. Delete was exercised end-to-end on a disposable test WAV: in-app confirm → system "Allow P-Music to delete this audio file?" dialog → Allow removed the file from disk (`/sdcard/Music/…` gone) and purged the Room row (library 1790 → 1789, live in the header after the count fix), with the search results dropping the row immediately; the device library and the Favorites smart playlist (`Favorites · 2`) were left untouched. Logcat clean, no crashes.

## [0.13.0] - 2026-08-07

### Sprint 13 — Smart playlists (`:features:playlist`)

**Domain (`:domain`):**
- `SmartPlaylistRule` sealed interface — `Favorites`, `MostPlayed`, `RecentlyAdded`, `RecentlyPlayed`, `NeverPlayed`, `Genre(name)` and `Artist(artistId, artistName)` — with a string codec: `encode()` yields `favorites`, `most_played`, `recently_added`, `recently_played`, `never_played`, `genre:<name>` and `artist:<id>:<name>`, and `parse(raw)` is its exact inverse (handles colons inside genre/artist names; malformed or unknown rules return null).
- `Playlist` gained a nullable `rule` string plus an `isSmart` helper; `PlaylistRepository.createPlaylist` accepts the optional `rule`.
- `SmartPlaylistRuleTest` — 12 tests covering encode/parse round-trips, colon-in-name handling and malformed-input rejection (the first `:domain` unit tests; `:domain` now has its own junit test implementation).

**Core database (`:core:database`):**
- `PlaylistEntity.rule` / `PlaylistProjection.rule` columns; `PlaylistDao` selects `p.rule AS rule` in every read and adds `observeRule(playlistId): Flow<String?>`; `SongDao` adds three smart queries — `observeNeverPlayed()` (playCount = 0), `observeByGenre(genre)` and `observeByArtist(artistId)` (all ordered by title).
- `MIGRATION_2_3` (`ALTER TABLE playlists ADD COLUMN rule TEXT`), `PMusicDatabase` bumped to version 3, `DatabaseModule` registers the migration.

**Data (`:data`):**
- `PlaylistRepositoryImpl` now injects both DAOs: `observePlaylistSongs` keys off `observeRule(…).flatMapLatest` so a playlist detail live-switches between the join table (manual) and the derived smart query; `observePlaylists` derives each smart playlist's live count by folding the per-rule `Flow<Map<Long, Int>>` of counts (the `combine(Iterable)` overload failed type inference, so counts are folded over 2-arity `combine`); `smartSongs(rule)` dispatches to the matching query with a `SMART_PLAYLIST_LIMIT = 100` cap.
- `PlaylistMappers.toDomain` gained an optional `smartCount`.

**UI (`:features:playlist`):**
- `PlaylistViewModel` adds `createSmartPlaylist(name, rule)` plus `genres` / `artists` flows for the value pickers.
- `PlaylistsScreen`: a `SmallFloatingActionButton` (AutoAwesome icon, stacked above the regular FAB) opens a `SmartPlaylistDialog` — name field, radio rule rows (`SmartRuleRow`), a value dropdown (`RuleValueDropdown`) that appears for Genre/Artist rules, Create disabled until name + rule are valid.
- `PlaylistItems`: smart playlists render an AutoAwesome icon with a `Label · N` subtitle (`playlistSubtitle`, where N is the live count); `PlaylistSongRow(readOnly = true)` hides the drag handle and overflow menu.
- Playlist detail for a smart playlist is read-only: no Add-songs button, empty state text points at the rule, and `PlaylistSongList(readOnly)` skips the drag `pointerInput` — while the overflow menu shows only Rename/Delete (no Add songs).

**App shell (`:app`):**
- Version bumped to 0.13.0 (versionCode 13).

**Verification (physical device, SDK 33, 1789-song library):** built + `lintDebug` + all unit tests green (`:domain` 12 new + `:data` 26). Fresh install migrated the DB to v3 with no Room errors. Created a **Favorites** smart playlist — list shows `Favorites · 2` (the device has exactly 2 favorites) and the detail renders the two songs read-only with a Play-all action; un-favoriting one song through the Favorites tab dropped the count to `Favorites · 1` live, and re-favoriting restored `Favorites · 2` (proves the count and list re-derive from the live query). Created a **Genre** smart playlist via the value dropdown (`Genre · Unknown · 1789`), confirming the picker path. The smart playlist overflow menu lists only Rename/Delete (no Add songs); a manual playlist still shows the normal `0 songs` subtitle and an editable detail with Add songs. Delete works for both types. The smart playlist (rule persisted in Room) survives a force-stop restart with the count intact. Logcat clean, no crashes.

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
