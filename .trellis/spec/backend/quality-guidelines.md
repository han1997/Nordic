# Quality Guidelines

> Code quality standards for the Nordic media hub app.

---

## Overview

This document records project-specific code quality conventions discovered during development.

---

## Forbidden Patterns

### String-based error classification

**Don't**: Use `e.message?.contains("keyword")` to distinguish error types.
**Why**: Any message rewording or i18n change silently breaks the classification.
**Do**: Use typed exceptions (e.g., `NavidromeApiException`) with enum kinds.

### Duplicate utility functions

**Don't**: Create `formatPlayerDuration()` in one file and `formatTrackDuration()` in another when they do the same thing.
**Do**: Single source of truth in a shared file (`MusicFormatters.formatDuration()`).

---

## Required Patterns

### Shared Compose components must be `internal`

Small UI primitives reused across files (e.g., `MusicMetaChip`, `rememberPressScale`) must be `internal` (not `private`) so they can be imported without exposing them as public API. Private helpers that are duplicated across files should be lifted to a shared file with `internal` visibility.

### Design token system (Shape / Spacing / Typography / Alpha / Motion)

UI files under `ui/` must use the design tokens defined in `ui/theme/Shapes.kt`, `ui/theme/Spacing.kt`, `ui/theme/Type.kt`, `ui/theme/Alpha.kt`, and `ui/theme/Motion.kt` — not hardcoded `RoundedCornerShape(<num>.dp)`, `CircleShape`, `Modifier.padding(<num>.dp)`, `fontSize = <num>.sp`, `colorScheme.onSurface.copy(alpha = <magic>)`, or inline `tween(<num>, easing = FastOutSlowInEasing)` literals. `NordicTheme` wires `NordicTypography` and `NordicShapesMaterial` into `MaterialTheme`, so composables can resolve via `MaterialTheme.typography.<slot>` / `MaterialTheme.shapes.<slot>` or reference the `object`s directly.

**Token scales** (finite, named):

```kotlin
object NordicShapes  { val none, sm(12), md(16), lg(20), xl(24), full(50%) }
object NordicSpacing { val xs(4), sm(8), md(12), lg(16), xl(20), xxl(24), xxxl(32), content(16) }
object NordicAlpha   { val medium(0.68f), subtle(0.5f), faint(0.3f) }
object NordicMotion  { val durationMicro=150, durationShort=200, durationMedium=300, durationLong=450, easingStandard=FastOutSlowInEasing, easingDecelerate=LinearOutSlowInEasing, easingAccelerate=FastOutLinearInEasing, enterSlideUp, exitSlideDown, enterFade, exitFade, crossfadeSpec, slideDirectionSpec(forward) }
val NordicTypography = Typography(
    displaySmall(32/Bold), headlineMedium(22/Bold), titleMedium(16/SemiBold),
    titleSmall(14/SemiBold), bodyMedium(14/Normal), labelLarge(13/SemiBold), bodySmall(12/Medium)
)
```

**Rules**:
- `NordicTypography` 覆盖全部 15 个 Material 槽并声明行高/字距；上面的常用槽摘要不是完整定义。完整共享角色与响应式控件合同见 [ui-consistency.md](./ui-consistency.md)。
- New UI code uses tokens; existing magic numbers converge to the nearest tier (see the convergence maps in the comments atop each token file).
- `NordicShapes.full` (`RoundedCornerShape(50)`, percent-based) replaces both `RoundedCornerShape(999.dp)` and `CircleShape`.
- Dynamic expressions stay dynamic: `if (compact) NordicShapes.lg else NordicShapes.xl` — do not collapse a dynamic branch to a single token just because both sides now resolve via tokens.
- When a `Text` needs a slot style plus a different weight, pass `style = MaterialTheme.typography.<slot>` and keep the explicit `fontWeight = ...` override (the explicit param wins).
- `lineHeight = <num>.sp` literals are out of the `fontSize` scope and may remain explicit when a slot does not encode the desired line spacing.

**State-semantic alpha exception**: `MediaStateComponents` encodes the design-system state alphas (`EMPTY_STATE_CONTAINER_ALPHA = 0.72f` empty, `LOADING_STATE_CONTAINER_ALPHA = 0.76f` loading, `ERROR_STATE_SUBTITLE_ALPHA = 0.82f` error subtitle). These are state semantics, not generic text tiers — they stay as named local constants inside `MediaStateComponents` and must NOT be folded into `NordicAlpha`. `NordicAlpha` is only for generic secondary-text tiers on `onSurface`.

**Out of token scope** (left explicit, not migrated):
- `Color.White.copy(alpha=…)` in `VideoPlayerScreen` — video-overlay white-on-black context, separate from the `onSurface` secondary-text scope.
- Component dimensions (cover-art `size`, control heights, grid `minSize`) — these are layout sizing, not spacing tokens.
- `spacedBy(2.dp)` below `NordicSpacing.xs` (4.dp) — no tier to converge to; forcing `xs` would double the gap.
- Pre-existing micro-interaction `tween(...)` / `FastOutSlowInEasing` literals inside `AnimatedComponents.kt`, `ConfigCards.kt`, `MusicBrowseComponents.kt`, `PlaybackDock.kt`, `SharedComponents.kt`, `VideoPlayerScreen.kt` (press scale, chip selection, chrome fade) — **migrated** to `NordicMotion.durationMicro` / `durationShort` / `durationMedium` + `easingStandard`. The only remaining explicit `tween(...)` calls in these files are (a) the constant-driven `VIDEO_PLAYER_CHROME_FADE_MS` (which itself references `NordicMotion.durationShort`) and (b) `ConfigCards.kt` / `SharedComponents.kt` no-easing `tween(...)` calls where `LinearEasing` was the original behavior and is intentionally preserved (see "Micro-interaction convergence" below).

### Screen-transition animation contract

**Scope / Trigger**: Any change to `MainActivity` Tab switch wiring, Music / Audiobook / Video player overlay enter/exit, `MusicScreenV2.libraryPage` branch rendering, or a new screen-transition animation site in `ui/`.

**Signatures**:
- `object NordicMotion` (`ui/theme/Motion.kt`) — peer to `NordicShapes`/`NordicSpacing`/`NordicAlpha`/`NordicTypography`.
- `val durationShort = 200`, `durationMedium = 300`, `durationLong = 450` (Int millis)
- `val easingStandard: CubicBezierEasing = FastOutSlowInEasing` (enter+exit symmetric)
- `val easingDecelerate: CubicBezierEasing = LinearOutSlowInEasing` (enter)
- `val easingAccelerate: CubicBezierEasing = FastOutLinearInEasing` (exit)
- Reusable transitions: `enterSlideUp`, `exitSlideDown`, `enterFade`, `exitFade`, `crossfadeSpec: ContentTransform`, `slideDirectionSpec(forward: Boolean): ContentTransform`.
- Pure helper: `internal fun resolveMusicLibraryPageForward(from: MusicLibraryPage, to: MusicLibraryPage): Boolean` (`MusicScreenLogic.kt`).

**Contracts**:
- Screen-transition durations / easings MUST reference `NordicMotion.durationShort/Medium/Long` and `NordicMotion.easingStandard/Decelerate/Accelerate`. Do NOT inline `tween(<num>, easing = FastOutSlowInEasing)` in screen-transition code.
- Tab-level switch (`MainActivity` 0/1/2/3, incl. `ServerConfigScreen`) MUST use `Crossfade(targetState = selectedTab, animationSpec = tween(NordicMotion.durationMedium, easing = NordicMotion.easingStandard), label = "...")`. The `Crossfade` lambda MUST take a `tab` parameter and branch on it; do NOT read outer `selectedTab` inside the lambda. Each tab branch MUST be wrapped in `rememberSaveableStateHolder().SaveableStateProvider(key = tab)` so list scroll positions and `rememberSaveable` state survive tab switches instead of tearing down the whole screen.
- Full-screen player overlay enter/exit (Music / Audiobook / Video) MUST use `AnimatedVisibility(enter = NordicMotion.enterSlideUp, exit = NordicMotion.exitSlideDown)` (slide from bottom + fade). Do not use `fadeIn()/fadeOut()` defaults for full-screen player overlays — the slide gives the user a directional cue that the overlay came from the playback affordance.
- `MusicScreenV2.libraryPage` rendering MUST be wrapped in `AnimatedContent(targetState = libraryPage, transitionSpec = { NordicMotion.slideDirectionSpec(resolveMusicLibraryPageForward(initialState, targetState)) }, label = "...")`. Direction (forward = slide-left, back = slide-right) is resolved by nav-stack depth via `resolveMusicLibraryPageForward`. Each `libraryPage` branch renders its own inner `LazyColumn` with stable `key`/`contentType` per the "Compose media list stability" rule.
- `ModalBottomSheet` (e.g. `MusicQueueSheet`) MAY keep Material3 default animation. Do NOT override the sheet's drag-to-dismiss animation spec with a custom `tween` — forcing a duration regresses drag-to-dismiss feel. Only calibrate a sheet spec if there was an explicit hardcoded duration to begin with.
- Animation `transitionSpec` / `Crossfade` `animationSpec` / `AnimatedContent` lambda MUST NOT read outer Compose state (spec: Compose performance state isolation). Only `initialState` / `targetState` (provided by the animation container) + `NordicMotion` static tokens + pure helpers may be read inside.
- `BackHandler`s MUST stay at the composable scope (outside `AnimatedContent` / `Crossfade` lambdas). Moving a `BackHandler` inside an animation lambda changes its registration priority (last-registered-wins) and can break sub-navigation back semantics.
- No `delay(...)` timer-based chrome reveal may be introduced (spec: Performance-first persistent media chrome / no-timer-reveal). The only allowed `delay(...)` in `MusicScreenV2` is the pre-existing search debounce `Job` (held in an `AtomicReference`, per performance-state-isolation).

**Validation & Error Matrix**:
| Condition | Behavior |
|---|---|
| New screen-transition animation site | Duration/easing reference `NordicMotion.*`; no inline `tween(<num>)` literal |
| Tab switch (incl. to/from `ServerConfigScreen`) | `Crossfade` with `durationMedium`/`easingStandard`; lambda takes `tab` param, does not read outer `selectedTab` |
| Full-screen player overlay | `enterSlideUp`/`exitSlideDown`; no plain `fadeIn()`/`fadeOut()` |
| `libraryPage` direction | `resolveMusicLibraryPageForward(initialState, targetState)` decides forward/back; forward = slide-left, back = slide-right |
| `AnimatedContent` wrapping a `LazyColumn` | Inner `LazyColumn` retains stable `key` + `contentType` per item family |
| `BackHandler` priority | Handler stays at composable scope, outside animation lambda |
| Sheet (ModalBottomSheet) | Default Material3 animation; no custom spec override unless a hardcoded duration pre-existed |

**Good/Base/Bad Cases**:
- Good: Tab Music→Video crossfades 300ms; opening Music player slides up from bottom; Home→AlbumDetail slides left; `BackHandler` priority unchanged.
- Base: Single-tab render (no transition) — `Crossfade`/`AnimatedContent` no-op, no animation cost.
- Bad: Inline `tween(300, easing = FastOutSlowInEasing)` in a new screen-transition site — magic number, not a token.
- Bad: `Crossfade { when (selectedTab) { ... } }` reads outer `selectedTab` instead of the lambda's `tab` param — causes broad recomposition + wrong transition target.
- Bad: `AnimatedVisibility(enter = fadeIn(), exit = fadeOut())` on a full-screen player overlay — no directional cue.
- Bad: `BackHandler` moved inside `AnimatedContent` lambda — registration priority changes, back semantics regress.

**Tests Required**:
- `resolveMusicLibraryPageForward(from, to)` unit tests covering: Home→AlbumDetail (true), AlbumDetail→Home (false), Playlists→PlaylistDetail (true), PlaylistDetail→Playlists (false), same-page (false), and every enum transition the navigation graph allows.
- Compile + `testDebugUnitTest` + `lintDebug` sequential gates after any screen-transition-animation change.

**Wrong vs Correct**:
```kotlin
// Wrong: inline tween literal; reads outer state inside lambda.
Crossfade(targetState = selectedTab, animationSpec = tween(300, easing = FastOutSlowInEasing)) {
    when (selectedTab) { ... }
}
```

```kotlin
// Correct: NordicMotion token; lambda takes tab param; no outer state read.
Crossfade(
    targetState = selectedTab,
    animationSpec = tween(NordicMotion.durationMedium, easing = NordicMotion.easingStandard),
    label = "main-tab-crossfade"
) { tab ->
    when (tab) { 0 -> MusicScreenV2(...); 1 -> AudiobookScreen(...); 2 -> VideoScreen(...); 3 -> ServerConfigScreen(...) }
}
```

```kotlin
// Wrong: plain fade for a full-screen player overlay; no directional cue.
AnimatedVisibility(visible = showPlayer, enter = fadeIn(), exit = fadeOut()) { MusicPlayerScreen(...) }
```

```kotlin
// Correct: slide up from bottom + fade; directional cue matches "overlay rises from the playback affordance".
AnimatedVisibility(visible = showPlayer, enter = NordicMotion.enterSlideUp, exit = NordicMotion.exitSlideDown) { MusicPlayerScreen(...) }
```

### Micro-interaction convergence

**Scope / Trigger**: Any change to a component-internal micro-interaction animation site (press-scale, chip-select, chrome-fade, dock fade, config-panel expand/shrink) in `ui/`.

**Signatures**:
- `NordicMotion.durationMicro = 150` (Int millis) — for < 200ms micro-interactions (press-scale, chip-select, chrome-fade).
- `NordicMotion.durationShort = 200` — for slightly heavier transitions (expand/shrink, short fade).
- `NordicMotion.easingStandard: Easing = FastOutSlowInEasing` — covers all micro-interaction easings.

**Contracts**:
- Micro-interaction durations / easings MUST reference `NordicMotion.durationMicro` / `durationShort` / `durationMedium` + `easingStandard`. Do NOT inline `tween(<num>, easing = FastOutSlowInEasing)` in micro-interaction code.
- `durationMicro` (150ms) is for press-scale / chip-select / chrome-fade (< 200ms). `durationShort` (200ms) is for slightly heavier transitions (expand/shrink). Do NOT use `durationMicro` for screen transitions; do NOT use `durationShort`/`durationMedium` for press-scale.
- **Behavior-preservation rule (CRITICAL)**: when converging a literal `tween`, if the original had NO `easing` arg (defaults to `LinearEasing`), keep it that way — only swap the duration source; do NOT add `easing = NordicMotion.easingStandard` where it didn't exist. Adding `easingStandard` where `LinearEasing` was intended changes the animation feel (linear vs eased) and is a behavior regression.
- When the original HAD `easing = FastOutSlowInEasing`, replace with `easing = NordicMotion.easingStandard`. Do NOT drop the easing arg.
- Enter/exit combinations (`+ expandVertically()`, `+ shrinkVertically()`, `togetherWith`, `+ fadeIn()`) MUST be preserved structurally — only the duration/easing source changes.
- `VideoPlayerScreen.VIDEO_PLAYER_CHROME_FADE_MS` is a named constant that references `NordicMotion.durationShort` (the 6 chrome-fade sites use the constant, not a direct token ref, so the fade duration can be tuned in one place).
- **Press-scale must be draw-phase (recomposition-free)**: new press-scale sites MUST use `Modifier.pressScale(interactionSource, ...)` (`AnimatedComponents.kt`), which reads the animated value inside a `graphicsLayer { }` lambda so the animation only re-renders the layer. Do NOT reintroduce `val scale = rememberPressScale(...)` + `Modifier.scale(scale)` — reading the animated float during composition recomposes the whole card every animation frame. `rememberPressScale` remains only for sites that need the raw value for non-scale purposes; `PlaybackDock.PolishedNavItem` keeps its own inline `graphicsLayer` variant (0.97f pressed scale).

**Validation & Error Matrix**:
| Condition | Behavior |
|---|---|
| New micro-interaction animation site | Duration ∈ {`durationMicro`, `durationShort`}; easing ∈ {`easingStandard`} or absent (if LinearEasing intended); no inline `tween(<num>)` literal |
| Original `tween(<num>)` had no easing | Converge to `tween(NordicMotion.duration<tier>)` — NO easing arg added |
| Original `tween(<num>, easing = FastOutSlowInEasing)` | Converge to `tween(NordicMotion.duration<tier>, easing = NordicMotion.easingStandard)` — easing preserved |
| Enter/exit combination (`+ expandVertically()`, `togetherWith`) | Structure preserved; only duration/easing source changes |
| `VIDEO_PLAYER_CHROME_FADE_MS` | References `NordicMotion.durationShort`; 6 chrome-fade sites use the constant |

**Good/Base/Bad Cases**:
- Good: `rememberPressScale(durationMillis = NordicMotion.durationMicro)`; chip-select `tween(durationMicro, easingStandard)`; `fadeOut(tween(durationShort))` (no easing, LinearEasing preserved).
- Base: Single micro-interaction site converges in one line; no structural change.
- Bad: `tween(150, easing = FastOutSlowInEasing)` left inline — magic number, not a token.
- Bad: `fadeOut(tween(NordicMotion.durationShort, easing = NordicMotion.easingStandard))` where the original was `fadeOut(tween(200))` (no easing) — adds easing where none was intended, changes LinearEasing → FastOutSlowInEasing, a feel regression.
- Bad: `tween(NordicMotion.durationShort)` where the original was `tween(150, easing = FastOutSlowInEasing)` — dropped the easing arg, changes FastOutSlowInEasing → LinearEasing, a feel regression.

