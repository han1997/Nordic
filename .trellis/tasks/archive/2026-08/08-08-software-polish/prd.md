# 屏幕切换动画打磨

## Goal

在不新增功能的前提下,为 Music / Audiobook / Video 三个主域之间的 Tab 切换、打开/关闭播放器、展开/收起播放队列等高频过渡补齐 Compose 动画补间,让交互从「瞬间跳变」升级为「顺滑过渡」,提升整体感知质量。

## What I already know

* 项目用 state-driven navigation(无 Jetpack Navigation Compose):`MainActivity.selectedTab`、`showPlayer`/`showAudiobookPlayer`/`showVideoPlayer`、`MusicScreenV2.libraryPage` 等枚举/布尔状态驱动屏幕切换 —— 这些状态天然适合 `AnimatedContent` / `Crossfade` / `AnimatedVisibility`。
* 底部 Dock 已有 `AnimatedBottomDock`(可见性 fade 切换)、`bottomDockVisible` handle 等;spec `quality-guidelines.md` 已规约「Performance-first persistent media chrome」「Compose media player chrome auto-hide」「Bottom dock must NOT auto-reveal on a timer」—— 动画打磨需遵守既有 chrome auto-hide / hide-on-scroll 规约,不能引入定时器。
* `MusicScreenV2` 内 Home / AlbumDetail / ArtistDetail / PlaylistDetail 之间用 `libraryPage` 枚举切换(`MusicScreenV2.kt:92` 附近),目前无 transition。
* `MainActivity` 的 player overlay 用 `AnimatedVisibility` 控制 showPlayer 等布尔(`MainActivity.kt:246` 附近 `selectedTab`、`showPlayer`);Tab 之间切换是 `selectedTab` 纯状态替换,无 `AnimatedContent` 包裹。
* `SharedComponents.kt` / `AnimatedComponents.kt` 已存在共享动画组件,应优先复用而非新建。
* 设计 token 系统(`NordicShapes` / `NordicSpacing` / `NordicAlpha` / `NordicTypography`)已稳定;动画时长/缓动没有现成 token,但 spec 不禁止新增一个 `NordicMotion` token object(类似 `NordicShapes`)集中管理 duration/easing。
* spec `quality-guidelines.md` 已有「Compose performance state isolation」「Compose media list stability」「`remember(source){...}` 切片」等规则 —— 动画必须避免 broad recomposition,`AnimatedContent` 的 transition lambda 内不应触发外层 state 读取。

## Assumptions (temporary)

* 用户期望的「顺滑」是 200–350ms 标准短动画 + ease-in-out,不是 Material3 Motion 的复杂 staging;MVP 用 Compose 内置 `tween`/`spring` 即可。
* Tab 切换的视觉模型用「Crossfade」最稳(两个整屏共存时透明度叠加,无位移误差);打开播放器用「slide + fade」(从底部上推);队列 sheet 已有 BottomSheet 风格,沿用即可。
* 不动 Media3 / Repository / Cache 层;纯 UI 改动。

## Open Questions

* None — 方向已锁定。

## Requirements

* 四个 Tab(音乐 / 有声书 / 视频 / 配置)切换时有 200–350ms crossfade,而非瞬切 —— Tab 3 `ServerConfigScreen` 也纳入 crossfade,避免切配置也跳变。
* 打开/关闭全屏播放器(Music/Audiobook/Video)用上推/下滑 + fade,而非瞬现/瞬灭。
* `MusicScreenV2` 内 `libraryPage`(Home/AlbumDetail/ArtistDetail/PlaylistDetail)之间切换用 slide+fade(左/右方向跟随导航栈深度),而非瞬切。
* `MusicQueueSheet` 展开/收起保持现有 BottomSheet 行为,仅校准 duration/easing 与其他动画一致。
* 所有动画遵守 spec `quality-guidelines.md` 的 chrome auto-hide / hide-on-scroll / no-timer-reveal 规约 —— 不引入 `delay(...)` 自动重现 chrome。
* 所有动画的 duration/easing 集中到一个新的 `NordicMotion` token(放在 `ui/theme/Motion.kt`),不散落 magic number。
* 动画 lambda 内不读取外层 Compose state(避免 broad recomposition),用 `AnimatedContent`/`Crossfade` 的 `transitionSpec` 静态写法。
* 不破坏现有 `BackHandler` 优先级、不引入新的 `pointerInput` 手势冲突(上一轮已修过 cover/lyrics tap 与 drag 的手势优先级)。
* Audiobook/Video 内层 navigation 现阶段无内层 sub-page 切换,不预留动画插槽;待真正加内层 navigation 时再扩(届时复用 `NordicMotion` token 即可)。

## Acceptance Criteria (evolving)

* [ ] 切 Tab(音乐→有声书→视频→配置,含 Tab 3 `ServerConfigScreen`)肉眼可见 crossfade,而非瞬切。
* [ ] 打开/关闭 Music / Audiobook / Video 播放器肉眼可见上推/下滑+fade。
* [ ] `MusicScreenV2` 内 Home→AlbumDetail→ArtistDetail→PlaylistDetail 路径切换有方向感正确的 slide+fade。
* [ ] `ui/theme/Motion.kt` 新增 `NordicMotion` token object,所有 animation duration/easing 引用它,无散落 magic number。
* [ ] `compileDebugKotlin` / `testDebugUnitTest` / `lintDebug` 全部通过。
* [ ] 现有测试无回归;如有动画纯逻辑 helper(如 `resolveSlideDirection`),新增单测。
* [ ] 现有 `BackHandler` 优先级未被动画破坏(切歌/切 Tab 途中按返回仍按既有逻辑)。

