# 任选方向进行软件打磨(第二轮)

## Goal

延续上一轮"NordicMotion token + 屏幕过渡动画"打磨,再选一个低风险、感知收益明确的方向继续提升整体质量。上一轮 spec 的 "Out of token scope" 显式列出了一批遗留的内联 `tween(<num>, easing = FastOutSlowInEasing)` 字面量(组件内部 micro-interactions),可作为本轮的收敛目标。

## What I already know

* 上一轮新增了 `ui/theme/Motion.kt` 的 `NordicMotion` token object(durationShort=200/Medium=300/Long=450 + easingStandard/Decelerate/Accelerate + enterSlideUp/exitSlideDown/enterFade/exitFade/crossfadeSpec/slideDirectionSpec)。
* spec `quality-guidelines.md` 的 "Out of token scope" 明确列出:`AnimatedComponents.kt` / `ConfigCards.kt` / `MusicBrowseComponents.kt` / `PlaybackDock.kt` / `SharedComponents.kt` / `VideoPlayerScreen.kt` 中的 pre-existing micro-interaction `tween(...)` / `FastOutSlowInEasing` literals "left explicit, not migrated"——"A future convergence pass may migrate them to `NordicMotion.durationShort` / `easingStandard`; until then they are left explicit to avoid scope creep in unrelated tasks."
* 已扫描到的内联 tween/easing literal 分布:
  - `AnimatedComponents.kt:52` — press-scale `tween(durationMillis, FastOutSlowInEasing)` (1 处,`durationMillis` 是参数,可默认 `NordicMotion.durationShort`)
  - `ConfigCards.kt:124,147` — connection-test spinner `tween(150, FastOutSlowInEasing)` + fadeIn/fadeOut 300/200 (2 处)
  - `MusicBrowseComponents.kt:374,378,462,466,528,532` — segmented tab indicator `tween(180, FastOutSlowInEasing)` (6 处)
  - `PlaybackDock.kt:181,185,189` — dock fade/scale `tween(150/180, FastOutSlowInEasing)` (3 处)
  - `SharedComponents.kt:102,103` — expand/shrink `fadeIn(tween(300, FastOutSlowInEasing)) + expandVertically()` / `fadeOut(tween(200))` (2 处,exit 用了无 easing 的 `tween(200)`)
  - `VideoPlayerScreen.kt:172,173,197,198,253,254` — chrome fade `tween(VIDEO_PLAYER_CHROME_FADE_MS, FastOutSlowInEasing)` (6 处,常量驱动)
* `MediaStateComponents.kt` 的 `MediaLoadingCard` 是纯文字+静态 Surface,无 loading 指示动画 —— 加载态视觉上是"静止卡片",感知质量偏低。而 `MediaStateCard`(empty/error)也没有任何入场动画,切到空态/错误态是瞬现。
* `NordicMotion` 目前只有 screen-transition 级 token,没有专为 micro-interaction(按钮 press、chip select、chrome fade)的更短时长档位 —— `durationShort=200` 对 press-scale/chrome-fade 偏长,主流微交互通常 120–180ms。

## Assumptions (temporary)

* 用户期望"打磨"仍是视觉感知层,而非新功能或重构。
* micro-interaction 时长统一到 `NordicMotion.durationShort` 或新增 `durationMicro=150` 是安全收敛(150ms 是 Compose/Material 默认 micro 区间)。

## Open Questions

* None — 方向已锁定 A(收敛 micro-interaction 到 `NordicMotion`)。新增 `durationMicro=150` 档位的决定见 Decision。

## Requirements

* 把 `AnimatedComponents.kt` / `ConfigCards.kt` / `MusicBrowseComponents.kt` / `PlaybackDock.kt` / `SharedComponents.kt` / `VideoPlayerScreen.kt` 中所有 pre-existing 内联 `tween(<num>, easing = FastOutSlowInEasing)` / `tween(<num>)` literal 替换为对 `NordicMotion` token 的引用。
* 在 `NordicMotion` 新增 `durationMicro = 150`(Int millis)档位 —— 用于 press-scale / chrome-fade / chip-select 这类 < 200ms 的微交互;`durationShort=200` 保留给略重的过渡(如 SharedComponents 的 expand/shrink)。
* 在 `NordicMotion` 新增 `easingEmphasized: Easing`(Material3 `EmphasizedEasing` 的简化版,或直接复用 `FastOutSlowInEasing` 别名)—— 不强求,若 `easingStandard` 已够用就不新增;**决定:不新增,`easingStandard` 覆盖所有 micro-interaction**(避免 token 膨胀)。
* `VideoPlayerScreen` 的 `VIDEO_PLAYER_CHROME_FADE_MS` 常量改为引用 `NordicMotion.durationShort`(原值若为 ~250ms 则收敛到 200;若差异 > 50ms 则保留原值并加注释说明,不强行改感知)。
* `AnimatedComponents.kt` 的 `rememberPressScale(durationMillis: Int = ...)` 默认值改为 `NordicMotion.durationMicro`。
* 不改动任何动画的**行为语义** —— 只换 duration/easing 来源,enter/exit 组合方式、`+ expandVertically()` 等结构保持不变。
* 不改动 `Motion.kt` 的现有 screen-transition API(`enterSlideUp`/`exitSlideDown`/`crossfadeSpec`/`slideDirectionSpec`)—— 只新增 `durationMicro` 常量,不动现有 transition factory。
* 移除 6 个文件中不再使用的 `import androidx.compose.animation.core.FastOutSlowInEasing`(若替换后不再直接引用);`LinearOutSlowInEasing` / `FastOutLinearInEasing` 同理。
* 收敛后 spec `quality-guidelines.md` 的 "Out of token scope" 列表中相关 bullet 改为 "migrated"(或删除该 bullet),并在 "Screen-transition animation contract" 补一条 micro-interaction 收敛说明。