**Tests Required**:
- No unit tests needed for pure token replacement (no logic change).
- Compile + `testDebugUnitTest` + `lintDebug` sequential gates after any micro-interaction convergence.
- If a constant like `VIDEO_PLAYER_CHROME_FADE_MS` is retargeted to a token, verify no test hardcoded the old literal value.

**Wrong vs Correct**:
```kotlin
// Wrong: inline literal; easing added where original had none (LinearEasing → FastOutSlowInEasing, feel regression).
fadeOut(tween(200, easing = NordicMotion.easingStandard))
```

```kotlin
// Correct: duration token; no easing arg (preserves original LinearEasing).
fadeOut(tween(NordicMotion.durationShort))
```

```kotlin
// Wrong: easing dropped where original had FastOutSlowInEasing (FastOutSlowInEasing → LinearEasing, feel regression).
tween(NordicMotion.durationMicro)
```

```kotlin
// Correct: duration + easing both tokenized; easing preserved.
tween(durationMillis = NordicMotion.durationMicro, easing = NordicMotion.easingStandard)
```

**Why**: Centralizing the token scale prevents visual drift across 20+ UI files, makes the design intent legible at the call site (`NordicShapes.md` vs `RoundedCornerShape(14.dp)`), and gives future theme variants (dynamic color, window-size buckets) a single extension point instead of a full-UI re-scan.

### Shared media state surfaces

Top-level Music, Audiobook, and Video screens should use the shared media state components instead of reimplementing one-off loading, error, and empty cards:

```kotlin
MediaStateCard(
    title = "连接失败",
    subtitle = errorMessage,
    tone = MediaStateTone.Error
)

MediaLoadingCard(
    title = "正在同步 Navidrome",
    subtitle = "加载专辑、歌曲和歌手..."
)
```

Use `MediaStateDensity.Compact` for detail-level empty states and the default prominent density for first-run/setup/empty-library states.

**Why**: These surfaces encode the design-system alpha levels (`0.72f` empty, `0.76f` loading, error container for errors). Repeating local `Surface` blocks causes visual drift and makes copy/encoding fixes harder to audit.

### Segmented control visual consistency

MusicSegmentedTabs、SongSortSegmentedControl、AlbumSortSegmentedControl 必须复用 `MediaSegmentedControl<T>`，而不是分别维护三份近似实现。

- 外层 `surfaceVariant` 0.56、md(16dp)；内层选中 `surface` 0.96、sm(12dp)，微动效使用 NordicMotion。
- 项的真实点击区域至少 48dp，4dp 内边距使容器至少 56dp；大字体自然增高。
- 真实测量标签和 padding；只有项数 ≤4 且等分空间足够时使用 Row，否则 LazyRow。禁止缩小字体/触控区来硬塞标签。
- 使用 `selectableGroup` / `Role.Tab` / selected 语义；未选中可读文字使用 onSurfaceVariant。
- 细节、边界与测试要求见 [共享 UI 一致性合同](./ui-consistency.md)。

### Shared media page shell

Top-level Music, Audiobook, and Video browsing screens should use the shared page-shell components for headers instead of reimplementing local title/action rows. Server connection editing belongs in the unified `ServerConfigScreen`, not in per-media inline config panels.

**Contracts**:
- Use `MediaPageHeader(...)` for the screen title, dynamic subtitle, optional visible back button, and header actions. It measures the available width/font scale and routes excess actions into a menu rather than shrinking 48dp targets; root/detail headings use the shared hierarchy.
- Header actions should stay in `HeaderActionGroup` / `HeaderAction` so refresh, theme, and search affordances keep the same surface, sizing, and disabled behavior across media domains.
- Icon-only controls and visual placeholders in shared UI must use Material vector icons (`ImageVector` / `Icon`), not text glyph pseudo-icons such as `"↻"`, `"⚙"`, `"▶"`, `"♪"`, `"×"`, or `"▤"`. Every actionable icon needs a meaningful `contentDescription`; purely decorative placeholders should use `contentDescription = null`.
- Do not add new per-media config gear actions, `showConfig` state, or `MediaConfigPanel` server forms to Music, Audiobook, or Video screens. Use the bottom-nav `配置` tab and `ServerConfigScreen` instead.
- First-run/setup empty states should direct users to the `配置` tab instead of referencing an off-screen header gear.

```kotlin
MediaPageHeader(
    title = "视频",
    subtitle = browserSubtitle,
    actions = buildList {
        add(
            HeaderAction(
                icon = Icons.Default.Refresh,
                contentDescription = "刷新视频库",
                enabled = !isLoading,
                onClick = { refresh() }
            )
        )
    },
    colorScheme = colorScheme,
    showBack = libraryPage != MusicLibraryPage.Home,
    onBack = ::returnToHome
)
```

**Why**: Media browse screens are parallel product surfaces and should stay focused on browsing/playback. Centralizing server configuration reduces duplicated save/test logic and prevents hidden config `BackHandler` priority bugs from detail pages.

## Scenario: Server Configuration Screen and Connection Tests

### 1. Scope / Trigger

- Trigger: Any change to `ServerConfigScreen`, bottom-nav server configuration entry, `ConfigCards`, `NavidromeRepository.testConnection`, `AudiobookShelfRepository.testConnection`, `EmbyRepository.testConnection`, or the lightweight API endpoints they call.
- Scope: centralized server configuration UI, save behavior through `ConfigRepository`, connection-test behavior, and removal of per-media inline config panels.

### 2. Signatures

- UI:
```kotlin
@Composable
fun ServerConfigScreen(colorScheme: ColorScheme, isDark: Boolean, onThemeToggle: (Boolean) -> Unit)
```
- Repository probes:
```kotlin
suspend fun NavidromeRepository.testConnection()
suspend fun AudiobookShelfRepository.testConnection(): Int
suspend fun EmbyRepository.testConnection(): Int
```
- Retrofit:
```kotlin
@GET("rest/ping.view")
suspend fun NavidromeApi.ping(...): Response<SubsonicResponse>
```

### 3. Contracts

- Server editing belongs in the bottom-nav `配置` tab through `ServerConfigScreen`. Music, Audiobook, and Video screens must not define per-media config gear actions, `showConfig` state, or inline `MediaConfigPanel` server forms.
- `ServerConfigScreen` owns temporary form state initialized from the saved config flows. Test connection uses the current form values, even when the user has not saved them yet.
- Saving must call `ConfigRepository.saveNavidromeConfig(...)`, `saveAudiobookConfig(...)`, and `saveVideoConfig(...)`. Do not write directly to `EncryptedConfigStore` or DataStore from UI.
- Test connection is a lightweight probe only. It must not save config, write media caches, start playback, sync progress, or trigger full media-library/catalog refresh.
- Navidrome test uses Subsonic `ping.view` with normal auth params. Success requires a valid Subsonic `ok` response through the existing `requireResponse` path.
- AudiobookShelf test performs login/token resolution and `getLibraries()` only; it must not request library items, item detail, playback sessions, progress endpoints, or cache writes.
- Emby test authenticates and requests user views/libraries only; it must not request `Users/{userId}/Items`, item catalog pages, playback progress, or cache writes.
- Connection test results are UI state only: show pending, success, and contextual failure messages per card. A failed test must not mutate saved config.
- Existing media screens continue to observe saved config flows and refresh from their own `LaunchedEffect(savedConfig)` paths after a save.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| User edits form fields but does not save, then taps test | Probe uses the edited in-memory form values |
| Test succeeds | Show success feedback on that service card; do not save config automatically |
| Test fails | Show contextual error on that service card; keep current form state unchanged |
| User taps save | Persist only through `ConfigRepository`; saved-config flows notify media screens |
| Music/Audiobook/Video screen rendered | No server config gear, `showConfig`, inline config form, or config BackHandler exists |
| Navidrome test | Calls `ping.view`; does not fetch albums/songs/artists/playlists |
| AudiobookShelf test | Calls login + libraries only; does not fetch items/detail or start playback |
| Emby test | Calls auth + views only; does not fetch item catalog or report progress |

### 5. Good/Base/Bad Cases

- Good: User enters unsaved Navidrome credentials, taps `测试连接`, gets success, then taps `保存配置`; Music refreshes from the saved flow afterward.
- Good: User tests Emby with an API key and the test counts available libraries without pulling any item pages.
- Base: Empty config fields fail readiness/test gracefully and leave saved config unchanged.
- Bad: Test connection reuses `refreshMusicData` / `refreshAudiobooks` / `refreshVideo`, causing a full sync and cache writes just to validate credentials.
- Bad: Media pages keep old hidden `showConfig` BackHandlers after the config UI moved to a dedicated tab.
- Bad: Test connection saves form values implicitly, surprising users who expected a dry run.

### 6. Tests Required

- Repository tests: Navidrome test calls `/rest/ping.view`; ABS test calls login + `/api/libraries` only; Emby test calls auth + views only and does not request `/Items`.
- UI/helper tests where feasible: bottom nav preserves Music/Audiobook/Video indexes (`0/1/2`) and maps `3` to `配置`.
- Compile/lint gates after removing per-media config actions to catch stale state, imports, and BackHandlers.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Testing a server by running the full domain refresh performs unnecessary sync work.
Button(onClick = { scope.launch { refreshVideo() } }) {
    Text("测试连接")
}
```

```kotlin
// Media pages keep stale config UI after the dedicated config tab exists.
add(HeaderAction(icon = Icons.Default.Settings, contentDescription = "配置") { showConfig = !showConfig })
MediaConfigPanel(visible = showConfig) { VideoConfigCard(...) }
```

```kotlin
// Text glyph pseudo-icons are not stable across fonts, encodings, or accessibility tools.
Text("▶")
Text("♪")
```

#### Correct
```kotlin
// Test uses current form values, is lightweight, and does not persist.
Button(onClick = {
    scope.launch { EmbyRepository(videoForm).testConnection() }
}) {
    Text("测试连接")
}
```

```kotlin
// Media pages stay focused on browsing/playback; config lives in selectedTab == 3.
when (selectedTab) {
    0 -> MusicScreenV2(...)
    1 -> AudiobookScreen(...)
    2 -> VideoScreen(...)
    3 -> ServerConfigScreen(...)
}
```

```kotlin
// Icon affordances use vector assets with accessibility semantics.
HeaderAction(
    icon = Icons.Default.Refresh,
    contentDescription = "刷新音乐库",
    enabled = !isLoading,
    onClick = refreshMusic
)
Icon(
    imageVector = Icons.Default.MusicNote,
    contentDescription = null
)
```

### Compose media list stability

Image-heavy `LazyColumn` and `LazyRow` sections should provide stable `key` values from domain identity and a stable `contentType` for each row/card family. Small fixed control rows such as sort chips do not need this.

Cache preview slices with `remember(sourceList) { sourceList.take(n) }` when they are passed into lazy lists or playback callbacks, and prefer `itemsIndexed` when item click handling needs the index.
Do not resolve playback click indexes with `list.indexOf(song)`; duplicate song entries can resolve to the wrong item and large lists pay an unnecessary O(n) lookup on click. Use the index provided by `itemsIndexed` and position-aware keys such as `"playlist-song-${song.id}-$index"`.
When a shelf is only a visual preview of a longer playback source, keep the preview slice separate from the playback queue. For example, the Music home recently-added shelf may render 12 cards but should pass the full `recentlyAddedSongs` backing list to playback so listening continues beyond the visible preview.

Playback scrubbers should keep local scrub state while dragging and call the playback engine's `seekTo(...)` only from `onValueChangeFinished`. Do not call seek on every slider `onValueChange`; it can flood Media3 with repeated seeks and make video/audio playback stutter.

### 队列拖动的实测几何与可访问操作

**1. 范围 / 触发**：修改 `MusicQueueSheet` 的拖动、移除或上下移菜单时适用。保留父级共享位移状态，不因 Compose BOM 升级顺带重写拖动模型。

**2. 关键签名**：
- `QueueDragState(draggedIndex: Int?, accumulatedPx: Float, targetIndex: Int?, draggedExtentPx: Float)`。
- `QueueItemBounds(index: Int, offset: Int, size: Int)`。
- `resolveQueueDropTarget(draggedIndex: Int, deltaPx: Float, items: List<QueueItemBounds>, count: Int): Int`。
- `resolveQueueRowDisplacement(rowIndex: Int, dragState: QueueDragState, rowHeightPx: Float): Float`。

**3. 可执行合同**：
- 拖动状态放在 `MusicQueueSheet` 父级。手指位移通过 `accumulatedPx` 累积，被拖行跟随手指，其他行只在跨过目标时让位。
- 从 `LazyListLayoutInfo.visibleItemsInfo` 获取真实 offset/size，以行中心选择当前可见范围内最近目标；被拖行 extent 为实测高度加 `mainAxisItemSpacing`。大字体行不能从固定 64dp 推算落点。
- 邻行按被拖行的 extent 反向位移，用 NordicMotion 的 micro tween 平滑恢复。`rowHeightPx` 只保留兼容旧状态的回退与抬起反馈尺度，不替代正常拖放中的实测几何。
- `pointerInput` 的 `onDrag`/`onDragEnd` 经 `rememberUpdatedState` 读取最新回调，松手提交一次原 `onMoveQueueItem(from, to)`；取消只清状态，不提交。
- 当前播放、重复歌曲与队列顺序仍由原引擎持有；UI 不建立第二份生产队列。位置感知 key 继续使用 `"${song.id}:$index"`。
- 行保留 42dp 封面和 48dp 拖动/更多目标；下一首、移除、上移、下移放入菜单，保留 enabled 及原回调。不能要求所有用户通过长按拖动才能排序。
- 不为拖动增加常驻阴影；保持现有缩放/alpha 的交互反馈。

**4. 验证与错误矩阵**：

| 条件 | 结果 |
|---|---|
| 不同高度的相邻行、向上/向下拖动 | 按实际中心选目标，邻行按被拖行 extent 让位 |
| 无拖动 / 未跨过目标 | 邻行位移为 0，不提交无效移动 |
| 无效 draggedIndex / count=0 | 返回 -1；调用方不提交 |
| 非有限 delta / 缺少当前可见行 | 保持原索引，不用猜测落点 |
| 超出当前可见范围 | 限制在可见有效目标；未实现自动滚动，不宣称可跨屏拖放 |
| 取消拖动 | 清状态，无队列回调 |
| 大字体 / 无法拖动 | 菜单上下移仍可达；当前播放索引由引擎结果驱动 |

**5. 正反案例**：正确：160px 行跨过 100px 行后，邻行按被拖行的完整 extent 让位；错误：将手指距离除以固定 64dp，再用另一行的高度移动邻项。

**6. 必需测试**：`QueueItemGeometryTest` 覆盖双向、可变高度、视口边界、NaN/空列表和 extent；保留 `MusicQueueSheetTest` 的兼容性用例；`UiCatalogInteractionTest` 验证真实长按拖动提交最新落点，以及大字体更多菜单的下移回调。

**7. 错误与正确写法**：

```kotlin
// 错误：视觉行高变化后，固定尺寸得出错误索引。
val target = index + (offset / fixed64DpPx).roundToInt()
// 正确：目标取自渲染几何，松手使用最近一次更新的 targetIndex。
val bounds = listState.layoutInfo.visibleItemsInfo.map { QueueItemBounds(it.index, it.offset, it.size) }
val target = resolveQueueDropTarget(index, offset, bounds, queue.size)
```

### Music player progress controls

**Scope / Trigger**: Any change to `MusicPlayerScreen` progress slider gestures, music relative seek controls, `MusicPlaybackViewModel.seekBackBy` / `seekForwardBy`, or `MusicPlaybackEngine.seekBy` helpers.

**Signatures**:
- `internal fun resolvePlayerThinSliderPosition(pointerX: Float, trackWidth: Int, durationSeconds: Int): Float`
- `internal fun resolvePlayerThinSliderThumbOffsetPx(trackWidthPx: Float, thumbSizePx: Float, progress: Float): Float`
- `internal fun resolveMusicSeekByPosition(currentPositionSeconds: Int, deltaSeconds: Int, durationSeconds: Int): Int`
- `fun MusicPlaybackEngine.seekBackBy(intervalSeconds: Int = MUSIC_SKIP_BACK_SECONDS)`
- `fun MusicPlaybackEngine.seekForwardBy(intervalSeconds: Int = MUSIC_SKIP_FORWARD_SECONDS)`

**Contracts**:
- The music progress slider must use the pointer's absolute x position within the track to compute scrub position. Do not compute drag position from a stale external `position + dragAmount` value.
- Dragging updates only local scrub display state. A real `seekTo(...)` happens only on normal drag release.
- Drag cancel clears local scrub state and must not submit a real seek.
- `PlayerThinSlider` 支持点按轨道与可访问性 `setProgress`：先设置本地目标，再完成一次 seek；真实拖动仍只在正常松手时提交，取消不提交。
- 共享滑轨的回调通过 `rememberUpdatedState` 读取最新值，切换曲目/章节/剧集后不能保留旧 scrub 状态。默认高度不改变其他播放器，视频通过传入 modifier 扩大有效触控高度。
- Relative music seek must clamp to `0..durationSeconds` when duration is known and positive.
- When duration is unknown or `<= 0`, relative music seek should match video relative seek behavior: clamp only to non-negative `0..Int.MAX_VALUE`, allowing forward seek from the current non-negative position instead of forcing the target to `0`.
- Music UI controls should expose short relative seek through the playback ViewModel/Engine, not by calculating target positions in Compose.

**Validation & Error Matrix**:
| Condition | Behavior |
|---|---|
| Pointer x before track start | Slider position resolves to `0f` |
| Pointer x after track end | Slider position resolves to duration |
| Track width `<= 0` | Slider position resolves to `0f`; no crash |
| Duration `<= 0` for slider | Use a safe duration of `1` for display math; no crash |
| Drag release | Submit one seek to the final scrub position |
| Drag cancel | Clear scrub state; no seek submitted |
| Relative seek with known duration | Clamp target inside `0..durationSeconds` |
| Relative seek with unknown duration | Clamp target to non-negative int range only |

**Good/Base/Bad Cases**:
- Good: User drags the music slider from any start point and the thumb follows the finger's absolute track position.
- Good: User cancels a drag and playback does not jump.
- Good: 30-second forward seek at 20s with unknown duration resolves to 50s, matching video relative seek behavior.
- Base: 10-second back seek at 6s resolves to 0s.
- Bad: Slider uses `position + dragAmount.x / width * duration`, so recomposition-stale position makes the thumb lag or drift.
- Bad: `onDragCancel` calls the same completion callback as `onDragEnd`, causing an accidental seek.
- Bad: Unknown duration is treated as max position `0`, so every forward relative seek becomes a no-op jump to 0.

**Tests Required**:
- Unit tests for `resolvePlayerThinSliderPosition(...)` covering absolute x mapping, clamping, invalid width, and zero duration.
- Unit tests for `resolvePlayerThinSliderThumbOffsetPx(...)` covering progress clamping and thumb travel bounds.
- Unit tests for `resolveMusicSeekByPosition(...)` covering backward clamp to 0, forward clamp to known duration, and unknown-duration non-negative behavior.

**Wrong vs Correct**:
```kotlin
// Wrong: dragAmount is incremental and `position` may be stale during the gesture.
val newPosition = (position + dragAmount.x / size.width * duration).coerceIn(0f, duration.toFloat())
onPositionChange(newPosition)
```

```kotlin
// Correct: compute from the pointer's current absolute x inside the track.
onPositionChange(resolvePlayerThinSliderPosition(change.position.x, size.width, safeDuration))
```

```kotlin
// Wrong: unknown duration collapses all relative seeks to 0.
val maxPosition = durationSeconds.coerceAtLeast(0)
return (currentPositionSeconds + deltaSeconds).coerceIn(0, maxPosition)
```

```kotlin
// Correct: known duration clamps to duration; unknown duration clamps only to non-negative int range.
if (durationSeconds > 0) {
    val target = currentPositionSeconds.coerceIn(0, durationSeconds).toLong() + deltaSeconds.toLong()
    return target.coerceIn(0L, durationSeconds.toLong()).toInt()
}
val target = currentPositionSeconds.coerceAtLeast(0).toLong() + deltaSeconds.toLong()
return target.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
```

```kotlin
val homeSongs = remember(recentlyAddedSongs) { recentlyAddedSongs.take(12) }

