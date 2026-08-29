# Research: Compose UI Performance & Smoothness (Nordic media hub)

- **Query**: Techniques to improve Jetpack Compose fluidity on an Android app using Jetpack Compose + Material3, AGP 8.2, Kotlin 1.9.20, Compose BOM 2024.01.00.
- **Scope**: Mixed (internal code audit + external AndroidX/Compose API research)
- **Date**: 2026-08-29
- **Author**: trellis-research agent

---

## 0. Project version baseline (read this first — several "expected" APIs are NOT available at the pinned versions)

Gradle facts (`app/build.gradle.kts`):
- `composeBom = platform("androidx.compose:compose-bom:2024.01.00")` → maps to **Compose 1.6.0**, **Material3 1.2.0**, **Foundation 1.6.0**.
- Kotlin `1.9.20`, `kotlinCompilerExtensionVersion = "1.5.4"`.
- Coil `io.coil-kt:coil-compose:2.5.0`.
- `androidx.lifecycle:lifecycle-runtime-compose:2.7.0` and `lifecycle-viewmodel-compose:2.7.0` → `collectAsStateWithLifecycle` is available (added in lifecycle 2.6.0).
- `compileSdk/targetSdk = 34`, `minSdk = 26`.

### Version-compatibility matrix (critical caveats)

| API the task asks about | Artifact / version it shipped in | Available in THIS project (BOM 2024.01.00)? |
|---|---|---|
| `Scrollbar` / `rememberScrollbarAdapter` from `androidx.compose.foundation` | **Compose Multiplatform (desktop/skiko) ONLY — NOT on Android.** The `androidx.compose.foundation` `Scrollbar` / `VerticalScrollbar` / `rememberScrollbarAdapter` are part of the Compose Multiplatform desktop source set (`androidx.compose.foundation:foundation` on JVM-desktop). On Android there is **no native scrollbar composable** in the stable foundation library. | **NO** — do not import `androidx.compose.foundation.Scrollbar` on Android. Build a custom thumb or use a 3rd-party Android lib. |
| `rememberOverscrollEffect()` | `androidx.compose.foundation` **1.8.0** | **NO** (project is 1.6.0). But the `OverscrollEffect` type + the `overscrollEffect =` parameter on `verticalScroll`/`horizontalScroll`/`LazyColumn`/`LazyRow` exist since 1.2.0. Default edge-glow overscroll is automatic. |
| `MaterialTheme.motionScheme` / `MotionScheme` | Material3 **1.5.0** (introduced as Expressive in 1.5.0-alpha23/alpha24; stable 1.5.0) | **NO** (project is 1.2.0). The existing `NordicMotion` object is the correct approach for this version. |
| `PullToRefreshBox` / `rememberPullToRefreshState` (`androidx.compose.material3.pulltorefresh`) | Material3 **1.3.0** (package `androidx.compose.material3.pulltorefresh` "Added in 1.3.0") | **NO** (project is 1.2.0). To use it, bump BOM to ≥ `2024.06.00` (Compose 1.6.8 / material3 1.3.0) or use the M2 `androidx.compose.material` `PullRefresh` API (available now). |

> **Headline**: the project is on a 2024-01 BOM. Three of the five APIs the brief names (`foundation` Scrollbar, `motionScheme`, `PullToRefreshBox`) are **newer than the pinned version**. Where relevant, the version-appropriate alternative is given below.

---

## 1. Isolating high-frequency state (`positionMillis` ticking)

### Current project behavior (audit)
- `MusicPlaybackViewModel.kt:36` exposes `val positionMillis: StateFlow<Long>`, sampled every **100 ms** (`POSITION_MILLIS_SAMPLE_INTERVAL_MS = 100L`), `stateIn(viewModelScope, WhileSubscribed(5000), 0L)`.
- `MainActivity.kt:788` collects it at the top: `val currentPositionMillis by musicVM.positionMillis.collectAsStateWithLifecycle()`, then passes `positionMillis = currentPositionMillis` (a `Long`) into `MusicPlayerScreen` (line 799).
- `MusicPlayerScreen.kt` forwards `positionMillis: Long` to `PlayerPrimaryDisplay` (line 449) → `PlayerLyricsDisplay` (line 531). `PlayerLyricsDisplay` recomputes `activeIndex` via `remember(filteredLines, isSynced, positionMillis)` (line 541) and a `LaunchedEffect(activeIndex, ...)` (line 574) triggers `scrollToItem`/`animateScrollToItem` on the lyric `LazyColumn`.

