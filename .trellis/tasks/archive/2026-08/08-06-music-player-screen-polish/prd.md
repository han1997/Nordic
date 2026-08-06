# 音乐播放页面美化

## Goal

美化 `MusicPlayerScreen`，使其视觉与交互更接近主流音乐软件（Spotify / Apple Music / YouTube Music / 网易云 / QQ 音乐），让正在播放页面在排版、控件、信息层级与质感上符合用户的既有心智模型。

## What I already know

* 播放页在 `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`（635 行），由 `MusicPlayerScreen` + `PlayerTopBar` + `PlayerPrimaryDisplay`（封面 / 歌词切换）+ `PlayerConsole`（进度条 + 控制行）+ `PlayerControlButton` 组成。
* 控件全部使用 **文字字形**：`▶ Ⅱ ‹ › ↺ ↺1 ↺A ⇄ ≡ ⌄`。这是最显眼的「不像主流」信号——主流 App 一律使用矢量图标。
* 项目已依赖 `androidx.compose.material:material-icons-core` 与 `material-icons-extended`（`app/build.gradle.kts:51-52`），可直接用 `Icons.Filled.*` / `Icons.AutoMirrored.Filled.*`。
* 同项目 `VideoPlayerScreen.kt` 已用 `Icons.Filled.PlayArrow / Pause / FastRewind / FastForward / Close / Fullscreen / AspectRatio` 配合自建 `VideoPlayerChromeButton(icon, primary, size)`——已有图标按钮的范例可对齐。
* 设计令牌已就绪：`NordicShapes`(none/sm/md/lg/xl/full)、`NordicSpacing`(xs..xxxl/content)、`NordicAlpha`(medium/subtle/faint)、`NordicTypography`（displaySmall/headlineMedium/titleMedium/titleSmall/bodyMedium/labelLarge/bodySmall）。规范要求新 UI 必须用令牌，禁硬编码 `RoundedCornerShape`/`dp`/`sp`/`alpha`。
* 背景已是「封面 12% alpha + 垂直渐变」营造氛围；封面是正方形 `Surface(NordicShapes.xl, shadowElevation=10dp)`；歌词区是 5/7 行居中渐变卡片。
* `compact` 分支（`maxHeight < 740.dp`）已存在，美化不能破坏小屏布局。
* 底层能力齐备：播放引擎 `MusicPlaybackEngine`、队列 `MusicQueueSheet`、歌词 `MusicLyrics`、收藏（star）经 `NavidromeRepository`、repeat/shuffle 状态已有回调入口（`onToggleRepeat`/`onToggleShuffle`）。

## Assumptions (temporary)

* 「主流感」的主要差距在控件图标化 + 信息层级 + 进度条质感，而非需要新增大型功能。
* 用户希望保留现有「点封面切换歌词」的交互与现有回调签名，做视觉/交互层美化，不重写播放引擎或数据流。
* 收藏（star/favorite）按钮是否纳入本次范围待确认。

## Decision (ADR-lite)

**Context**: 当前播放页用文字字形做控件、用 Material3 原生 Slider，与主流音乐 App 视觉差距最大。需选定一个主流方向作为美化基线。

**Decision**: 采用 **Option B — Apple Music 富信息**。在图标化 + 细线进度条 + 控制行重排（A 的全部）之上，进一步：标题/艺人移到封面下方（主流信息层级）、顶栏瘦身为 chevron-down + “正在播放”、封面下方新增含 ♥ 收藏的二级 meta 行、加深封面背景的毛玻璃质感。

**Consequences**: 视觉更高级、信息层级更贴近 Apple Music；需要新增 `onToggleFavorite` 回调并把 `starred` 字段加到 `NavidromeSong`（Gson 自动绑定 Subsonic `starred` 属性，无需手写映射）；需小心 `compact` 分支在「封面下方标题 + meta 行 + 控制行」挤压下仍可读；动态调色（Palette）仍不在范围内。

## Favorite plumbing (derived)