LazyRow {
    itemsIndexed(
        items = homeSongs,
        key = { _, song -> "home-song-${song.id}" },
        contentType = { _, _ -> "home-song-card" }
    ) { index, song ->
        SongShelfCard(
            song = song,
            onClick = { onSongSelected(homeSongs, index) }
        )
    }
}
```

**Why**: Stable keys preserve item identity across inserts/reorders, `contentType` lets Compose reuse compatible item composition, and remembered slices avoid allocating new preview lists on unrelated recompositions.

### Compose performance state isolation

High-frequency playback state, search debounce jobs, and other non-rendering handles should not force broad recomposition.

**Contracts**:
- Keep UI-rendered values in Compose state.
- Keep non-rendering mutable handles such as `Job?` in a remembered non-state holder, for example `remember { AtomicReference<Job?>(null) }`, when changing the handle should not redraw the screen.
- Extract expensive or mostly static screen content into child composables whose parameters do not include fast-ticking playback position.
- Cache derived lists and labels with `remember(source) { ... }` when they are passed into lazy lists or callbacks.
- If one small player sub-surface needs higher-frequency position data than the shared playback state (for example synced lyric highlighting), expose a narrow `StateFlow` sidecar from the owning ViewModel with `SharingStarted.WhileSubscribed(...)` and a direct engine query. Keep the broad `MusicPlaybackState.positionSeconds` / player chrome path on the coarser cadence so the whole screen does not recompose every 100ms.

```kotlin
// Wrong: every debounce job replacement invalidates the composable.
var searchJob by remember { mutableStateOf<Job?>(null) }
searchJob = scope.launch { /* search */ }

// Correct: job identity is operational state, not rendered UI state.
val searchJob = remember { AtomicReference<Job?>(null) }
searchJob.get()?.cancel()
searchJob.set(scope.launch { /* search */ })
```

```kotlin
// Correct: lyric timing gets a narrow high-frequency sidecar; the broad playback state remains coarse.
val positionMillis: StateFlow<Long> = flow {
    while (coroutineContext.isActive) {
        emit(engine.currentPositionMillis())
        delay(100L)
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)
```

**High-frequency flow delivery (leaf collection)**:
- Do NOT collect a fast-ticking `StateFlow` at a high ancestor and pass the resolved `Long`/`Float` down as a parameter — every emit re-composes that ancestor and its whole subtree.
- DO pass the `StateFlow` itself (stable reference, never changes identity) from the owner down to the narrowest leaf that renders the ticking value, and call `collectAsStateWithLifecycle()` inside that leaf. Only the leaf recomposes on each tick.
- DO wrap values derived from the tick that change slower than the tick (e.g. active lyric index) in `derivedStateOf`, keyed via `remember` on the stable inputs only (NOT the fast-changing value). Readers then skip recomposition until a derived boundary actually crosses.
- Keep the broad shared playback state (`positionSeconds`, player chrome) on the coarse cadence so whole screens do not recompose at the tick rate.

```kotlin
// Wrong: MainActivity collects at the top and pushes a Long downwards.
val currentPositionMillis by musicVM.positionMillis.collectAsStateWithLifecycle()
MusicPlayerScreen(positionMillis = currentPositionMillis, ...)

// Correct: Flow object is stable; collection happens only at the leaf that needs it.
MusicPlayerScreen(positionMillisFlow = musicVM.positionMillis, ...)

@Composable
private fun SyncedLyricsList(positionMillisFlow: StateFlow<Long>, ...) {
    val positionMillis by positionMillisFlow.collectAsStateWithLifecycle()
    val cues = remember(lyrics) { lyrics.displayCues() }
    val activeIndex by remember(cues) {
        derivedStateOf { resolveActiveMusicLyricIndex(cues, positionMillis) }
    }
    ...
}
```

**Why**: Playback ticks and debounce bookkeeping can update often. Isolating operational state prevents unrelated home/library content from being recomposed just because a handle changed.

### Custom LazyColumn scrollbar (`MusicScrollbar`)

**Scope / Trigger**: Any change to `MusicScrollbar` (`ui/MusicScrollbar.kt`) or adding/removing it on a `LazyColumn` (music pages via `MusicPageList`, synced lyrics in `MusicPlayerScreen`, queue sheet in `MusicQueueSheet`).

**Signatures**:
- `internal fun MusicScrollbar(state: LazyListState, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = MusicScrollbarAlpha), enabled: Boolean = true)`

**Contract**:
- Android's `androidx.compose.foundation.Scrollbar` / `VerticalScrollbar` / `rememberScrollbarAdapter` are Compose Multiplatform desktop/skiko ONLY — never import them on Android (unresolved).
- `MusicScrollbar` is display-only (no thumb dragging). It must be a thin rounded `Box` (`MusicScrollbarThickness = 4.dp`, clipped to `NordicShapes.full`) whose size/offset derive from `LazyListState` via `derivedStateOf`, so only the thumb recomposes on scroll.
- It must hide automatically when the content fits the viewport: `visibleItemsInfo.isNotEmpty() && totalItemsCount > visibleItemsInfo.size`.
- Thumb fraction clamps to a minimum visible ratio (`MusicScrollbarMinThumbFraction`) so long lists never collapse to a dot; scroll fraction is computed index + pixel-offset based so partial item scrolls track smoothly.
- Place it inside the same `Box` as the target `LazyColumn`, aligned `CenterEnd` with `NordicSpacing.xs` end padding; do not layer it over other scroll-affordance content.
- `enabled` must gate composition (early return) so a disabled scrollbar costs nothing.

**Why**: Long lazy lists (songs, lyrics, queue) need a scroll affordance, but Android has no native Compose scrollbar at BOM 2024.01.00. The derived-state thumb is cheap and only recomposes the thumb itself.

### pointerInput lambda freshness with rememberSaveable key changes

**Scope / Trigger**: Any `pointerInput(Unit)` or `pointerInput(constantKey)` block that captures a lambda parameter which closes over a `MutableState` created by `rememberSaveable(changingKey)` or `remember(changingKey)`.

**Problem**: When `pointerInput` uses a constant key (e.g. `Unit`), its coroutine never restarts on recomposition. The lambda captured inside `detectTapGestures` / `detectDragGestures` etc. holds a reference to the `MutableState` that existed at first composition. If a `rememberSaveable(song?.id)` or similar key-based state holder creates a **new** `MutableState` when the key changes (e.g. switching songs), the old gesture detector keeps writing to the discarded `MutableState` while the UI reads the new one — taps become silently ignored.

**Contract**:
- When a `pointerInput(constantKey)` gesture handler captures a lambda that references a key-scoped `MutableState`, use `rememberUpdatedState` to bridge the latest lambda into the non-restarting gesture coroutine.
- Do NOT change the `pointerInput` key to the lambda itself (`pointerInput(onToggleDisplay)`) — that restarts the gesture detector on every recomposition, causing unnecessary coroutine cancellation/recreation and potential gesture interruption.
- `rememberUpdatedState` is the canonical Compose pattern: `pointerInput(Unit)` stays non-restarting (performance), and the `State`-backed delegate always reads the current lambda at invocation time.

**Validation & Error Matrix**:
| Condition | Behavior |
|---|---|
| `pointerInput(Unit)` captures lambda closing over `rememberSaveable(song?.id)` state | Lambda goes stale when `song?.id` changes; taps silently ignored |
| `pointerInput(onToggleDisplay)` (key = lambda) | Gesture detector restarts on every recomposition; unnecessary overhead, potential gesture interruption |
| `rememberUpdatedState(onToggleDisplay)` + `pointerInput(Unit)` | Gesture detector stays alive; `currentToggle()` dispatches to the latest lambda; correct and performant |

**Wrong vs Correct**:
```kotlin
// Wrong: pointerInput(Unit) captures a lambda that closes over a key-scoped MutableState.
// When song?.id changes, rememberSaveable creates a new MutableState, but the old
// gesture detector keeps writing to the discarded one — taps become dead.
Box(
    modifier = modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { onToggleDisplay() })
    }
)
```

```kotlin
// Correct: rememberUpdatedState bridges the latest lambda into the non-restarting coroutine.
val currentToggle by rememberUpdatedState(onToggleDisplay)
Box(
    modifier = modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { currentToggle() })
    }
)
```

**Why**: This is a subtle, silent failure — no crash, no error, just dead taps after a state-key change. The bug is hard to diagnose because the gesture detector appears to be registered and working; it simply writes to a `MutableState` that nothing reads anymore. `rememberUpdatedState` is already an established pattern in this codebase (`VideoPlayerScreen.kt`, `VideoPlayerGestures.kt`).

### Performance-first persistent media chrome

Floating playback bars, bottom navigation, and other persistent media chrome must justify their always-visible cost. When playback is paused, idle, stopped, or otherwise not actively changing, prefer a collapsed, hidden, or on-demand surface if the chrome blocks page content or keeps expensive UI subscribed to fast-changing playback state.

**Contracts**:
- Treat performance and content readability as the default priority; decorative or convenience chrome is secondary.
- Do not keep floating player controls visible by default on static pages unless they provide an immediate user action that is more important than the obscured content.
- Prefer state gates such as `isPlaying`, active media identity, or explicit user expansion to decide when player chrome is shown.
- Keep collapsed/hidden chrome detached from high-frequency playback position updates so idle screens do not recompose on every tick.
- Preserve clear recovery paths: users must still be able to reopen playback controls or navigate back to the active player through an explicit affordance.
- Bottom navigation dock must NOT auto-reveal on a timer after scroll/fling stops. Scroll and fling hide the full dock; it stays hidden until an explicit user action (a small `BottomDockHandle` tap) or a tab/player state reset restores it. Timer-based `delay(...)` reveal for the bottom dock is forbidden — it re-interrupts content reading the user explicitly chose to do.
- Dock hide/show must be threshold-gated and gesture-driven (`resolveBottomDockScrollIntent` in `PlaybackDock.kt`, threshold `BottomDockScrollThreshold` = 24dp): a sustained scroll in one direction crosses the threshold before the dock hides or re-shows; light touches never dismiss it. Reversing scroll direction resets the accumulator. While hidden, scrolling back up past the threshold — or reaching a list's bottom edge (unconsumed upward scroll in `onPostScroll`) — restores the dock; both are explicit gestures, not timers. The handle's clickable area must meet the 48dp touch-target standard even when its visual pill is smaller.
- When a floating dock is moved out of `Scaffold.bottomBar`, keep visible-dock spacing driven by the dock's measured height (for example via `onSizeChanged`) and subtract any scaffold bottom inset before applying content padding. Do not hardcode a dock height constant, because that breaks as soon as dock content, typography, or insets change.

```kotlin
// Wrong: idle chrome permanently covers content and stays subscribed to ticks.
FloatingPlaybackBar(
    state = playbackState,
    modifier = Modifier.align(Alignment.BottomCenter)
)

