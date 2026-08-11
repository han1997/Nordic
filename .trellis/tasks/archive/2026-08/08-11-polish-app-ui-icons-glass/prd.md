# 打磨软件 UI 图标与毛玻璃效果

## Goal

在不重做整体视觉架构的前提下，打磨 Nordic app 的全局 UI 质感：优先统一按钮/导航/头部操作的图标语言，替换文本符号伪图标，并在适合的 persistent chrome 和媒体播放表面加入克制的毛玻璃/霜面层次。

## What I Already Know

* 用户希望打磨软件 UI，重点是图标、按钮图标，并可参考同类软件在适当位置加入毛玻璃效果。
* 当前产品视觉方向是内容优先、轻盈、现代、媒体内容作为视觉重心，毛玻璃效果应克制使用。
* `DESIGN.md` 明确要求不要把 glassmorphism 当默认样式；霜面/玻璃效果适合 dock、播放器 overlay 等特定表面。
* 当前 `MusicPlayerScreen`、`VideoPlayerScreen` 等局部已经使用 Material vector icons，但共享 header、back、bottom nav、dock play/pause 仍有文本符号伪图标。
* 代表性文件包括 `AnimatedComponents.kt`、`SharedComponents.kt`、`PlaybackDock.kt`、`MusicBrowseComponents.kt`、`MusicQueueSheet.kt`、`ServerConfigScreen.kt`、`MusicScreenV2.kt`、`AudiobookScreen.kt`、`VideoScreen.kt`。
* 同类媒体软件参考见 `research/media-app-ui-reference.md`：统一线性图标、专辑/媒体内容驱动的氛围层、少量高价值玻璃表面。

## Assumptions

* 本轮优先做全局共享控件层 polish，而不是逐屏重做所有卡片。
* 图标优先使用现有 Android Material Icons，避免新增图标依赖或自绘 SVG。
* 毛玻璃效果要落在 dock、header action group、播放器/媒体 chrome 等合理位置，列表行和普通卡片不泛化。

## Requirements

* Header action、back button、theme toggle、refresh、bottom nav、dock play/pause 等按钮不再依赖文本符号作为主图标。
* 图标尺寸、颜色、禁用态、按压反馈、content description 保持统一，并符合现有 Nordic tokens。
* 在适合位置加入或强化霜面/毛玻璃质感：优先是 playback dock、bottom handle、header action group、播放器表面；避免普通列表行泛化玻璃。
* 保持现有导航、播放、刷新、主题切换、配置入口行为不回归。
* 用户可见 UI 改进记录到 `CHANGELOG.md`。

## Acceptance Criteria

* [ ] 共享头部操作和底部导航使用统一 vector icon，不再显示乱码/文本符号伪图标。
* [ ] 关键按钮有可理解的无障碍描述。
* [ ] 新增霜面/玻璃效果只出现在高价值 persistent chrome 或播放器表面，不降低文字/图标对比度。
* [ ] 音乐、有声书、视频、配置 tab 的主要导航和操作入口仍可正常使用。
* [ ] `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug` 通过。

## Definition Of Done

* PRD 范围经用户确认。
* 代码改动聚焦图标/按钮/玻璃质感，不做无关视觉重构。
* 遵循 `DESIGN.md`、Impeccable polish 指南和 Trellis backend UI 规范。
* 用户可见行为更新 `CHANGELOG.md`。

## Technical Approach

* 将 `HeaderAction` 从文本 `icon: String` 迁移为 vector icon + content description，并提供小型 helper 让各屏调用保持简洁。
* 将 `ScreenBackButton`、`HeaderActionGroup`、`PlaybackDock`、`PolishedBottomNav` 等共享控件替换为 Material vector icons。
* 在 dock / handle / header action group 维持现有 surface alpha 和边框体系，并通过更一致的半透明 surface、gradient highlight 和 tonal elevation 强化霜面层次。
* 保持现有 press-scale、Nordic spacing/shape/alpha tokens，不引入新依赖。

## Decision (ADR-lite)

**Context**: 当前 app 的多数播放器控制已使用 Material vector icons，但共享 header 和 bottom dock 仍有文本符号伪图标，容易出现乱码、对齐不稳和无障碍语义不足。用户希望重点打磨图标、按钮图标，并适度加入毛玻璃效果。

**Decision**: 本轮优先统一共享控件层与 persistent chrome。图标统一到 Material vector icons；玻璃效果只强化 dock、handle、header actions 等高价值表面，不改普通列表和卡片。

**Consequences**: 全局视觉一致性会明显提升，改动范围仍集中；后续若继续打磨，可再逐屏处理卡片/空状态/配置页细节。

## Out Of Scope

* 不重做页面信息架构、导航结构或三大媒体页面内容布局。
* 不引入新图标库、新图片资产或大型动画系统。
* 不把所有卡片/list row 都改成毛玻璃。
* 不修改底层媒体播放、Navidrome/AudiobookShelf/Emby 数据接口。

## Technical Notes

* Task directory: `.trellis/tasks/08-11-polish-app-ui-icons-glass`
* Research: `.trellis/tasks/08-11-polish-app-ui-icons-glass/research/media-app-ui-reference.md`
* Impeccable context target: `app/src/main/java/com/nordic/mediahub/ui`
* Relevant shared components: `AnimatedComponents.kt`, `SharedComponents.kt`, `PlaybackDock.kt`
* Manual detector required after UI edits: `node C:\Users\hanhu\.agents\skills\impeccable\scripts/detect.mjs --json <changed targets>`