## Definition of Done

* 代码改动 + 必要单测(动画纯逻辑 helper)。
* 三个 Gradle 任务顺序通过。
* 若发现新规约(如 `NordicMotion` token 规约),记到 `trellis-update-spec`。

## Out of Scope (explicit)

* 新增功能、新屏幕、新交互。
* 改动 Media3 / Repository / Cache / Repository 行为。
* 改动 LRC 解析、播放队列逻辑、scrobble 阈值。
* 逐元素 staggered 动画 / shared-element transition(超出 MVP,感知收益与复杂度不成正比)。
* 引入新的动画库(Lottie / Accompanist);只用 Compose 内置 `AnimatedContent`/`Crossfade`/`AnimatedVisibility` + `tween`/`spring`。
* Audiobook/Video 内层 navigation 动画(现阶段无内层 sub-page;待真正加时再扩)。
* LazyColumn item-level staggered / per-item enter animation。

## Technical Approach

### Step 1 — 新增 `ui/theme/Motion.kt`

```kotlin
object NordicMotion {
    val durationShort = 200
    val durationMedium = 300
    val durationLong = 450
    val easingStandard: CubicBezierEasing = FastOutSlowInEasing
    val easingDecelerate: CubicBezierEasing = LinearOutSlowInEasing
    val easingAccelerate: CubicBezierEasing = FastOutLinearInEasing
    val enterSlide: EnterTransition
    val exitSlide: ExitTransition
    val enterFade: EnterTransition
    val exitFade: ExitTransition
    val crossfade: ContentTransform
    // ...
}
```

### Step 2 — `MainActivity` Tab 切换加 `Crossfade`

把 `when (selectedTab) { 0 -> MusicScreenV2(...); 1 -> AudiobookScreen(...); 2 -> VideoScreen(...); 3 -> ServerConfigScreen(...) }` 包进 `Crossfade(targetState = selectedTab, animationSpec = tween(NordicMotion.durationMedium, easing = NordicMotion.easingStandard)) { ... }`。

### Step 3 — 播放器 overlay 上推/下滑

把 `showPlayer`/`showAudiobookPlayer`/`showVideoPlayer` 的 `AnimatedVisibility` 改用 `slideInVertically(initialOffsetY = { it }) + fadeIn()` / `slideOutVertically(targetOffsetY = { it }) + fadeOut()` + `tween(NordicMotion.durationMedium, easing = NordicMotion.easingStandard)`,让播放器从底部推上而非瞬现。

### Step 4 — `MusicScreenV2` `libraryPage` 切换 `AnimatedContent`

用 `AnimatedContent(targetState = libraryPage, transitionSpec = { resolveSlideDirection(targetState, initialState).toTransitionSpec() })` 包裹 Home/AlbumDetail/ArtistDetail/PlaylistDetail 渲染;`resolveSlideDirection` 是纯 helper(可单测),按导航栈深度返回 enter/exit 方向。

### Step 5 — 校准 `MusicQueueSheet` duration/easing

沿用 `NordicMotion` token,不改逻辑。

## Decision (ADR-lite)

**Context**: 项目 UI 层已稳定(无 TODO、无硬编码 token),但屏幕切换无补间动画,感知「跳变」。需要在不引入新功能/依赖的前提下补齐动画。

**Decision**:
1. 用 Compose 内置 `AnimatedContent`/`Crossfade`/`AnimatedVisibility` + `tween`/`spring`,不引新库。
2. 新增 `ui/theme/Motion.kt` 的 `NordicMotion` token object 集中管理 duration/easing,与 `NordicShapes`/`NordicSpacing`/`NordicAlpha`/`NordicTypography` 同级。
3. 三个层面:Tab 切换用 `Crossfade`、播放器 overlay 用 slide+fade、`libraryPage` 用 `AnimatedContent` + 方向感 slide。
4. 动画 lambda 内不读取外层 state;遵守 chrome auto-hide / no-timer 规约。

**Consequences**:
- 新增 `NordicMotion` token 是一次小扩张,但与既有 token 体系一致,未来 Audiobook/Video 也能复用。
- `AnimatedContent` 比 `Crossfade` 重,但方向感 slide 感知更好;两者都用 `tween(durationMedium)` 统一节奏。
- 风险:`AnimatedContent` 内 `LazyColumn` 的 key 稳定性需验证(spec 已有 list stability 规约);`BackHandler` 优先级需回归测试(切歌后的封面 tap 手势是上一轮刚修的回归点)。

## Technical Notes

* 主 Tab wiring:`app/src/main/java/com/nordic/mediahub/MainActivity.kt`(selectedTab、showPlayer/showAudiobookPlayer/showVideoPlayer 的 AnimatedVisibility、AnimatedBottomDock)
* Music 内 navigation:`app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`(libraryPage 切换)
* 队列 sheet:`app/src/main/java/com/nordic/mediahub/ui/MusicQueueSheet.kt`
* 共享动画组件:`app/src/main/java/com/nordic/mediahub/ui/AnimatedComponents.kt` / `SharedComponents.kt`
* 主题 token:`app/src/main/java/com/nordic/mediahub/ui/theme/{Shapes,Spacing,Type,Alpha}.kt`
* 现有规约:`.trellis/spec/backend/quality-guidelines.md`(chrome auto-hide、list stability、performance state isolation、pointerInput gesture priority)
