# UI 与逻辑进一步优化

## Goal

在现有 Nordic Media Hub 三类媒体客户端基础上，继续收敛全局 UI 体验并修正跨页面、播放器和底部 Dock 的状态逻辑，使内容浏览、媒体切换、播放进入/退出和返回操作更稳定、更易理解。优先复用现有 Compose 设计系统与状态流，不引入新的媒体服务或无关重构。

## What I Already Know

* 项目是 Android Jetpack Compose 单仓库应用，整合音乐（Navidrome）、有声书（AudiobookShelf）和视频（Emby）。
* 全局页面编排位于 `app/src/main/java/com/nordic/mediahub/MainActivity.kt`，当前由 `selectedTab`、三个播放器可见状态、全屏状态、队列 Sheet 状态和 Dock 状态共同驱动。
* 底部 Dock 已支持滚动隐藏、隐藏后显示小把手、切换 tab 或关闭播放器时恢复；相关实现位于 `MainActivity.kt` 与 `ui/PlaybackDock.kt`。
* 音乐库已有页面导航栈、搜索、排序、详情刷新后的选中项修正等逻辑，主要位于 `ui/MusicScreenLogic.kt`。
* 项目已有大量 UI、播放、数据层单元测试；最近变更集中在 Dock、播放器、音乐队列和加载状态。
* 产品设计要求内容优先、轻盈、统一，避免深色科技风、过度卡片化和默认 Material 视觉；设计规范记录在 `PRODUCT.md` 与 `DESIGN.md`。

## Assumptions (Temporary)

* 本轮优化优先覆盖最影响日常使用的共享壳层和媒体播放/浏览状态，而非逐页重新设计。
* 现有公共行为和中文文案应保持兼容，只有在发现含义不清或状态错误时才调整。
* 需要以现有测试和新增的纯逻辑测试作为行为回归保护。

## Open Questions

* 无阻塞性范围问题，待最终确认后进入实现。

## Requirements (Evolving)

* 审查全局底部 Dock、tab 切换、播放器层级和系统返回行为，消除可见状态与实际播放状态不一致的情况。
* 优先优化音乐、有声书、视频播放器的进入、退出、返回、全屏和媒体切换行为。
* 优先优化底部 Dock 的显示、隐藏、小把手恢复、播放器打开时的避让和当前播放反馈。
* 仅在播放器/Dock 流程需要时调整媒体页面的加载、空状态、错误和未配置反馈。
* 不同媒体类型之间采用严格单媒体播放：先安全关闭当前媒体，成功后再启动目标媒体；关闭失败时保留原媒体和播放器状态、展示错误，并且不启动目标媒体。
* Dock 采用手动恢复：滚动开始时隐藏完整 Dock，仅保留低干扰小把手；点击小把手、切换 Tab 或关闭播放器时恢复完整 Dock；滚动停止不会自动恢复。
* 保持现有 Nordic 设计系统的色彩、透明度、圆角、动效和内容优先原则，避免新增孤立视觉模式。
* 对修改过的状态解析、导航或播放行为补充/更新测试。

## Acceptance Criteria (Evolving)

* [ ] 关键媒体浏览和播放器流程在 UI 状态变化后不会显示过期 Dock、过期详情或错误的媒体播放器。
* [ ] 音乐、有声书、视频之间切换时不会出现两个播放引擎同时活动；旧媒体关闭失败时目标媒体不会启动。
* [ ] 媒体切换失败后原播放器可继续操作，并能看到明确且可恢复的错误反馈。
* [ ] 系统返回在普通页面、播放器、全屏视频和 Sheet 场景下行为符合用户预期。
* [ ] 加载、空数据、未配置和失败状态提供一致且可执行的反馈。
* [ ] UI 改动在日间/夜间主题和常见窄屏尺寸下不造成文字、控制项或内容重叠。
* [ ] 相关单元测试、lint 和 type-check 通过。

## Confirmed Scope

* 主攻范围：播放器与 Dock 体验。
* 包含：音乐播放器、有声书播放器、视频播放器、视频全屏、系统返回、媒体切换、底部 Dock 隐藏与恢复。
* 包含：对共享状态解析和关键播放流程补充回归测试。
* Dock 不因滚动停止自动出现，避免遮挡正在浏览的媒体内容。

## Decision (ADR-lite)

**Context**: 当前三类媒体由独立播放 ViewModel 管理，跨媒体切换同时涉及异步关闭、播放器可见状态和新媒体启动；立即切换可能造成播放重叠和界面状态竞争。

**Decision**: 使用严格单媒体播放。旧媒体关闭成功后才启动目标媒体；关闭失败时中止切换、保留并恢复原播放器，同时展示错误。

**Consequences**: 媒体切换会等待旧会话完成关闭，但状态可预测，不需要额外确认弹窗，也不会产生双媒体并行播放。

## Definition of Done

* 完成确认范围内的 UI 和逻辑修改。
* 为共享逻辑或回归风险补充测试。
* 通过项目 lint、type-check 和相关测试。
* 若产生新的稳定约定，更新 `.trellis/spec/`；同步必要的变更日志。

## Out of Scope

* 不新增 Plex、WebDAV 或其他媒体服务接入。
* 不重写现有数据仓库、播放引擎或网络协议。
* 不在本轮进行与用户流程无关的全量视觉重构。

## Technical Notes

* 已检查：`PRODUCT.md`、`DESIGN.md`、`MainActivity.kt`、`PlaybackDock.kt`、`MusicScreenLogic.kt`、`CHANGELOG.md`。
* 相关代码范围：`app/src/main/java/com/nordic/mediahub/ui/`、`app/src/main/java/com/nordic/mediahub/playback/`、`app/src/test/java/com/nordic/mediahub/ui/`、`app/src/test/java/com/nordic/mediahub/playback/`。
* 后续实现前需读取 `.trellis/spec/backend/index.md` 及其预开发清单，并根据实际触及的层补充具体规范上下文。
