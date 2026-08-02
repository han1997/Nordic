# UI全面检查与完善

## Goal

对 Nordic media hub Android app 的 Jetpack Compose UI 层做一次全面检查与完善，在近期 T1-T8 code review、共享组件抽取、大屏拆分、滚动卡顿优化之后，进一步发现并修复残留的视觉一致性、代码质量、功能边界与性能问题，使 UI 层达到可发布的质量标准。

## What I already know

- 单仓 Kotlin/Jetpack Compose Android app，UI 代码集中在 `app/src/main/java/com/nordic/mediahub/ui/`。
- UI 包约 23 个文件，主要大文件：`MusicScreenV2.kt`(1208)、`MusicBrowseComponents.kt`(705)、`AudiobookScreen.kt`(646)、`MusicPlayerScreen.kt`(610)、`VideoPlayerScreen.kt`(586)、`MusicHomeSections.kt`(423)、`AudiobookPlayerScreen.kt`(421)、`MusicQueueSheet.kt`(420)。
- 已有共享组件：`SharedComponents.kt`(234) 内含 `MetaChip`/`ToneMetaChip`/`ScreenBackButton`/`CoverArt`/`PrimaryActionButton`；`AnimatedComponents.kt`、`MediaStateComponents.kt`、`PlaybackDock.kt`。
- 主题：`theme/Theme.kt` + `theme/Color.kt`，Material3 dark/light，primary=紫(B098FF/7B5FD3)、secondary=天蓝(7DD3FC/0EA5E9)。
- 近期已完成：T1-T8 code review 全部完成、6 个 MetaChip + 2 个 BackButton 变体合并、CoverArt 统一、`formatLongDuration` 统一、MusicScreenV2/VideoScreen 拆分、播放状态隔离 + crossfade + remember 衍生字符串（滚动卡顿修复）。
- spec 层：`.trellis/spec/backend/` 含 directory-structure / quality-guidelines / error-handling / database-guidelines / logging / 三个服务集成契约。验证命令：`compileDebugKotlin` / `testDebugUnitTest` / `lintDebug`。

## Assumptions (temporary)

- "全面检查" 覆盖视觉一致性 + 代码质量 + 功能边界，而非只做视觉。
- 本次以"检查 + 修复"为主，不做大规模新功能或重设计。
- 仍以 main 分支单 PR / 多逻辑提交方式推进。

## Decisions

- **范围侧重：视觉/UX 一致性为主**（Option 1）。在近期重构之后排查残留的视觉不一致：间距/圆角/字号/颜色用法、空/加载/错误态、暗色模式、可访问性、Music/Video/Audiobook 三端视觉对齐。
- **MVP 边界：设计 token 体系化**（Option 1）。新建 `theme/Shapes.kt` + `Spacing.kt` + `Type.kt`，把三类魔数收敛成有限 token 集，全 23 个 UI 文件迁移到 token。根因治理、一次根治。

## Open Questions

- (全部已收敛 — 见 Decisions / Technical Approach / ADR)

## Requirements (evolving)

- 新建设计 token 体系：`theme/Shapes.kt`（shape tokens）+ `theme/Spacing.kt`（spacing tokens）+ `theme/Type.kt`（typography scale + secondary text alpha tiers）
- 全 23 个 UI 文件迁移：`RoundedCornerShape(<魔数>)` → shape token；`Modifier.padding(<魔数>)` → spacing token；`fontSize=<魔数>.sp` + `fontWeight` → typography token；`colorScheme.onSurface.copy(alpha=<魔数>)` → 统一 secondary-text alpha tier
- `CircleShape` 与 `RoundedCornerShape(999.dp)` 统一为单一 `Shapes.Full`（或 CircleShape）写法

## Acceptance Criteria (evolving)

- [ ] `theme/Shapes.kt` / `Spacing.kt` / `Type.kt` 存在并被 `MaterialTheme` 接入（Shapes 经 `MaterialTheme(shapes=...)`，Typography 经 `MaterialTheme(typography=...)`，Spacing 以 `NordicSpacing` 对象或 extension 暴露）
- [ ] UI 文件中不再出现裸 `RoundedCornerShape(<数字>.dp)` 字面量（允许 `RoundedCornerShape(Percent)` 或动态计算除外）
- [ ] UI 文件中不再出现裸 `fontSize = <数字>.sp` 字面量（typography token 覆盖所有正文/标题/chip/caption 场景）
- [ ] 次要文字 alpha 收敛为 ≤ 4 个命名 tier（如 onSurfaceMedium / onSurfaceSubtle / onSurfaceFaint），不再散落 11 种魔数
- [ ] `compileDebugKotlin` / `testDebugUnitTest`(312) / `lintDebug` 全绿
- [ ] trellis-check APPROVE

