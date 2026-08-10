# 任选方向打磨软件：有声书 seek 边界一致性

## Goal

选择一个低风险但用户可直接感知的软件完成度缺口并落地。本轮打磨有声书播放的相对 seek 边界：当有声书播放器暂时没有发布有效总时长时，`+30` / `-30` 仍应基于当前非负位置安全跳转，而不是因为 `durationSeconds <= 0` 被强制归零。

## What I Already Know

* 用户要求“任选方向进行软件打磨”，并确认按计划实施。
* 项目是 Kotlin/Jetpack Compose Android 媒体中心，含音乐、AudiobookShelf 有声书、Emby 视频三类播放体验。
* 最近一轮已完成音乐播放进度控制打磨，并在 `.trellis/spec/backend/quality-guidelines.md` 中沉淀了相对 seek 的未知时长边界：已知时长 clamp 到 `0..duration`，未知时长只 clamp 到非负范围。
* 视频播放器的 `resolveVideoRelativeSeekPositionSeconds(...)` 已采用未知时长时允许非负前进的策略。
* 有声书播放器的 `resolveAudiobookRelativeSeekPositionSeconds(...)` 当前将 `durationSeconds.coerceAtLeast(0)` 作为最大值；当 duration 为 0 或未知时，当前位置会先被 clamp 到 0，`+30` 也会返回 0。
* `AudiobookPlayerScreen` 已有 `-30` / `+30` 控制，`MainActivity` 已将它们接到 `AudiobookPlaybackViewModel.seekBackBy` / `seekForwardBy`。
* `AudiobookPlaybackEngineTest` 已覆盖相对 seek 的已知时长边界，但缺少未知时长行为测试。

## Requirements

* 更新有声书相对 seek 纯函数，使 `durationSeconds > 0` 时保持现有 `0..durationSeconds` clamp 行为。
* 当 `durationSeconds <= 0` 时，使用当前非负位置加 delta，并 clamp 到 `0..Int.MAX_VALUE`，与视频和音乐相对 seek 策略一致。
* 保持有声书 `-30`、`+30` 控件、章节跳转、倍速切换和播放/暂停 UI 不变。
* 补充单元测试覆盖未知时长下前进和后退行为。
* 更新 `CHANGELOG.md`，记录用户可见的有声书播放控制打磨。

## Acceptance Criteria

* [ ] `resolveAudiobookRelativeSeekPositionSeconds(position=20, duration=0, delta=30)` 返回 `50`。
* [ ] `resolveAudiobookRelativeSeekPositionSeconds(position=20, duration=0, delta=-30)` 返回 `0`。
* [ ] 已知时长行为保持不变：前进超过末尾 clamp 到总时长，后退超过开头 clamp 到 0。
* [ ] 有声书章节跳转、倍速循环、播放/暂停行为不回归。
* [ ] `CHANGELOG.md` 在 `未发布` 区块用中文记录该用户可见改进。
* [ ] `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug` 通过。

## Definition Of Done

* 代码改动聚焦 `AudiobookPlaybackEngine` 的相对 seek 边界和对应测试。
* 不新增 UI 控件，不改变有声书播放页布局。
* 不改变 AudiobookShelf API、播放会话同步、书签或缓存语义。

## Technical Approach

复用音乐/视频相对 seek 的边界策略：在有有效 duration 时 clamp 到总时长；没有有效 duration 时只保证目标位置非负。实现应尽量只改 `resolveAudiobookRelativeSeekPositionSeconds(...)` 和 `AudiobookPlaybackEngineTest`，再补 `CHANGELOG.md`。

## Decision (ADR-lite)

**Context**: 有声书 `+30` / `-30` 已经有 UI 和 ViewModel/Engine 链路，但纯函数在未知时长下会把 seek 目标归零，和音乐/视频刚沉淀的播放控制契约不一致。

**Decision**: 不扩大到 UI 重设计、章节逻辑或播放会话同步；只修相对 seek 的未知时长边界。

**Consequences**: 改动很小，风险集中在有声书相对 seek；通过纯函数测试可以覆盖核心行为。

## Out of Scope

* 不调整有声书播放页视觉设计。
* 不新增跳转按钮、睡眠定时、书签入口或长按连续 seek。
* 不改变章节上一章/下一章逻辑。
* 不改变 AudiobookShelf progress sync 或 playback session 生命周期。

## Technical Notes

* 任务目录： `.trellis/tasks/08-10-optional-software-polish-audiobook-seek`
* 相关代码： `AudiobookPlaybackEngine.kt`、`AudiobookPlaybackEngineTest.kt`、`CHANGELOG.md`
* 相关规格： `.trellis/spec/backend/index.md`、`.trellis/spec/backend/quality-guidelines.md`、`.trellis/spec/backend/audiobookshelf-integration.md`、`.trellis/spec/backend/documentation-guidelines.md`
