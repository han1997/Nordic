# 优化底部导航显示机制

## Goal

优化底部导航 / 播放 Dock 的显示机制，解决当前用户滚动后静止一段时间 Dock 自动出现、遮挡内容并造成不适的问题，让底部导航的出现更符合用户主动意图。

## What I already know

* 当前 `MainActivity.kt` 里 `BOTTOM_DOCK_REVEAL_DELAY_MS = 650L`。
* `bottomDockVisible` 默认 `true`。
* 滚动时 `hideBottomDockForScroll(scheduleReveal = true)` 会隐藏 Dock，并通过 `scheduleBottomDockReveal()` 在 650ms 后自动重新显示。
* fling 时 `onPreFling` 会隐藏 Dock 且取消 reveal，但 `onPostFling` 又会调用 `scheduleBottomDockReveal()`。
* `LaunchedEffect(selectedTab, showPlayer, showAudiobookPlayer, showVideoPlayer)` 会在切 tab 或 player 状态变化时强制显示 Dock。
* 当前 Dock 内容由 `PlaybackDock.kt` 的 `PolishedPlaybackDock` / `PolishedBottomNav` 渲染，包含 Now Playing 区域 + 四个 tab（音乐 / 有声书 / 视频 / 配置）。
* 相关规范已有 “Performance-first persistent media chrome”：静态页面应优先内容可读性，不应让持久 chrome 在静止时自动遮挡内容。

## Assumptions (temporary)

* 用户不舒服的关键点不是“滚动时隐藏”，而是“用户停止操作后 Dock 自动回来”。
* 更合理的方式应该让 Dock 的重新出现由明确的用户动作触发，而不是计时器触发。
* 仍需要保留清晰的恢复路径，避免用户找不到底部导航。

## Open Questions

* （已解决，见 Requirements / Decision）

## Requirements (evolving)

* 滚动内容时底部 Dock 应隐藏，避免遮挡页面内容。
* Dock 不应因为静止一段时间自动展示。
* Dock 隐藏后采用手动恢复，不使用定时器自动恢复。
* 用户必须有明确的手动恢复入口可以让 Dock 返回：Dock 隐藏时显示一个很小的底部胶囊把手，点击后展开完整 Dock。
* 底部把手应占用尽量少空间，不显示完整导航内容，不遮挡列表主体。
* 进入播放器层（Music / Audiobook / Video player）时仍隐藏 Dock。
* 切换 tab 后可以显示 Dock，避免用户刚切换后失去导航反馈。

## Acceptance Criteria (evolving)

* [ ] 滚动后停止不再自动显示底部 Dock。
* [ ] fling 后停止不再自动显示底部 Dock。
* [ ] 用户有明确的恢复动作可以让 Dock 返回。
* [ ] Dock 隐藏时只显示低干扰底部小把手；点击把手恢复完整 Dock。
* [ ] 切换 tab / 关闭 player 后导航状态仍可恢复。
* [ ] 不破坏 Music / Audiobook / Video / 配置 四个 tab 的选择行为。
* [ ] 编译、单测、lint 通过。

## Definition of Done (team quality bar)

* Tests added/updated where logic is testable.
* Gradle gates pass: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`.
* Spec updated if a new persistent chrome convention is established.

## Out of Scope (explicit)

* 重做底部 Dock 视觉设计。
* 移除底部导航本身。
* 改播放器 UI、播放队列、服务器配置页逻辑。
* 新增复杂手势系统或全局导航框架。

## Technical Notes

* Main logic: `app/src/main/java/com/nordic/mediahub/MainActivity.kt` (`scheduleBottomDockReveal`, `hideBottomDockForScroll`, `bottomDockScrollConnection`).
* Dock UI: `app/src/main/java/com/nordic/mediahub/ui/PlaybackDock.kt`.
* Relevant spec: `.trellis/spec/backend/quality-guidelines.md` → Performance-first persistent media chrome.

## Technical Approach

* 移除或停用 `scheduleBottomDockReveal()` 的自动延迟恢复逻辑，确保 scroll/fling 后不会因静止自动显示 Dock。
* 将 Dock 状态拆成：完整 Dock 可见 / 隐藏但显示小把手 / 播放器层完全隐藏。
* 新增轻量 `BottomDockHandle`（或同等命名）Composable：仅在没有 player layer 且完整 Dock 隐藏时显示，点击设置 `bottomDockVisible = true`。
* 继续保留 `LaunchedEffect(selectedTab, showPlayer, showAudiobookPlayer, showVideoPlayer)` 在切 tab / player 状态变化时恢复或重置 Dock，避免导航状态丢失。
* 尽量复用 `AnimatedBottomDock` 动画风格，保持进入/退出一致。

## Decision (ADR-lite)

**Context**: 当前 Dock 滚动隐藏后会在 650ms 静止期后自动出现，用户明确反馈“不舒服”。

**Decision**: Dock 隐藏后不再定时自动出现，改为底部小把手手动恢复；切 tab / player 状态变化仍可重置 Dock 可见性。

**Consequences**: 内容阅读不再被静止自动出现的 Dock 打断；用户仍有可发现的恢复入口；需要新增一个低干扰 handle UI 并验证不会破坏四个 tab 的导航行为。
