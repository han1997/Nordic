# Research: Audit of Audiobook & Video Modules

- **Query**: Comprehensive audit of audiobook and video modules for feature gaps, player UX, visual polish, and logic/data robustness, to drive implementation batches.
- **Scope**: internal (code audit)
- **Date**: 2026-08-31

## Summary

The music module is the visual/UX reference and is notably richer (now-playing dock, swipe-to-dismiss player, queue sheet, lyrics, thin custom slider). The audiobook and video modules are functional but lag in four areas: (1) audiobook **bookmarks are a dead feature** — a full `AudiobookBookmarkRepository` exists but is wired into zero UI/ViewModel; (2) there is **no persistent now-playing entry point** for audiobook/video — playback only lives in the full-screen player layer, and the dock's now-playing bar is music-only; (3) the video player has **no double-tap feedback / buffering indicator / remaining-time**, and its custom gesture slider uses the stock Material3 `Slider` while music uses a hand-rolled thin slider; (4) multiple robustness gaps around sync failures on close, ended-state handling, and page-size assumptions.

Priority: **P0** = dead/polish-gap features users can see immediately (bookmarks unused, no now-playing for AB/video, video buffering indicator). **P1** = gesture/UX and slider-consistency, visual parity. **P2** = edge-case robustness and state-machine hardening.

---

## Feature Gaps

### P0-1. Audiobook bookmarks exist but are completely unwired (dead feature)
- **File**: `app/src/main/java/com/nordic/mediahub/data/AudiobookBookmarkRepository.kt` (entire file) — full CRUD + max-50-per-item cap + per-item scoping. Grep confirms **only** references are in `data/AudiobookBookmarkRepository.kt` itself and `test/.../AudiobookBookmarkRepositoryTest.kt`. No `AudiobookPlayerScreen`, `AudiobookPlaybackViewModel`, or `AudiobookScreen` touches it.
- **Gap**: A polished audiobook player needs bookmark/chapter-marker support (tap a bookmark button at current position, jump to saved bookmarks, list them). The data layer is already built and tested — this is purely wiring + UI. High value, low effort.
- **Concrete**: Add bookmark add/list/jump actions to `AudiobookPlayerScreen` (a `bookmark` icon next to the seek controls), surface saved bookmarks in a sheet, persist via existing repository, seek to saved positions via existing `onSeek`.

### P0-2. No persistent now-playing entry point for audiobook / video (only music has one)
- **Files**: `ui/PlaybackDock.kt:254-352` `PolishedNowPlayingBar` binds only to `musicVM` `currentSong`/`isPlaying`. `MainActivity.kt:743-767` `PlaybackDockSlot` passes only `musicVM`. When an audiobook or video plays, playback only exists inside the full-screen `AudiobookPlayerLayer` / `VideoPlayerLayer` (`MainActivity.kt:709-723`). The dock (`resolveBottomDockPresentation`, `MainActivity.kt:63-72`) hides while any player layer is up, and when dismissed the user has **no way to return to AB/video playback** except re-navigating to the item.
- **Gap**: Music has a dock "now playing" bar that survives player close; audiobook/video have nothing equivalent. Audiobook especially benefits — it's a long-form, background-type listen.
- **Concrete**: Generalize the now-playing slot to render AB (cover, title, author, play/pause, chapter progress) and video (thumbnail/title, play/pause) when `hasPlayerLayer` is false but the respective engine has a session/video. Reuse the existing dock slot pattern.

### P0-3. Video: no buffering progress indicator, double-tap seek feedback, or remaining-time label
- **Files**: `ui/VideoPlayerScreen.kt` — center message shows "缓冲中" text only (line 188-192); `ui/VideoPlayerGestures.kt:48-60` double-tap seeks immediately with no visual feedback; controls show `visiblePosition`/duration but no remaining time (line 658-672).
- **Gap**: Mainstream players show a buffered-bar + a spinner/indicator, an overlay "±30s / ±10s" toast on double-tap, and a "-MM:SS" remaining label. All three are absent.
- **Concrete**: (a) expose buffer position from `VideoPlaybackEngine` (`player.bufferedPosition`) and render a secondary track on the slider; (b) on double-tap, show a transient overlay chip with the skip delta + target time; (c) replace/augment the duration label with remaining time.

### P1-4. Audiobook: no sleep timer
- **File**: `ui/AudiobookPlayerScreen.kt` — the controls row (222-271) has prev-chapter / -30s / play / +30s / next-chapter, no sleep-timer. `AudiobookPlaybackEngine.kt` has no timer concept.
- **Gap**: Sleep timer is a hallmark audiobook feature (stop after 10/20/30/45/60 min or end-of-chapter).
- **Concrete**: Add a sleep-timer button + bottom-sheet in `AudiobookPlayerScreen`; engine stops after N minutes or at next chapter boundary (reuse `resolveNextAudiobookChapterStartSeconds`, `AudiobookPlaybackEngine.kt:387-398`).

