# Mainstream Music Player UI Conventions

- **Query**: Now Playing / full-screen player UI conventions across Spotify, Apple Music, YouTube Music, 网易云音乐, QQ音乐
- **Scope**: external (mainstream app UIs) + internal mapping to our repo
- **Date**: 2026-08-06

> **Caveat (read first):** Live web verification **failed this session** — every external fetch (`m3.material.io`, `developer.android.com`, `wikipedia.org`, `google.com`, `api.github.com`) returned a transport error, so no URL could be fetched live. The per-app breakdowns below are based on **widely-documented, stable public knowledge** of these apps' full-screen players (conventions that have held for multiple major versions and are reproduced across UI-teardown blogs, Mobbin, and each vendor's own help center). Canonical source URLs are listed at the bottom for the main agent to verify. Do **not** treat any specific pixel value here as a hard spec — treat it as a reliable directional convention. Where I am less than fully certain, I say so inline.

---

## Summary (cross-app consensus)

- **Album art is a centered square, rounded (~8–16dp), with a drop shadow**, occupying the upper ~50–60% of the screen. Never full-bleed, never a text placeholder.
- **The background is album-art-derived** — either a heavily blurred + darkened full-bleed copy of the cover (Apple Music, YouTube Music, 网易云) or a dominant-color gradient extracted from the cover (Spotify). This is *dynamic per track*. (Our app deliberately does **not** use dynamic color — see "Conventions mapped to our repo".)
- **The progress bar is a thin line with a small circular thumb**, never a stock chunky slider. Time labels sit **below** the bar: elapsed on the left, total (Spotify sometimes shows remaining as a negative value) on the right. Active track = white or accent; inactive = translucent gray.
- **Control row order is consistent**: secondary (shuffle/play-mode) → **Previous → Play/Pause → Next** → secondary (repeat). **Play/Pause is the biggest, a filled circle.** Shuffle/Repeat are outlined, smaller, and get an accent tint + small indicator when active; **repeat-one is shown with a small "1" badge** on the repeat icon.
- **Everything uses vector icons** — no text glyphs. **Favorite/heart** sits near the title (prominent) or in a secondary action row. **Lyrics** is a dedicated, toggleable view (button or tap-art) with the current line bold + larger and neighbors dimmed (high-end apps add per-syllable "karaoke" highlight). **Dismiss** is a chevron-down at top-left **plus** a swipe-down gesture.

---

## Per-app breakdown

### Spotify (Android)

**A. Layout & hierarchy**
- Top bar: chevron-down (left); a small "正在播放" / now-playing label is absent or minimal; on the right a "devices/Connect" speaker icon and a "..." more button (version-dependent). Title/artist are **not** in the top bar — they sit **below the album art**.
- Album art: centered square, modest rounding (~8–12dp), drop shadow, dominates the upper-middle ~60%.
- Below art: track title (large bold, ~22–24sp) + artist (smaller, ~14–16sp, tappable to open artist). No marquee — long titles ellipsize.
- Background: a **dominant-color gradient extracted from the album art**, fading to near-black at the bottom. Dynamic per track.

**B. Progress bar / scrubber**
- Thin horizontal line with a small circular thumb (thumb enlarges slightly when dragged). Active = white (or per-track accent); inactive = translucent white/gray. Custom-drawn, not a stock slider.
- Time labels **below**: elapsed left; **remaining** (e.g. `-2:13`) on the right in many versions.

**C. Control row** (left→right)
- **Shuffle → Previous → Play/Pause → Next → Repeat**.
- Play/Pause = filled white circle (~64dp) with a dark play/pause glyph — biggest by far.
- Shuffle/Repeat = outlined ~24dp icons; when active they get a **Spotify-green tint + a small dot indicator** beneath. **Repeat-one shows a small "1" badge** on the repeat icon.
- Secondary controls live on **two** extra rows: (1) just under the title — heart + download-circle + "..." more; (2) a bottom row — Connect/devices (left), **Lyrics** pill (center), Queue (right).

**D. Favorite / heart**
- In the meta row directly under the title, prominent, left-aligned. Toggled = **filled green heart**.

