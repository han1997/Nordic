# Polish Lyrics Page Visuals

## Goal

Improve the music player's lyrics display so it feels integrated with the player surface instead of showing an obvious outer frame and an inner square-like pale panel.

## What I Already Know

* The user specifically called out the lyrics page as visually ugly because it still has an outer border and an inner white square background.
* The affected UI is in `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`.
* `PlayerLyricsDisplay` currently wraps the lyrics in a rounded `Surface` with border and shadow, then adds an inner `Box` with a linear gradient starting from `colorScheme.surface.copy(alpha = 0.9f)`.
* In light theme, that inner surface color can read as a white block inside the lyrics panel.
* Project design context says this is a product UI where media should remain the focus, surfaces should be subtle, and cards should not feel over-decorated.

## Requirements

* Remove the harsh outer framed look from the lyrics display.
* Remove or soften the inner square/pale panel effect so the lyrics area reads as one continuous rounded surface.
* Polish the music playback page so the background, artwork, metadata, status, and control console feel like one cohesive player surface.
* Place music home content in this order: "刚刚同步", "最近添加", "最近专辑", "常听歌手".
* Move the player song title, playback status, album, and duration into the top information area so the center of the player can focus on artwork or lyrics.
* Keep the lyrics toggle behavior unchanged: tapping the artwork/lyrics area still switches between album art and lyrics.
* Keep existing lyrics loading, empty, synced, and unsynced display behavior unchanged.
* Keep playback engine behavior, queue actions, seeking, repeat, previous/next, and play/pause callbacks unchanged.
* Stay consistent with the existing Nordic design language: restrained translucent surfaces, subtle gradients, no heavy decorative card treatment.

## Acceptance Criteria

* [x] The lyrics panel no longer has an obvious outer border.
* [x] The lyrics panel no longer shows a separate inner white square/rectangle.
* [x] Loading and empty lyrics states remain centered and readable.
* [x] Active and inactive lyric lines remain visually distinct.
* [x] Playback page uses a softer integrated background and control surface without changing playback behavior.
* [x] Music home shows "刚刚同步" first, "最近添加" second, "最近专辑" third, and "常听歌手" fourth.
* [x] Player top area shows song title, playback status, album, and duration; the middle no longer repeats the track information.
* [x] The project builds or at least compiles the changed Kotlin source successfully.

## Definition of Done

* Lint/type-check/build command run where practical.
* No behavior changes outside lyrics display styling.
* Trellis quality check completed.

## Technical Approach

Adjust `PlayerLyricsDisplay` in place. Prefer a single rounded clipped container with one continuous translucent gradient and no explicit border. Tune padding, gradient alpha, and lyric text contrast to match the existing player artwork and console surfaces.

## Out of Scope

* Changing Navidrome lyrics fetching or parsing.
* Adding scrolling lyrics.
* Changing synced lyrics windowing logic.
* Redesigning the full player screen outside the lyrics display.

## Technical Notes

* Product design context loaded from `PRODUCT.md` and `DESIGN.md`.
* Impeccable product register guidance loaded from `C:/Users/hanhu/.agents/skills/impeccable/reference/product.md`.
* Relevant source inspected: `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`.
* Verification passed:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`
* Playback page polish verification passed:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`
* Top player information and home section order verification passed:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`
* Spec update review: no `.trellis/spec` update needed because these changes only adjusted local player styling and introduced no new code contract or reusable convention.