### P1-5. Video: no playback-speed control
- **File**: `ui/VideoPlayerScreen.kt:674-719` controls row has aspect-ratio / rewind / play / forward / fullscreen — no speed. `VideoPlaybackEngine` sets no `PlaybackParameters`.
- **Gap**: Video speed control (0.5x–2x) is common; audiobook already has it (`AudiobookPlaybackEngine.kt:33` speed list + `onCyclePlaybackSpeed`).
- **Concrete**: Add speed cycle button + expose speed in state; engine sets `player.setPlaybackSpeed`.

---

## Player Interaction UX

### P1-1. Audiobook/video sliders are stock Material3; music uses a custom thin slider (inconsistent)
- **Files**: `ui/AudiobookPlayerScreen.kt:192-206` uses `Slider` (Material3, default thickness/thumb). `ui/VideoPlayerScreen.kt:637-651` uses `Slider`. Music uses the hand-rolled `PlayerThinSlider` (`ui/MusicPlayerScreen.kt:919-989`).
- **Gap**: Visible inconsistency in the player chrome across the three modules; music is the established design language (thin 4dp track, 12dp thumb, `NordicShapes.full`).
- **Concrete**: Extract/share a thin-slider composable (or port `PlayerThinSlider` into a shared component) and use it in both the audiobook and video player for progress scrubbing.

### P1-2. Audiobook player has no swipe-to-dismiss / gesture-driven close (music does)
- **File**: `ui/AudiobookPlayerScreen.kt:94-275` — close is only the top-bar down-chevron (`AudiobookPlayerTopBar`, line 342-374). Music implements full swipe-to-dismiss (`ui/MusicPlayerScreen.kt:240-313`).
- **Gap**: Long-form listener UX strongly benefits from drag-to-close; also inconsistent with music.
- **Concrete**: Port the music swipe-to-dismiss gesture (vertical drag + threshold + spring rebound) into the audiobook player.

### P2-3. Video double-tap seek has no half-screen visual cue and uses fixed ±10s/±30s asymmetric defaults
- **File**: `ui/VideoPlayerGestures.kt:20-21,48-60` — left half = −10s, right half = +30s (asymmetric). No indicator shown.
- **Gap**: Users can't tell which side is which or how far it jumped. Also the music convention elsewhere is symmetric ±30s (audiobook), and video buttons use −10/+30 too (`VideoPlaybackEngine.kt:33-34`).
- **Concrete**: Standardize double-tap to a symmetric interval (e.g. ±10s, consistent with the visible rewind/forward buttons), and show a transient seek-delta overlay. (Note the UI buttons already expose −10/+30; aligning gesture + buttons + overlay is the fix.)

### P2-4. Video: controls auto-hide has no "tap to show remaining-time preview" and hides even while scrubbing ends too eagerly
- **File**: `ui/VideoPlayerScreen.kt:131-136` — the auto-hide `LaunchedEffect` keys on `scrubPosition`, so dragging keeps them visible, but there is no persistent buffering indicator and no interaction to re-show just the time.
- **Gap**: Minor; the auto-hide behavior itself is reasonable. Flagged as a P2 consistency item (see P0-3).

### P2-5. Audiobook chapter chip is non-interactive in the player (chapters are browsable only in detail)
- **File**: `ui/AudiobookPlayerScreen.kt:171-173` shows current chapter as a passive `MetaChip`; the only chapter navigation is prev/next buttons. `AudiobookScreen.kt:588-599` lists chapters in detail but rows are non-clickable (`AudiobookChapterRow`, line 796-816, no onClick).
- **Gap**: Users cannot tap a chapter row to jump to it from either the detail list or the player.
- **Concrete**: Make chapter rows tappable to `onSeek(chapter.startSeconds)` in the player (and/or open chapter list in the player). Reuses `resolveCurrentAudiobookChapter` infra.

---

## Visual / UI Polish

### P1-1. Video detail hero action buttons diverge from shared button components
- **Files**: `ui/VideoDetailScreen.kt:301-345` defines a **private** `SecondaryActionButton` duplicating `PrimaryActionButton` (from `ui/SharedComponents.kt:285-338`) and its own press-scale. The hero (line 281-296) hand-builds the button column.
- **Gap**: Duplication of button design language; risks drift. Use the shared `PrimaryActionButton`/a shared secondary variant.
- **Concrete**: Replace private `SecondaryActionButton` with a shared secondary button component; keep the hero overlay styling (white-on-image) via parameters.