## Definition of Done (team quality bar)

- compileDebugKotlin / testDebugUnitTest(312 tests) / lintDebug 全绿
- trellis-check APPROVE
- 如有行为变更，更新相关 spec / notes

## Out of Scope (explicit)

- 可访问性常量（MinTouchTarget 48dp 校验）— 留到后续任务
- 显式暗色 WCAG AA 对比度校验 / 暗色专用 alpha tier — 留到后续任务（token 化会自动改善暗色可读性，作为免费红利）
- 空/加载/错误态文案与图标一致性 — 不在 token 范围
- 截图测试基线建立 — 本任务靠 compile + lint + 人工巡检
- 新功能、重设计、布局重构 — 纯视觉 token 迁移
- 服务集成层（api/data/playback）改动 — 不触碰

## Expansion Notes (DIVERGE)

### 1. Future evolution
- token 体系一旦建立，后续 dark/light 主题切换、动态取色（Material You / Android 12+）都可直接基于 token 扩展，无需再扫全 UI。
- 若将来引入 tablet/landscape 布局，spacing/shape token 可按 window-size 分档。

### 2. Related scenarios
- **空/加载/错误态** — `MediaStateComponents.kt` 已有，但各屏是否一致复用？token 化后视觉自然对齐，但文案/图标一致性不在 token 范围内。
- **暗色模式** — 当前 alpha 魔数在暗色下对比度参差；统一 alpha tier 后暗色可读性会一并改善，是 token 化的"免费红利"。
- **可访问性** — 48dp 触摸目标、文字对比度（WCAG AA）：token 化不直接解决，但 spacing token 可设 `MinTouchTarget = 48.dp` 常量引导。

### 3. Failure & edge cases
- **视觉回归风险** — token 化是纯视觉重构，最大风险是"迁移后某处看起来不一样了"。无截图测试基线时，只能靠 compile + lint + 人工巡检。
- **token 取值偏差** — 现有魔数集合较大（shape 12 档/spacing 20 档/font 14 档），收敛到 5-7 档时必然有"四舍五入"，需明确收敛规则（就近 vs 向小档 vs 向大档）。
- **动态计算的 shape** — `MediaStateComponents.kt:45` 有 `RoundedCornerShape(if (compact) 20.dp else 24.dp)`，token 化后应改为 `if (compact) Shapes.md else Shapes.lg`，保留动态语义。

## Research References

- (暂无外部 research；本任务为 repo 内 token 收敛，无外部库选型)

## Technical Approach

### 新建 token 文件（`ui/theme/`）

依据 `directory-structure.md`：`ui/theme/` 是 "Material color/theme definitions" 的归属层，三个新文件均放此目录。

**`theme/Shapes.kt`** — Material3 `Shapes` 实例 + 命名常量：
```kotlin
object NordicShapes {
    val none = RoundedCornerShape(0.dp)
    val sm   = RoundedCornerShape(12.dp)   // 小卡片/chip 内嵌
    val md   = RoundedCornerShape(16.dp)   // 卡片默认
    val lg   = RoundedCornerShape(20.dp)   // 大卡片/封面
    val xl   = RoundedCornerShape(24.dp)   // sheet/surface
    val full = RoundedCornerShape(50 perc) // 胶囊/圆 —— 取代 999.dp 与 CircleShape
}
val NordicShapesMaterial = Shapes(
    extraSmall = NordicShapes.sm, small = NordicShapes.sm,
    medium = NordicShapes.md, large = NordicShapes.lg, extraLarge = NordicShapes.xl
)
```

**`theme/Spacing.kt`** — `Dp` 常量对象：
```kotlin
object NordicSpacing {
    val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp
    val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp
    val content = 16.dp   // 屏幕内容标准边距语义别名
}
```

**`theme/Type.kt`** — Material3 `Typography` + 命名 alpha tier：
```kotlin
object NordicAlpha {
    val medium = 0.68f; val subtle = 0.5f; val faint = 0.3f
}
val NordicTypography = Typography(
    displaySmall = TextStyle(32.sp, Bold),
    headlineMedium = TextStyle(22.sp, Bold),
    titleMedium = TextStyle(16.sp, SemiBold),
    titleSmall = TextStyle(14.sp, SemiBold),
    bodyMedium = TextStyle(14.sp, Normal),
    labelLarge = TextStyle(13.sp, SemiBold),
    bodySmall = TextStyle(12.sp, Medium)
)
// 接入：MaterialTheme(typography = NordicTypography, shapes = NordicShapesMaterial, ...)
```

### 收敛规则（统一）