// Correct: static pages stay readable; controls return while active or requested.
if (playbackState.isPlaying || showPlayerControls) {
    FloatingPlaybackBar(
        state = playbackState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
```

```kotlin
// Wrong: bottom dock auto-reappears after scroll stops, re-interrupting reading.
fun scheduleBottomDockReveal() {
    scope.launch { delay(650); bottomDockVisible = true }
}

// Correct: full dock stays hidden after scroll; a small handle restores it on tap.
if (!showPlayer && !bottomDockVisible) {
    BottomDockHandle(colorScheme = colorScheme, onClick = { bottomDockVisible = true })
}
```

```kotlin
// Correct: measure the dock and derive content padding from the live height.
var measuredDockHeight by remember { mutableStateOf(0.dp) }
val dockBottomPadding = if (bottomDockPresentation == BottomDockPresentation.Dock) {
    (measuredDockHeight - scaffoldBottomInset).coerceAtLeast(0.dp)
} else {
    0.dp
}
```

**Why**: Always-visible overlays reduce usable screen space and can keep expensive Compose surfaces alive. Media UI should spend recomposition and screen real estate only where it improves the current workflow. The bottom dock is persistent chrome: a timer that re-shows it while the user is reading static content defeats the hide-on-scroll intent and causes "自动出现" discomfort.

### Compose media player chrome auto-hide

Video / audio player overlays that subscribe to high-frequency playback position must hide their chrome when the user is not interacting with it. This is the concrete implementation pattern for the "Performance-first persistent media chrome" rule above.

**Contracts**:
- Player chrome (top bar, control row, scrim) starts **visible on first open** (`controlsVisible = true`) so first-time users see that controls exist before they fade. A tap on the video surface toggles `controlsVisible` thereafter.
- While playing, chrome auto-hides after 4 seconds of inactivity. The auto-hide timer is suppressed while paused, while the user is scrubbing (`scrubPosition != null`), or while an error/buffering state is visible (`statusTone != null`).
- Chrome is wrapped in `AnimatedVisibility` so the hidden branch leaves the composition tree entirely — the control row does not stay subscribed to per-second `positionSeconds` ticks when hidden.
- The auto-hide `LaunchedEffect` must key on every condition it reads (`controlsVisible`, `isPlaying`, `scrubPosition`, `statusTone`) so any change restarts the timer.
- Error / buffering / no-video center messages are composed independently of `controlsVisible` so they remain visible when chrome is hidden.
- Gestures (double-tap to seek, horizontal drag to scrub, pinch to cycle aspect) do not toggle `controlsVisible`; only a bare tap does. `detectTapGestures` `onDoubleTap` priority handles this disambiguation.
- When a player surface has both a child tap target and a parent swipe-to-dismiss area, avoid parent `detectDragGestures { change.consume() }` because it can consume short taps before the child `detectTapGestures` sees them. Prefer a direction-specific parent detector such as `detectVerticalDragGestures`, record whether the drag started inside the allowed region, and keep displacement thresholds in the drag callback. Do not use `detectDragGesturesAfterLongPress` for quick swipe dismissal; it fixes tap priority but regresses the expected quick-swipe UX.

```kotlin
// Visible on first open so users discover chrome before it auto-hides.
var controlsVisible by remember { mutableStateOf(true) }

LaunchedEffect(controlsVisible, isPlaying, scrubPosition, statusTone) {
    if (isPlaying && controlsVisible && scrubPosition == null && statusTone == null) {
        delay(4000)
        controlsVisible = false
    }
}

Box(Modifier.fillMaxSize().videoPlayerGestures(
    onToggleControls = { controlsVisible = !controlsVisible },
    onSeekRelative = { delta -> onSeek((state.positionSeconds + delta).coerceAtLeast(0)) },
    onScrub = { scrubPosition = it },
    onScrubFinished = { scrubPosition?.let { onSeek(it.roundToInt()) }; scrubPosition = null },
    onCycleAspectRatio = onCycleAspectRatio,
    isFullscreen = isFullscreen,
    durationSeconds = durationSeconds
)) {
    VideoPlayerSurface(...)
    AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
        VideoPlayerScrim()
    }
    if (statusTone != null || state.isBuffering || video == null) {
        VideoPlayerCenterMessage(...) // independent of controlsVisible
    }
    AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            VideoPlayerTopBar(...)
            VideoPlayerControls(visiblePosition = visiblePosition, ...) // only composed when visible
        }
    }
}
```

**Why**: A `LaunchedEffect` with stale keys silently keeps chrome visible forever; gating chrome composition behind `AnimatedVisibility` is what actually delivers the performance win (hidden controls stop reading `positionSeconds`). Error/buffering visibility must not depend on `controlsVisible` or the user loses feedback on a frozen stream.

### Compose BackHandler for sub-navigation

Every composable that manages page-level state visible to the user (e.g., `libraryPage`, `selectedVideo`, `showConfig`, `showPlayer`) must declare a `BackHandler` with the same logic as its manual back button. Without it, the Android system back gesture finishes the Activity and exits the app instead of returning to the previous screen.

**Priority rule**: `BackHandler` uses last-registered-wins priority. Declare `showConfig` handlers AFTER page-level handlers so collapsing a config panel takes precedence over page navigation when both conditions are true.

**Visibility rule**: Scope each handler to the visible surface it owns. For example, a config-panel handler should be enabled only when the browse surface is visible and `showConfig` is true; it should not stay enabled after a detail page replaces the browse surface.

```kotlin
// Page-level back — declared first (lower priority)
BackHandler(enabled = libraryPage != MusicLibraryPage.Home) {
    if (libraryPage == MusicLibraryPage.PlaylistDetail) {
        selectedTab = 2
        libraryPage = MusicLibraryPage.Playlists
    } else {
        selectedTab = 0
        libraryPage = MusicLibraryPage.Home
    }
}

// Config dismiss back — declared after (higher priority)
BackHandler(enabled = showConfig) {
    showConfig = false
}
```

```kotlin
// Correct: hidden browse config cannot intercept Back from a visible detail page.
BackHandler(enabled = selectedVideo == null && showConfig) {
    showConfig = false
}
```

**Why**: The app uses state-driven navigation without Jetpack Navigation Compose. Every sub-page, detail view, player overlay, and inline config panel is controlled by boolean/enum state. Android's default back behavior calls `Activity.finish()`, which exits the app — the opposite of what users expect when navigating within the app.

### Cross-media config reset feedback

**Scope / Trigger**: Any change to saved media config handling in Music, Audiobook, or Video screens where config changes clear user-visible detail, search, filter, or sub-page state.

**Signatures**:
- Music uses local `musicResetNotice` in `MusicScreenV2` and gates it from `LaunchedEffect(savedConfig)`.
- Audiobook uses `internal fun shouldShowAudiobookConfigResetNotice(previousConfigChanged: Boolean, libraryPage: AudiobookLibraryPage, selectedItem: AudiobookItemDetail?): Boolean`.
- Video uses `internal fun shouldShowVideoConfigResetNotice(previousConfigChanged: Boolean, selectedVideo: VideoItem?, searchQuery: String, searchExpanded: Boolean, selectedTypeFilter: VideoTypeFilter): Boolean`.

**Contract**:
- Treat saved config changes as a local navigation boundary for the affected media screen.
- Compute whether a notice is needed before calling the reset function, because reset clears the evidence (`selectedItem`, `selectedVideo`, page, search, or filter state).
- Show a compact local `MediaStateCard` only when the config key changed after initial launch and the reset visibly collapses detail/search/filter/sub-page state.
- Do not show a reset notice for first launch, same-config emissions, or default Home/browse states with no active selection/search/filter.
- Clear the notice when the user takes a normal navigation action in that media screen, such as opening a new detail page, pressing detail back, selecting a library, or clearing browser filters with Back.
- Keep feedback local to each screen; do not add a shared routing abstraction unless multiple screens already require the exact same state shape.

**Validation & Error Matrix**:
- Previous config is null -> reset state if needed, but no notice.
- Previous config cache key equals current cache key -> no notice.
- Audiobook config key changes while Detail is visible -> return Home and show one Audiobook reset notice.
- Audiobook config key changes while Home has no selected item -> return Home with no notice.
- Video config key changes while detail/search/search-expanded/non-All filter is active -> clear those states and show one Video reset notice.
- Video config key changes while default browse state is active -> no notice.

**Good/Base/Bad Cases**:
- Good: User changes AudiobookShelf server while reading a book detail; the screen returns to Home and explains the reset.
- Good: User changes Emby server while a video filter is active; the filter clears and a compact note explains the reset.
- Base: First launch with no ready config shows the setup empty state without a reset notice.
- Bad: The screen silently collapses from detail/search/filter to Home after a config edit.
- Bad: The reset notice appears on every app launch or on same-config DataStore emissions.
- Bad: The code computes notice eligibility after calling `reset...AfterConfigChange()`, so it can no longer know whether visible state was cleared.

**Tests Required**:
- Focused helper tests for first launch/no-op config emissions returning false.
- Focused helper tests for visible state collapse returning true.
- Existing compile, unit test, and lint gates after changing Compose reset paths.

**Wrong vs Correct**:
```kotlin
// Wrong: reset first, then try to infer whether anything visible changed.
resetVideoStateAfterConfigChange()
val shouldShowNotice = selectedVideo != null || searchQuery.isNotBlank()
```

```kotlin
// Correct: capture the visible state before the reset boundary clears it.
val shouldShowNotice = shouldShowVideoConfigResetNotice(
    previousConfigChanged = previousConfigChanged,
    selectedVideo = selectedVideo,
    searchQuery = searchQuery,
    searchExpanded = searchExpanded,
    selectedTypeFilter = selectedTypeFilter
)
resetVideoStateAfterConfigChange()
if (shouldShowNotice) videoResetNotice = "视频配置已更新，已回到视频首页。"
```

### Config readiness checks centralized

`NavidromeConfig.isReadyForMusicSync()` is defined once in `ServerConfig.kt` and imported where needed. Do not inline `serverUrl.isNotBlank() && username.isNotBlank()` or create duplicate extension functions.

### High-refresh display mode (OEM smart refresh rate bypass)

**Scope / Trigger**: Any change to `MainActivity.onCreate` display-mode handling, or diagnosing "120Hz phone feels janky" reports.

**Contract**:
- `MainActivity.onCreate` MUST request the highest refresh rate available at the current resolution via `window.attributes.preferredDisplayModeId`. OEM "smart refresh rate" policies (ColorOS/OriginOS etc.) cap third-party apps at 60Hz unless the app explicitly requests a high-refresh mode — without this the app never runs at 120Hz regardless of the panel.
- Selection logic lives in the pure function `resolvePreferredDisplayModeId(modes: List<DisplayModeSpec>, currentModeId: Int)` (`MainActivity.kt`): among modes sharing the current mode's resolution, highest refresh rate wins (ties → smaller mode id); never downgrade resolution for rate; fall back to the current mode when nothing is faster or the current mode is missing from the list.
- `DisplayModeSpec(id, width, height, refreshRate)` is a plain data class — do NOT pass `android.view.Display.Mode` directly, because JVM unit tests cannot construct framework classes (stub android.jar reflection fails with `NoSuchMethodException`).
- `Context.getDisplay()` is API 30+; the call site must branch on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` and fall back to `@Suppress("DEPRECATION") windowManager.defaultDisplay` below R (lint `NewApi` gate).
- There is intentionally NO user-facing toggle for this (user decision, 2026-09-10).

**Tests Required**: `MainActivityTest.resolvePreferredDisplayModeId_*` covering multi-mode selection, other-resolution exclusion, missing-current-mode fallback, and single-mode identity.

### Coil crossfade must be explicit at every call site

`AuthedAsyncImage(crossfadeEnabled = false)` MUST still call `crossfade(false)` on the `ImageRequest`. Omitting the call silently falls back to the global image loader's crossfade (160ms in `MainActivity`), stacking crossfade animations on the RenderThread during fast list scrolling — the exact jank the parameter exists to prevent. The correct pattern is `crossfade(crossfadeEnabled)` followed by the duration override when enabled.

### CoverArt gradient backdrop is conditional

`CoverArt` (`SharedComponents.kt`) skips its gradient `background` brush when a real image is rendering (`showImage == true`). Grid pages stack dozens of cards; an always-on backdrop under an opaque image is pure overdraw. Keep the brush for fallback states (initials / icon / text) — those need a visible surface.

### R8 keep rules for this app

Release builds run R8 with `proguard-rules.pro`. The non-obvious entries: Retrofit needs `Signature`/annotation `keepattributes` + interface keep rules; Gson DTOs (`api.**`, `data.**`) are kept because response bodies convert reflectively; Tink (via security-crypto) needs `-dontwarn org.joda.time.Instant` (optional `KeysDownloader` dependency) and its own keep block. If a new reflective dependency is added, expect R8 `Missing class` errors and consult `app/build/outputs/mapping/release/missing_rules.txt` rather than guessing.

**Retrofit suspend 的 full-mode 陷阱（2026-09-10）**：`-keepattributes Signature` 加 API/DTO keep 仍不够；必须保留 `kotlin.coroutines.Continuation`、`retrofit2.Response`、`retrofit2.Call` 的泛型定义。否则方法末参会被擦为原始 Continuation，Retrofit 2.9 的反射解析抛出 `Class cannot be cast to ParameterizedType`。使用精确的 `-keep,allowoptimization,allowshrinking,allowobfuscation` 类型规则，不关闭 R8，不扩大为保留整个依赖库。`RetrofitReleaseContractTest` 防止规则回退，但 debug/JVM 单测和签名检查无法发现 R8 的实际泛型擦除；必须额外检查打包后的三个 API 的嵌套泛型签名，执行命令与断言见 [构建身份、版本与签名](./build-release.md)。

### Media repository instance reuse

When a composable or screen needs a media repository such as `NavidromeRepository`, `AudiobookShelfRepository`, or `EmbyRepository`, use `remember(config) { Repository(config) }` keyed on config changes. Do not construct repositories inline in every refresh/list-detail suspend call when the saved config already has a remembered repository. Each construction creates a new Retrofit + OkHttpClient.

```kotlin
val navidromeRepository = remember(savedConfig) {
    if (savedConfig.isReadyForMusicSync()) NavidromeRepository(savedConfig) else null
}

val repo = if (targetConfig == savedConfig) {
    navidromeRepository ?: NavidromeRepository(targetConfig)
} else {
    NavidromeRepository(targetConfig)
}
```

Construct a temporary repository only for unsaved form values being tested/saved, where `targetConfig != savedConfig`.

### Navidrome cover-art URL mapping

**Scope / Trigger**: Any change to `NavidromeRepository` mapping for album, playlist, or song `coverArt` fields.

**Signatures**:
- `private fun String?.toCoverArtUrlOrNull(): String?`
- `private fun NavidromeSong.withCoverArtUrl(fallbackCoverArt: String? = null): NavidromeSong`

**Contract**:
- `null`, empty, and whitespace-only Navidrome `coverArt` ids are absent and must map to `null`.
- Non-blank `coverArt` ids must map to authenticated `/rest/getCoverArt.view?id=<id>` URLs.
- Song mapping checks the song's own non-blank `coverArt` first, then a non-blank album or playlist fallback. A blank song value must not block a valid fallback, and a blank fallback must not produce `id=`.
- Stream URL generation is independent of cover-art mapping and should still use `stream.view`.

**Validation & Error Matrix**:
- Album or playlist summary `coverArt = ""` or `"   "` -> returned `coverArt = null`.
- Playlist or album detail fallback `coverArt = ""` or `"   "` with entry missing cover art -> entry `coverArt = null`.
- Entry `coverArt = ""` or `"   "` with non-blank fallback -> entry uses fallback cover URL.
- Any non-blank id -> authenticated cover-art URL is preserved.

**Good/Base/Bad Cases**:
- Good: A blank album cover does not show a broken image request with `id=`.
- Good: Playlist entries inherit a valid playlist cover when their own `coverArt` is blank.
- Base: Entries with no cover art and no valid fallback return `null`.
- Bad: Calling `coverArt?.let(::buildCoverArtUrl)` or `(coverArt ?: fallbackCoverArt)?.let(...)`, because blank strings are treated as real ids.

**Tests Required**:
- Repository unit test asserting blank album or playlist summary cover ids map to `null`.
- Repository unit test asserting blank playlist/album fallback ids do not create song cover URLs.
- Repository unit test asserting a blank song cover id can still use a valid fallback.
- Existing or new repository assertion that non-blank ids still produce authenticated `getCoverArt.view` URLs.

**Wrong vs Correct**:
```kotlin
// Wrong: blank strings become /rest/getCoverArt.view?id=.
coverArt = (coverArt ?: fallbackCoverArt)?.let(::buildCoverArtUrl)

// Correct: treat blank ids as absent before applying fallback.
coverArt = coverArt.toCoverArtUrlOrNull() ?: fallbackCoverArt.toCoverArtUrlOrNull()
```

### Navidrome artist index browsing

**Scope / Trigger**: Any change to Music artist browsing UI, `NavidromeApi` artist index DTOs/endpoints, or `NavidromeRepository.getArtists()`.

**Signatures**:
- `SubsonicData.artists: ArtistsIndex?`
- `ArtistsIndex.index: List<ArtistIndex>?`
- `ArtistIndex.artist: List<NavidromeArtist>?`
- `NavidromeApi.getArtists(...): Response<SubsonicResponse>`
- `override suspend fun NavidromeRepository.getArtists(): List<NavidromeArtist>`

**Contract**:
- `getArtists()` calls Subsonic `getArtists.view` and flattens every `artists.index[].artist[]` entry into a single app-facing artist list.
- Artist index arrays are optional wire fields. DTOs must model both `artists.index` and nested `index.artist` as nullable, and repository mapping must normalize each level with `orEmpty()`.
- Missing or null nested artist arrays are empty index groups; they must not prevent other index groups from contributing artists.
- Returned artists must include computed initials through `withInitials()` before reaching UI.

**Validation & Error Matrix**:
- Subsonic/HTTP error while loading artists -> preserve `NavidromeApiException`.
- Unknown failure while loading artists -> wrap as `"获取歌手失败: ..."` for UI context.
- Missing `artists` -> return an empty artist list.
- Missing or null `artists.index` -> return an empty artist list.
- Missing or null nested `index.artist` -> skip that index group and keep mapping other groups.

**Good/Base/Bad Cases**:
- Good: Artist browsing flattens multiple Subsonic index groups and computes initials for every returned artist.
- Good: A compatible server sends an empty or partial artist index response, and the repository returns a successful empty/partial artist list.
- Base: Empty server artist list shows artist empty state.
- Bad: Nested artist arrays are modeled as non-null Kotlin lists and flattened directly.

**Tests Required**:
- Repository unit test asserting `getArtists()` requests `/rest/getArtists.view`, flattens index groups, and computes initials.
- Repository unit test asserting missing and null `artists.index` arrays map to an empty artist list.
- Repository unit test asserting missing and null nested `index.artist` arrays are skipped without dropping artists from other groups.

**Wrong vs Correct**:
```kotlin
// Wrong: optional nested wire arrays are modeled as non-null DTO fields.
data class ArtistsIndex(
    val index: List<ArtistIndex> = emptyList()
)

data class ArtistIndex(
    val name: String,
    val artist: List<NavidromeArtist> = emptyList()
)
```

```kotlin
// Correct: model optional index arrays as nullable and normalize while flattening.
data class ArtistsIndex(
    val index: List<ArtistIndex>? = null
)

data class ArtistIndex(
    val name: String,
    val artist: List<NavidromeArtist>? = null
)

val artists = subsonic.artists?.index.orEmpty().flatMap { index ->
    index.artist.orEmpty()
}
```

### Navidrome all-song navigation data

The Music tab's "歌曲" navigation must use all songs, not the recently added preview list.

