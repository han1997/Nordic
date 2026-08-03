# 全面 UI 统一与操作逻辑优化

## Goal

统一 Nordic Media Hub 的 Android Compose UI 与核心操作逻辑，让音乐、视频、有声书三类媒体在导航、标题区、配置入口、刷新、列表/详情、播放入口、空态/加载/错误态上形成一致的产品体验，同时保留各媒体类型的内容特征。

## What I already know

* 用户提出目标："全面UI统一、操作逻辑更加合理"。
* 项目是 Android/Kotlin Compose 单体应用，主 UI 位于 `app/src/main/java/com/nordic/mediahub/ui/`。
* 产品文档定位为统一的多媒体管理客户端，整合音乐 Navidrome、有声书 AudiobookShelf、视频 Emby/Plex/WebDAV。
* 设计系统已明确：内容优先、三类媒体共享设计语言、界面退居幕后让媒体成为主角。
* 当前已有主题、间距、形状、共享组件：`theme/Theme.kt`、`theme/Spacing.kt`、`SharedComponents.kt`。
* 主要目标页面包括 `MusicScreenV2.kt`、`VideoScreen.kt`、`AudiobookScreen.kt`，主导航与播放浮层逻辑在 `MainActivity.kt` 和 `PlaybackDock.kt`。
* 已观察到三类媒体页面的结构存在相似但分散的实现：标题区、配置展开、刷新、返回、内容列表、详情页和状态处理各自实现。

## Assumptions (temporary)

* 本任务优先做现有功能的统一和交互梳理，不引入新的媒体服务或大规模架构重写。
* UI 统一应遵守现有 `PRODUCT.md` / `DESIGN.md` 的视觉方向，而不是另起一套视觉风格。
* 操作逻辑优化以用户可感知的导航、刷新、配置、返回、播放入口一致性为主，避免重写数据层协议。

## Open Questions

* Requirements are ready for final confirmation.

## Requirements (evolving)

* 三类媒体页面应共享一致的页面骨架：标题区、说明文案、操作按钮组、配置面板、状态展示和内容容器节奏。
* 交互逻辑应减少例外：配置展开/收起、刷新、返回、搜索/筛选、进入详情、发起播放的行为应在媒体类型之间尽量一致。
* 保留媒体类型差异：音乐偏库浏览和队列，有声书偏章节/进度，视频偏海报/剧集/继续观看。
* 本轮 MVP 覆盖首页、浏览、详情、配置、刷新、空态/加载态/错误态，以及从这些页面进入播放的入口语义。
* 播放器内部页面不作为主要改造范围；仅在浏览/详情入口需要一致时做必要调整。
* 优先复用或提炼已有共享组件，避免给三类媒体继续增加相似但分散的本地 UI 实现。
* 视觉统一应基于既有设计系统的 alpha 层级、pill 形按钮/芯片、press-scale 反馈和内容优先布局。
* 操作逻辑优先级采用“导航状态一致”：统一返回、详情/浏览切换、配置展开、刷新加载、错误/空态入口，播放入口和搜索筛选只在相关页面需要时顺带整理。

## Acceptance Criteria (evolving)

* [ ] 音乐、视频、有声书首页的标题区与主要操作入口视觉和行为一致。
* [ ] 配置、刷新、空态、加载态、错误态在三类媒体页面中使用一致的组件或一致的模式。
* [ ] 返回逻辑和详情/浏览切换不互相冲突，Android Back 与界面返回按钮一致。
* [ ] 配置展开、刷新加载、错误/空态入口在三类媒体页面中有一致的操作反馈和文案语义。
* [ ] 播放入口语义更清晰，继续播放/从头播放/播放当前项等操作不混淆。
* [ ] 单元测试覆盖被调整的纯逻辑函数或状态决策；现有 UI 相关测试通过。
* [ ] Lint / Gradle 测试通过，至少运行与 UI 逻辑相关的测试集合。

## Definition of Done

* Tests added/updated where behavior changes.
* Lint / typecheck / relevant Gradle tests pass.
* Specs/notes updated if a reusable UI or operation convention is introduced.
* No unrelated visual restyle outside confirmed scope.

## Out of Scope (explicit)

* 不新增后端服务类型。
* 不重写播放引擎或媒体仓库协议。
* 不引入新的设计系统库。
* 不替换现有产品视觉方向。
* 不全面重做音乐、有声书、视频播放器内部 UI。

## Decision (ADR-lite)

**Context**: 初始目标很宽，若同时改浏览、详情、底部 Dock 和三个播放器，风险与验证成本会过高。

**Decision**: MVP 采用“浏览详情优先”：统一三类媒体的首页、浏览、详情、配置、刷新、状态展示和播放入口语义，播放器内部只做必要入口一致性。

**Consequences**: 本轮能优先解决日常使用中最频繁的跨媒体体验割裂；播放器内部统一可作为后续任务独立推进。

## Decision (ADR-lite): Operation Priority

**Context**: “操作逻辑更加合理”可覆盖导航、播放、搜索筛选、配置刷新等多类行为，需要先锁定主要收益点。

**Decision**: 本轮优先统一导航状态：返回、详情/浏览切换、配置展开、刷新加载、错误/空态入口。

**Consequences**: 播放入口和搜索筛选不作为单独大范围重做，但在浏览/详情统一过程中遇到明显不一致时一并修正。

## Technical Notes

* Relevant UI files inspected: `MainActivity.kt`, `SharedComponents.kt`, `VideoScreen.kt`, `MusicScreenV2.kt`, `AudiobookScreen.kt`, `theme/Theme.kt`, `theme/Spacing.kt`。
* Additional UI files inspected: `VideoBrowseComponents.kt`, `VideoDetailScreen.kt`, `MusicBrowseComponents.kt`, `MusicHomeSections.kt`, `MediaStateComponents.kt`, `ConfigCards.kt`。
* Existing reusable pieces include `MediaStateCard`, `MediaLoadingCard`, `MetaChip`, `ToneMetaChip`, `ScreenBackButton`, `CoverArt`, and `PrimaryActionButton`.
* Candidate consolidation areas: page header/action group, segmented chip/filter row, section header/action, config save action style, compact/prominent state cards, and detail hero/play-action pattern.
* Existing design context loaded from `PRODUCT.md` and `DESIGN.md` via Impeccable context script.
* Impeccable update notice: installed v4.0.2, latest v4.0.4; update is optional and not required for this task.
