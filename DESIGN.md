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
  iron-muted: "#6B6B7B"
  coral-accent: "#FB7185"
  coral-accent-deep: "#E11D48"
typography:
  display:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "30sp"
    fontWeight: 700
    lineHeight: 1.1
  headline:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "24sp"
    fontWeight: 700
    lineHeight: 1.17
  title:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: 1.4
  label:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "13sp"
    fontWeight: 500
    lineHeight: 1.3
    letterSpacing: "0.02em"
  caption:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "11sp"
    fontWeight: 500
    lineHeight: 1.3
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
    rounded: "{rounded.xl}"
    padding: "0dp"
    height: "48dp"
  dock-playback:
    backgroundColor: "{colors.frost-surface}"
    textColor: "{colors.ash-text}"
    rounded: "{rounded.4xl}"
    padding: "12dp 12dp 10dp"
    height: "124dp"
  button-play:
    backgroundColor: "{colors.dusk-violet}"
    textColor: "{colors.snow-mist}"
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
- **Iron Muted** (#6B6B7B / oklch(48% 0.01 290)): secondary text.
- **Coral Accent Deep** (#E11D48 / oklch(51% 0.24 15)): light-mode destructive, currently unused.

### Named Rules

**The Translucency Rule.** Every non-primary surface is derived from `surfaceVariant.copy(alpha=X)` or `surface.copy(alpha=X)`, not from discrete hex tokens. The alpha value encodes the surface's role: 0.42 for list rows (subtle), 0.56 for standard containers (present), 0.72 for loading/emphasis (prominent), 1.0 for structural containers (solid). This is the depth system; do not bypass it with arbitrary colors.

**The Accent Scarcity Rule.** Dusk Violet and Fjord Cyan appear on interactive states and gradient overlays only. They are never used as card backgrounds, screen backgrounds, or large-area fills. The exception is the fill of the primary play button, which earns its saturation by being the single action point on any given screen.

## 3. Typography

**Display Font:** system-ui, Roboto, sans-serif
**Body Font:** system-ui, Roboto, sans-serif (same family; product UI does not need display/body pairing)
**Label Font:** system-ui, Roboto, sans-serif (weight and tracking differentiate; not a separate family)

**Character:** A single well-tuned sans doing all the work. Scale ratio is tight (1.2-1.25 between steps) as befits a product UI; headings command attention through weight and size, not through a different family. The system speaks Android native.

### Hierarchy

- **Display** (Bold, 30sp, 1.1 line-height): screen titles. "Music", "Audiobook", "Video" at the top of each tab. Only element at this scale.
- **Headline** (Bold, 24sp, 1.17 line-height): hero banner album name, large player album art fallback. One per visible surface.
- **Title** (SemiBold, 20sp, 1.3 line-height): section headers, empty-state headings, search button icons, large fallback icons.
- **Body** (Regular, 15sp, 1.4 line-height): card titles, list row titles, now-playing song title, config field text, search body text. The workhorse.
- **Label** (Medium, 13sp, 0.02em tracking): secondary text in cards, action button labels, nav item labels, segmented tab labels, chip body text. The information-dense voice.
- **Caption** (Medium, 11sp, 1.3 line-height): title meta text, artist album counts, hero badge labels, nav item labels in compact mode. Smallest readable size.

### Intermediate Sizes

Between the named steps, the system uses 14sp (SemiBold, for hero artist name, section subtitles, meta chip body) and 12sp (Regular, for timestamps, tiny meta, queue sheet secondary text). These are not separate roles; they are contextual variants of Label and Caption respectively.

### Named Rules

**The Native Font Rule.** No custom font files. The system uses the Android platform default (Roboto, with Noto Sans CJK fallback). A custom typeface would add bundle size without earning its keep in a product UI where content, not chrome, is the visual event.

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
Refined and translucent. Light touch, soft edges, subtle feedback.
- **Shape:** pill (999dp radius) for all button shapes
- **Primary (filled):** Dusk Violet background, Snow Mist text, 38dp size, shadowElevation 2dp. Used exclusively for play/pause in the dock and player.
- **Secondary (tonal):** primary.copy(alpha=0.18f) background, primary text. Used for non-filled control buttons in the player (shuffle, repeat, favorite).
- **Ghost:** transparent background, onSurface icon/text. Used for top-bar actions, back buttons, search buttons.
- **Disabled (filled):** primary.copy(alpha=0.32f) background, primary.copy(alpha=0.32f) text.
- **Disabled (tonal):** surface.copy(alpha=0.34f) background, primary.copy(alpha=0.16f) icon/text for inactive track, onSurface.copy(alpha=0.28f) for text.
- **Press feedback:** scale to 0.985x over 150ms (default) or 0.94x for header/icon buttons, easing FastOutSlowInEasing.

### Chips
Content-bearing metadata surfaces. Never interactive in the current implementation; always informational.
- **Style:** pill shape (999dp), surface.copy(alpha=0.64f) background, onSurface.copy(alpha=0.72f) text at 12sp Medium (home meta) or surfaceVariant.copy(alpha=0.62f) background at 11sp (player meta).
- **Internal padding:** horizontal 9dp, vertical 5dp.

### Cards / Containers
Not the lazy default; here they carry specific tonal roles.
- **List Row** (song, artist, album): surfaceVariant.copy(alpha=0.42f) background, RoundedCornerShape(16dp), BorderStroke(1dp, onSurface.copy(alpha=0.045f)). Internal padding horizontal 10dp, vertical 9dp. 52dp artwork at 12dp corners (default) or 14dp (album list row). Press scale 0.992x.
- **Hero Banner:** surfaceVariant.copy(alpha=0.62f), RoundedCornerShape(24dp), inner padding 18dp, 124dp artwork at 20dp corners. Gradient overlay: primary(0.18f) to secondary(0.1f) to surfaceVariant(0.82f).
- **Config Card:** surfaceVariant (alpha 1.0), RoundedCornerShape(12dp), column padding 16dp, verticalArrangement spacedBy(12dp). Focused text field border: primary. Unfocused: onSurface.copy(alpha=0.2f).
- **Video Card:** surfaceVariant (alpha 1.0), RoundedCornerShape(16dp), 180dp thumbnail. Content padding 14dp.
- **Audiobook Card:** surfaceVariant (alpha 1.0), RoundedCornerShape(12dp), 64dp artwork at 8dp corners. Row padding 14dp.

### Inputs / Fields
- **Search field:** OutlinedTextField, RoundedCornerShape(16dp), focused border primary, unfocused onSurface.copy(alpha=0.2f), placeholder onSurface.copy(alpha=0.4f).

### Navigation
- **Playback Dock:** persistent bottom. Surface at 0.94f alpha, RoundedCornerShape(28dp), shadowElevation 12dp, tonalElevation 6dp, BorderStroke(1dp, onSurface.copy(alpha=0.08f)). Now-playing bar (66dp height) sits above divider (1dp, onSurface.copy(alpha=0.07f)), then bottom nav row (58dp height).
- **Nav items:** pill shape (18dp on unselected/selected background), selected bg primary.copy(alpha=0.13f), icon 19dp size, label 13sp SemiBold (selected) / 13sp Medium (unselected). Selected text/icon: primary. Unselected: onSurface.copy(alpha=0.58f). Press scale 0.97x at 150ms.
- **Segmented Tabs:** outer container surfaceVariant.copy(alpha=0.56f), RoundedCornerShape(18dp), height 48dp, BorderStroke(1dp, onSurface.copy(alpha=0.06f)). Selected tab: surface at 0.96f, RoundedCornerShape(14dp), tonalElevation 2dp. Color transitions: 180ms FastOutSlowInEasing.

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
- **Do** stagger list item entrance with delay(index * 50ms) for a quiet cascade effect.

### Don't:
- **Don't** use over-decorated card designs. Cards are translucent vessels for content, not visual events themselves. Nested cards are always wrong.
- **Don't** use deep-tech dark mode aesthetics. This is not a developer tool or a cyberpunk dashboard. The dark background is Polar Shadow (#0A0A0F), tinted toward violet warmth, not neutral gray or neon.
- **Don't** use flat Material Design default styling. Surface alpha layers, gradient overlays, and pill geometry are deliberate departures from the default.
- **Don't** add shadows to cards or list rows at rest. Shadows are reserved for the dock and player. If a card has a shadow, it is wrong.
- **Don't** use bounce, elastic, or spring animation curves. FastOutSlowInEasing is the only easing. Violations are immediately visible.
- **Don't** use gradient text (background-clip: text with gradient). Typography earns emphasis through weight and size, not decoration.
- **Don't** use glassmorphism as a default. Frosted-glass effects are reserved for specific surfaces (the dock, player overlay), not applied everywhere.
- **Don't** use side-stripe borders (border-left/right > 1px as colored accent on cards, list items, or alerts). Rewrite with full borders, background tints, or nothing.