### P1-2. Audiobook detail header "继续播放" button is a raw `Surface` not the shared `PrimaryActionButton`
- **File**: `ui/AudiobookScreen.kt:754-766` — hand-built Surface for "继续播放" with no press-scale animation, inconsistent with video's `PrimaryActionButton` and music's button treatment.
- **Concrete**: Use shared `PrimaryActionButton` for the audiobook "继续播放" CTA.

### P2-3. Video browse shelf empty state is silent for spotlight sections
- **Files**: `ui/VideoBrowseComponents.kt:147-231` — `VideoSpotlightSections` renders only non-empty rows (`if (videos.isEmpty()) return`, line 190); if all three shelves are empty the whole section just vanishes with no empty hint, while the grid shows "没有匹配的视频".
- **Gap**: Minor visual consistency — when there's genuinely no "继续观看/最受好评/未播放" content the section disappears entirely rather than giving a lightweight hint.
- **Concrete**: (optional) show a single compact "暂无推荐" hint when all shelves are empty.

### P2-4. Video player info panel is right-side overlay; on portrait it's cramped
- **File**: `ui/VideoPlayerScreen.kt:251-270,464-560` — info panel is a `widthIn(max=420.dp)` panel aligned `CenterEnd` that can fill most of a portrait screen. Works, but not optimized for portrait.
- **Gap**: Low priority; consider bottom-sheet on portrait later.

---

## Logic / Data Robustness

### P1-1. Close/sync failure keeps the player open with no retry, and blocks state cleanup
- **Files**: `playback/AudiobookPlaybackViewModel.kt:118-153` and `playback/VideoPlaybackViewModel.kt:99-129` — on `close*Playback` failure, `onFailed` is invoked and the player stays visible (`MainActivity.kt:322-323,337-338` set `show*Player = true`). The engine is **not** stopped on failure, so playback continues; the only way out is to retry close.
- **Gap**: If the network is down (common — the very reason sync fails), the user is trapped in the player with no "close anyway" option and the playback keeps running.
- **Concrete**: On close-sync failure, offer a "仍要关闭" (close anyway) path that stops the engine and lets the user leave; the failed progress sync can be retried in background or dropped.

### P1-2. Periodic progress sync only runs while a session/video is active and stops on `null` step
- **Files**: `playback/ProgressSync.kt:12-28` loops `delay(30_000)` and returns on a `null` step; `AudiobookPlaybackViewModel.kt:46-82` and `VideoPlaybackViewModel.kt:45-88` cancel the `syncJob` when `sessionId`/`video.id` changes. `lastSyncedPosition` is monotonic (line 21: `maxOf(step.positionSeconds, lastSyncedPosition)`).
- **Gap**: (a) There's a known stale-position hazard: `maxOf(current, lastSynced)` can over-report if the user scrubs backward and the engine's position is genuinely lower — but here the engine position is authoritative and monotonic during a session, so it's mostly safe; (b) sync is 30s granularity only — a quick open/close within 30s relies solely on the close sync (P1-1), making that failure mode more critical.
- **Concrete**: Verify close-path always attempts a final sync even if periodic never fired; add a coarse but immediate sync on `onIsPlayingChanged(false)` as a safety net.

### P2-3. Video `play()` ended-state handling can double-prepare
- **File**: `playback/VideoPlaybackEngine.kt:156-190` — after `setMediaItem`+`prepare`, it checks `if (player.playbackState == STATE_ENDED) seekTo(0)` and `if (STATE_IDLE) prepare()`. For a freshly-set new item, state is IDLE/BUFFERING and `prepare()` may be called a second time if `setMediaItem` didn't transition yet; `playFromStart` (192-220) has the same IDLE re-prepare. Generally harmless but can cause a redundant prepare.
- **Concrete**: Guard re-prepare behind the previous item actually being replaced (track whether `shouldReplaceCurrentVideoItem` was true) or rely on a single `prepare()` after `setMediaItem`.

### P2-4. Emby/video sync reports progress even when the player never reached a playable state
- **File**: `data/EmbyRepository.kt:126-136,138-148` `syncPlaybackProgress`/`stopPlaybackProgress` fire regardless of `isBuffering`/error state. If a video failed to load (`onPlayerError`), the periodic sync keeps reporting the stale 0-position.
- **Concrete**: Gate sync to only run when the engine has no `errorMessage` and position actually advanced.