**Consequence**: every 100 ms tick re-runs the read site in `MainActivity`, which re-composes `MusicPlayerScreen` → `PlayerPrimaryDisplay` → `PlayerLyricsDisplay` (and the whole subtree that reads `positionMillis`). This is the exact anti-pattern the brief calls out: *"passing a frequently-changing `Long` param still recomposes the caller."*

### Correct pattern (state hoisting to the lowest common owner + collect inside the leaf)
Two equivalent, version-safe approaches:

**Option A — pass the `StateFlow` (stable reference), collect at the leaf.**
The `StateFlow` object itself never changes identity, so passing it down as a parameter does **not** trigger recomposition. Only the leaf that calls `collectAsStateWithLifecycle()` on it re-composes on each tick.

```kotlin
// leaf that actually needs the live position:
@Composable
private fun PlayerLyricsDisplay(
    positionMillisFlow: StateFlow<Long>,   // stable reference -> no recomposition on emit
    lyrics: MusicLyrics?,
    /* ... */
) {
    val positionMillis by positionMillisFlow.collectAsStateWithLifecycle()
    // derived value: only changes when the active line index crosses a boundary (seconds, not 100ms)
    val activeIndex by remember(filteredLines, isSynced) {
        derivedStateOf { resolveActiveLyricIndex(filteredLines, positionMillis) }
    }
    // ... LazyColumn items read `activeIndex`; only the lines whose `active` flag flips recompose
}
```

**Option B — give the leaf the ViewModel (or a `ViewModelStoreOwner`) and collect there.**
Same effect: `val positionMillis by musicVM.positionMillis.collectAsStateWithLifecycle()` inside `PlayerLyricsDisplay`, not in `MainActivity`.

```kotlin
// Do NOT do this at the top of the tree:
val currentPositionMillis by musicVM.positionMillis.collectAsStateWithLifecycle() // ← re-composes MainActivity every 100ms
// Pass the flow/VM, not the Long.
```

### Supporting techniques
- **`derivedStateOf`** (`androidx.compose.runtime.derivedStateOf`): wraps a calculation that re-runs on every state change but only *emits* a new value when the result differs (like `distinctUntilChanged`). Use it for `activeIndex` (changes every few seconds, far less often than `positionMillis`). Pitfall from Android docs: `derivedStateOf` is **expensive** — only use it when the output changes less often than the input. Non-state captures (e.g., a `threshold: Int` param) must be passed as `remember` keys or they are frozen at first composition.
- **`@Stable` on data classes.** Compose compiler 1.5.4 (Kotlin 1.9.20) does **not** enable strong-skipping/stable-type-inference by default (that landed in compiler 1.5.8+/Kotlin 2.x). So passing `song: NavidromeSong?`, `lyrics: MusicLyrics?`, etc. down the tree can trigger extra recomposition unless those classes are annotated `@Stable` (or are immutable `data class`es Compose can prove stable). Annotate the model types (`NavidromeSong`, `MusicLyrics`, `MusicLyricsLine`) with `@Stable` to keep them out of the recomposition path. (`@Stable` is in `androidx.compose.runtime`.)
- **`remember` for expensive computations.** The `remember(filteredLines, isSynced, positionMillis)` block is correct; just ensure the *key* set does not include the fast-changing value unnecessarily. For purely derived-from-position values, prefer `derivedStateOf` so the *readers* skip recomposition.