* `NavidromeSong` 当前**直接复用为 Subsonic wire DTO**（`SongList.song` / `Starred2.song` 都用它），且**无 `starred` 字段**。
* 方案：给 `NavidromeSong` 加 `val starred: String? = null`。Gson 会自动从 Subsonic JSON 的 `starred` 属性绑定（非空时间戳 = 已收藏）。
* `NavidromeRepository.star(id=...)` / `unstar(id=...)` 已存在（`NavidromeRepository.kt:554/573`），按 `id` 收藏单曲。
* 播放页加 `onToggleFavorite: (songId: String, starred: Boolean) -> Unit` 回调；♥ 初值取 `song.starred != null`，点击乐观更新并调用 VM → repo.star/unstar。
* 注意：`NavidromeMusicCache` 缓存 songs，新增可空字段为**加性**变更（非语义改写），按 spec 评估是否需 bump `MUSIC_CACHE_SCHEMA_VERSION`（实现时核对）。

## Requirements (evolving)

### 控件与进度条（图标化 + 细线）
* 所有播放控制替换为 Material 矢量图标，与 `VideoPlayerScreen` 一致：
  * `▶`/`Ⅱ` → `Icons.Filled.PlayArrow` / `Icons.Filled.Pause`
  * `‹`/`›` → `Icons.AutoMirrored.Filled.SkipPrevious` / `Icons.AutoMirrored.Filled.SkipNext`（AutoMirrored 保证 RTL 正确）
  * `↺` / `↺1` / `↺A` → `Icons.AutoMirrored.Filled.Repeat`；单曲循环在图标上加小 "1" 角标
  * `⇄` → `Icons.Filled.Shuffle`
  * `≡` → `Icons.AutoMirrored.Filled.QueueMusic`
  * `⌄` 关闭 → `Icons.Filled.KeyboardArrowDown`
* Material3 `Slider` 重塑为细线 + 小圆 thumb（用 `SliderDefaults` 的 `thumb`/`track` 插槽或自定义 `Canvas`，保留 `onValueChange`/`onValueChangeFinished` 回调与 `colorScheme.primary` active track）。
* active shuffle/repeat 用 `colorScheme.primary` 着色（现有 `active` bg 逻辑保留）。

### 布局与信息层级（Apple Music 化）
* 标题/艺人从顶栏移到**封面下方**：标题大粗体 + 艺人较小着色。
* 顶栏瘦身为：左侧 `KeyboardArrowDown` + 居中 "正在播放"（不再放标题/专辑/duration chip）。
* 封面下方新增 **meta 行**：左侧 ♥ 收藏 + 右侧 队列图标（队列从主控制行移出）。
* 主控制行重排为 `Shuffle → Prev → Play/Pause(最大实心 primary 圆) → Next → Repeat`。
* 加深封面背景毛玻璃质感：在现有 12% alpha cover overlay 上加深 scrim（不引 Palette，不做动态调色）。
* 保留「点封面切换歌词」交互与歌词区现有渲染。

### 收藏（favorite）
* `NavidromeSong` 增加 `val starred: String? = null`（Gson 自动绑定 Subsonic `starred` 属性，非空时间戳 = 已收藏）。
* `MusicPlayerScreen` 增加 `onToggleFavorite: (songId: String, starred: Boolean) -> Unit` 回调。
* ♥ 初值取 `song.starred != null`；点击乐观更新本地状态并调用 VM → `NavidromeRepository.star(id=songId)` / `unstar(id=songId)`。
* 切歌时本地乐观状态随新 song 的 `starred` 重置。
* 实现时核对 `NavidromeMusicCache` 是否需 bump `MUSIC_CACHE_SCHEMA_VERSION`（加性可空字段，按 spec 判断）。

### 下滑关闭手势
* 在播放页根容器加 swipe-down 手势，触发 `onClose`（与 chevron-down 按钮、系统 Back 行为一致）。
* 仅当手势起始于顶部/封面区时触发；不得与歌词滚动或进度条拖拽冲突。

### 通用约束
* 保持设计令牌化，不引入硬编码 shape/spacing/alpha/typography 字面量。
* 不破坏 `compact`（`maxHeight < 740.dp`）小屏布局与四个 tab 导航。
* 不改动播放引擎、队列 sheet、歌词解析、现有回调签名（仅新增 `onToggleFavorite`）。