**E. Lyrics**
- Dedicated **"Lyrics" button** in the bottom action row. Opens a synced-lyrics card that slides up; current line large/bold; **per-syllable karaoke highlight** (powered by Musicxmatch). Tap-to-dismiss returns to the artwork view.

**F. Gestures**
- Swipe **down** to dismiss. (No left/right swipe-to-skip on the full player.)

**G. Dismiss**
- Chevron-down top-left **+** swipe down.

---

### Apple Music (iOS "Now Playing")

**A. Layout & hierarchy**
- Top bar: chevron-down (left), small "正在播放" centered, AirPlay + "..." more (right).
- Album art: centered square, rounded (~12dp), soft shadow, upper area.
- Below art: title (bold) + artist. The **artist is tinted with a per-album accent color** (Apple Music extracts an accent per album — often pink/red/orange).
- Background: **heavily blurred, enlarged copy of the album art, darkened** — the signature frosted-glass Apple Music look. Dynamic per track.

**B. Progress bar / scrubber**
- Thin line, small circular thumb. Active = **the per-album accent** (pink/red/etc.). Time below: elapsed left, total right.

**C. Control row** (left→right)
- Modern iOS: **Lyrics (quote-bubble icon) → Previous → Play/Pause → Next → Queue (list icon)**. Play/Pause = filled circle, biggest (white or accent).
- AirPlay devices icon and "..." more sit in the **top bar** (not the control row). Shuffle and Repeat are smaller icons, sometimes tucked into a secondary position / overflow (version-dependent).

**D. Favorite / love**
- Apple Music uses a **star / "Love" toggle** (not a heart), near the title area or in a small row. Loved = filled star. Less prominent than Spotify's heart.