### P2-5. Audiobook `durationSeconds` uses server duration, not track-summed duration
- **File**: `playback/AudiobookPlaybackEngine.kt:288-304` — `durationSeconds` is `session.durationSeconds.coerceAtLeast(per-track duration)`. `resolveAudiobookRelativeSeekPositionSeconds` (400-414) clamps to this; if the server duration is shorter than the actual summed audio tracks, the slider/seek can be clamped incorrectly and `seekTo` near the end may resolve to the last track's local offset (via `resolveAudiobookTrackSeekPosition`, 347-365) beyond the reported duration.
- **Concrete**: Derive duration from `tracks.sumOf { it.durationSeconds }` (falling back to session duration) so the UI timeline matches the playable media.

### P2-6. No reconnect handling for ExoPlayer if `MediaController` reconnects with a stale session
- **File**: `playback/AudiobookPlaybackEngine.kt:63-78,118-143` — `onDisconnected` reconnects after 500ms and replays `pendingSession`. If the underlying `MusicPlaybackService` lost the actual playback, this can restart from the session's `startTimeSeconds` rather than the last-known live position, potentially re-listening.
- **Concrete**: On reconnect, re-issue playback from `_state.value.positionSeconds` (the last-known absolute position) rather than `session.startTimeSeconds`.

### P2-7. Video `resolveVideoInitialStartPositionMs` double-clamps resume but has no "resume near end" skip
- **File**: `playback/VideoPlaybackEngine.kt:323-333` — resumes at `playbackPositionSeconds` unless it equals/beats duration. For near-complete videos (e.g. 95% watched) it resumes at 95% rather than restarting; minor but a "watch from start" path exists via detail.
- **Concrete**: (optional) treat `resumeSeconds >= 90% duration` as from-start, matching common player behavior. P2.

---

## Recommended Batch Grouping

Each batch is cohesive and independently implementable + verifiable (unit-testable pure logic + UI).

### Batch A — Bookmark feature activation (audiobook) — highest user value, low risk
- Wire `AudiobookBookmarkRepository` into `AudiobookPlaybackViewModel` + `AudiobookPlayerScreen`.
- Add bookmark button at current position; list/jump/delete saved bookmarks (sheet or list) in the player.
- Show bookmarks in `AudiobookScreen` detail chapter list.
- Verify: `AudiobookBookmarkRepositoryTest` still passes; add UI-level tests for add/delete/jump.

### Batch B — Persistent now-playing for audiobook & video + dock parity
- Generalize `PolishedNowPlayingBar`/`PlaybackDockSlot` (`PlaybackDock.kt`, `MainActivity.kt`) to render AB (cover/title/author/play-pause) and video (title/play-pause) when their engine has content but no full player layer.
- Ensure the dock reappears on close via existing `bottomDockVisible` flow.
- Verify: switching tabs while AB plays keeps a tappable now-playing bar that reopens the player.

### Batch C — Player interaction & slider consistency (audiobook + video)
- Extract a shared thin-slider composable from music's `PlayerThinSlider`; use in audiobook & video players.
- Add video buffered-position indicator + remaining-time label; add double-tap seek-delta overlay; standardize double-tap interval with buttons.
- Add audiobook swipe-to-dismiss (port from music).
- Add sleep timer to audiobook player (engine + UI).
- Verify: pure functions (`resolveVideoPlayerTimeline`, new seek-delta/remaining helpers) unit-tested; visual parity confirmed vs music.

### Batch D — Close-path robustness & state-machine hardening (audiobook + video)
- Add "close anyway" on close-sync failure (don't trap user; stop engine; background-retry progress).
- Gate progress sync to playable, error-free state; add a sync-on-pause safety net.
- Derive audiobook duration from summed tracks; clamp seek/duration consistently.
- Verify: close under network-fail path releases the player; duration/seek helpers unit-tested.

### Batch E — Visual parity & detail-screen cleanup
- Replace private `SecondaryActionButton` (video) and raw audiobook "继续播放" surface with shared button components.
- Add swipe-dismiss parity details and empty-shelf hint for video spotlights.
- Verify: screen-by-screen visual comparison vs music module; shared-component reuse.

---

## Caveats / Not Found

- **Bookmark repository is confirmed unwired** — no production caller; this is the single biggest "feature gap" because the data layer is already complete and tested.
- `PolishedPlaybackDock`'s now-playing bar is strictly music-bound; no audiobook/video variant exists to reuse — Batch B is net-new generalization, not refactor-only.
- No existing video buffered-position plumbing in `VideoPlaybackEngine` — Batch C requires adding `bufferedPosition` to `VideoPlaybackState`.
- The exact `MainActivity` dock render block (lines 628-687) was not fully read; Batch B should re-read that region before editing to place the generalized now-playing slot correctly.