## Acceptance Criteria (evolving)

* [ ] 所有播放控制使用 Material 矢量图标，无文字字形占位。
* [ ] 进度条为细线 + 小圆 thumb，不再是 Material3 原生粗滑块外观。
* [ ] 控制行顺序为 `Shuffle → Prev → Play/Pause → Next → Repeat`；单曲循环显示 "1" 角标。
* [ ] 标题/艺人在封面下方；顶栏为 chevron-down + "正在播放"。
* [ ] 封面下方有 meta 行：♥ 收藏（左）+ 队列图标（右）。
* [ ] ♥ 初值反映 `song.starred`；点击调用 star/unstar 并乐观更新；切歌后重置。
* [ ] 下滑手势可关闭播放页，且不与歌词滚动/进度条拖拽冲突。
* [ ] 封面背景毛玻璃质感加深，不引入 Palette。
* [ ] `compact` 分支不破坏。
* [ ] 编译、单测、lint 通过。

## Definition of Done (team quality bar)

* Tests added/updated where logic is testable.
* Gradle gates pass: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`.
* Spec updated if a new player-screen convention is established.

## Technical Approach

* **MusicPlayerScreen.kt** 为主要改动文件：拆分/重排 `PlayerTopBar`（瘦身）、`PlayerPrimaryDisplay`（封面 + 下方标题/meta 行）、`PlayerConsole`（细线进度条 + 重排控制行）、`PlayerControlButton`（接收 `ImageVector` 而非文字 `label`）。
* **NavidromeModels.kt**：`NavidromeSong` 加 `val starred: String? = null`。
* **MainActivity.kt**（`MusicPlayerLayer`）：新增 `onToggleFavorite` 回调，调用 `MusicPlaybackViewModel`（或直接 `navidromeRepository.star/unstar`）并乐观更新 `currentSong.starred`。
* **MusicPlaybackViewModel.kt**：可能新增 `toggleFavorite(songId, starred)` 方法，调用 repo + 刷新当前 song 状态（实现时按最小侵入决定）。
* 进度条细线化：优先用 `SliderDefaults.colors` + 自定义 `thumb`/`track` 插槽；若插槽不足以达成主流细线外观，再考虑自绘 `Canvas`，仍走现有 `onValueChange`/`onValueChangeFinished`。
* 下滑关闭：根 `Box` 包一层 `Modifier.pointerInput`/`swipeable`（或 `Modifier.nestedScroll`），阈值满足时调 `onClose`；手势起始于封面/顶部区，避开歌词滚动与进度条。
* 复用 `VideoPlayerScreen` 的 `VideoPlayerChromeButton(icon, primary, size)` 模式作为图标按钮范例，保持两屏视觉一致。
* 所有新 UI 走 `NordicShapes/Spacing/Alpha/Typography` 令牌。

## Out of Scope (explicit)

* Palette 动态调色 / 逐字卡拉OK高亮 / 标题跑马灯 / "..." more 按钮（暂无定义动作）。
* 改动 `MusicScreenV2` 浏览页、底部 Dock、播放引擎、队列 sheet、歌词解析。
* 改 Audiobook/Video 播放页（仅以本次音乐页图标风格为参照）。
* 新增复杂手势系统或全局导航框架。

## Technical Notes

* 主文件: `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`。
* 图标依赖: `app/build.gradle.kts:51-52`（material-icons-core/extended）。
* 图标按钮范例: `VideoPlayerScreen.kt` 的 `VideoPlayerChromeButton(icon, primary, size)`。
* 设计令牌: `ui/theme/{Shapes,Spacing,Type,Color}.kt`。
* 相关规范: `.trellis/spec/backend/quality-guidelines.md` → 设计令牌系统 / Performance-first persistent media chrome。

## Research References

* [`research/mainstream-music-player-ui.md`](research/mainstream-music-player-ui.md) — 跨 Spotify/Apple Music/YouTube Music/网易云/QQ 的播放页 UI 约定 + 三个落地选项。注：本次外部网页抓取受限，结论为方向性而非像素级。