**Scope / Trigger**: Any change to the Music screen song tab, `NavidromeRepository` song loading, or `NavidromeMusicCache` song fields.

**Signatures**:
- `data class NavidromeSong(..., val created: String? = null)` must preserve the Subsonic song `created` timestamp for added-time sorting.
- `NavidromeApi.getAlbumList2(..., type: String, size: Int, offset: Int)` must expose `offset` so repositories can page through album lists.
- `NavidromeRepository.getAllSongs(): List<NavidromeSong>` is the source for the song navigation page.
- `NavidromeRepository.getRecentlyAddedSongs(albums, limit)` is only for recently added preview surfaces.

**Contract**:
- All songs are loaded by paging `getAlbumList2(type = "alphabeticalByName", size = ALBUM_PAGE_SIZE, offset = n)` until a short page or empty page, then expanding each album with `getAlbum`.
- Album detail responses may omit `song` or send it as null. DTOs must allow nullable `NavidromeAlbumDetail.song`, and repository mapping paths such as `getAllSongs()`, `getRecentlyAddedSongs(...)`, and `getAlbumSongs(...)` must convert it to an empty song list.
- Song added-time sorting is a UI sort over `NavidromeSong.created`, newest first. Do not use the limited `recentlyAddedSongs` preview as the Songs page data source.
- Home "最近添加" may show `recentlyAddedSongs`, but selecting navigation "歌曲" must render cached/refreshed `songs` from `getAllSongs()`.
- When changing `NavidromeMusicCache` field semantics, bump `MUSIC_CACHE_SCHEMA_VERSION` so stale cached data is not displayed under the new meaning.

**Validation & Error Matrix**:
- Subsonic/HTTP error while paging albums -> preserve `NavidromeApiException`.
- Unknown failure while loading all songs -> wrap as `"获取全部歌曲失败: ..."` for UI context.
- Empty album list -> return an empty song list, do not fall back to random songs.
- Album detail `song` is missing or null -> treat that album as having zero songs, do not crash the repository flow.
- Missing song `created` value -> added-time sort keeps those songs after timestamped songs via empty-string fallback, then title tiebreaker.

**Good/Base/Bad Cases**:
- Good: Song tab displays every track returned by all paged albums.
- Good: A Subsonic-compatible server omits an album detail `song` array, and the repository returns an empty song list for that album instead of crashing.
- Base: Recently added carousel displays only the limited recent-song set.
- Bad: Song tab uses `getRecentlyAddedSongs(...)` or `getRandomSongs(...)`, or drops `created` during DTO mapping / Media3 queue conversion.
- Bad: Album detail `song` is modeled as a non-null Kotlin list and repository mapping calls `.map` directly, allowing Gson-omitted fields to become runtime nulls.

**Tests Required**:
- Repository unit test with `MockWebServer` asserting `getAllSongs()` requests `type=alphabeticalByName`, `size=100`, `offset=0`, then expands album tracks via `getAlbum`.
- Repository unit test asserting album song JSON `created` is preserved on returned `NavidromeSong`.
- Repository unit tests asserting missing and null album detail `song` arrays map to empty song lists in both all-song expansion and direct `getAlbumSongs(...)`.

**Wrong vs Correct**:
```kotlin
// Wrong: binds the Songs navigation page to a recent preview.
val freshSongs = repo.getRecentlyAddedSongs(freshAlbums)

// Correct: keep preview and all-song navigation separate.
val freshRecentlyAddedSongs = repo.getRecentlyAddedSongs(freshAlbums)
val freshSongs = repo.getAllSongs()
```

```kotlin
// Wrong: omitted album detail song arrays can become runtime nulls with Gson.
val songs = albumDetail.song.map { it.withCoverArtUrl(albumDetail.coverArt) }

// Correct: normalize optional arrays at the repository boundary.
val songs = albumDetail.song.orEmpty().map { it.withCoverArtUrl(albumDetail.coverArt) }
```

### Navidrome recent-song random fallback

**Scope / Trigger**: Any change to `NavidromeRepository.getRecentSongs()`, `NavidromeApi.getRandomSongs(...)`, or the `SongList` DTO.

**Signatures**:
- `data class SongList(val song: List<NavidromeSong>? = null)`
- `suspend fun NavidromeRepository.getRecentSongs(): List<NavidromeSong>`
- `private suspend fun NavidromeRepository.getRecentSongsFromAlbums(): List<NavidromeSong>`

**Contract**:
- `getRecentSongs()` tries Subsonic `getRandomSongs.view` first. A non-empty random-song result returns immediately after mapping every song through `withCoverArtUrl()`.
- `randomSongs.song` is an optional wire array. DTOs must model it as nullable, and repository mapping must normalize it with `orEmpty()` before mapping.
- Missing, null, or empty random-song arrays should keep the existing fallback to album-derived recent songs.
- The fallback should use lower-level album helpers (`getAlbumList(type = "newest", size = RECENT_ALBUM_LIMIT)` plus `getSongsFromAlbums(...)`) instead of routing through the public `getRecentAlbums()` logging wrapper.
- Random-song endpoint failures are fallback-eligible. Fallback album failures still preserve `NavidromeApiException` or get wrapped by `getRecentSongs()` with music context.

**Validation & Error Matrix**:
- `randomSongs.song` contains songs -> return mapped random songs with stream and cover-art URLs; do not request fallback albums.
- `randomSongs` is missing, `randomSongs.song` is missing/null, or `randomSongs.song = []` -> request newest albums and expand album tracks as fallback recent songs.
- Random-song request throws HTTP/Subsonic/API or unknown error -> attempt the same album-track fallback.
- Fallback album request throws `NavidromeApiException` -> rethrow the typed error.
- Fallback album request throws unknown error -> wrap as `"鑾峰彇姝屾洸澶辫触: ..."`.

**Good/Base/Bad Cases**:
- Good: A Subsonic-compatible server omits `randomSongs.song`, and the home recent-song shelf still fills from recent albums.
- Good: Present random songs map directly to playable songs without extra album calls.
- Base: Empty random-song result behaves like a random endpoint with no useful data.
- Bad: `SongList.song` is modeled as a non-null Kotlin list and relies on Gson applying constructor defaults.
- Bad: Fallback calls `getRecentAlbums()` and pulls Android `Log` side effects into an internal repository fallback path.

**Tests Required**:
- Repository unit test asserting present random songs return mapped playable songs and do not request fallback albums.
- Repository unit tests asserting missing and null `randomSongs.song` arrays fall back to album-derived recent songs.
- Existing full repository/unit gates after changing DTO nullability or fallback control flow.

**Wrong vs Correct**:
```kotlin
// Wrong: optional random-song arrays are treated as constructor-defaulted lists.
data class SongList(
    val song: List<NavidromeSong> = emptyList()
)

val songs = subsonic.randomSongs?.song?.map { it.withCoverArtUrl() } ?: emptyList()
```

```kotlin
// Correct: model the wire array as nullable and normalize at the repository boundary.
data class SongList(
    val song: List<NavidromeSong>? = null
)

val songs = subsonic.randomSongs?.song.orEmpty().map { it.withCoverArtUrl() }
```

### Navidrome album browsing sort modes

**Scope / Trigger**: Any change to the Music screen album page, album sort UI, `NavidromeRepository.getAlbums(...)`, or `NavidromeApi.getAlbumList2(...)`.

**Signatures**:
- `enum class NavidromeAlbumSort { RecentlyAdded, ReleaseYear, Name }`
- `AlbumList.album: List<NavidromeAlbum>?`
- `NavidromeApi.getAlbumList2(..., type: String, size: Int, offset: Int, fromYear: Int? = null, toYear: Int? = null)`
- `suspend fun NavidromeRepository.getAlbums(sort: NavidromeAlbumSort): List<NavidromeAlbum>`

**Contract**:
- Album browsing is entered from the Music home album section's "All" action; do not replace the top-level Music tabs unless the PRD explicitly changes that scope.
- `RecentlyAdded` maps to `getAlbumList2(type = "newest")`.
- `ReleaseYear` maps to `getAlbumList2(type = "byYear", fromYear = 2100, toYear = 1900)` so the API returns newest release years first across a broad year range.
- `Name` maps to `getAlbumList2(type = "alphabeticalByName")`.
- Album browsing should page with `size = 100` and `offset = n` until an empty or short page.
- Album list arrays are optional wire fields. DTOs must model `albumList2.album` as nullable, and repository mapping must normalize it with `orEmpty()` so missing/null arrays are empty album pages.
- The sorted album list is view state, not part of `NavidromeMusicCache`; cached `albums` remains the home/recent album preview unless the cache contract is explicitly revised and schema-bumped.

**Validation & Error Matrix**:
- Subsonic/HTTP error while loading sorted albums -> preserve `NavidromeApiException`.
- Unknown failure while loading sorted albums -> wrap as `"获取专辑列表失败: ..."` for UI context.
- Empty sorted album response -> show album empty state; do not fall back to songs or random albums.
- Missing or null `albumList2.album` -> treat the page as empty, stopping paging with the albums accumulated so far.

**Good/Base/Bad Cases**:
- Good: Home shows recent album previews, "All" opens album browsing, and sort chips refetch with the mapped API type.
- Good: A compatible server omits `albumList2.album`, and album browsing returns a successful empty page.
- Base: Recently added sort shows the same ordering family as the home album preview but can page beyond the preview limit.
- Bad: Release-year sorting uses alphabetical albums and sorts locally; this can be incorrect across paged server data.
- Bad: Album list arrays are modeled as non-null Kotlin lists and mapped directly, relying on Gson defaults for omitted fields.

**Tests Required**:
- Repository unit test with `MockWebServer` asserting `getAlbums(...)` sends `type=newest`, `type=byYear&fromYear=2100&toYear=1900`, and `type=alphabeticalByName` for the three sort modes.
- Repository unit test asserting missing and null `albumList2.album` arrays map to an empty album list.

**Wrong vs Correct**:
```kotlin
// Wrong: local sort after fetching alphabetical data can miss server-side page ordering.
val albums = repo.getAlbums(NavidromeAlbumSort.Name).sortedByDescending { it.year ?: 0 }

// Correct: request the server-side album list type for the selected sort.
val albums = repo.getAlbums(NavidromeAlbumSort.ReleaseYear)
```

```kotlin
// Wrong: optional album arrays are modeled as non-null DTO fields.
data class AlbumList(
    val album: List<NavidromeAlbum> = emptyList()
)
```

```kotlin
// Correct: normalize optional album pages at the repository boundary.
data class AlbumList(
    val album: List<NavidromeAlbum>? = null
)

val albums = subsonic.albumList2?.album.orEmpty().map { it.withCoverArtUrl() }
```

### Navidrome search result mapping

**Scope / Trigger**: Any change to Music search UI, `NavidromeApi` search DTOs/endpoints, or `NavidromeRepository.search(...)`.

**Signatures**:
- `SubsonicData.searchResult3: SearchResult3?`
- `SearchResult3.artist: List<NavidromeArtist>?`
- `SearchResult3.album: List<NavidromeAlbum>?`
- `SearchResult3.song: List<NavidromeSong>?`
- `NavidromeApi.search3(..., query: String): Response<SubsonicResponse>`
- `suspend fun NavidromeRepository.search(query: String): SearchMusicResult`
- `data class SearchMusicResult(val artists: List<NavidromeArtist>, val albums: List<NavidromeAlbum>, val songs: List<NavidromeSong>)`

**Contract**:
- `search(query)` calls Subsonic `search3.view` with the trimmed non-blank query.
- Search result buckets are optional wire fields. DTOs must model `searchResult3.artist`, `searchResult3.album`, and `searchResult3.song` as nullable, and repository mapping must normalize each bucket with `orEmpty()` before producing `SearchMusicResult`.
- Search artists returned to UI must include computed initials through the same `withInitials()` mapping used by artist browsing.
- Search albums and songs must convert non-blank cover art through `getCoverArt.view`.
- Search songs returned to UI must include playable `streamUrl` values built through `stream.view`.

**Validation & Error Matrix**:
- Subsonic/HTTP error while searching -> preserve `NavidromeApiException`.
- Unknown failure while searching -> wrap as `"搜索失败: ..."` for UI context.
- Missing `searchResult3` -> return `SearchMusicResult()` with empty buckets.
- Missing or null `searchResult3.artist` -> return an empty artist bucket.
- Missing or null `searchResult3.album` -> return an empty album bucket.
- Missing or null `searchResult3.song` -> return an empty song bucket.

**Good/Base/Bad Cases**:
- Good: Search results can include artists, albums, and songs together, and each bucket maps through the repository helpers before reaching UI.
- Good: A compatible server omits one or more search buckets, and the repository returns successful empty buckets instead of surfacing an error.
- Base: Empty search result buckets render as empty UI sections.
- Bad: Search result arrays are modeled as non-null Kotlin lists and repository code maps them directly.

**Tests Required**:
- Repository unit test asserting `search(query)` requests `/rest/search3.view` with the trimmed query and maps artist initials, album cover art, song cover art, and song stream URLs.
- Repository unit test asserting missing and null `artist`, `album`, and `song` arrays map to empty result buckets.

**Wrong vs Correct**:
```kotlin
// Wrong: optional wire arrays are modeled as non-null DTO fields.
data class SearchResult3(
    val artist: List<NavidromeArtist> = emptyList(),
    val album: List<NavidromeAlbum> = emptyList(),
    val song: List<NavidromeSong> = emptyList()
)
```

```kotlin
// Correct: model optional arrays as nullable and normalize each search bucket.
data class SearchResult3(
    val artist: List<NavidromeArtist>? = null,
    val album: List<NavidromeAlbum>? = null,
    val song: List<NavidromeSong>? = null
)

SearchMusicResult(
    artists = result.artist.orEmpty().map { it.withInitials() },
    albums = result.album.orEmpty().map { it.withCoverArtUrl() },
    songs = result.song.orEmpty().map { it.withCoverArtUrl() }
)
```

### Navidrome playlist browsing

**Scope / Trigger**: Any change to Music playlist UI, `NavidromeApi` playlist DTOs/endpoints, or `NavidromeRepository` playlist methods.

**Signatures**:
- `SubsonicData.playlists: NavidromePlaylistList?`
- `SubsonicData.playlist: NavidromePlaylistDetail?`
- `NavidromePlaylistList.playlist: List<NavidromePlaylist>?`
- `NavidromePlaylistDetail.entry: List<NavidromeSong>?`
- `NavidromeApi.getPlaylists(...): Response<SubsonicResponse>`
- `NavidromeApi.getPlaylist(..., playlistId: String): Response<SubsonicResponse>`
- `suspend fun NavidromeRepository.getPlaylists(): List<NavidromePlaylist>`
- `suspend fun NavidromeRepository.getPlaylistSongs(playlistId: String): List<NavidromeSong>`

**Contract**:
- Playlist browsing is read-only unless the PRD explicitly includes playlist mutation.
- `getPlaylists()` calls Subsonic `getPlaylists.view` and maps `playlists.playlist`.
- `getPlaylistSongs(playlistId)` calls `getPlaylist.view` with `id=<playlistId>` and maps `playlist.entry`.
- Playlist summary/detail arrays are optional wire fields. DTOs must model `playlists.playlist` and `playlist.entry` as nullable, and repository mapping must normalize them with `orEmpty()` before producing app-facing lists.
- Playlist and playlist-song cover art must be converted through `getCoverArt.view`; playlist detail cover art may be used as a fallback for entries with missing `coverArt`.
- Playlist entries returned to UI must include playable `streamUrl` values built through `stream.view`.
- Playlist data is view state, not part of `NavidromeMusicCacheRepository`, unless the cache schema is explicitly revised and version-bumped.
- The Music playlist tab should show an empty state for empty responses, not a "coming soon" placeholder.

**Validation & Error Matrix**:
- Subsonic/HTTP error while loading playlists or playlist detail -> preserve `NavidromeApiException`.
- Unknown failure while loading playlists -> wrap as `"获取歌单失败: ..."` for UI context.
- Unknown failure while loading playlist songs -> wrap as `"获取歌单曲目失败: ..."` for UI context.
- Empty `playlists` or missing `playlist` detail -> return empty lists; do not fall back to albums, songs, or random songs.
- Missing or null `playlists.playlist` -> return an empty playlist list.
- Missing or null `playlist.entry` -> return an empty song list.

**Good/Base/Bad Cases**:
- Good: Playlist tab loads real Navidrome playlists, opens detail, and plays the detail song list through the existing `onSongSelected(list, index)` flow.
- Good: A compatible server omits `playlists.playlist` or `playlist.entry`, and the repository returns a successful empty list instead of surfacing an error.
- Base: Empty server playlist list shows a compact empty state.
- Bad: Optional playlist arrays are modeled as non-null Kotlin lists and repository code assumes Gson defaults protect omitted fields.
- Bad: Playlist tab remains a hard-coded placeholder after playlist APIs exist, or UI calls Retrofit directly instead of `NavidromeRepository`.

**Tests Required**:
- Repository unit test asserting `getPlaylists()` requests `/rest/getPlaylists.view` and maps playlist fields plus cover art URLs.
- Repository unit test asserting missing and null `playlists.playlist` arrays map to an empty playlist list.
- Repository unit test asserting `getPlaylistSongs(id)` requests `/rest/getPlaylist.view?id=<id>`, maps entries, applies fallback cover art, and builds stream URLs.
- Repository unit test asserting missing and null `playlist.entry` arrays map to an empty song list.
- Compile/lint checks for UI callback wiring.

