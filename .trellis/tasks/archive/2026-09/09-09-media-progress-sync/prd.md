# 媒体进度与播放状态同步补全

## Goal

补全三域（音乐/有声书/视频）的服务器端播放状态同步缺口：音乐播放从不 scrobble（服务器最近播放/播放计数不更新），有声书后台化时缺少 ON_STOP 即时同步安全网。视频域同步已完整，仅真机验证。

## What I already know

* 用户决策：音乐 scrobble 用标准两段式（now-playing + 提交）；范围 = 音乐 scrobble + 有声书 ON_STOP 安全网；视频仅验证不动代码。
* `NavidromeApi.scrobble(username, token, salt, id, submission)` 已存在（`NavidromeApi.kt:286`）且 `NavidromeRepository.scrobble(songId, submission)`（`NavidromeRepository.kt:720`）已封装，但**无任何调用方**。
* `MusicPlaybackViewModel` 有 `_repository: StateFlow<NavidromeRepository?>`、`engine.state`（`MusicPlaybackState`: currentSong/isPlaying/positionSeconds/durationSeconds/queue/queueIndex）。
* 收藏失败模式：`MutableSharedFlow<Unit>` 静默失败（`favoriteError`），scrobble 失败沿用同模式不弹 UI。
* 有声书：`AudiobookPlaybackViewModel.syncProgress(session, currentTimeSeconds, deltaSeconds)`；`resolveAudiobookProgressSyncBaselineSeconds` 防回退；30s 周期 + 暂停即报已有；**MainActivity 只有视频的 `LifecycleEventEffect(ON_STOP)`**（`MainActivity.kt:1068`）。
* 视频域：30s 周期 + 暂停即报 + 关闭 Stopped 重试 + ON_STOP 关窗即停，全部完整。
* Emby 播完标记已看由服务器按 position 处理（PositionTicks ≥ duration），无需客户端额外调用。

## Requirements (confirmed)

### M1 音乐 scrobble（Navidrome，标准两段式）
* 开始播放一首歌（currentSong 变化且非本地下载兜底场景）→ `scrobble(submission = false)`（now-playing），每首歌会话一次。
* 播放过半（position ≥ duration/2，duration > 0）或切歌/停止/播完离开当前歌 → `scrobble(submission = true)`（提交播放记录），每首歌只提交一次。
* 已提交后不再重复提交；now-playing 与提交互相独立（先提交后切歌不补发 now-playing）。
* 失败静默：`MutableSharedFlow<Unit>`（先例 `favoriteError`），不弹 UI 错误；不重试（下次播放自然再 scrobble）。
* duration 未知（≤0）时：切歌/停止/播完时直接提交，不做过半判定。
* 实现位置：`MusicPlaybackViewModel`，过半判定做成纯函数便于单测。

### M2 有声书 ON_STOP 安全网
* `MainActivity` 加 `LifecycleEventEffect(ON_STOP)`：有声书播放中（session 非空）后台化时立即 `syncProgress` 一次，**不关播放器**（有声书继续后台播，与视频「关窗即停」语义不同）。
* 复用 `resolveAudiobookProgressSyncBaselineSeconds` 防回退；失败静默（Log，不弹 UI）。
* 与视频 ON_STOP 分支互不干扰（视频分支已存在，不动）。

### M3 视频（仅验证）
* 无代码改动；真机验收确认播放完成后 Emby 标记已看。

## Acceptance Criteria

* [ ] 音乐播放一首歌后，Navidrome 服务器「最近播放」更新、播放计数 +1（真机验收）。
* [ ] 快速切歌（< 半首）也提交前一首的 scrobble（切走时触发提交）。
* [ ] 同一首歌重复过半/切回不重复提交。
* [ ] scrobble 网络失败不弹 UI 错误、不崩溃、不影响播放。
* [ ] 有声书播放中后台化 → 服务器进度立即更新（真机验收），播放不中断。
* [ ] 单测：过半判定纯函数、每歌一次语义、切歌触发提交、duration 未知行为。
* [ ] compile + test + lint + assemble 全绿；CHANGELOG 条目；manual-checklist + APK。

## Out of Scope

* Navidrome 服务端播放队列同步（getPlayQueue/savePlayQueue）。
* ABS 多设备会话冲突处理。
* 视频域代码改动（Emby 已看标记服务器自动处理）。
* 音乐进度（position）上报——Subsonic 协议无此概念，只有 scrobble。

## Decision (ADR-lite)

**Context**：scrobble 提交时机（过半 vs 播完 vs 两者）。
**Decision**：过半或离开（切歌/停止/播完）任一条件触发提交，每首歌一次。标准两段式与 Subsonic/Airsonic/官方 App 语义一致。
**Consequences**：快速切歌也计一次播放（与主流客户端一致）；`submission=false` 的 now-playing 让服务器「正在播放」列表可见本客户端。

## Technical Notes

* scrobble 状态机字段：`scrobbledSongId: String?`（已提交的歌）、`nowPlayingSongId: String?`（已发 now-playing 的歌）。切歌检测用 `currentSong?.id` 变化。
* 过半判定纯函数：`shouldScrobbleMusicSubmission(positionSeconds, durationSeconds, alreadySubmitted): Boolean`。
* `NavidromeSong.id` 为 scrobble 的 `id` 参数。
* 本地下载歌曲（无服务器 id 或离线播放）照常 scrobble，失败静默即可（id 是服务器 id）。