### Do / Don't
- **DO** pass `StateFlow<Long>` (or the ViewModel) down; collect inside the narrowest leaf that renders the ticking value.
- **DO** use `derivedStateOf` for values derived from `positionMillis` that change slower than the tick (active lyric index, progress ratio).
- **DON'T** pass the resolved `Long`/`Float` value as a composable parameter from a high ancestor that also collected it — every emit re-composes that ancestor and its whole subtree.
- **DON'T** put `collectAsStateWithLifecycle()` for `positionMillis` in `MainActivity`/`MusicPlayerScreen` if only a deep leaf uses it.

### Edge cases
- The `positionMillis` flow ticks at **100 ms**, not 250 ms–1 s. If 100 ms granularity is not needed for the progress UI, consider raising `POSITION_MILLIS_SAMPLE_INTERVAL_MS` to ~250 ms to halve recomposition frequency — but isolation (above) is the real fix regardless of interval.
- `stateIn(WhileSubscribed(5000))` stops the upstream after 5 s with no collector; once the leaf collects, it restarts. No behavior change from isolation.
- Collecting inside the leaf means the leaf recomposes every 100 ms. That is acceptable *only* if that leaf is tiny and its children mostly skip (via stable params / `derivedStateOf`). For the lyric list, only `LyricLineText` items whose `active` flag changed will recompose — cheap.

---

## 2. Scrolling smoothness APIs

### 2a. Scrollbars — Android has NO native `Scrollbar` in `androidx.compose.foundation`
- **Fact**: `androidx.compose.foundation.Scrollbar`, `VerticalScrollbar`, `HorizontalScrollbar`, `rememberScrollbarAdapter` are **Compose Multiplatform desktop/skiko only**. The Android `androidx.compose.foundation` artifact does not expose them. (Confirmed via AndroidX source: the `Scrollbar` file lives under `compose/foundation/foundation/src/skikoMain`; community note: "available for Compose Multiplatform without Android".)
- **For Android**, options at this BOM:
  - Build a custom scrollbar: a `Box` thumb whose offset/size is derived from `LazyListState` (`firstVisibleItemIndex`, `firstVisibleItemScrollOffset`, `layoutInfo.totalItemsCount`, `layoutInfo.visibleItemsInfo`), driven by `derivedStateOf` so only the thumb recomposes. Show it only when `layoutInfo.totalItemsCount` exceeds the viewport (i.e., content is scrollable).
  - 3rd-party Android libs (e.g., `io.github.oikvpqya.compose.fastscroller` — a Compose Multiplatform lib that *does* target Android) if a dependency bump is acceptable.

```kotlin
// Android custom scrollbar pattern (no AndroidX Scrollbar available)
val listState = rememberLazyListState()
val thumb by remember {
    derivedStateOf {
        val total = listState.layoutInfo.totalItemsCount
        val viewport = listState.layoutInfo.visibleItemsInfo.sumOf { it.size }
        if (total == 0 || viewport <= 0) null
        else {
            val scrollRange = (listState.layoutInfo.totalItemsCount - listState.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
            val ratio = listState.firstVisibleItemScrollOffset.toFloat() /
                (listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1)
            val pct = (listState.firstVisibleItemIndex + ratio) / scrollRange
            pct.coerceIn(0f, 1f)
        }
    }
}
// draw a thin rounded Box at `thumb` fraction; only this Box recomposes
```