**Wrong vs Correct**:
```kotlin
// Wrong: optional wire arrays are modeled as non-null DTO fields.
data class NavidromePlaylistDetail(
    val entry: List<NavidromeSong> = emptyList()
)
```

```kotlin
// Correct: model optional arrays as nullable and normalize at the repository boundary.
data class NavidromePlaylistDetail(
    val entry: List<NavidromeSong>? = null
)

val songs = detail.entry.orEmpty().map { song ->
    song.withCoverArtUrl(detail.coverArt)
}
```

```kotlin
// Wrong: UI bypasses repository mapping and auth conventions.
api.getPlaylist(username, token, salt, playlistId = id)
```

```kotlin
// Correct: UI asks repository for app-facing playable songs.
val songs = repo.getPlaylistSongs(playlist.id)
onSongSelected(songs, index)
```

### Navidrome music detail loading errors

**Scope / Trigger**: Any change to Music album detail, artist detail, or playlist detail loading paths in `MusicScreenV2`.

**Signatures**:
- `internal fun musicAlbumDetailLoadErrorMessage(error: Throwable): String`
- `internal fun musicArtistDetailLoadErrorMessage(error: Throwable): String`

**Contract**:
- Starting album or artist detail navigation must clear the previous screen-level `errorMsg` before launching the detail load.
- Album detail load failures must set a contextual error such as `"获取专辑曲目失败: ..."` instead of swallowing the exception.
- Artist detail load failures must set a contextual error such as `"获取歌手专辑失败: ..."` instead of swallowing the exception.
- Do not convert network/API failures into valid empty album or empty artist states. Empty states are only for successful empty responses.
- Artist detail responses may omit `album` or send it as null. DTOs must allow nullable `NavidromeArtistDetail.album`, and `getArtistAlbums(...)` must convert it to an empty album list.
- Playlist detail loading should keep its existing contextual error pattern and remain the reference for detail-load failure behavior.

**Validation & Error Matrix**:
- Album detail repository call throws -> set album-detail load error and stop the loading indicator.
- Artist detail `album` is missing or null -> return an empty artist album list without surfacing an error.
- Artist detail repository call throws -> set artist-detail load error and stop the loading indicator.
- Error has no message -> use a non-empty fallback message such as `"未知错误"`.
- Detail load starts after an older error -> clear the old error before showing loading/content for the new target.

**Good/Base/Bad Cases**:
- Good: Album songs request fails and the Music tab shows a contextual album-song load error.
- Good: Artist albums request fails and the Music tab shows a contextual artist-album load error.
- Good: Artist detail succeeds with missing/null `album`, and the Music tab can show a real empty artist-album state.
- Base: Album or artist detail request succeeds with an empty list; show the appropriate empty detail state.
- Bad: Catching `Exception` with an empty catch block, causing a failed request to look like a real empty album or artist.
- Bad: Artist detail `album` is modeled as a non-null Kotlin list and repository mapping calls `.map` directly, allowing Gson-omitted fields to become runtime nulls.

**Tests Required**:
- Unit tests for album and artist detail error-message helpers, including the context prefix and cause/fallback.
- Repository unit tests asserting missing and null artist detail `album` arrays map to empty album lists, and present artist albums still map cover art URLs.

**Wrong vs Correct**:
```kotlin
// Wrong: omitted artist detail album arrays can become runtime nulls with Gson.
val albums = detail.album.map { it.withCoverArtUrl() }

// Correct: normalize optional arrays at the repository boundary.
val albums = detail.album.orEmpty().map { it.withCoverArtUrl() }
```

### Navidrome music config-change state reset

**Scope / Trigger**: Any change to saved Navidrome config handling, Music library navigation, album/artist/playlist detail state, album/playlist list loading, or Music search state in `MusicScreenV2`.

**Signatures**:
- `internal enum class MusicLibraryPage`
- `internal fun resolveMusicLibraryPageAfterConfigChange(currentPage: MusicLibraryPage): MusicLibraryPage`
- `internal data class MusicBackStackEntry(val page: MusicLibraryPage, val selectedTab: Int)`
- `internal fun resolveMusicBackNavigation(currentPage: MusicLibraryPage, stack: List<MusicBackStackEntry>): MusicBackNavigationResult`
- `internal fun <T> reconcileMusicSelection(current: T?, refreshed: List<T>, idOf: (T) -> String): T?`
- `LaunchedEffect(savedConfig)` is the config-boundary reset point for Music screen view state.

**Contract**:
- Music detail navigation is source-aware. Before entering `AlbumDetail`, `ArtistDetail`, or `PlaylistDetail`, push the current non-detail page and selected tab into a lightweight back stack. System `BackHandler` and `MediaPageHeader(onBack)` must continue to call the same back function.
- Detail back must prefer the most recent valid source entry. Invalid detail entries should be skipped; if no valid source remains, fall back to Home, except playlist detail may safely fall back to the Playlists tab/page.
- Music refresh/list-load paths that replace `albums`, `artists`, `sortedAlbums`, or `playlists` must reconcile selected detail objects by id. If the selected object still exists, update it to the refreshed instance; if it disappeared, clear its detail payload and route away from the now-invalid detail page.
- A saved config change is a navigation boundary. Reset `libraryPage` to `MusicLibraryPage.Home` and `selectedTab` to the home tab before applying cached or fresh data for the new config.
- Clear account-scoped detail state on config changes: `selectedAlbum`, `albumDetailSongs`, `selectedArtist`, `artistAlbums`, `selectedPlaylist`, and `playlistSongs`.
- Clear account-scoped list/search state on config changes: `sortedAlbums`, `playlists`, album sort back to `RecentlyAdded`, `searchQuery`, `searchResult`, `searchError`, `isSearching`, and the pending `searchJob`.
- When a saved config change visibly collapses a non-Home page or existing content/search state, show a lightweight local explanation. Do not show this message on normal first launch or no-op config emissions.
- Reset account-scoped loading flags so a previous config's in-flight work cannot leave the new config stuck in a loading state.
- Version or otherwise guard asynchronous album-list, playlist-list, search, and detail-load writes so responses from a previous config cannot repopulate state after the boundary reset.
- Preserve not-ready config behavior: library content, cache timestamp, and screen errors are cleared when `savedConfig.isReadyForMusicSync()` is false.

**Validation & Error Matrix**:
- User opens album detail from Albums/Search/ArtistDetail -> back returns to the recorded valid source context instead of always returning Home.
- Back stack contains stale detail entries -> skip them and return to the nearest non-detail entry, or fallback safely.
- Refresh replaces albums/artists/playlists and the selected detail id still exists -> selected detail object updates to the refreshed instance.
- Refresh replaces albums/artists/playlists and the selected detail id no longer exists -> clear selected detail payload and leave the invalid detail page.
- Ready config replaces another ready config -> return to Home, clear detail/search/list state, then apply cache/refresh for the new config.
- Ready config becomes not-ready -> return to Home and clear music content, cache timestamp, detail/search/list state, and errors.
- Config change collapses a visible non-Home/content/search state -> show one local explanatory message.
- First launch or same config re-emission -> no config-reset message.
- Old album/artist/playlist detail request finishes after config change -> ignore its data/error/loading writes.
- Old album-list, playlist-list, or search request finishes after config change -> ignore its stale result/error/loading writes.

**Good/Base/Bad Cases**:
- Good: User opens Artist -> AlbumDetail, taps system back, and returns to ArtistDetail when that origin is still valid.
- Good: User refreshes while viewing a playlist that was deleted on the server; the app clears the stale playlist detail and returns to Playlists.
- Good: User switches Navidrome account from an album detail page and sees the Music home for the new account, not the old album.
- Good: User edits Navidrome config while on Search and sees a compact note explaining the Music page returned to the new library home.
- Good: A slow previous search response cannot show results under the new account.
- Base: A first app launch with no ready config still shows no music content and no stale cache timestamp.
- Bad: Detail back always sets `libraryPage = Home`, losing the user's drill-in source context.
- Bad: Refresh updates `albums` but leaves `selectedAlbum` pointing at an album id no longer present in the refreshed list.
- Bad: Only clearing `playlistSongs` while leaving `selectedAlbum` or `libraryPage = AlbumDetail` from the previous account.
- Bad: Resetting state first, but allowing an old coroutine to repopulate `albumDetailSongs`, `artistAlbums`, `playlists`, or `searchResult` afterward.

**Tests Required**:
- Focused helper tests for `resolveMusicLibraryPageAfterConfigChange(...)` covering every `MusicLibraryPage`.
- Focused helper tests for `resolveMusicBackNavigation(...)` covering source-context return, stale detail-entry skipping, playlist fallback, and empty-stack fallback.
- Focused helper tests for `reconcileMusicSelection(...)` covering retained selection, refreshed-instance replacement, and missing-selection clearing.
- Compile and unit test gates after changing the Compose reset path.

**Wrong vs Correct**:
```kotlin
// Wrong: only some view state is reset, and old async requests can still write.
playlistSongs = emptyList()
selectedPlaylist = null
```

```kotlin
// Correct: treat config changes as a state boundary and guard stale async writes.
resetMusicStateAfterConfigChange()
val requestVersion = musicConfigStateVersion
refreshMusicData(savedConfig, requestVersion)
```

```kotlin
// Wrong: detail back collapses every drill-in path to Home.
fun navigateBackFromMusicPage() {
    selectedTab = 0
    libraryPage = MusicLibraryPage.Home
}
```

```kotlin
// Correct: visible back and system back share source-aware navigation.
fun navigateBackFromMusicPage() {
    val result = resolveMusicBackNavigation(libraryPage, musicBackStack)
    selectedTab = result.selectedTab
    libraryPage = result.page
    musicBackStack = result.remainingStack
}
```

### Navidrome lyrics parsing

**Scope / Trigger**: Any change to `NavidromeRepository.getLyrics(...)`, lyric DTOs, plain LRC parsing, or `MusicPlayerScreen` lyric rendering.

完整的加载状态、句组规范化、2 秒跟随、暂停浏览、会话视图记忆及测试合同见 [音乐歌词](./music-lyrics.md)。

**Signatures**:
- `suspend fun NavidromeRepository.getLyrics(song: NavidromeSong): MusicLyrics?`
- `NavidromeLyricsList.structuredLyrics: List<NavidromeStructuredLyrics>?`
- `NavidromeStructuredLyrics.line: List<NavidromeStructuredLyricLine>?`
- `NavidromeStructuredLyricLine.value: String?`
- `data class MusicLyrics(val lines: List<MusicLyricsLine>, val synced: Boolean)`
- `data class MusicLyricsLine(val startMillis: Int? = null, val text: String)`

**Contract**:
- Try `getLyricsBySongId.view` first; fall back to `getLyrics.view` only when song-id lookup yields no lyrics and the song has a non-blank artist.
- Structured lyrics take priority over plain `lyrics.value` when they contain non-blank lines. Parse candidates before selecting: prefer actually timed lyrics, not merely the wire `synced` flag. Normalize timestamp order and timed duplicates before presentation; preserve untimed text after the timed prefix.
- OpenSubsonic `structuredLyrics` and nested `line` arrays are optional wire fields. DTOs must model both as nullable, and repository mapping must normalize each level with `orEmpty()`.
- Missing or null `lyricsList.structuredLyrics` means no usable structured lyrics. Missing or null nested structured `line` means that structured entry has no usable lines. In both cases, keep plain lyric fallback available.
- OpenSubsonic structured lyric `line.value` is optional text. DTOs must model it as nullable, and repository mapping must treat missing, null, empty, or whitespace-only values as absent lyric lines.
- A structured lyrics entry with only absent/blank line values is unusable and must not block fallback to plain lyrics.
- OpenSubsonic structured lyrics use millisecond `line.start` values. Preserve those values as milliseconds before applying any offset.
- OpenSubsonic `structuredLyrics.offset` is optional and in milliseconds. Positive means lyrics appear sooner and negative means later, so subtract the signed offset from each structured line start. Clamp adjusted timestamps below zero to `0`.
- Plain LRC timestamp rows such as `[00:10.00]Line` become synced `MusicLyricsLine(startMillis = 10000, text = "Line")`.
- Known LRC metadata-only rows such as `[ar:...]`, `[ti:...]`, `[al:...]`, `[length:...]`, and `[offset:...]` must be skipped instead of displayed in the lyric view.
- Plain LRC `[offset:<signed milliseconds>]` applies globally to timestamped rows. Follow LRC semantics: positive offsets make lyrics appear sooner, so subtract the signed offset from parsed timestamps. Clamp adjusted timestamps below zero to `0`.
- Bracketed plain lyric rows that are not known LRC metadata, such as `[Chorus]` or `[custom:Keep this line]`, remain visible lyric text.
- Empty or whitespace-only parsed rows are ignored; if nothing remains, return `null`.

**Validation & Error Matrix**:
- Song-id lyric lookup throws or returns no usable lyrics -> fallback to artist/title lookup when possible.
- Final applicable lyric lookup fails -> propagate the typed/contextual error to the lyric-only error/retry state, never the playback error state. A successful empty lookup returns `null`; cancellation is rethrown immediately without starting a fallback lookup.
- Missing or null `lyricsList.structuredLyrics` -> no structured lyrics; fall back to plain lyrics when present.
- Missing or null structured `line` -> structured entry is ignored as unusable; fall back to another usable structured entry or plain lyrics.
- Missing, null, empty, or blank structured `line.value` -> skip that line and keep other usable lines from the same structured entry.
- Structured lyrics contain no usable `line.value` text -> fall back to plain lyrics when present.
- Structured line start `2000` with no offset -> `startMillis = 2000`.
- Structured line start `120` with song duration `300` -> `startMillis = 120`; do not infer seconds from values below the song duration.
- Structured offset `250` with line start `2000` -> `startMillis = 1750`.
- Structured offset `-250` with line start `2000` -> `startMillis = 2250`.
- Structured positive offset pushes an adjusted timestamp below zero -> clamp to `0`.
- Metadata-only LRC rows -> omit from `MusicLyrics.lines`.
- `[offset:+500]` with `[00:10.00]Line` -> `startMillis = 9500`.
- `[offset:-500]` with `[00:10.00]Line` -> `startMillis = 10500`.
- Positive offset pushes an adjusted timestamp below zero -> clamp to `0`.
- Non-metadata bracketed plain rows -> keep as unsynced text.

**Good/Base/Bad Cases**:
- Good: LRC with metadata and two timed rows displays only the two lyric rows, synced to timestamps.
- Good: Structured lyrics preserve OpenSubsonic millisecond starts and apply the structured offset without showing any metadata.
- Good: Missing or null structured lyric arrays do not crash lyric loading and still allow plain fallback.
- Good: Structured entries with missing or null `line` arrays are skipped as unusable while other usable lyrics can still be selected.
- Good: Structured lines with missing, null, empty, or blank `value` are skipped while valid structured lines from the same entry remain visible.
- Good: LRC offset metadata shifts all timed rows by the file's intended global adjustment without showing the offset row.
- Base: Plain unsynced lyrics display in source order.
- Bad: Optional structured lyric arrays are modeled as non-null DTO lists and accessed directly.
- Bad: Structured offset is ignored, leaving all synced lines consistently early or late.
- Bad: `NavidromeStructuredLyricLine.value` is modeled as a non-null string and accessed directly, allowing explicit null line text to crash lyric parsing.
- Bad: Structured `line.start = 2000` is treated as seconds and becomes `2000000`.
- Bad: Structured `line.start = 120` is treated as seconds because it is smaller than the song duration.
- Bad: `[ar:Artist]` or `[offset:+500]` appears as a lyric line in `MusicPlayerScreen`.
- Bad: `[offset:+500]` is added to `startMillis`, making lyrics appear later instead of sooner.
- Bad: `[Chorus]` is dropped just because it uses brackets.

**Tests Required**:
- Repository unit tests asserting missing and null `lyricsList.structuredLyrics` arrays fall back to plain lyrics.
- Repository unit test asserting missing and null structured `line` arrays are ignored as unusable structured lyrics and fall back to plain lyrics.
- Repository unit test asserting missing, null, empty, and blank structured `line.value` fields are skipped without dropping valid structured lines.
- Repository unit test asserting structured entries with no usable line values fall back to plain lyrics.
- Repository unit test with `MockWebServer` asserting known LRC metadata rows are skipped while timed lyric rows keep expected `startMillis`.
- Repository unit tests asserting structured lyric millisecond starts are preserved with no offset, including small values below the song duration; positive structured offsets make lines earlier, negative structured offsets make lines later, and adjusted timestamps below zero clamp to `0`.
- Repository unit tests asserting positive offset makes lines earlier, negative offset makes lines later, and adjusted timestamps below zero clamp to `0`.
- Repository unit test asserting non-metadata bracketed plain rows remain visible and unsynced.

**Wrong vs Correct**:
```kotlin
// Wrong: every non-timestamp bracket row is treated as lyric text.
if (matches.isEmpty()) listOf(MusicLyricsLine(text = text))
```

