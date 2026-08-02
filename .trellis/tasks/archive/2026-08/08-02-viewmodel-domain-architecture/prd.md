# 引入 ViewModel 与领域模型架构

- 优先级: P1
- 创建: 2026-08-02
- Assignee: hhy
- 关联审查: `.trellis/workspace/hhy/code-review-2026-08-02.md`

## 背景

全仓零 `ViewModel/Hilt/@Inject`，所有状态活在 `MainActivity`（803 行 `MainScreen` 巨石）与 Composable 的普通 `remember` 中，配置变更/进程死亡状态全丢。`MainActivity` 与 `MusicPlaybackService` 互相 import 形成真实循环依赖。5 个 ui 文件直接 import `api.NavidromeSong` 等 DTO，无领域模型层。本任务建立架构骨架，是 T7（UI 拆分）的前置依赖。

## 范围

### In scope
- 引入 `ViewModel`（必要时 Hilt）持有播放/配置所有权，向 Composable 暴露 `StateFlow`
- 抽 `MusicPlaybackViewModel`/`AudiobookPlaybackViewModel`/`VideoPlaybackViewModel`（持引擎、repo、同步循环、关闭编排），`MainScreen` 仅剩路由/脚手架
- 引入 `data/` 领域模型（`MusicTrack`/`AlbumSummary` 等），在仓库边界映射 API DTO↔领域模型；ui/playback 只吃领域类型，破 ui→api 直连（5 文件 + `MusicPlaybackEngine`/`MusicMediaItems`）
- 破 `MainActivity`↔`MusicPlaybackService` 循环：经 manifest `<media-session-activity>` 或 `data/` 的 `SessionActivityProvider`，service 不直接命名 `MainActivity`
- 抽取时一并修复 MainActivity 内吞错误模式：视频同步循环 `onFailure`（:576-584）、`closeVideoPlayback` 最终进度保存 `onFailure`（:476-480）；合并 `closeAudiobookPlayback`/`closeAudiobookPlaybackAfterSync` 重复、抽泛型 `periodicProgressSyncer`

### Out of scope
- MusicScreenV2/VideoScreen 的 Composable 物理拆分 + 共享组件提取 → T7
- 播放引擎内部健壮性 → T3
- 凭据加密 → T1

## 覆盖的审查发现

| ID | 严重度 | 位置 | 概述 |
|---|---|---|---|
| H2 | High | `MainActivity.kt:237-803`（全仓零 VM） | 巨石 + 无 ViewModel，状态易失 |
| H3 | High | `MainActivity.kt:41-43` + `MusicPlaybackService.kt:21,139` | 循环依赖 |
| H4 | High | 5 个 ui 文件 + `MusicPlaybackEngine.kt:12`/`MusicMediaItems.kt:8` | ui/playback 直连 api DTO，无领域模型 |
| M-MainActivity 重复 | Medium | `MainActivity.kt:396-462,516-586` | close 函数与同步循环重复 |
| M-吞错误 | Medium | `MainActivity.kt:476-480,576-584` | 视频同步/关闭吞失败 |

## 验收标准
- [ ] 至少 3 个播放 ViewModel 接管状态，`MainScreen` 行数大幅下降且不含播放编排
- [ ] ui/ 不再 import `api.*`；`playback/` 不持原始 `api.NavidromeSong`
- [ ] `MusicPlaybackService` 不再 import `MainActivity`
- [ ] 抽取的同步循环含 `onFailure` 处理，`closeAudiobook` 合并为单函数
- [ ] 配置变更/进程死亡后播放状态可恢复（rememberSaveable/ViewModel）
- [ ] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过

## 涉及 spec（待 1.3 jsonl 整理）
- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