## Acceptance Criteria

* [ ] `AnimatedComponents.kt` / `ConfigCards.kt` / `MusicBrowseComponents.kt` / `PlaybackDock.kt` / `SharedComponents.kt`` / `VideoPlayerScreen.kt` 中无散落的 `tween(<num>, easing = FastOutSlowInEasing)` / `tween(<num>)` literal(全部引用 `NordicMotion.durationMicro` / `durationShort` / `easingStandard`)。
* [ ] `Motion.kt` 新增 `durationMicro = 150`,不破坏现有 API。
* [ ] `VideoPlayerScreen.VIDEO_PLAYER_CHROME_FADE_MS` 引用 `NordicMotion.durationShort`(或保留原值并注释)。
* [ ] `AnimatedComponents.rememberPressScale` 默认 `durationMillis` 引用 `NordicMotion.durationMicro`。
* [ ] 无用的 `FastOutSlowInEasing` / `LinearOutSlowInEasing` / `FastOutLinearInEasing` import 已移除。
* [ ] `compileDebugKotlin` / `testDebugUnitTest` / `lintDebug` 全部通过。
* [ ] 现有测试无回归;无需新增单测(纯 token 替换,无逻辑变化)—— 但若 `VideoPlayerScreen` 常量被改值,需确认无测试硬编码旧值。
* [ ] spec `quality-guidelines.md` 的 "Out of token scope" bullet 更新(micro-interaction 已迁移)。

## Definition of Done

* 代码改动(纯 token 替换,无逻辑变化)。
* 三个 Gradle 任务顺序通过。
* spec 更新 `quality-guidelines.md` 的 "Out of token scope" + "Screen-transition animation contract" 补一条 micro-interaction 收敛说明。

## Out of Scope (explicit)

* 新增功能、新屏幕、新交互。
* 改动 Media3 / Repository / Cache / Repository 行为。
* 改动任何动画的**行为语义**(enter/exit 组合方式、`+ expandVertically()`、spring vs tween 选择保持不变)。
* 改动 `Motion.kt` 的现有 screen-transition API(`enterSlideUp`/`exitSlideDown`/`crossfadeSpec`/`slideDirectionSpec`)—— 只新增 `durationMicro` 常量。
* 新增 `easingEmphasized` / `easingEasingStandardAccelerate` 等 token(避免 token 膨胀,`easingStandard` 已够用)。
* 收敛 `ConfigCards.kt:147` 的 `fadeIn(tween(300)) togetherWith fadeOut(tween(200))` 时长差异(300 vs 200)—— 保留原差异(enter 略慢于 exit 是有意设计),只把 literal 换成 `NordicMotion.durationMedium` / `durationShort`。

## Decision (ADR-lite)

**Context**: 上轮新增 `NordicMotion` 只覆盖 screen-transition;6 个 UI 文件里仍有 ~20 处内联 `tween(<num>, FastOutSlowInEasing)` literal 用于 micro-interaction(press-scale / chrome-fade / chip-select / dock fade)。spec 明列为 "future convergence pass"。

**Decision**:
1. 新增 `NordicMotion.durationMicro = 150`(Int millis)档位,覆盖 < 200ms 的 micro-interaction;`durationShort=200` 保留给略重的过渡(expand/shrink)。
2. 所有 micro-interaction `tween` literal 替换为 `NordicMotion.durationMicro` / `durationShort` / `easingStandard`;不新增 `easingEmphasized`(`easingStandard` = `FastOutSlowInEasing` 已覆盖)。
3. `VideoPlayerScreen.VIDEO_PLAYER_CHROME_FADE_MS` 若原值接近 200ms 则收敛到 `durationShort`;若差异 > 50ms 则保留原值 + 注释(不强行改感知)。
4. 不改任何动画的行为语义,只换 duration/easing 来源。

**Consequences**:
- `durationMicro` 是 `NordicMotion` 的第 4 个时长档位(durationShort/Medium/Long/Micro),与 Material3 motion tier 一致;未来 micro-interaction 不再有 magic number。
- `VideoPlayerScreen` chrome fade 时长若被收敛到 200ms,感知会比原值(若为 250ms)略快 —— 但在 chrome auto-hide(4s)的上下文里,50ms 差异不可感知。
- 风险极低:纯 token 替换,无逻辑/结构变化;trellis-check 只需验证 compile/test/lint 绿 + import 清理。

## Technical Notes

* 上轮 token: `app/src/main/java/com/nordic/mediahub/ui/theme/Motion.kt`
* 上轮 spec 条款: `.trellis/spec/backend/quality-guidelines.md` 的 "Design token system" + "Screen-transition animation contract"
* micro-interaction 文件: `AnimatedComponents.kt` / `ConfigCards.kt` / `MusicBrowseComponents.kt` / `PlaybackDock.kt` / `SharedComponents.kt` / `VideoPlayerScreen.kt`
* 状态卡片: `app/src/main/java/com/nordic/mediahub/ui/MediaStateComponents.kt`