```kotlin
// Correct: skip only known metadata rows and preserve other bracketed lyric text.
if (matches.isEmpty() && rawLine.isLrcMetadataLine()) emptyList()
```

```kotlin
// Wrong: optional structured arrays are accessed as if Gson always applies defaults.
val structured = lyricsList?.structuredLyrics
    ?.filter { lyrics -> lyrics.line.any { it.value.isNotBlank() } }

// Correct: nullable wire arrays are normalized before filtering and mapping.
val structured = lyricsList?.structuredLyrics
    .orEmpty()
    .filter { lyrics -> lyrics.line.orEmpty().any { it.value.orEmpty().isNotBlank() } }
```

### Music playback queue management

**Scope / Trigger**: Any change to the music queue sheet, `MusicPlaybackEngine`, `MusicPlaybackState.queue`, `MusicPlaybackState.queueIndex`, or Media3 music playlist mutations.

**Signatures**:
- `data class MusicPlaybackState(..., val queue: List<NavidromeSong>, val queueIndex: Int)`
- `data class MusicPlaybackState(..., val shuffleModeEnabled: Boolean)`
- `fun MusicPlaybackEngine.playQueue(songs: List<NavidromeSong>, startIndex: Int = 0, allowUnplayableStartFallback: Boolean = true)`
- `internal fun resolvePlayableMusicQueue(songs: List<NavidromeSong>, startIndex: Int, allowUnplayableStartFallback: Boolean = true): PlayableMusicQueue?`
- `fun MusicPlaybackEngine.seekToQueueIndex(index: Int)`
- `fun MusicPlaybackEngine.moveQueueItemToPlayNext(index: Int)`
- `fun MusicPlaybackEngine.removeQueueItem(index: Int)`
- `fun MusicPlaybackEngine.clearUpcomingQueueItems()`
- `fun MusicPlaybackEngine.toggleShuffleMode()`
- `internal fun resolvePlayNextTargetIndex(index: Int, currentIndex: Int, itemCount: Int): Int?`
- `internal fun shouldReplaceCurrentMusicItem(currentMediaId: String?, currentStreamUrl: String?, requestedSong: NavidromeSong): Boolean`

**Contract**:
- `MusicPlaybackEngine` owns all queue mutations. Compose UI passes commands and renders `MusicPlaybackState`; it must not maintain or reorder a shadow queue.
- `MusicPlaybackEngine.playQueue(...)` must filter out songs whose `streamUrl` is null or blank before creating Media3 media items. Media3 should not receive empty-URI queue entries.
- When filtering a queue, preserve the requested start song if it is playable. If it is not playable and `allowUnplayableStartFallback = true`, map the start to the next playable song after the requested position, or the nearest earlier playable song when no later playable song exists.
- Direct song row/card selection must call `playQueue(..., allowUnplayableStartFallback = false)` through the Music screen callback. In this mode, an unplayable requested start returns no queue even when later songs are playable, and `playQueue(...)` publishes `"这首歌缺少播放地址"` instead of silently starting another track.
- If a requested queue has no playable songs, clear pending queue requests, keep existing playback state intact, and publish a contextual error instead of replacing Media3 items.
- After any Media3 playlist mutation, invalidate cached queue state (`cachedTimelineGeneration = -1`) before publishing state so `queue` and `queueIndex` cannot stay stale.
- Queue commands must guard invalid indexes. Invalid/current/already-next "play next" requests are no-ops.
- Removing the only queued item stops playback and clears state.
- `clearUpcomingQueueItems()` preserves the current item and removes only items after `queueIndex`.
- Queue UI keys must tolerate duplicate songs in the queue; do not key rows by `song.id` alone.
- If the Media3 controller is not connected yet, mutate `pendingQueue` and `_state` consistently so the eventual controller start uses the same queue order/index shown in UI.
- Pending-queue current-index recalculation must be position-based, not `song.id` based. Queues may contain the same song multiple times, so using `indexOfFirst { it.id == currentSong.id }` can highlight or start the wrong duplicate after a pending reorder.
- Shuffle mode is player state, not a locally shuffled list. `MusicPlaybackEngine.toggleShuffleMode()` must delegate to Media3 `shuffleModeEnabled`, publish `MusicPlaybackState.shuffleModeEnabled`, and listen for `onShuffleModeEnabledChanged`.
- Direct `MusicPlaybackEngine.play(song)` calls must replace the Media3 item when either the current media id differs or the current item URI differs from `song.streamUrl.orEmpty()`. Navidrome stream URLs may contain regenerated auth parameters, so same-id/different-stream requests must refresh Media3 instead of keeping a stale URL. Same-id/same-stream requests should keep the existing item and just clear transient errors.

**Validation & Error Matrix**:
- Index outside `0 until mediaItemCount` -> no-op.
- Current index outside queue bounds -> no-op for queue reordering/clearing.
- "Play next" on the current item or the item already immediately after current -> no-op.
- Queue contains songs with missing/blank `streamUrl` -> filter them before Media3 playlist creation.
- Direct selection requested start has missing/blank `streamUrl` and fallback disabled -> do not replace Media3 items; publish `"这首歌缺少播放地址"`.
- Queue contains no playable songs -> do not replace the active Media3 playlist; publish an error.
- Remove single-item queue -> call `stop()` and clear `MusicPlaybackState`.
- Duplicate song ids in queue -> use position-aware UI keys such as `"$id:$index"`.
- Duplicate song ids in a pending queue -> preserve the current position by index math after moves/removals; do not search by song id.
- Shuffle toggle -> update Media3 `shuffleModeEnabled`; UI should render active state from `MusicPlaybackState.shuffleModeEnabled`.
- Same current media id and same stream URL -> do not replace the Media3 item.
- Same current media id and different stream URL -> replace the Media3 item and prepare it.
- Different media id -> replace the Media3 item regardless of stream URL.

**Good/Base/Bad Cases**:
- Good: Queue sheet actions call `MusicPlaybackEngine`, engine mutates Media3, then publishes fresh `queue` and `queueIndex`.
- Good: Play-all queue contains unavailable tracks; playback engine queues only playable songs and maps the current index to the intended playable start.
- Good: Direct row/card click on an unavailable song passes fallback disabled and surfaces the missing-stream error without starting a different song.
- Good: Replaying a refreshed Navidrome song with the same id but a regenerated stream URL replaces Media3 so playback uses the fresh URL.
- Base: Tapping a queue row seeks with `seekToQueueIndex(index)` and closes the sheet.
- Bad: `playQueue(...)` maps every `NavidromeSong` to a Media3 item even when `streamUrl` is blank; continuous playback can fail when the player reaches that entry.
- Bad: Direct row/card click uses fallback-enabled queue resolution and starts the next playable song after the unavailable row.
- Bad: UI removes a row from a local list while Media3 keeps the original playlist, causing the highlighted item and actual playback item to diverge.
- Bad: Pending queue reordering finds the current item by `song.id`, causing duplicate queued songs to shift the current index to the first matching duplicate.
- Bad: UI shuffles its own list while Media3 keeps the original timeline, causing next/previous and queue sheet state to disagree.
- Bad: Direct play compares only `currentMediaItem.mediaId` and keeps an expired same-song stream URL.

**Tests Required**:
- Unit tests for pure queue index rules, especially future item, previous item, current item, already-next item, and invalid indexes.
- Unit tests for playable queue resolution: filtering blank stream URLs, preserving a playable requested start, mapping an unplayable start to next/previous playable entries, returning null for fallback-disabled unplayable starts, and all-unplayable queues.
- Unit tests for pending current-index recalculation when items before/after the current item move.
- Unit tests for current-item replacement decisions: same id/same stream does not replace, same id/different stream replaces, and different id replaces.
- Compile checks for Media3 API usage and callback wiring.
- Unit tests or focused helper tests when adding non-trivial queue ordering logic.

**Wrong vs Correct**:
```kotlin
// Wrong: local UI state diverges from Media3 playback state.
visibleQueue = visibleQueue.toMutableList().also { it.removeAt(index) }
```

```kotlin
// Wrong: duplicate song ids can point at the wrong queued instance.
val nextIndex = nextQueue.indexOfFirst { song -> song.id == currentSong?.id }
```

```kotlin
// Correct: command goes through playback layer and UI observes published state.
onRemoveFromQueue = playbackEngine::removeQueueItem
```

```kotlin
// Wrong: id-only comparison can keep an expired Navidrome stream URL.
if (controller.currentMediaItem?.mediaId != song.id) {
    controller.setMediaItem(song.toMediaItem())
}
```

```kotlin
// Correct: compare both identity and the stream URI Media3 will play.
val currentItem = controller.currentMediaItem
if (
    shouldReplaceCurrentMusicItem(
        currentMediaId = currentItem?.mediaId,
        currentStreamUrl = currentItem?.localConfiguration?.uri?.toString(),
        requestedSong = song
    )
) {
    controller.setMediaItem(song.toMediaItem())
}
```

### Music play-all start index

**Scope / Trigger**: Any change to Music screen bulk playback entry points, including album quick-play, album detail play-all, artist detail play-all, or playlist detail play-all.

**Signature**:
- `internal fun firstPlayableSongIndex(songs: List<NavidromeSong>): Int?`
- `onSongSelected: (List<NavidromeSong>, Int, Boolean) -> Unit`

**Contract**:
- Bulk playback must start at the first song whose `streamUrl` is not null or blank.
- If no songs in the list are playable, show a contextual UI error and do not call `onSongSelected`.
- Bulk play-all entry points must call `onSongSelected(songs, firstPlayableIndex, BULK_PLAY_ALLOW_UNPLAYABLE_START_FALLBACK)`.
- Do not change single song-row click behavior; a direct click calls `onSongSelected(songs, clickedIndex, DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK)` so the playback engine can surface the specific song error.

**Good/Base/Bad Cases**:
- Good: Album or playlist starts at track 2 when track 1 has no stream URL and track 2 is playable.
- Good: Tapping the unplayable track 1 directly surfaces `"这首歌缺少播放地址"` instead of starting track 2.
- Base: All tracks are playable, so play-all starts at index `0`.
- Bad: Play-all blindly calls `onSongSelected(songs, 0)` and fails before reaching playable tracks later in the list.
- Bad: Direct row clicks reuse play-all's first-playable index and ignore the clicked row.

**Tests Required**:
- Unit tests for `firstPlayableSongIndex(...)` covering playable first match, null/blank stream URLs, and no playable songs.

### Navidrome song favorite (star) optimistic update

**Scope / Trigger**: Any change to the favorite/heart toggle on `MusicPlayerScreen`, `MusicPlaybackViewModel.toggleFavorite`, `MusicPlaybackEngine.setCurrentSongStarred`, or the `NavidromeSong.starred` field semantics.

**Signatures**:
- `data class NavidromeSong(..., val starred: String? = null)`
- `fun MusicPlaybackEngine.setCurrentSongStarred(starred: Boolean)`
- `fun MusicPlaybackViewModel.toggleFavorite(songId: String, starred: Boolean)`
- `onToggleFavorite: (songId: String, starred: Boolean) -> Unit` (player screen callback)
- Repository: `suspend fun NavidromeRepository.star(id: String)` / `unstar(id: String)` (already exist)

**Contract**:
- `NavidromeSong` is reused directly as the Subsonic wire DTO, so `starred` binds automatically from the Subsonic `starred` attribute via Gson (a non-null timestamp string == favorited; absent/null == not favorited). No manual DTO mapping is needed.
- The player ♥ display is derived from `song.starred != null`, read straight from the published playback state's `currentSong` — no separate UI-side favorite state. The VM owns optimistic updates, so reading the published song covers both initial state and revert-after-failure.
- The toggle flow is: UI calls `onToggleFavorite(songId, desiredStarred)` → VM optimistically calls `engine.setCurrentSongStarred(desiredStarred)` → VM calls `repo.star(id)` / `unstar(id)` → on failure VM calls `engine.setCurrentSongStarred(!desiredStarred)` to revert.
- `setCurrentSongStarred(starred)` must write `currentSong.starred = if (starred) "" else null` so the non-null/empty-string value keeps `starred != null` true for the favorited state, while `null` correctly signals not-favorited. Do NOT use a boolean field — the DTO semantics are timestamp-or-null.
- The UI must key any local optimistic override on `song?.id` so switching tracks resets displayed state to the new song's `starred`.
- Star/unstar failures must not crash; the revert path restores the ♥ to its previous value. The repository already wraps `"收藏失败: ..."` / `"取消收藏失败: ..."` contextual messages.
- When the revert path fires, the VM must also emit a one-shot error event (`SharedFlow<Unit>`, `replay = 0`) so the UI can show a transient pill notification (e.g. "收藏操作失败，已恢复") explaining why the ♥ "jumped back". Without this, the silent revert looks like a bug to the user. The pill auto-hides after 2s and sits in screen space (outside any swipe-to-dismiss transformed content) so it does not tilt with the gesture.
- `starred` is a persisted cache field (cached songs carry it). Bump `MUSIC_CACHE_SCHEMA_VERSION` when the cache model's `starred` semantics change; adding the field as nullable-with-default is additive and still requires a bump so old caches rehydrate the field from the server rather than serving stale unstarred entries.

**Validation & Error Matrix**:
- `song.starred` is a non-null timestamp → ♥ shows favorited.
- `song.starred` is null → ♥ shows not favorited.
- User taps ♥ while favorited → VM calls `setCurrentSongStarred(false)` then `unstar`; on failure, reverts to `setCurrentSongStarred(true)`.
- User taps ♥ while not favorited → VM calls `setCurrentSongStarred(true)` then `star`; on failure, reverts to `setCurrentSongStarred(false)`.
- Repository null (config not ready) → VM reverts optimistic state, emits a one-shot error event, and returns; no crash.
- Song switches mid-flight → the new `currentSong` carries its own `starred`; the UI reads it directly so no stale per-song override lingers.
- Star/unstar failure → VM reverts the optimistic state AND emits a one-shot error event so the UI can show a transient pill notification explaining the revert. The event is a `SharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)` — `replay = 0` ensures missed emits (no collector) are dropped, which is the intended UX (no stale error from an old screen). The UI collects via `LaunchedEffect(flow) { flow.collect { ... } }` and auto-hides the pill after a short delay (2s).

**Good/Base/Bad Cases**:
- Good: User taps ♥, UI flips immediately, server call succeeds; state stays.
- Good: User taps ♥, server call fails, ♥ flips back automatically via the revert path.
- Base: First song load with `starred = "2026-08-06T..."` shows ♥ favorited immediately.
- Bad: UI keeps a separate `mutableStateOf<Boolean>` for favorite not keyed on `song?.id`, so switching to a different song shows the previous song's favorite state.
- Bad: `setCurrentSongStarred` writes `starred = null` for the favorited case, making `starred != null` always false and the ♥ never shows favorited.
- Bad: VM calls `star`/`unstar` without the optimistic update, so the ♥ only flips after the network round-trip (feels broken on slow networks).

**Tests Required**:
- Unit test for `setCurrentSongStarred(true)` asserting `currentSong.starred != null` (favorited); `setCurrentSongStarred(false)` asserting `currentSong.starred == null` (not favorited).
- VM test asserting `toggleFavorite(starred=true)` calls `engine.setCurrentSongStarred(true)` then `repo.star`, and reverts on `repo.star` failure.
- VM test asserting `toggleFavorite` with a null repository reverts the optimistic state, emits the one-shot error event, and does not crash.
- VM test asserting `toggleFavorite` on `repo.star`/`unstar` failure reverts the optimistic state AND emits the one-shot error event.

**Wrong vs Correct**:
```kotlin
// Wrong: separate UI state not keyed on song id — stale across track switches.
var isFavorite by remember { mutableStateOf(song?.starred != null) }
```

```kotlin
// Correct: read straight from the published currentSong; the VM owns the optimistic write.
private fun resolveFavoriteDisplay(song: NavidromeSong?): Boolean = song?.starred != null
```

```kotlin
// Wrong: optimistic update writes null for the favorited case.
fun setCurrentSongStarred(starred: Boolean) {
    _state.update { it.copy(currentSong = it.currentSong?.copy(starred = if (starred) null else null)) }
}
```

```kotlin
// Correct: favorited writes a non-null value (empty string) so starred != null stays true.
fun setCurrentSongStarred(starred: Boolean) {
    _state.update { it.copy(currentSong = it.currentSong?.copy(starred = if (starred) "" else null)) }
}
```

### Navidrome repository bounded-concurrency sync pattern

**Scope / Trigger**: Any change to `NavidromeRepository` per-item server-call loops (e.g. `getSongsFromAlbums` backing `getAllSongs` / `getRecentlyAddedSongs` / `getRecentSongsFromAlbums`), or any new repository method that expands a list by issuing one server request per element.

**Signatures**:
- `private const val ALBUM_DETAIL_CONCURRENCY = 6`
- `private suspend fun NavidromeRepository.getSongsFromAlbums(albums: List<NavidromeAlbum>, limit: Int? = RECENT_SONG_LIMIT): List<NavidromeSong>`
- `suspend fun loadNavidromeMusicRefresh(...)` — wraps the 4 source fetches in `coroutineScope { async { ... } }`

