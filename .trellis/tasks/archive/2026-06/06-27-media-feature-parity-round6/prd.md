# 媒体功能完善第六轮

## Goal

继续参考音流、Audiobookshelf 官方移动端、Emby/Yamby 的成熟体验完善 Nordic Media Hub。本轮聚焦 AudiobookShelf 官方移动端常见的“书签/听书笔记”能力，采用 local-first MVP，让用户在有声书播放过程中快速标记当前位置，并在播放器里查看、跳转和删除书签。

## What I Already Know

- 当前仓库干净，最近完成 round 5：
  - `b5e6bf0 feat(music): persist downloaded song metadata`
  - `e768235 fix(video): remove inactive subtitle offset controls`
  - `fa653ab docs(spec): record download metadata contract`
- Audiobook 当前能力：
  - AudiobookShelf 登录、书库/详情、播放 session、进度同步、关闭 session、章节、倍速、跳转、睡眠定时、继续收听、上次播放入口。
  - `AudiobookPlayerScreen` 已拿到 `AudiobookPlaybackState.positionSeconds`，这是全书绝对位置，适合作为本地书签位置。
  - `AudiobookPlaybackState.session.libraryItemId` 可作为书签归属 key。
- 本地持久化模式：
  - `PlayHistoryRepository` 和 `NavidromeMusicCacheRepository` 使用 `context.dataStore` + Gson JSON 字符串。
  - `ConfigRepository` 继续只负责配置；非配置本地状态可以用单独 repository。
- 当前没有发现书签/笔记相关代码。

## Requirements

- Add a local-first audiobook bookmark repository:
  - Persist bookmarks in DataStore JSON.
  - Scope bookmarks by AudiobookShelf `libraryItemId`.
  - Each bookmark stores id, library item id, position seconds, optional note/title text, and timestamp.
  - Keep a bounded number of bookmarks per audiobook to avoid unbounded preference growth.
- Add player UI for bookmarks:
  - User can add a bookmark at current audiobook position from the audiobook player.
  - User can see bookmarks for the current audiobook in the player.
  - User can tap a bookmark to seek to its position.
  - User can delete a bookmark.
  - Empty state is compact and does not block normal playback controls.
- Preserve existing playback/session close/progress behavior.
- Add unit tests for repository/pure helper behavior.

## Acceptance Criteria

- [x] Adding a bookmark at the current position persists it under the current `libraryItemId`.
- [x] Bookmark list is sorted newest-first or position-sensible in a predictable way.
- [x] Tapping a bookmark seeks the audiobook player to the stored absolute position.
- [x] Deleting a bookmark removes it from persisted state and UI.
- [x] Malformed bookmark JSON loads as an empty list instead of crashing.
- [x] Bookmarks are bounded per audiobook.
- [x] `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
- [x] `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
- [x] `.\gradlew.bat :app:lintDebug --no-daemon` passes.
- [x] Specs updated if the local bookmark persistence contract is new.

## Decision (ADR-lite)

**Context**: AudiobookShelf official apps support resume-oriented listening workflows, including bookmarks/notes. The server API contract for bookmarks is not currently modeled in this app, while local DataStore JSON persistence is already used for app-owned state.

**Decision**: Implement local-first audiobook bookmarks in this round. Do not guess AudiobookShelf server bookmark endpoints in this slice.

**Consequences**: Users get a useful bookmark workflow immediately. Bookmarks are local to the Android app until a later task adds server sync after the API contract is researched and specified.

## Out of Scope

- Server-synced AudiobookShelf bookmarks.
- Rich note editing with multiline text, tags, export, or cross-device sync.
- Podcast episode bookmarks.
- Full audiobook offline download.

## Technical Approach

- Add `AudiobookBookmarkRepository` in `data/`, modeled after `PlayHistoryRepository`.
- Add `AudiobookBookmark` domain model and pure helpers for add/delete/bounding.
- Wire `MainActivity` to load bookmarks for the active audiobook session and pass callbacks to `AudiobookPlayerScreen`.
- Extend `AudiobookPlayerScreen` with compact bookmark controls and rows.
- Add `AudiobookBookmarkRepositoryTest`.

## Technical Notes

- Task directory: `.trellis/tasks/06-27-media-feature-parity-round6`.
- Key files inspected:
  - `app/src/main/java/com/nordic/mediahub/ui/AudiobookPlayerScreen.kt`
  - `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`
  - `app/src/main/java/com/nordic/mediahub/data/ConfigRepository.kt`
  - `app/src/main/java/com/nordic/mediahub/data/PlayHistoryRepository.kt`
  - `app/src/main/java/com/nordic/mediahub/playback/AudiobookPlaybackEngine.kt`
  - `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
- Applicable specs:
  - `.trellis/spec/backend/index.md`
  - `.trellis/spec/backend/database-guidelines.md`
  - `.trellis/spec/backend/audiobookshelf-integration.md`
  - `.trellis/spec/backend/quality-guidelines.md`