- Shape：{8,10,12,13}→sm；{14,16,18}→md（14 向上、18 向下，就近）；{18,20}→lg（18 可选 lg 或 md，就近优先 lg 用于封面类）；{24,28,30}→xl；{999, CircleShape}→full。
- Spacing：{3,4,5}→xs；{6,7,8,9}→sm；{10,12,13,14}→md；{15,16,18}→lg；{20,22}→xl；{24,28}→xxl；{30,34}→xxxl。
- Typography：{32,38,54}→display；{20,22,24}→headline；{15,16,17,18}→title；{14}→titleSm/body；{13}→label；{11,12}→caption。
- Alpha：{0.64,0.66,0.68}→medium；{0.5,0.56,0.4}→subtle；{0.3,0.4}→faint。

### 关键约束（来自 spec）

- **`MediaStateComponents` alpha 例外** — `quality-guidelines.md:51-53` 明确 `MediaStateComponents` 编码了设计系统 alpha：empty `0.72f`、loading `0.76f`、error container。这三个值是状态语义而非通用文字层级，**不并入 `NordicAlpha` 三档**，保留在 `MediaStateComponents` 内部作为状态专用常量；`NordicAlpha` 仅用于通用文字层级。
- **动态 shape** — `MediaStateComponents.kt:45` 的 `RoundedCornerShape(if (compact) 20.dp else 24.dp)` 迁移为 `if (compact) NordicShapes.lg else NordicShapes.xl`，保留动态语义。
- **shared component `internal`** — `quality-guidelines.md:30-32`：跨文件复用的 UI 原语须 `internal`。`NordicShapes`/`NordicSpacing`/`NordicAlpha` 作为 theme token 可设为顶层 `object`（public 无害，因为 theme 包本就是 public API 出口）；但其上的便捷 composable helper（如有）须 `internal`。

### 迁移策略

1. 先建三个 token 文件 + `NordicTheme` 接入 typography/shapes。
2. 按文件逐个迁移（23 文件），每文件迁移后 `compileDebugKotlin` 快速验证。
3. 优先级顺序：SharedComponents → MediaStateComponents → 各 Home/Browse → 各 Player → 各 Screen。
4. 最后全量 `testDebugUnitTest` + `lintDebug`。

## Decision (ADR-lite)

**Context**: 近期 T1-T8 重构统一了组件，但 UI 层 100+ 处 `RoundedCornerShape`/`fontSize`/`padding` 仍是硬编码魔数，散落 23 文件；无 design token 层导致视觉一致性靠人脑维持，暗色可读性参差。
**Decision**: 引入三层 token（Shape/Spacing/Type + Alpha tier），收敛到有限档位，全 23 文件迁移；不动组件结构、不动服务集成层。
**Consequences**: 
- 一次根治视觉一致性根因；后续主题/动态取色/适配可基于 token 扩展。
- blast radius 大（每文件都动），风险靠 compile + lint + 人工巡检控制（无截图测试基线）。
- `MediaStateComponents` 状态 alpha 作为例外保留，不并入通用 alpha tier。
- 不引入可访问性/暗色显式校验（留后续任务）。

## Technical Notes

- UI 源码：`app/src/main/java/com/nordic/mediahub/ui/`（~23 文件）
- spec：`.trellis/spec/backend/`（quality-guidelines.md / directory-structure.md 为本次重点参考）
- 近期会话记录：journal-2.md Session 109-111（T7 拆分、滚动卡顿优化）

### Auto-Context 扫描发现（视觉一致性，已实测）

近期重构统一了**组件**，但**设计 token** 从未集中化，三类残留最突出：

1. **Shape token 缺失** — `RoundedCornerShape` 硬编码散落 100+ 处，取值集合 `{8,10,12,13,14,16,18,20,24,28,30,999}dp`；`CircleShape` 与 `RoundedCornerShape(999.dp)` 混用；从未使用 `MaterialTheme.shapes`。例：卡片角有 16/18/20/24 四种，胶囊有 999 与 CircleShape 两种写法。
2. **Spacing token 缺失** — padding/spacer 硬编码值集合 `{2,3,4,5,6,7,8,9,10,12,13,14,15,16,18,20,22,24,28,34}dp` 散落 58+ 处；无间距常量体系。例：MetaChip padding 有 `h10v5`、`h9v5`、`h13v5`、`h10v9` 四种。
3. **Typography token 缺失** — `fontSize` 硬编码 sp 集合 `{11,12,13,14,15,16,17,18,20,22,24,32,38,54}`；`fontWeight` 硬编码；基本不用 `MaterialTheme.typography`；次要文字用 `colorScheme.onSurface.copy(alpha = 0.x)` 魔数 alpha `{0.4,0.5,0.56,0.62,0.64,0.66,0.68,0.7,0.72,0.76,0.78}` 11 种取值。

**结论**：根因是缺少 design token 层。近期重构只统一了组件，未统一 token。这是"视觉/UX 一致性"赛道里杠杆最高的一刀。