**Contract**:
- When a repository method must issue one server request per element of a list (e.g. `getAlbum` per album to expand tracks), use `coroutineScope { albums.map { async { semaphore.withPermit { requestSubsonic { ... } } } }.awaitAll().flatten() }` with a bounded `Semaphore(<N>)`. `ALBUM_DETAIL_CONCURRENCY = 6` is the established cap for album-detail expansion; reuse it for sibling album-detail paths and introduce a sibling constant only if a different server endpoint family needs a different bound.
- Do NOT replace sequential per-item server calls with unbounded `async`/`launch` — that risks swamping the server, OkHttp Dispatcher, or device radios. The `Semaphore` bound is required.
- `awaitAll()` returns results in the same order as the original `async` list, so `.flatten()` preserves the caller-visible album iteration order. Do NOT shuffle, `.groupBy`, or otherwise reorder after `.flatten()` — the Songs navigation "歌曲" page order depends on `getAlbumList2(type = "alphabeticalByName")` order being preserved through expansion.
- Exception propagation: a failing `requestSubsonic { api.getAlbum(...) }` inside `async` surfaces at `awaitAll()`. Do NOT wrap the inner call in `runCatching` — let the typed `NavidromeApiException` propagate naturally so the surrounding public method's `catch (e: NavidromeApiException) { throw e }` block still rethrows it unchanged.
- Independent top-level fetches in `loadNavidromeMusicRefresh` may run concurrently via `coroutineScope { async { ... } }` when they don't share data dependencies. `getRecentAlbums()` must run first (sequentially) because `getRecentlyAddedSongs(freshAlbums)` consumes its result; the other three (`getRecentlyAddedSongs`, `getAllSongs`, `getArtists`) are independent and may be parallelized.
- When a method applies a `limit` (e.g. `getRecentlyAddedSongs(..., limit = RECENT_SONG_LIMIT)`), parallelizing removes the per-item early-break optimization — all items are now fetched in bounded batches and `take(limit)` is applied after `.flatten()`. The result set is identical to the previous sequential early-break path BECAUSE `awaitAll()` preserves list order; do NOT treat "fetches everything then trims" as a behavior regression, but DO note it when reviewing wall-clock expectations for small `limit` values on slow networks.

**Validation & Error Matrix**:
- One album's `getAlbum` HTTP/Subsonic/API error → `awaitAll()` rethrows the `NavidromeApiException` → public caller's `catch (e: NavidromeApiException) { throw e }` surfaces it unchanged.
- Empty album list input → early `return emptyList()` before constructing any `async` — no coroutine allocation, no server load.
- All album details succeed but return no songs → result is an empty flattened list (not an error).
- Limit applied → `.take(limit)` after `.flatten()` returns the same first-N set the sequential early-break path would have returned.

**Good/Base/Bad Cases**:
- Good: 20-album recent-song expansion completes in ⌈20/6⌉ = 4 concurrent batches instead of 20 sequential round trips; album order preserved; first-N limit deterministic.
- Good: One album 503s mid-batch → the typed `NavidromeApiException(Kind.HTTP, ...)` propagates unchanged through `awaitAll()`.
- Base: Single album → one `async` + `awaitAll()` is equivalent to a direct call; no concurrency cost.
- Bad: Unbounded `albums.map { async { requestSubsonic { ... } } }` → 100 simultaneous `getAlbum` requests on a large library, swamping the server and the OkHttp dispatch queue.
- Bad: Reordering results after `.flatten()` (e.g. by song id) → Songs navigation shows a different order than the server's alphabeticalByName album order.
- Bad: Wrapping the inner `requestSubsonic` in `runCatching { ... }.getOrNull()` → a failed `getAlbum` silently produces an empty list, hiding a server error from the user.

**Tests Required**:
- Repository test asserting the concurrent expansion requests the expected number of `getAlbum.view` calls (one per album) and returns results in album order after `.flatten()`. Use `runTest` and the existing fake `NavidromeApi` or `MockWebServer` patterns.
- Repository test asserting `getSongsFromAlbums(emptyList())` short-circuits without issuing any `getAlbum` request.
- When parallelizing a method with a `limit`, add a test asserting the first-N result is identical to the sequential early-break path for a known fixture (i.e. `awaitAll().flatten().take(limit)` matches the documented album-iteration order).

**Wrong vs Correct**:
```kotlin
// Wrong: unbounded async swamps the server and OkHttp dispatch queue.
val songs = coroutineScope {
    albums.map { async { requestSubsonic { api.getAlbum(...) } } }.awaitAll().flatten()
}
```

```kotlin
// Correct: Semaphore bounds concurrency to ALBUM_DETAIL_CONCURRENCY; awaitAll preserves order.
val semaphore = Semaphore(ALBUM_DETAIL_CONCURRENCY)
val songs = coroutineScope {
    albums.map { album ->
        async {
            semaphore.withPermit {
                requestSubsonic { api.getAlbum(config.username, auth.token, auth.salt, albumId = album.id) }
            }
        }
    }.awaitAll().flatten()
}
```

```kotlin
// Wrong: runCatching swallows typed errors and silently produces an empty list on failure.
async { runCatching { requestSubsonic { api.getAlbum(...) } }.getOrNull() }
```

```kotlin
// Correct: let NavidromeApiException propagate through awaitAll to the public method's catch block.
async { requestSubsonic { api.getAlbum(...) } }
```

### Cross-media playback handoff

#### 1. Scope / Trigger

Apply this contract when `MainActivity` or playback ViewModels switch between Music, Audiobook, and Video, or when manual player close can overlap an in-progress media switch.

#### 2. Signatures

- `internal enum class MediaPlaybackKind`
- `internal fun resolveMediaHandoffCloseSteps(...)`
- `internal fun runMediaHandoffCloseSteps(...)`
- Playback close callbacks use `onClosed: () -> Unit` and `onFailed: (String) -> Unit` semantics.

#### 3. Contracts

- Cross-media playback is strict and single-owner: close every active non-target medium sequentially, then start or reveal the target medium.
- A close failure terminates the handoff. Do not start the target medium, and preserve or restore the player that failed to close so its error remains actionable.
- Successful earlier close steps stay closed if a later step fails; do not reopen already-closed background players.
- Manual player close and cross-media handoff share one mutual-exclusion gate. A second close/switch request while a handoff is active is ignored rather than launching competing asynchronous work.
- Every asynchronous close step and the overall handoff consume a terminal callback only once. Duplicate success/failure callbacks must not start the target twice or run both terminal branches.
- Replaying the current video from the beginning still closes the active video session first so stopped progress reporting and local engine cleanup finish before `playFromStart(...)`.

#### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| No non-target medium is active | Start/reveal target immediately |
| All required closes succeed | Start/reveal target exactly once |
| Any close fails | Stop remaining steps; do not start target; keep failed player visible with error |
| Close callback fires twice | Consume the first terminal callback only |
| User closes a player during handoff | Ignore the competing request until the gate is released |
| Current video requests play-from-start | Close current session, then start from zero |

#### 5. Good/Base/Bad Cases

- Good: Audiobook closes successfully, video closes successfully, then Music starts.
- Base: Music is already the only active medium; selecting another Music item updates its queue without unnecessary close work.
- Bad: Stop Music immediately, start Video, and asynchronously close Audiobook afterward; a close failure leaves two media states competing.
- Bad: Reopen every previously closed player when the last close step fails.

#### 6. Tests Required

- Pure helper tests for required close-step order for every source/target combination.
- A failure-path test asserting later close steps and target start are not invoked.
- Duplicate-callback tests asserting terminal success/failure is consumed once.
- A current-video play-from-start test asserting Video is included in the close steps.
- Compile, unit test, and lint gates after changing handoff or close callback wiring.

#### 7. Wrong vs Correct

```kotlin
// Wrong: starts the target before asynchronous cleanup succeeds.
musicVM.stop()
videoVM.play(video)
audiobookVM.closeAudiobookPlayback(onClosed = {}, onFailed = {})
```

```kotlin
// Correct: target start is the success continuation of the serialized close chain.
runMediaHandoff(
    target = MediaPlaybackKind.Video,
    onReady = { videoVM.play(video) }
)
```

---

## Testing Requirements

Run the smallest reliable Gradle gate for the change. Gradle daemon is enabled (no `--no-daemon`), and tasks are combined into a single invocation so consecutive runs reuse a warm JVM and plugins. For the full two-tier verification model (fast = compile + test, full = + lint), see `index.md` → Verification.

- Kotlin compile check for app code changes:
  `.\gradlew.bat :app:compileDebugKotlin`
- Unit tests for repository, auth, playback, or copy-encoding behavior:
  `.\gradlew.bat :app:testDebugUnitTest`
- Android lint before broader UI/resource/dependency changes:
  `.\gradlew.bat :app:lintDebug`
- Debug assemble for final packaging verification when Media3 service, manifest, resources, or dependency wiring changes:
  `.\gradlew.bat :app:assembleDebug`

Repository tests use `MockWebServer` to assert request paths, query parameters, auth headers, response mapping, and typed error behavior. Existing examples:
- `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`

Playback logic tests should isolate pure calculations where possible, as in `app/src/test/java/com/nordic/mediahub/playback/AudiobookPlaybackEngineTest.kt`.

---

## Code Review Checklist

- [ ] All composables with page-level state have `BackHandler` matching their manual back button logic
- [ ] `showConfig` handlers are declared after page-level handlers (last-registered-wins)
- [ ] No string-based error type checks (use typed exceptions)
- [ ] No duplicate utility functions across files
- [ ] Shared Compose primitives use `internal` visibility
- [ ] Top-level media browse screens use shared page-shell headers and do not reintroduce per-media config gear/actions/forms
- [ ] Server editing and connection tests live in the bottom-nav `配置` tab / `ServerConfigScreen`
- [ ] Repeated loading/error/empty state cards use shared media state components
- [ ] Hidden config/search/filter state cannot consume Back while a detail/player surface is visible
- [ ] Persistent player/navigation chrome is hidden, collapsed, or explicitly justified when static, with performance and content readability prioritized
- [ ] `NavidromeRepository` is `remember`ed, not constructed per call
- [ ] Config readiness checks use `isReadyForMusicSync()`, not inlined
- [ ] All public `NavidromeRepository` methods have both `NavidromeApiException` and `Exception` catch blocks
- [ ] Music "歌曲" navigation uses `NavidromeRepository.getAllSongs()`, not the recently added preview list

---

## Common Mistakes

### Running Gradle verification tasks in parallel on Windows

**Don't**: Run `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, or `:app:assembleDebug` concurrently against the same checkout on Windows.

**Why**: These tasks share `app/build/` outputs. Parallel runs can lock class files and produce misleading incremental compilation errors such as `AccessDeniedException` or broad unresolved-reference cascades.

**Do**: Run Gradle verification as a single daemon-backed invocation so tasks run sequentially within one warm JVM (no `--no-daemon`, no parallel processes):

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

### Rewriting UTF-8 Kotlin files with PowerShell `Set-Content`

**Don't**: Use `Set-Content` or shell range rewrites for whole Kotlin source files unless you explicitly preserve UTF-8 encoding.

**Why**: Older Windows PowerShell defaults can rewrite UTF-8 files as UTF-16 LE with a BOM. Git then treats the file as binary, and non-ASCII UI copy can appear corrupted in diffs.

**Do**: Prefer `apply_patch` for source edits. If a whole-file rewrite is unavoidable, write with an explicit UTF-8 encoding and verify the file still starts with ASCII bytes such as `70 61 63 6B` for `package`.

### CRLF-sensitive scripted Kotlin edits

- 用脚本做精确文本替换前，先在内存中把 CRLF 规范化为 LF；不能假定 checkout 文件与本轮新文件使用相同换行。
- 每个结构性替换必须断言锚点存在且替换次数符合预期；写文件成功不代表接口声明已更新。
- 增加参数时同步核对声明和所有调用方；`HeaderAction` 的第三项为 enabled，回调使用命名参数 `onClick = ...`，不要凭印象传位置参数。
- 用 UTF-8 无 BOM 写回；保存后立即搜索新增符号的声明与调用，再运行编译，不能只重复修复报错调用点。

### Mistaking PowerShell display mojibake for source corruption

**Don't**: Treat garbled Chinese shown by PowerShell `Get-Content` as proof that the source file is corrupted.

**Why**: The console code page can render valid UTF-8 text as mojibake while the file bytes, `rg` output, `git diff`, and tests still see the correct Chinese. Fixing a false-positive "encoding bug" risks rewriting good files and introducing real corruption.

**Do**: Confirm with `rg "<expected Chinese text>" <file>`, `git diff`, or an existing encoding guard such as `UiCopyEncodingTest` before editing. Only repair encoding when the source bytes or tests prove the file itself contains mojibake markers.

### Asserting position-indexed results against concurrent repository fetches in MockWebServer tests

**Don't**: Assert `listOf(...) == songs.map { ... }` (or `songs[0].id == ...`, `songs[1].coverArt == ...`) in a MockWebServer test that exercises a `coroutineScope { async { ... }.awaitAll() }` code path where the concurrent requests receive **distinct, per-id fixture bodies**.

**Why**: MockWebServer serves enqueued responses in FIFO order, but `async` blocks dispatch concurrent requests whose completion order is nondeterministic. The request for album-1 may land at the server after the request for album-2, so the server attaches album-1's request to the first enqueued fixture (which was built for album-2). The test then intermittently fails because `songs[0]` is album-2's track instead of album-1's, depending on dispatcher timing.

**Do**: When a test exercises a parallelized repository path against MockWebServer, use ONE of:
- Enqueue **identical response bodies** for every concurrent request and assert only aggregate/order-independent facts (total count, per-song id presence via `.first { it.id == "..." }`, stream/cover URL mapping, request count).
- Or assert by **identity lookup**, never by list position: `val song1 = songs.first { it.id == "song-1" }; assertEquals(..., song1.streamUrl)`.
- Or use a single non-concurrent path for the order-sensitive assertion and a separate concurrent-path test with identical fixtures for the parallelism assertion.

**Detection signal**: if a test that was green before parallelization becomes flaky (passes 10× then fails once) after the production code changed from a `for (item in list)` sequential loop to `list.map { async { } }.awaitAll()`, this is almost certainly the FIFO-vs-completion-order mismatch. Fix the test assertions, do not revert the parallelization.

### Mixing Compose library versions that the BOM did not certify together

**Don't**: Assume any `material3` version pairs safely with the resolved `animation-core`/`foundation` versions. Compose libraries call each other across module boundaries with exact JVM descriptors, and covariant return-type changes (e.g. `KeyframesSpecConfig.at(...)` returning `KeyframesSpec$KeyframeEntity` in animation-core 1.7+ vs erased `KeyframeBaseEntity` in 1.6.x) are binary-incompatible in both directions even when source code compiles cleanly.

**Why**: A source-level dependency graph that compiles fine can still crash at runtime with `NoSuchMethodError` inside Material internals (e.g. `LinearProgressIndicator` → keyframes builder) the first time a code path renders. The Video tab media-library chip tap triggered `MediaLoadingCard`, so a "dependency problem" surfaced as "tapping a library chip crashes the app". Rebuilding/reinstalling does NOT fix it — the broken pair ships in every APK until the version pair changes.

**Do**:
- When overriding a BOM-provided version (e.g. pinning `material3:1.2.1` over BOM 2024.01.00's resolved 1.1.2), verify the pinned version was *compiled against* the compose version actually resolved in the graph (material3 1.2.x ↔ compose 1.6.x ✔; material3 1.1.2 expects animation-core 1.7+ covariant signatures ✘).
- Confirm with `javap -c` on the library's classes: the caller's `invokevirtual` descriptor must exist verbatim in the resolved dependency. Example evidence: m3 1.1.2 calls `at:(Ljava/lang/Object;I)Landroidx/compose/animation/core/KeyframesSpec$KeyframeEntity;` while animation-core 1.6.0 only declares `at:(Ljava/lang/Object;I)Landroidx/compose/animation/core/KeyframeBaseEntity;`.
- When a runtime `NoSuchMethodError`/`NoClassDefFoundError` points at androidx.compose internals, check version pairing FIRST before suspecting app code.
- Beware a corrupted local Gradle metadata cache constraining a BOM to a version the real BOM pom does not map (symptom: `dependencies` reports a pair known to be incompatible). Pin the needed version explicitly and move on; report the anomaly.

### MockWebServer：带请求体的取消测试

- `MockResponse.throttleBody` 会影响请求体接收和响应体发送。测试 PROPFIND 等带 body 的请求时，不能用每周期 1 字节节流再立即等待 takeRequest；服务器尚未收完请求，这并不证明客户端取消失效。
- 验证“收到响应头后取消响应体读取”应使用 `setBodyDelay`，在 IO 调度器等待 RecordedRequest，再取消协程并断言及时结束。测试客户端显式不走代理，验证 root URL 等于 MockWebServer URL。
- 纯 JVM 测试不直接构建依赖 Android TextUtils 的 Media3 Format；使用平台中立轨道快照覆盖映射和选择，Android 适配层留给设备验收。不要通过 returnDefaultValues 掩盖 Android stub。