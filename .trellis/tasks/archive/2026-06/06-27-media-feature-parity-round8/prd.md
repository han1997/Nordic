# 媒体功能完善第八轮

## Goal

继续参考音流等成熟音乐播放器体验完善 Nordic Media Hub。本轮聚焦音乐播放页的同步歌词交互：用户在逐行歌词模式下点击某一句歌词时，播放器跳转到该句对应时间。

## What I Already Know

- 工作树在本轮开始前干净。
- Music player 已有歌词显示开关、同步/纯文本歌词模式、歌词偏移、当前行高亮和大字模式。
- `MusicLyricsLine.startMillis` 已保存同步歌词时间点。
- `MusicPlayerScreen` 已接收 `onSeek: (Int) -> Unit`，可以把歌词点击转成秒级 seek。
- `selectVisibleLyricLines(...)` 当前是私有 helper，只返回文本和 active 状态，缺少时间点，无法让 UI 行点击 seek。

## Requirements

- 在同步歌词显示时，每一行有时间点的歌词可点击。
- 点击歌词行调用现有 `onSeek`，跳转到该行 `startMillis / 1000` 的位置。
- 纯文本歌词或无时间点歌词不触发 seek。
- 保持现有歌词显示开关、偏移、同步/纯文本切换和大字模式行为。
- 给歌词可见窗口 helper 增加聚焦单测，覆盖 active 行和 startMillis 传递。

## Acceptance Criteria

- [x] 同步歌词可见行保留 `startMillis`。
- [x] 点击有 `startMillis` 的歌词行调用 `onSeek` 到对应秒数。
- [x] 纯文本歌词行不可 seek。
- [x] `selectVisibleLyricLines` helper 有单测覆盖 active 行和时间点。
- [x] `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
- [x] `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
- [x] `.\gradlew.bat :app:lintDebug --no-daemon` passes.

## Definition of Done

- Tests added or updated for helper behavior.
- Lint/type-check/test gates pass sequentially on Windows.
- Docs/specs updated only if a reusable contract or convention changes.
- Changes committed before finish-work.

## Out of Scope

- Full-screen lyrics redesign.
- Per-line context menus, lyric sharing, translation lyrics, karaoke word-level timing.
- Persisting lyric offset per song.

## Technical Approach

- Thread `onSeek` from `MusicPlayerScreen` into `PlayerLyricsDisplay`.
- Extend `VisibleLyricLine` with `startMillis`.
- Make `VisibleLyricLine` and `selectVisibleLyricLines(...)` internal so JVM tests can cover the pure helper.
- In `PlayerLyricsDisplay`, apply a line-level `Modifier.clickable(enabled = line.startMillis != null)` and call `onSeek((line.startMillis / 1000).coerceAtLeast(0))`.

## Decision (ADR-lite)

**Context**: Mature music apps commonly support tapping synced lyrics to jump to that line. Nordic already parses synced lyrics and shows active lines, so this can be added without changing Navidrome API behavior.

**Decision**: Implement tap-to-seek at the UI layer using existing `MusicLyricsLine.startMillis` and existing player `onSeek`.

**Consequences**: This improves playback ergonomics while keeping lyric parsing and playback engine contracts unchanged.

## Technical Notes

- Task directory: `.trellis/tasks/06-27-media-feature-parity-round8`.
- Key files:
  - `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`
  - `app/src/main/java/com/nordic/mediahub/data/MusicLyrics.kt`
  - `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
- Applicable specs:
  - `.trellis/spec/backend/index.md`
  - `.trellis/spec/backend/directory-structure.md`
  - `.trellis/spec/backend/quality-guidelines.md`