**Do/Don't**: DO show a scrollbar only when content overflows and is scrollable. DON'T import `androidx.compose.foundation.Scrollbar` on Android (won't resolve / wrong platform).

### 2b. Overscroll / rubber-band / stretch
- **Default**: `verticalScroll`, `horizontalScroll`, `LazyColumn`, `LazyRow` apply the platform default overscroll (edge glow/stretch on Android) automatically via `LocalOverscrollFactory`. You get it for free.
- **`overscrollEffect` parameter**: available on these modifiers since 1.2.0. Pass `overscrollEffect = null` to **disable** overscroll (e.g., for a pager-like surface where you don't want the stretch).
- **`rememberOverscrollEffect()`** (`androidx.compose.foundation.rememberOverscrollEffect()`) — **Added in 1.8.0**, NOT in this project's 1.6.0. It returns an `OverscrollEffect?` from `LocalOverscrollFactory`. `ScrollableDefaults.overscrollEffect()` is the deprecated predecessor (deprecated 1.8.0).
- The `OverscrollEffect` interface (1.2.0+) has `withoutVisualEffect()` (keeps event handling, drops the visual) and `node` (1.8.0). For custom visuals you implement `OverscrollEffect` and attach via `Modifier.overscroll(effect)` + feed events through `scrollable`/`verticalScroll` with the same `overscrollEffect`.

```kotlin
// disable overscroll on a specific lazy list (API available in 1.6.0):
LazyColumn(state = listState, overscrollEffect = null) { /* ... */ }
```

**Do/Don't**: DO rely on the default edge glow. DON'T try to call `rememberOverscrollEffect()` on this BOM (unresolved). Custom overscroll visuals can fight the Lazy default — only customize when you have a concrete reason, and pass the *same* instance to both the container and `Modifier.overscroll`.

### 2c. Fling / inertia
- Default is `ScrollableDefaults.flingBehavior()` (a natural, platform-feel fling). `LazyColumn`/`verticalScroll` accept `flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior()`.
- To customize "heavier" or "snappier": implement a `FlingBehavior` (copy framework `DefaultFlingBehavior`) driven by a `DecayAnimationSpec<Float>`:
  - `rememberSplineBasedDecay<T>()` — `androidx.compose.animation.rememberSplineBasedDecay` (artifact `androidx.compose.animation:animation`) — the **native Android fling curve** decay. Use for the default-feel-but-tuned behavior.
  - `exponentialDecay(frictionMultiplier, absVelocityThreshold)` — `androidx.compose.animation.core.exponentialDecay` — velocity-proportional friction; raise `frictionMultiplier` (>1) to stop sooner (snappier/shorter travel), lower it (<1) for a longer, heavier glide.

```kotlin
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.exponentialDecay

// snappier: more friction → shorter travel
val snappyFling: FlingBehavior = ScrollableDefaults.flingBehavior(
    exponentialDecay(frictionMultiplier = 1.6f)
)
// or native Android spline feel:
val splineFling: FlingBehavior = ScrollableDefaults.flingBehavior(rememberSplineBasedDecay())
LazyColumn(flingBehavior = snappyFling) { /* ... */ }
```

> Note: `ScrollableDefaults.flingBehavior()` in the 1.6.0 API reference exposes the no-arg form; the decay-accepting overload exists in newer versions but a custom `FlingBehavior` (as above, wrapping `animateDecay(decay)`) is version-proof on 1.6.0.

**Do/Don't**: DO leave the default unless there's a specific feel goal. DON'T tweak fling globally for the whole app without testing nested scroll (a heavier decay can feel sluggish inside a `LazyRow`-in-`LazyColumn`).

### 2d. Nested scroll coordination
- `LazyRow` inside `LazyColumn` works automatically (each handles its own orientation).
- For **sticky headers / collapsing top bars**, use `Modifier.nestedScroll(connection)` with a `NestedScrollConnection` (or M3 `TopAppBar` `scrollBehavior`):
```kotlin
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.nestedScroll

val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
val listState = rememberLazyListState()
Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = { TopAppBar(title = { Text("Album") }, scrollBehavior = scrollBehavior) }
) { padding ->
    LazyColumn(state = listState, contentPadding = padding) { /* ... */ }
}
```
- `nestedScroll` is available in `androidx.compose.foundation` (all 1.x).

---

## 3. Image loading smoothness (Coil 2.5.0)

### Current project usage (`AuthedAsyncImage.kt`)
- Uses Coil `AsyncImage` with an `ImageRequest.Builder` that sets `data(url)`, `diskCacheKey(cleanCacheKey)`, `memoryCacheKey(cleanCacheKey)` where `cleanCacheKey = stripAuthQuery(url)` (strips the auth token from the cache key so cached entries don't fragment per-token).
- **No `crossfade` and no `placeholder`** are set in `AuthedAsyncImage`. Meanwhile `MainActivity.kt:264` builds a *separate* `ImageRequest` with `.crossfade(160)` and a `MediaAuthHeaderInterceptor` for the player artwork — so two different loading styles exist.

### Smoothness guidance (Coil 2.5.0)
- **Crossfade** avoids pop-in when the bitmap arrives:
```kotlin
ImageRequest.Builder(context)
    .data(url)
    .diskCacheKey(cleanCacheKey)
    .memoryCacheKey(cleanCacheKey)
    .crossfade(true)                 // or crossfade(200)
    .placeholder(/* Drawable or painterResource */)
    .build()
```
  `AsyncImage` also accepts a top-level `Modifier.crossfade()`? No — crossfade is an `ImageRequest` option (`ImageRequest.Builder.crossfade`). For `AsyncImage(model = ...)` you must put it on the `ImageRequest`.
- **Authenticated requests** (`MediaAuthHeaderInterceptor`, defined in `data/AuthUrl.kt`, added to OkHttp clients in `MainActivity`, `MusicPlaybackService`, `VideoPlaybackEngine`): Coil uses its **own** `ImageLoader`/OkHttpClient unless you configure one. To avoid failed/pop-in cover-art loads, install a singleton `ImageLoader` whose `OkHttpClient` includes `MediaAuthHeaderInterceptor()`:
```kotlin
val imageLoader = ImageLoader.Builder(context)
    .okHttpClient { OkHttpClient.Builder().addInterceptor(MediaAuthHeaderInterceptor()).build() }
    .crossfade(true)
    .build()
// Coil.setImageLoader(imageLoader) once at startup, or provide via LocalContext
```
  This guarantees Coil's requests are authenticated and crossfade by default. (Currently `AuthedAsyncImage` relies on the auth token being in the URL query string (`data(url)` with the full url), which works but is inconsistent with the header-interceptor approach used elsewhere — note for consistency.)
- **`memoryCacheKey`/`diskCacheKey`**: already correct in `AuthedAsyncImage`; keep the `stripAuthQuery` cache key so re-auth (token rotation) doesn't bust the cache.
- **`contentScale`**: pass `ContentScale.Crop` for artwork (already done in `PlayerArtwork`), `ContentScale.Fit` elsewhere — unchanged.

### Do / Don't
- **DO** add `crossfade(true)` + a `placeholder` `Drawable` to `AuthedAsyncImage`'s `ImageRequest` to kill pop-in.
- **DO** register `MediaAuthHeaderInterceptor` on Coil's `ImageLoader` OkHttpClient if cover art needs auth via header.
- **DON'T** rebuild a fresh `ImageRequest` on every recomposition without `remember` (current code uses `remember(url) { ... }` — correct).

### Edge cases
- Coil 2.5.0 `crossfade` default duration is 100 ms; explicit `200` is smoother for large artwork.
- If the URL carries the auth token as a query param (current `data(url)`), the `placeholder` shows until the decoded bitmap is ready; `crossfade` then animates the swap. Without it, the image "pops" in.

---

## 4. Motion / transition consistency

### Version fact (important)
- `MaterialTheme.motionScheme` and the `MotionScheme` class ship in **Material3 1.5.0** (Expressive). The project is on **Material3 1.2.0** → **NOT available**.
- Therefore the existing `NordicMotion` object (`ui/theme/Motion.kt`) — durations 150/200/300/450 ms, easings `FastOutSlowInEasing`/`LinearOutSlowInEasing`/`FastOutLinearInEasing` (M3 "standard" emphasized-decelerate/accelerate equivalents) — is the **correct, version-appropriate** motion system for this BOM. Keep it.
- Upgrade path (only if BOM is bumped to ≥ 2024.09.00 / material3 1.5.0+): adopt `MaterialTheme.motionScheme` (`MotionScheme.standard()` / `MotionScheme.expressive()`), and replace hand-written `tween(...)` with `MaterialTheme.motionScheme.defaultSpatialSpec<T>()` / `defaultEffectsSpec<T>()` so components become theme-aware. Not needed now.

### Physics (`spring`) vs fixed (`tween`)
- **`spring`** (physics-based) — use for interactive/continuous or "snappy-but-organic" motion: drag releases, expand/collapse, size changes that should feel alive. `spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)` etc. (`androidx.compose.animation.core.spring`).
- **`tween`** (fixed duration/easing) — use for discrete state changes with a deterministic feel: enter/exit, crossfade, slide between screens. The M3 guideline: **enter = decelerate easing, exit = accelerate easing** (the `NordicMotion` `easingDecelerate`/`easingAccelerate` already encode this).
- `expandIn` / `shrinkOut` (visibility animations) and `AnimatedContent` `SizeTransform` are available in all 1.x (`androidx.compose.animation.*`). Use `SizeTransform { initialSize, targetSize -> ... }` to avoid layout jumps when content size changes (e.g., lyric line height change on active toggle).
- `AnimatedContent` with `SizeTransform` is good for the player's artwork↔lyrics toggle (`PlayerPrimaryDisplay`); pair with `materialish` easings from `NordicMotion`.

```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

AnimatedContent(
    targetState = showLyrics,
    transitionSpec = {
        fadeIn(NordicMotion.enterFade.animationSpec) togetherWith
            fadeOut(NordicMotion.exitFade.animationSpec) using
            SizeTransform(sizeAnimationSpec = { _, _ ->
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            })
    }
) { lyrics -> if (lyrics) PlayerLyricsDisplay(...) else PlayerArtwork(...) }
```

### Do / Don't
- **DO** keep `NordicMotion` for 1.2.0; align new transitions to its durations/easings for consistency.
- **DO** prefer `spring` for gesture-driven/continuous motion, `tween` for discrete enter/exit.
- **DON'T** adopt `MaterialTheme.motionScheme` on this BOM (won't compile). Don't hand-roll different durations per component — reuse `NordicMotion` tokens.
- **DON'T** mix `FastOutSlowInEasing` for *exit* (exit should be accelerate); `NordicMotion.easingAccelerate` exists for that.

---

## 5. Pull-to-refresh smoothness

### Version fact (important)
- `PullToRefreshBox` / `rememberPullToRefreshState` in `androidx.compose.material3.pulltorefresh` — **Added in Material3 1.3.0**. The project is on **1.2.0** → **NOT available** without a BOM bump.
- `PullToRefreshState` type itself appears "Added in 1.2.0" in the reference, but the `PullToRefreshBox` container and the `pulltorefresh` package are 1.3.0. So on 1.2.0 you cannot use `PullToRefreshBox`.

### Options
**A — Bump BOM to ≥ `2024.06.00` (Compose 1.6.8 / material3 1.3.0).** Then use the current API:
```kotlin
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults

val state = rememberPullToRefreshState()
PullToRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = Modifier.fillMaxSize(),
    state = state,
    indicator = {
        PullToRefreshDefaults.Indicator(   // default Material3 indicator
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
) {
    LazyColumn(Modifier.fillMaxSize()) { /* items */ }
}
```
Notes: `indicator` defaults to `PullToRefreshDefaults.Indicator`; `enabled`/`threshold` params added in 1.3.0 (`PullToRefreshDefaults.PositionalThreshold`). The box consumes nested scroll so it won't fight a child `LazyColumn`.

**B — Stay on 1.2.0: use M2 `PullRefresh`** from `androidx.compose.material` (ships with the BOM; `rememberPullRefreshState`, `pullRefresh` modifier, `PullRefreshIndicator`). This is the non-deprecated predecessor to the accompanist `SwipeRefresh`.
```kotlin
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator

val pullState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefresh)
Box(Modifier.pullRefresh(pullState).fillMaxSize()) {
    LazyColumn { /* items */ }
    PullRefreshIndicator(isRefreshing, pullState, Modifier.align(Alignment.TopCenter))
}
```
> The "deprecated `SwipeRefresh`" the brief references is the **accompanist** `com.google.accompanist:accompanist-swiperefresh` (`SwipeRefresh` / `rememberSwipeRefreshState`). Prefer the M2 `PullRefresh` (Option B) or the M3 `PullToRefreshBox` (Option A) over accompanist.

### Do / Don't
- **DO** pick M3 `PullToRefreshBox` (bump BOM to 1.3.0+) or M2 `PullRefresh` (current BOM).
- **DON'T** use accompanist `SwipeRefresh` (deprecated) when M2/M3 APIs are available.
- **DON'T** wrap a `PullToRefreshBox` around content that isn't scrollable at the top — the gesture only fires at scroll offset 0; pair with a `LazyColumn`/`verticalScroll` child.

### Edge cases
- `PullToRefreshBox` uses nested scroll; combining it with a child that has its own `nestedScroll` (e.g., collapsing `TopAppBar`) needs the same `nestedScrollConnection` propagated — test the gesture doesn't get swallowed.
- Inside `isRefreshing`, keep the list mounted (don't clear items) so the indicator has stable layout; the M3 sample conditionally renders items but keeps the `LazyColumn`.

---

## 6. Consolidated risks / edge cases

1. **Scrollbar is desktop-only** — `androidx.compose.foundation.Scrollbar` will not compile on Android. Custom thumb or 3rd-party lib required.
2. **`rememberOverscrollEffect()` needs Compose 1.8.0** — not in 1.6.0. Use `overscrollEffect = null` to disable, or accept the default.
3. **`MaterialTheme.motionScheme` needs Material3 1.5.0** — not in 1.2.0. Keep `NordicMotion`; revisit only after a BOM bump.
4. **`PullToRefreshBox` needs Material3 1.3.0** — not in 1.2.0. Use M2 `PullRefresh` or bump BOM.
5. **State isolation requires the flow collected inside the leaf**, not passed as a `Long`/`Float` param from an ancestor — otherwise the ancestor (and subtree) recomposes every 100 ms. Passing the *`StateFlow`* (stable) is fine.
6. **`derivedStateOf` is costly** — only use when output changes less often than input; freeze non-state captures via `remember` keys.
7. **`@Stable` model classes** — compiler 1.5.4 lacks strong-skipping; annotate `NavidromeSong`/`MusicLyrics`/`MusicLyricsLine` `@Stable` to avoid unnecessary recomposition when passed down.
8. **Coil auth** — Coil's `ImageLoader` needs `MediaAuthHeaderInterceptor` if cover art is header-authenticated; otherwise add `crossfade`+`placeholder` to `AuthedAsyncImage` to avoid pop-in.
9. **`positionMillis` ticks at 100 ms** (not 250 ms–1 s) — isolation matters even more; optionally raise `POSITION_MILLIS_SAMPLE_INTERVAL_MS` if 100 ms granularity is unnecessary.

---

## 7. Internal references (where the relevant code lives)

| Concern | File | Lines |
|---|---|---|
| `positionMillis` StateFlow (100 ms tick) | `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackViewModel.kt` | 36–45, 172–175 |
| Top-level collection + param pass-down (anti-pattern) | `app/src/main/java/com/nordic/mediahub/MainActivity.kt` | 788, 799 |
| `positionMillis` threaded through player | `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt` | 131, 334, 431, 449, 531, 541–543, 1061, 1071 |
| Lyric `LazyColumn` + activeIndex scroll | `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt` | 572–627, 663+ |
| Authed image (Coil, no crossfade/placeholder) | `app/src/main/java/com/nordic/mediahub/ui/AuthedAsyncImage.kt` | 22–36 |
| Auth header interceptor | `app/src/main/java/com/nordic/mediahub/data/AuthUrl.kt` | 45+ |
| Motion tokens | `app/src/main/java/com/nordic/mediahub/ui/theme/Motion.kt` | 19–83 |
| Lyric index resolution | `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt` | 1059–1102 |
