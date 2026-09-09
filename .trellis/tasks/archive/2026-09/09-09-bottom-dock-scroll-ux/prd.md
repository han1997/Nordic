# 底部导航 Dock 滚动交互优化

## Goal

优化底部导航 Dock 的出现/隐藏体验。现状：任何 1px 滚动就隐藏（过度敏感），恢复只能点小把手（麻烦），滚到底把手常驻，滚动方向不分。改为：滚动阈值防误触 + 向上滚手势恢复 + 滚到底恢复 + 把手触达增强。不违反 spec「禁止定时器恢复」禁令（全部手势驱动）。

## What I already know

* 用户决策：A+B+C+D 全套；滚动阈值 24dp。
* 现状实现：`MainActivity.kt:663-708`——`hideBottomDockForScroll()` 在 NestedScrollConnection 的 onPreScroll/onPostScroll/onPreFling/onPostFling 中，任何 `available.y != 0f` 即隐藏；恢复 = 点把手 / 切 tab / 开关播放器（`LaunchedEffect(selectedTab, ...)`）。
* `resolveBottomDockPresentation(hasPlayerLayer, fullDockVisible)`（`MainActivity.kt:71`）：Hidden/Dock/Handle 三态。
* `BottomDockHandle`（`PlaybackDock.kt:152`）：胶囊 Surface + `padding(bottom = sm)`，触达区小于 48dp 标准。
* spec 合同（quality-guidelines.md:684）：**只禁止定时器 reveal**；「explicit user action」恢复是被认可的路径——向上滚/到底都是显式手势，符合。
* 动画：`AnimatedBottomDock` 用 260/150ms + NordicMotion easing（不动）。
* dock 高度测量：`measuredDockHeight` + `dockBottomPadding` 动画（不动）。

## Requirements (confirmed)

### R1 滚动阈值防误触（A）
* 累计滚动位移（带符号 px）≥ 24dp 才触发隐藏；轻碰/微小滚动不触发。
* 累计值在意图触发后或滚动方向反转后复位。

### R2 向上滚恢复（B，核心）
* dock 隐藏（Handle 态）时，向上滚（手指下拉，`available.y > 0`）累计 ≥ 24dp → 恢复 dock。
* 同样手势驱动、阈值防误触；恢复后累计复位。
* dock 可见时向上滚不产生任何效果（不隐藏也不复位累计——见纯函数语义）。

### R3 滚到底恢复（C）
* 列表滚动到底（`onPostScroll` 出现未消费的向上 available = overscroll bottom）且该次滚动为向下滚动产生 → 恢复 dock。
* 与 R2 独立：到底恢复不需要累计满阈值（到底本身是强信号），但仍需处于 Handle 态。

### R4 把手触达增强（D）
* `BottomDockHandle` 点击区域扩到 ≥ 48dp 高（视觉尺寸不变，扩大可点击 padding/minTouchTarget）。

### 保留不变
* 播放器层打开时 Hidden；切 tab/开关播放器重置 Dock；动画参数；dock 高度测量与 padding 动画。

## Acceptance Criteria

* [ ] 轻碰/微滚（<24dp）不隐藏 dock；正常滚动一屏必隐藏。
* [ ] dock 隐藏后向上滚一屏自动恢复；轻碰不恢复。
* [ ] 列表滚到底自动恢复 dock。
* [ ] 把手点击区域 ≥ 48dp（无障碍扫描通过），视觉不变。
* [ ] 播放器层打开时 dock 始终隐藏；切 tab 重置显示——均不回归。
* [ ] 单测：滚动意图纯函数（阈值边界、方向反转复位、Handle 态判定）。
* [ ] compile + test + lint + assemble 全绿；CHANGELOG 条目；manual-checklist + APK。

## Out of Scope

* 定时器自动恢复（spec 禁止）。
* 播放器层内滚动逻辑。
* dock 视觉重设计、把手样式改动（仅触达区）。

## Decision (ADR-lite)

**Context**：恢复策略——定时器（主流 App 常用但本项目 spec 明确禁止）vs 手势驱动。
**Decision**：手势驱动（向上滚 + 到底），阈值 24dp。与 spec「Performance-first persistent media chrome」的意图一致：不打断阅读，但用户主动往回翻时给回导航。
**Consequences**：向上滚恢复依赖 NestedScrollConnection 收到未消费的向上滚动——LazyColumn/垂直滚动容器标准行为，但非滚动容器页面（如配置页表单）不会触发，把手兜底仍在。

## Technical Notes

* 纯函数（放 `PlaybackDock.kt`）：`resolveBottomDockScrollIntent(accumulatedDeltaPx: Float, thresholdPx: Float, dockVisible: Boolean): BottomDockScrollIntent`——`Hide`/`Show`/`None`。语义：dockVisible && accumulated ≥ threshold → Hide；!dockVisible && accumulated ≤ -threshold → Show；方向反转即复位（由调用方在状态更新时处理）。
* 累计状态：`remember { mutableStateOf(0f) }`（px，密度换算 `thresholdPx = with(density) { 24.dp.toPx() }`）。
* 方向约定：`available.y`/`consumed.y` 正值 = 手指下拉 = 内容向底部方向滚回（向上看）；负值 = 手指上推 = 向下看内容。Hide 用负向累计，Show 用正向累计。
* fling：onPreFling/onPostFling 只传递速度给同一累计器（按速度方向累加一次较大的 delta 或直接复用 scroll 累计——实现时取简单一致方案：fling 只在其方向上补一次阈值判定，不重复累计）。
* 到底恢复：`onPostScroll(consumed, available)` 中 `available.y > 0f`（未消费的向上滚动 = 已到底）→ 若 Handle 态直接 Show。
* MainActivity 的 scroll connection `remember` key 保持 `(showPlayer, showAudiobookPlayer, showVideoPlayer)`，累计状态独立 remember。