**E. Lyrics**
- Dedicated **lyrics icon** (speech bubble with a quotation mark). Tapping toggles to a full synced-lyric view: current line large + bold, upcoming lines dimmed, **per-word karaoke highlight** (Apple Music's signature feature). Also reachable via swipe.

**F. Gestures**
- Swipe down to dismiss; scrub-by-drag; (mini-player supports swipe to next/prev).

**G. Dismiss**
- Chevron-down top-left **+** swipe down.

---

### YouTube Music (Android)

**A. Layout & hierarchy**
- Very close to Spotify's structure. Top bar: chevron-down (left), small title/artist centered, Cast + "..." (right).
- Album art: centered square, rounded (~8–12dp), shadow.
- Below art: title (bold white) + artist (smaller, tappable).
- Background: **heavily blurred, darkened album art full-bleed** (very dark, near-black). Dynamic per track.

**B. Progress bar / scrubber**
- Thin line, small thumb. Active = white (or YouTube brand red); inactive gray. Time below: elapsed left, total right.

**C. Control row** (left→right)
- **Shuffle → Previous → Play/Pause → Next → Repeat**. Play/Pause = filled circle, biggest (white or brand-red).
- YouTube's signature: a **thumbs-up / thumbs-down** pair near the title for like/dislike. Shuffle/Repeat outlined, smaller, tinted when active; repeat-one gets a "1" badge.
- Bottom secondary row: **Lyrics**, Queue, Share, "..." more.

**D. Favorite / like**
- **Thumbs-up** near the title (YouTube's signature upvote), not a heart. Toggled = filled.

**E. Lyrics**
- Dedicated **"Lyrics" button** in the bottom action row. Opens a synced-lyrics view.

**F. Gestures**
- Swipe down to dismiss.

**G. Dismiss**
- Chevron-down top-left **+** swipe down.

---

### NetEase Cloud Music (网易云音乐) Android

**A. Layout & hierarchy**
- Distinctive **vinyl-record** aesthetic in the classic view: the album art sits inside a spinning black vinyl disc (with a tonearm); in modern full-screen it can be a centered disc. Strongly **lyrics-forward** — the upper area is the "disc" view and the user can switch to a full synced-lyrics view.
- Top bar: chevron-down (left), "正在播放"/title, "..." more (right).
- Below cover/disc: title + artist.
- Background: blurred album art, darkened, with **NetEase red accents (#C20C0C)**.

**B. Progress bar / scrubber**
- Thin custom slider with a small thumb. Active = white or red; inactive gray. Time below.

**C. Control row** (left→right, typical)
- **[Queue/playlist] → Previous → Play/Pause → Next → [play-mode toggle]**.
- NetEase's **play-mode button cycles**: 顺序播放 → 列表循环 → 单曲循环 → 随机播放, each with a distinct icon. Play/Pause = biggest, **red filled circle**.
- Secondary: NetEase is famous for **评论 (comments)** — a comment entry / comment-count chip near the bottom; plus favorite (收藏/heart), share, download, "…".

**D. Favorite / heart**
- Heart icon, **prominent**, near the title or in the action row. Toggled = **filled red heart**.

**E. Lyrics**
- **Lyrics-forward** flagship feature. Tapping the cover/upper area or a lyrics button toggles to a full synced-lyric view with **karaoke-style highlight** of the current line; neighbors dimmed.

**F. Gestures**
- Swipe down to dismiss; **tap cover to toggle disc↔lyrics** view.

**G. Dismiss**
- Chevron-down top-left **+** swipe down.

---

### QQ Music (QQ音乐) Android

**A. Layout & hierarchy**
- Also a **vinyl/disc** aesthetic in classic mode (spinning disc) or a centered rounded square in modern mode. Title/artist below. Lyrics-forward.
- Top bar: chevron-down (left), title/artist, "..." more (right).
- Background: blurred album art + brand gradient (QQ Music green/orange), darkened.

**B. Progress bar / scrubber**
- Thin custom slider, small thumb. Active = brand green/orange; time below.

**C. Control row** (left→right, typical)
- **[play-mode] → Previous → Play/Pause → Next → [favorite/heart or queue]**. Play/Pause = biggest, **filled brand-colored circle**. Play-mode toggle like NetEase (cycles order/loop/shuffle/repeat-one with distinct icons).

**D. Favorite / heart**
- Heart prominent, toggled filled. Sometimes sits at the far right of the control row.

**E. Lyrics**
- **Lyrics-forward**; dedicated lyrics view, karaoke highlight, tap-to-toggle (tap the cover area).

**F. Gestures**
- Swipe down to dismiss; tap cover to toggle lyrics.

**G. Dismiss**
- Chevron-down **+** swipe down.

---

## Cross-app comparison tables

### Control row order & iconography

| App | Left secondary | Prev | Play/Pause | Next | Right secondary | Play/Pause style | Active-state signal |
|---|---|---|---|---|---|---|---|
| Spotify | Shuffle | ✓ | ✓ (biggest, filled circle) | ✓ | Repeat | filled white circle | green tint + dot; repeat-one "1" badge |
| Apple Music | Lyrics | ✓ | ✓ (biggest, filled circle) | ✓ | Queue | filled circle (accent/white) | accent tint |
| YouTube Music | Shuffle | ✓ | ✓ (biggest, filled circle) | ✓ | Repeat | filled white/red circle | tint; repeat-one "1" badge |
| 网易云 | Queue/playlist | ✓ | ✓ (biggest, **red** filled circle) | ✓ | play-mode toggle | filled red circle | mode-icon swap |
| QQ音乐 | play-mode | ✓ | ✓ (biggest, brand filled circle) | ✓ | favorite/queue | filled brand circle | mode-icon swap / heart fill |
| **Our app (current)** | Repeat, Shuffle | ✓ | ✓ (biggest, filled primary circle) | ✓ | Queue | filled primary circle | primary tint + `active` bg |

> Our control order is **Repeat, Shuffle, Prev, Play, Next, Queue** — i.e. *both* secondaries on the left and Queue on the right. Mainstream puts **one secondary on each side** (Shuffle-left / Repeat-right, or Lyrics-left / Queue-right). Reordering to the mainstream shape is cheap.

### Progress bar style

| App | Track shape | Thumb | Active color | Time label position |
|---|---|---|---|---|
| Spotify | thin line | small circle (expands on drag) | white / per-track accent | below; remaining (negative) on right |
| Apple Music | thin line | small circle | per-album accent | below; total on right |
| YouTube Music | thin line | small circle | white / red | below; total on right |
| 网易云 | thin line | small circle | white / red | below |
| QQ音乐 | thin line | small circle | brand green/orange | below |
| **Our app (current)** | **stock Material3 `Slider`** (chunky rounded track + default thumb) | default Material thumb | `colorScheme.primary` | below; total on right |

> Every mainstream app uses a **thin line + small thumb**. Our stock `Slider` is the most visible "not mainstream" signal alongside the text glyphs.

### Favorite placement

| App | Icon | Placement | Prominence | Toggled state |
|---|---|---|---|---|
| Spotify | heart | meta row under title | prominent | filled green heart |
| Apple Music | star / love | near title | medium | filled star |
| YouTube Music | thumbs-up | near title | prominent | filled thumb |
| 网易云 | heart | action row / near title | prominent | filled red heart |
| QQ音乐 | heart | control-row edge / action row | prominent | filled heart |
| **Our app** | none yet | — | — | star exists via `NavidromeRepository` but no UI |

### Lyrics entry

| App | Trigger | Presentation | Highlight |
|---|---|---|---|
| Spotify | dedicated Lyrics button (bottom row) | slide-up card | current line bold; per-syllable karaoke |
| Apple Music | dedicated lyrics icon (quote bubble) | full toggle view | current line bold; per-word karaoke |
| YouTube Music | dedicated Lyrics button (bottom row) | toggle view | current line bold |
| 网易云 | tap cover / lyrics toggle | full view, disc↔lyrics | current line bold + larger; per-line karaoke |
| QQ音乐 | tap cover / lyrics button | full view | current line bold; karaoke |
| **Our app** | **tap album art to toggle** (already implemented) | centered 5/7-line card | **active line bold + larger** (already implemented) |

> Our lyrics infra is already close to the 网易云/QQ shape (tap-art toggle, active line bold+larger). The gap is: no **dedicated button** (only tap-art), and highlight is **per-line** not per-syllable.

### Dismiss gesture

| App | Chevron-down button | Swipe down |
|---|---|---|
| Spotify | ✓ (top-left) | ✓ |
| Apple Music | ✓ (top-left) | ✓ |
| YouTube Music | ✓ (top-left) | ✓ |
| 网易云 | ✓ (top-left) | ✓ |
| QQ音乐 | ✓ (top-left) | ✓ |
| **Our app** | ✓ (top-left, currently `⌄` text glyph) | ✗ (not implemented) |

---

## Conventions mapped to our repo constraints

Repo facts (from `MusicPlayerScreen.kt`, `ui/theme/*`, `app/build.gradle.kts:51-52`, and the PRD):
- Jetpack Compose + Material3; **`material-icons-core` + `material-icons-extended` already on the classpath**; `VideoPlayerScreen.kt` already uses `Icons.Filled.PlayArrow/Pause/FastRewind/FastForward/Close/Fullscreen/AspectRatio` via a local `VideoPlayerChromeButton(icon, primary, size)` — there is an existing icon-button pattern to align with.
- Design tokens enforced: `NordicShapes` (none/sm/md/lg/xl/full), `NordicSpacing` (xs…xxxl/content), `NordicAlpha` (medium/subtle/faint), `NordicTypography` (displaySmall/headlineMedium/titleMedium/titleSmall/bodyMedium/labelLarge/bodySmall). **No hardcoded `RoundedCornerShape`/`dp`/`sp`/`alpha`.**
- Compact branch `maxHeight < 740.dp` already exists; must not break.
- Background is already "cover @ 12% alpha + vertical gradient" — an ambient approximation of the blurred-cover convention.
- Lyrics already supported (`MusicLyrics`, synced), tap-art toggle, active line bold+larger. `onOpenQueue` callback already wired. **Favorite/star exists in the data layer but has no UI.**
- **Dynamic color is NOT used** and the PRD treats it as out of scope.

### Cheap wins (icon + token, no structural change, no new callbacks)
- **Swap every text glyph for a Material vector icon** (biggest single "mainstream" signal, deps already present, precedent in `VideoPlayerScreen`):
  - `▶`/`Ⅱ` → `Icons.Filled.PlayArrow` / `Icons.Filled.Pause`
  - `‹`/`›` → `Icons.AutoMirrored.Filled.SkipPrevious` / `Icons.AutoMirrored.Filled.SkipNext` (AutoMirrored for RTL correctness)
  - `↺` / `↺1` / `↺A` → `Icons.AutoMirrored.Filled.Repeat` / `RepeatOne` (+ a small "1" badge for repeat-one)
  - `⇄` → `Icons.Filled.Shuffle`
  - `≡` → `Icons.AutoMirrored.Filled.QueueMusic`
  - `⌄` close → `Icons.Filled.KeyboardArrowDown` (or `ExpandMore`)
- **Badge repeat-one with a small "1" overlay** and tint active shuffle/repeat with `colorScheme.primary` (the `active` bg logic already exists).
- **Restyle the Material3 `Slider` to a thin line + small thumb** using `SliderDefaults` + custom `thumb`/`track` slots (no custom `Canvas` needed), keeping `colorScheme.primary` active track. Keeps token compliance and the existing `onPositionChange`/`onValueChangeFinished` callbacks untouched.
- All of the above **preserves** the current callback signatures and the `compact` branch.

### Medium cost (structural tweak, no new callbacks)
- **Reorder the control row** to the mainstream shape: `Shuffle → Prev → Play/Pause → Next → Repeat` (move Queue off the primary row, or keep it as a 6th icon — Spotify-like apps don't put queue in the primary row). Pure layout change.
- **Strengthen the ambient background** toward the blurred-cover look: increase blur/layer a darker scrim on the existing 12%-alpha cover overlay (we already render the cover; just deepen the scrim). Cheap approximation of Apple-Music/YT-Music frosted bg without the Palette library.

### Expensive / structural (new callbacks or new infra — flag, don't assume)
- **Favorite/heart button**: needs a new `onToggleFavorite`/`star` callback (PRD lists this as an open question). Data layer supports it (`NavidromeRepository` star).
- **Dedicated Lyrics button** in a secondary row (we currently only expose lyrics via tap-art). No new data, but a new UI affordance.
- **Per-track dynamic background color** (Palette extraction) — explicitly NOT used and out of scope per PRD; would add the `androidx.palette` dependency + an image-load hook. Most expensive, lowest priority.
- **Per-syllable karaoke lyrics** — we currently do per-line highlight; per-syllable needs timed-word data we likely don't have. Medium-high, data-dependent.
- **Swipe-down-to-dismiss gesture** — needs a `Modifier.nestedScroll`/`swipeable` wrapper on the screen root; touches the screen container. Medium.

---

## Recommended option set for our app

### Option A — Spotify-like (minimal-mainstream)
**Adopt:**
- Keep centered rounded album art (we have it: `Surface(NordicShapes.xl, shadowElevation=10dp)`).
- Thin-line progress bar via styled Material3 `Slider` (small circular thumb).
- **Vector-icon control row in order: Shuffle → Previous → Play/Pause → Next → Repeat** (move Queue off the primary row, or keep as a 6th). Play/Pause stays the biggest filled primary circle (already is).
- Repeat-one shown with a small "1" badge; active shuffle/repeat tinted `colorScheme.primary` (already have `active` bg).
- Chevron-down vector icon for close (replace `⌄`).
- Keep tap-art lyrics toggle as-is.

**What changes vs current:** glyph→icon swap for 6 controls; Slider restyle; control-row reorder; repeat-one badge.

**Pros:** Highest "mainstream feel" per LOC; all deps present; `VideoPlayerScreen` precedent; preserves every callback signature and the `compact` branch; fully token-compliant.

**Cons:** Minor control-row reordering; Slider restyle needs care to not regress the compact branch; queue loses its primary-row spot (or stays as a slightly crowded 6-icon row).

### Option B — Apple-Music-like (rich info + frosted background)
**Adopt:** everything in A, plus
- Move **title/artist below the album art** (more mainstream hierarchy); slim the top bar to chevron-down + small "正在播放" + close.
- Add a **secondary meta row under the title** (favorite/heart + "..." more) — favorite needs a new callback.
- Deepen the background toward the **frosted blurred-cover** look (strengthen scrim on the existing cover overlay; no Palette needed).

**What changes vs current:** top-bar restructure (title moves under art); new favorite button + callback; stronger background scrim.

**Pros:** Closest to "premium" feel; strongest information hierarchy; matches the most aesthetically-regarded mainstream player.

**Cons:** Structural reorg of `PlayerTopBar`; **favorite needs a new callback** (PRD open question — must confirm scope); more layout work to keep `compact` readable; background darkening must not hurt cover legibility.

### Option C — 网易云-like (lyric-forward)
**Adopt:** everything in A, plus
- Elevate **lyrics to first-class**: add a **dedicated lyrics toggle button** (not only tap-art), and make the lyric view the hero for synced lyrics.
- Enhance lyric line rendering: current line **bold + larger + accent-colored** with neighbors dimmed (partly already done); add per-line karaoke emphasis (per-syllable is data-dependent — defer unless timed-word data exists).
- Add a **secondary action row**: favorite (heart) + Queue + "..." more.

**What changes vs current:** new dedicated lyrics button + secondary action row; favorite callback; lyric-line color/emphasis polish.

**Pros:** Plays to our existing lyrics infra (`MusicLyrics` synced already supported, active-line highlight already implemented); differentiated, "lyric-forward" product identity; aligns with the strongest Chinese-market mental model.

**Cons:** Adds a secondary row = new UI + callback additions (favorite, possibly more); **per-syllable karaoke is data-dependent** and may be out of reach; fitting a secondary row + hero lyrics in the `compact` branch is the hardest of the three.

> **Note across all options:** the per-track dynamic background is common to all five apps but is the single most expensive item (Palette library) and is explicitly out of scope per the PRD. Recommend **not** pursuing it; the existing static gradient + deepened cover scrim is an acceptable cheap approximation (Option B leans on this).

---

## Source URLs
*(Could not be fetched live this session — transport errors on all external sites. Listed for the main agent to verify; all are canonical public references for the conventions described above.)*

- Material 3 — Sliders: https://m3.material.io/components/sliders/overview
- Material 3 — Buttons: https://m3.material.io/components/buttons/overview
- Material 3 — Music player guidance (composites): https://m3.material.io/components/cards
- Apple Human Interface Guidelines — Now Playing / Media: https://developer.apple.com/design/human-interface-guidelines/
- AndroidX Compose Material3 `Slider` reference: https://developer.android.com/reference/kotlin/androidx/compose/material3/Slider
- AndroidX Compose — Sliders guide: https://developer.android.com/jetpack/compose/components/slider
- AndroidX Material Icons reference: https://developer.android.com/reference/kotlin/androidx/compose/material/icons/Icons
- Spotify — Now Playing / Lyrics help: https://support.spotify.com/
- Apple Music — Lyrics / Now Playing help: https://support.apple.com/guide/music
- YouTube Music help: https://support.google.com/youtubemusic/
- 网易云音乐 / QQ音乐 帮助中心 (lyrics, 播放页): https://music.163.com / https://y.qq.com
- UI teardown / screenshot references (industry-standard catalogs): https://mobbin.com (Spotify, Apple Music, YT Music player screens)

## Caveats / Not Found
- **Live web verification failed this session** — every external URL returned a transport error (incl. `google.com`, `api.github.com`, `developer.android.com`, `m3.material.io`, `wikipedia.org`). The breakdown is from widely-documented, stable public knowledge of these apps and should be treated as **directional, not pixel-exact**. The main agent should spot-check against Mobbin screenshots or the apps' own help centers before locking exact corner radii / thumb sizes.
- Specific version differences (e.g. Apple Music's shuffle/repeat placement, Spotify's remaining-vs-total time label) vary by release; the tables record the **dominant** convention, not every variant.
- 网易云/QQ exact control-row order varies by skin/version; recorded the **typical** layout.
- **Not researched** (out of this topic's scope): mini-player/now-playing-bar conventions, queue-sheet UX, browse/library screens.
