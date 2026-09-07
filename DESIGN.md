---
name: Nordic Media Hub
description: Unified self-hosted media client for music, audiobooks, and video
colors:
  dusk-violet: "#B098FF"
  dusk-violet-deep: "#7B5FD3"
  fjord-cyan: "#7DD3FC"
  fjord-cyan-vivid: "#0EA5E9"
  polar-shadow: "#0A0A0F"
  frost-surface: "#1A1A24"
  twilight-slab: "#252530"
  snow-mist: "#F5F4F8"
  cloud-surface: "#FFFEFF"
  drift-variant: "#EBEAF0"
  ash-text: "#E8E8EE"
  slate-muted: "#9898A8"
  ink-text: "#1A1A20"
  iron-muted: "#666676"
  coral-accent: "#FB7185"
  coral-accent-deep: "#E11D48"
typography:
  display:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "32sp"
    fontWeight: 700
    lineHeight: "40sp"
    letterSpacing: "0sp"
  headline:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "22sp"
    fontWeight: 700
    lineHeight: "28sp"
    letterSpacing: "0sp"
  title:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "22sp"
    letterSpacing: "0sp"
  body:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
    letterSpacing: "0sp"
  label:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "13sp"
    fontWeight: 600
    lineHeight: "18sp"
    letterSpacing: "0sp"
  caption:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "18sp"
    letterSpacing: "0sp"
rounded:
  xs: "8dp"
  sm: "12dp"
  md: "14dp"
  lg: "16dp"
  xl: "18dp"
  "2xl": "20dp"
  "3xl": "24dp"
  "4xl": "28dp"
  pill: "999dp"
spacing:
  xs: "2dp"
  sm: "4dp"
  md: "8dp"
  lg: "12dp"
  xl: "16dp"
  "2xl": "18dp"
  "3xl": "20dp"
  "4xl": "28dp"
components:
  card-list-row:
    backgroundColor: "{colors.frost-surface}"
    rounded: "{rounded.lg}"
    padding: "10dp 12dp"
  card-config:
    backgroundColor: "{colors.twilight-slab}"
    rounded: "{rounded.sm}"
    padding: "16dp"
  chip-meta:
    backgroundColor: "{colors.frost-surface}"
    textColor: "{colors.ash-text}"
    rounded: "{rounded.pill}"
    padding: "5dp 9dp"
  tab-segmented:
    backgroundColor: "{colors.twilight-slab}"
    rounded: "16dp"
    padding: "4dp"
    minHeight: "56dp"
  dock-playback:
    backgroundColor: "{colors.frost-surface}"
    textColor: "{colors.ash-text}"
    rounded: "{rounded.4xl}"
    padding: "12dp 12dp 10dp"
    height: "124dp"
  button-play:
    backgroundColor: "{colors.dusk-violet}"
    textColor: "{colors.polar-shadow}"
    rounded: "{rounded.pill}"
    padding: "0dp"
    size: "38dp"
  input-search:
    backgroundColor: "transparent"
    textColor: "{colors.ash-text}"
    rounded: "{rounded.lg}"
    padding: "0dp 16dp"
---

# Design System: Nordic Media Hub

## 1. Overview

**Creative North Star: "The Listening Room"**

Nordic Media Hub is a room where the media is the exhibit and the interface is the gallery wall. Every surface recedes. Every gradient is light playing through frosted glass. The eye lands on album artwork, video thumbnails, and audiobook covers first; the chrome around them is there to hold, not to compete. The aesthetic comes from a high-latitude winter interior: pale walls, the deep violet of afternoon sky, the cyan of fjord light reflected off snow.

The system speaks through translucency, not weight. Cards are never solid blocks; they are veiled layers of the surface beneath them, alpha-modulated to create depth without shadows. Shadows are reserved for elements that must feel physically separate (the playback dock, the full player) or that lift in response to touch. Everything else breathes through tonal layering.

This is explicitly not a dark-mode tech dashboard, nor a flat Material default, nor an over-decorated card garden. The palette is committed (violet carries 30-60% of accent surfaces) but never loud. Motion is responsive and efficient, never choreographed. Typography is a single sans family at home on Android, scaled with restrained contrast so labels and body never feel like they belong to a different app.

**Key Characteristics:**
- Alpha-as-depth: surfaces are defined by `surfaceVariant.copy(alpha=...)`, not by discrete color tokens
- Flat-at-rest: shadows appear only on interaction or persistent elevated surfaces (dock, player)
- Press-scale feedback: every interactive element responds with a subtle 0.985x scale, no bounce
- Gradient-as-identity: primary-to-secondary linear gradients replace solid fills in artwork placeholders and hero surfaces
- Pill geometry: all buttons, chips, nav items, and meta chips use 999dp radius

## 2. Colors: The Nordic Light Palette

The palette evokes winter light at high latitude: deep twilight, pale snow, and the cyan of fjord water. Violet is the dominant accent; cyan is the secondary, used sparingly. Coral accents are defined but reserved for future error/destructive states.

### Dark Theme

- **Dusk Violet** (#B098FF / oklch(72% 0.14 290)): primary accent. Used for active states, selected navigation, filled buttons, and the warm band in gradient overlays. Carries 30-60% of accent surfaces but never as background.
- **Fjord Cyan** (#7DD3FC / oklch(82% 0.08 220)): secondary accent. Used in gradient overlays alongside violet, for inactive slider tracks, and as a cool counterpoint in hero artwork gradients. Appears at lower alpha than violet.
- **Polar Shadow** (#0A0A0F / oklch(8% 0.01 290)): the deep background. Near-black with a violet tint, not neutral gray. Every screen root.
- **Frost Surface** (#1A1A24 at 87% opacity / oklch(14% 0.01 290)): the primary surface tint. Used for dock background, card surfaces at high alpha, and the base tone for all surface-variant overlays.
- **Twilight Slab** (#252530 / oklch(18% 0.01 290)): surface variant for solid containers (config cards, segmented tab backgrounds, list row borders). Always at full opacity when used directly.
- **Ash Text** (#E8E8EE / oklch(92% 0.005 290)): primary text. Tinted warm, not pure white.
- **Slate Muted** (#9898A8 / oklch(66% 0.01 290)): secondary text. The color of metadata, timestamps, inactive lyrics, and de-emphasized labels.
- **Coral Accent** (#FB7185 / oklch(69% 0.18 15)): defined but currently unused. Reserved for destructive actions and error states.

### Light Theme

- **Dusk Violet Deep** (#7B5FD3 / oklch(48% 0.14 290)): the light-theme primary. Darker and more saturated than its dark counterpart to maintain contrast on pale surfaces.
- **Fjord Cyan Vivid** (#0EA5E9 / oklch(67% 0.14 230)): the light-theme secondary. A saturated sky blue.
- **Snow Mist** (#F5F4F8 / oklch(97% 0.003 290)): background. Warm off-white with a violet whisper.
- **Cloud Surface** (#FFFEFF at 87% opacity / oklch(99.5% 0.001 290)): surface. Nearly white, carrying the same 87% opacity pattern as the dark surface.
- **Drift Variant** (#EBEAF0 / oklch(94% 0.005 290)): surface variant for solid container fills in light mode.
- **Ink Text** (#1A1A20 / oklch(11% 0.01 290)): primary text. Not pure black, tinted toward violet.
- **Iron Muted** (#666676): secondary text; verified against the light surface-variant background.
- **Coral Accent Deep** (#E11D48 / oklch(51% 0.24 15)): light-mode destructive, currently unused.

### Named Rules

**The Translucency Rule.** Every non-primary surface is derived from `surfaceVariant.copy(alpha=X)` or `surface.copy(alpha=X)`, not from discrete hex tokens. The alpha value encodes the surface's role: 0.42 for list rows (subtle), 0.56 for standard containers (present), 0.72 for loading/emphasis (prominent), 1.0 for structural containers (solid). This is the depth system; do not bypass it with arbitrary colors.

**The Accent Scarcity Rule.** Dusk Violet and Fjord Cyan appear on interactive states and gradient overlays only. They are never used as card backgrounds, screen backgrounds, or large-area fills. The exception is the fill of the primary play button, which earns its saturation by being the single action point on any given screen.

## 3. Typography

统一使用 Android 平台默认无衬线（Roboto / Noto Sans CJK fallback），不引入字体文件。`ui/theme/Type.kt` 是完整的运行时定义，Material 原生输入框、菜单与弹窗也使用这套层级。

| Material 槽 | 字号 / 行高 | 字重 |
|---|---|---|
| displayLarge / displayMedium / displaySmall | 40/48、36/44、32/40sp | Bold |
| headlineLarge / headlineMedium / headlineSmall | 28/36、22/28、20/28sp | Bold / Bold / SemiBold |
| titleLarge / titleMedium / titleSmall | 20/28、16/22、14/20sp | SemiBold |
| bodyLarge / bodyMedium / bodySmall | 16/24、14/20、12/18sp | Normal / Normal / Medium |
| labelLarge / labelMedium / labelSmall | 13/18、12/16、11/16sp | SemiBold / Medium / Medium |

所有槽字距为 0sp，避免中文与英文混排时意外落回 Material 默认 tracking。根页面标题用 displaySmall，返回型子页标题用 headlineMedium；普通正文与元信息分别用 bodyMedium / bodySmall。搜索输入与 placeholder 同为 bodyLarge。

较长标题与详情动作需要在受限宽度/大字体下换行或使用明确省略策略，不以强行缩小字体解决空间不足。

## 4. Elevation

This system is flat at rest. Depth is conveyed through alpha-modulated surface tinting, not through shadow. Shadows are reserved for elements that are physically separate from the content plane: the playback dock (persistent, floating above the bottom nav) and the full player artwork (the visual hero).

### Tonal Layering

The primary depth mechanism. Eight surface-variant alpha bands encode depth without shadow:

| Alpha | Role | Usage |
|---|---|---|
| 0.42 | Recessed | List rows (song, artist, album) |
| 0.50 | Standard | Player console, detail empty state |
| 0.56 | Container | Header action groups, segmented tabs, back/search buttons |
| 0.62 | Emphasized | Hero banner, meta chip surfaces, player top controls |
| 0.72 | Prominent | Loading placeholder, empty state (no config) |
| 0.76 | Near-solid | Loading surface |
| 0.94 | Dock | Playback dock, selected tab fill |
| 1.00 | Solid | Config cards, simple cards, audiobook/video cards |

### Shadow Vocabulary

- **Dock Shadow** (`shadowElevation: 12dp, tonalElevation: 6dp`): the playback dock. Permanent, not interactive. The only persistent shadow on any screen.
- **Hero Shadow** (`shadowElevation: 10dp`): full-player artwork surface. Signals "this element is above the plane."
- **Panel Shadow** (`shadowElevation: 6dp`): lyrics display surface in the full player.
- **Action Shadow** (`shadowElevation: 4dp`): filled play/pause control button in the player.
- **Touch Shadow** (`shadowElevation: 2dp`): now-playing play button in the dock. A whisper of lift on the primary action point.

### Named Rules

**The Flat-at-Rest Rule.** Cards, list rows, chips, and containers cast no shadow in their default state. Shadows appear only as a response to persistent elevation (dock, player) or interaction focus. If a card has a shadow at rest, it is wrong.

## 5. Components

### Buttons

- **导航/页头/搜索图标**：统一 48dp 实际点击区，md(16dp) 圆角或所在操作组的透明背景；图标 20–24dp，采用同一按压反馈。返回不单独增加阴影。
- **主要详情动作**：primary / onPrimary 成对颜色，最小 52dp 高；允许两行长文字并自然增高，不强制所有主题白字。
- **次要详情动作**：primaryContainer / onPrimaryContainer，最小 48dp 高，保留清晰禁用状态。
- **媒体播放工具**：媒体专用视觉尺寸可不同，但实际点击区域和内容前景必须遵循可访问性与对应播放器合同；Dock/播放器逐页细化仍需单独验证。
- **反馈**：默认 press scale 0.985，图标 0.94，150ms 标准 easing；可操作图标具有中文语义，不用空的伪按钮装饰。

### Chips

- `MetaChip` 等静态元信息仍是小尺寸信息载体，不应误标成可点击操作。
- `MediaChoiceChip` 是互斥选择：md 圆角、至少 48dp 高、selected/disabled/Role.Tab 语义明确，选中底色与正文采用成对主题角色。
- 大字体允许控件自然增高；长库名最多 240dp 宽，保留完整无障碍文本。

### Cards / Containers
Not the lazy default; here they carry specific tonal roles.
- **List Row** (song, artist, album): surfaceVariant.copy(alpha=0.42f) background, RoundedCornerShape(16dp), BorderStroke(1dp, onSurface.copy(alpha=0.045f)). Internal padding horizontal 10dp, vertical 9dp. 52dp artwork at 12dp corners (default) or 14dp (album list row). Press scale 0.992x.
- **Hero Banner:** surfaceVariant.copy(alpha=0.62f), RoundedCornerShape(24dp), inner padding 18dp, 124dp artwork at 20dp corners. Gradient overlay: primary(0.18f) to secondary(0.1f) to surfaceVariant(0.82f).
- **Config Card:** surfaceVariant (alpha 1.0), RoundedCornerShape(12dp), column padding 16dp, verticalArrangement spacedBy(12dp). Focused text field border: primary. Unfocused: onSurface.copy(alpha=0.2f).
- **Video Card:** surfaceVariant (alpha 1.0), RoundedCornerShape(16dp), 180dp thumbnail. Content padding 14dp.
- **Audiobook Card:** surfaceVariant (alpha 1.0), RoundedCornerShape(12dp), 64dp artwork at 8dp corners. Row padding 14dp.

### Inputs / Fields
- **Search field:** 统一 `MediaSearchField`，md 圆角、primary/outline 焦点边框、surfaceVariant 0.42 底色、onSurfaceVariant 提示文字；包括搜索图标、清除入口和 Search IME。业务查询仍由调用方管理。

### Navigation
- **Playback Dock:** persistent bottom. Surface at 0.94f alpha, RoundedCornerShape(28dp), shadowElevation 12dp, tonalElevation 6dp, BorderStroke(1dp, onSurface.copy(alpha=0.08f)). Now-playing bar (66dp height) sits above divider (1dp, onSurface.copy(alpha=0.07f)), then bottom nav row (58dp height).
- **Nav items:** pill shape (18dp on unselected/selected background), selected bg primary.copy(alpha=0.13f), icon 19dp size, label 13sp SemiBold (selected) / 13sp Medium (unselected). Selected text/icon: primary. Unselected: onSurface.copy(alpha=0.58f). Press scale 0.97x at 150ms.
- **Segmented Tabs:** 复用 `MediaSegmentedControl`：outer md(16dp)/surfaceVariant 0.56，inner sm(12dp)/selected surface 0.96；4dp 内边距、48dp 最小真实操作高度，整体至少 56dp。测量真实标签后决定等分/横向滚动，150ms 微动效。

### Now Playing Bar
The signature component. Floating above the bottom nav, it is the only persistent connection between the content plane and the media plane.
- **Layout:** 66dp height. Artwork 46dp at 13dp corners. Play button: primary fill, pill, 38dp, shadowElevation 2dp. Song title 15sp SemiBold, subtitle 12sp Regular at onSurface.copy(alpha=0.56f).
- **Artwork gradient:** primary.copy(alpha=0.28f) to secondary.copy(alpha=0.18f) as a linear gradient overlay when no cover art is available.

### Bottom Sheet (Queue)
- **Shape:** RoundedCornerShape(topStart=24dp, topEnd=24dp). Container color: surface. Content bottom padding 28dp.
- **Queue Row:** Surface, RoundedCornerShape(12dp), horizontal padding 12dp. Current item: primary.copy(alpha=0.1f) background. 42dp artwork at 10dp corners. Gradient on current: primary(0.28f) to secondary(0.2f).

## 6. Do's and Don'ts

### Do:
- **Do** derive all non-primary surface fills from `surfaceVariant.copy(alpha=X)` using the eight alpha bands (0.42, 0.50, 0.56, 0.62, 0.72, 0.76, 0.94, 1.00). These are the depth system.
- **Do** use Dusk Violet exclusively for interactive accents (active state, selected nav, filled play button, gradient warm band). Its rarity is the point.
- **Do** apply press-scale feedback (0.985x at 150ms with FastOutSlowInEasing) to every interactive surface. Trust the system; don't invent new motion curves.
- **Do** use pill geometry (999dp) for buttons, chips, nav items, and meta chips. It is the shape vocabulary's signature.
- **Do** let album artwork, video thumbnails, and audiobook covers carry the visual weight. The interface recedes.
- **Do** use the gradient pattern (primary at alpha to secondary at lower alpha to surfaceVariant at high alpha) for artwork placeholders and hero surfaces. It is the identity system, not decoration.
- **Do** enter with fadeIn(300ms) and exit with fadeOut(200ms). FastOutSlowInEasing always. This is the only transition rhythm.
- **Do** keep long-list interactions immediate; do not add per-item delays merely for decoration.

### Don't:
- **Don't** use over-decorated card designs. Cards are translucent vessels for content, not visual events themselves. Nested cards are always wrong.
- **Don't** use deep-tech dark mode aesthetics. This is not a developer tool or a cyberpunk dashboard. The dark background is Polar Shadow (#0A0A0F), tinted toward violet warmth, not neutral gray or neon.
- **Don't** use flat Material Design default styling. Surface alpha layers, gradient overlays, and pill geometry are deliberate departures from the default.
- **Don't** add shadows to cards or list rows at rest. Shadows are reserved for the dock and player. If a card has a shadow, it is wrong.
- **Don't** use bounce, elastic, or spring animation curves. FastOutSlowInEasing is the only easing. Violations are immediately visible.
- **Don't** use gradient text (background-clip: text with gradient). Typography earns emphasis through weight and size, not decoration.
- **Don't** use glassmorphism as a default. Frosted-glass effects are reserved for specific surfaces (the dock, player overlay), not applied everywhere.
- **Don't** use side-stripe borders (border-left/right > 1px as colored accent on cards, list items, or alerts). Rewrite with full borders, background tints, or nothing.

## 7. 当前共享组件验收

共享主题的文字前景以实际合成背景进行 4.5:1 对比度测试；深色浅紫按钮和浅色亮青按钮使用深色前景。浅色次要文字调整为 #666676，保证在 surfaceVariant 上可读。

页头根据可用宽度和字体大小把多余操作收纳到菜单，不丢失功能。共享控件的自动化合同见 `.trellis/spec/backend/ui-consistency.md`；这些合同不代表整应用已完成逐页视觉验收，任务覆盖矩阵和真机反馈仍是必要证据。

跨域共享组件现状（第三至六批落实）：音乐、有声书、视频的播放器弹层（倍速、章节、定时、书签、均衡器、播放队列）统一使用同一容器与选择行（选中 primaryContainer 背景块 + 勾选，未选中浅灰容器）；有声书详情概览复用音乐集合自适应布局，视频详情简介、分集筛选与海报推荐卡复用同一展开、选中与字体增长策略；配置页表单、类型选择与操作按钮对齐同一输入/主次动作语言。加载/空/错误状态卡全应用共用，标题支持无障碍标题导航。以上为源码与自动化层面的统一；逐页真机验收按覆盖矩阵继续。

## 8. 音乐集合与列表

专辑、歌手、歌单详情使用同一自适应概览：普通/宽屏封面 128/160dp，文字区不足时纵向排列，metadata 自动换行。首页集合概览不伪装播放动作，点击仍进入专辑。

音乐列表统一 52dp artwork、16dp 外圆角、12/8dp 横纵内边距，长名称可两行；歌手头像保留圆形。尾部时长按实际空间移动，横向卡片在大字体下从 124dp 增大到最多 160dp。缺失图片通过共享 CoverArt 兜底。

导航区域显示类别，集合真实名称在概览中呈现；仅明确空数据展示空态，错误与加载使用各自反馈。具体行为和验证边界见 `.trellis/spec/backend/music-ui.md`；真机验收仍需逐页完成。
