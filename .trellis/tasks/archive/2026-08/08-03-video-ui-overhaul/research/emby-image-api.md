# Research: Emby Image API — Backdrop / Thumb / Logo Retrieval

- **Query**: How to extend `EmbyItemDto` + `EmbyRepository` to fetch backdrop images for a video detail hero surface (Fields value, DTO shape, URL pattern, episode-vs-series fallback, Logo/Thumb, auth).
- **Scope**: mixed (internal repo inspection + external Jellyfin/Emby source)
- **Date**: 2026-08-03

## TL;DR

- Add `BackdropImageTags` to the `Fields` query (required on Emby-original; harmless no-op on Jellyfin where the array is returned by default).
- DTO field is `BackdropImageTags` → JSON array of strings → model as `List<String>?`.
- `ImageTags` is a `Map<String,String>` keyed by `ImageType` name, but **`Backdrop` is NOT a key in it** — backdrops live exclusively in the `BackdropImageTags` array (because Backdrop allows multiple images).
- Image URL: `/Items/{itemId}/Images/Backdrop?maxWidth=1280&quality=90&tag={tag}` (index 0 is the best/primary backdrop; indexed path `/Items/{id}/Images/Backdrop/0` is equivalent).
- For Episodes: the episode usually has NO own backdrop. Use `ParentBackdropImageTags[0]` + `ParentBackdropItemId` (the Series id) — both are returned on the same DTO. `SeriesId` is equivalent to `ParentBackdropItemId` for episodes and can be the fallback id.
- `Logo` and `Thumb` use the same `/Items/{id}/Images/{type}` pattern. `Thumb` (16:9) is the right surface for continue-watching thumbnails; `Backdrop` (16:9) is the right surface for the detail hero. `Logo` (wide transparent PNG) overlays on the hero.
- Auth is identical to other endpoints: `X-Emby-Token` header (preferred) or `api_key` query fallback. Same `MediaAuthHeaderInterceptor` path applies.

---

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt` | `EmbyItemDto` (no backdrop fields yet), `EmbyApi.getItems` `Fields` query (no `BackdropImageTags`). |
| `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt` | `primaryImageUrl(...)` builder (lines 268-285), `toVideoItem()` (lines 231-254) — pattern to mirror for backdrops. |
| `app/src/main/java/com/nordic/mediahub/ui/VideoDetailScreen.kt` | Detail surface that needs the backdrop hero (currently a 72%-width portrait `CoverArt`). |
| `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt` | Existing mock-JSON tests assert `/Items/{id}/Images/Primary` URL shape — backdrop tests will mirror these. |
| `.trellis/spec/backend/emby-integration.md` | Existing contract: `Fields` list, thumbnail URL builder, `EMBY_HEADER_AUTH_ENABLED` / `MediaAuthHeaderRegistry` auth pattern. |
| `.trellis/tasks/08-03-video-ui-overhaul/prd.md` | PRD assumption (line 37): “Emby server exposes Backdrop image tags when requested via `Fields=BackdropImageTags`.” |

### External References (authoritative Jellyfin source — Emby fork)

All citations are from `jellyfin/jellyfin` `master` (fetched 2026-08-03). Jellyfin is a fork of Emby 3.5.2; the image API and DTO shapes are identical to Emby for the fields discussed here.

- `MediaBrowser.Model/Dto/BaseItemDto.cs`
  - L487: `public Dictionary<ImageType, string> ImageTags` — the single-image-per-type map (Primary, Art, Logo, Thumb, Banner, etc.).
  - L493: `public string[] BackdropImageTags` — **JSON string array**, separate from `ImageTags` because Backdrop allows multiple images.
  - L499: `public string[] ScreenshotImageTags` (obsolete).
  - L305: `public Guid? ParentBackdropItemId` — the id of the parent (Series) that owns the backdrop, populated for episodes.
  - L311: `public string[] ParentBackdropImageTags` — the parent’s backdrop tags, populated for episodes.
  - L299: `public Guid? ParentLogoItemId` + L505 `public string ParentLogoImageTag`.
  - L542: `public Guid? ParentThumbItemId` + L548 `public string ParentThumbImageTag`.
  - L443: `public string SeriesPrimaryImageTag`.
- `MediaBrowser.Model/Entities/ImageType.cs`
  - Enum: `Primary=0, Art=1, Backdrop=2, Banner=3, Logo=4, Thumb=5, Disc=6, Box=7, Screenshot=8(obsolete), Menu=9, Chapter=10, BoxRear=11, Profile=12`. Used as the `{imageType}` path segment and as the keys of `ImageTags` (serialized by name, not number).
- `Jellyfin.Api/Controllers/ImageController.cs`
  - L552: `[HttpGet("Items/{itemId}/Images/{imageType}")]` — `GetItemImage`, accepts `maxWidth, maxHeight, width, height, quality, tag, format, percentPlayed, unplayedCount, blur, backgroundColor, foregroundLayer, imageIndex` (all `[FromQuery]`).
  - L630: `[HttpGet("Items/{itemId}/Images/{imageType}/{imageIndex}")]` — `GetItemImageByIndex`, same query params. **Index 0 is the first/primary backdrop.**
  - `[Authorize]` on all GET image endpoints → same auth as JSON API.
- `Emby.Server.Implementations/Dto/DtoService.cs`
  - L994-998: `dto.BackdropImageTags = GetTagsAndFillBlurhashes(dto, item, ImageType.Backdrop, backdropLimit)` — populated when `backdropLimit > 0`.
  - L1006-1023: `dto.ImageTags` is populated only for image types where `AllowsMultipleImages(type) == false`. **Backdrop allows multiple images, so Backdrop is NEVER a key in `ImageTags`.**
  - L1642-1651: Parent-backdrop fallback — walks up the parent chain (Episode → Season → Series) and, when the item has no own backdrop and no inherited backdrop yet, sets `dto.ParentBackdropItemId = parent.Id` and `dto.ParentBackdropImageTags = GetTagsAndFillBlurhashes(dto, parent, ImageType.Backdrop, images)`. The walk continues until `parent is Series` (L1599), so episodes inherit the **Series** backdrop.
- `Jellyfin.Api/Controllers/ItemsController.cs`
  - L213: `fields` is bound as `ItemFields[]` via `CommaDelimitedCollectionModelBinder`.
  - L227-228, L261: separate `imageTypeLimit`, `enableImageTypes`, `enableImages` query params control image-tag population (defaults: all images enabled, unlimited).
  - L283-284: `new DtoOptions { Fields = fields }.AddAdditionalDtoOptions(enableImages, enableUserData, imageTypeLimit, enableImageTypes)`.
- `Jellyfin.Api/ModelBinders/CommaDelimitedCollectionModelBinder.cs`
  - L65-73: each comma token is run through `TypeDescriptor.GetConverter(elementType).ConvertFromString(...)`; **`FormatException` is caught and the token is silently dropped** (logged at DEBUG). So unknown `Fields` values like `BackdropImageTags`/`ImageTags` do NOT cause a 400 — they are no-ops on Jellyfin.
- `MediaBrowser.Model/Querying/ItemFields.cs` (master) — **does NOT contain** `BackdropImageTags`, `ImageTags`, `LogoImageTags`, or `ThumbImageTags` as enum members. Confirmed the same for tags `v10.7.7` and `v10.8.13` (only `ScreenshotImageTags` existed, now obsolete). Image tags are NOT gated by `Fields` in Jellyfin — they are gated by `EnableImages` / `ImageTypes` / `ImageTypeLimit`, all of which default to “all on”.

### Per-question answers

**Q1 — Fields parameter.** On modern **Jellyfin**, image tags are NOT controlled by `Fields`; they’re controlled by `enableImages` (default `true`), `enableImageTypes` (default = all), and `imageTypeLimit` (default = `int.MaxValue`). So `BackdropImageTags` is returned by default with no `Fields` change. The current `Fields=...,ImageTags,...` value is a **no-op on Jellyfin** (`ImageTags` is not a valid `ItemFields` member; the binder silently drops it) — image tags come back anyway.

On **Emby (original, closed-source)** the PRD’s assumption holds: Emby’s `ItemFields` enum recognizes image-tag fields (`ImageTags`, `BackdropImageTags`, `LogoImageTags`, `ThumbImageTags`) and gates population on them. The user’s existing `Fields=ImageTags` works on their Emby server, which is consistent with Emby (not Jellyfin) accepting that value.

**Exact `Fields` value to add: `BackdropImageTags`.**
Resulting `Fields`:
```
Overview,ProductionYear,SeriesId,SeriesName,ParentIndexNumber,IndexNumber,RunTimeTicks,ChildCount,ImageTags,BackdropImageTags,CommunityRating,UserData
```
This is required on Emby and a harmless no-op on Jellyfin — safe cross-server. (Optionally also add `LogoImageTags` / `ThumbImageTags` now if the hero will overlay a logo; see Q5.)

**Q2 — DTO shape.**
- `BackdropImageTags` → JSON **array of strings**, e.g. `"BackdropImageTags": ["a1b2...","c3d4..."]`. Kotlin type: `List<String>? = null` with `@SerializedName("BackdropImageTags")`.
- `ImageTags` → JSON **object** keyed by `ImageType` **name** (`"Primary"`, `"Art"`, `"Logo"`, `"Thumb"`, `"Banner"`, …). The existing `Map<String, String>?` model is correct. **`Backdrop` is NOT a key in `ImageTags`** — backdrops are multi-image and live only in the `BackdropImageTags` array (DtoService L1012 filters to `!AllowsMultipleImages(type)`). So do not try to read `imageTags["Backdrop"]`; it will always be null.

Exact DTO additions:
```kotlin
@SerializedName("BackdropImageTags") val backdropImageTags: List<String>? = null,
@SerializedName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
@SerializedName("ParentBackdropImageTags") val parentBackdropImageTags: List<String>? = null,
// Optional, for logo overlay / thumb card (see Q5):
@SerializedName("ParentLogoImageTag") val parentLogoImageTag: String? = null,
@SerializedName("ParentThumbItemId") val parentThumbItemId: String? = null,
@SerializedName("ParentThumbImageTag") val parentThumbImageTag: String? = null,
```
Note: `ParentBackdropItemId` comes back as a GUID string in JSON (Emby uses string ids throughout; the existing `EmbyItemDto.id: String?` already treats ids as strings).

**Q3 — Image endpoint URL format.**
- Pattern (mirror existing `primaryImageUrl`): `/Items/{itemId}/Images/Backdrop?maxWidth=1280&quality=90&tag={tag}`.
- Indexed form `/Items/{itemId}/Images/Backdrop/0` is equivalent to the non-indexed form with `imageIndex=0` (ImageController L552 vs L630). Use index `0` — the first backdrop is the primary/default backdrop Emby/Jellyfin picks for the item.
- An item can have multiple backdrops (`BackdropImageTags` is an array); MVP scope (per PRD “Out of Scope: Multiple backdrop carousel”) uses only index 0.
- `tag` is the cache tag from `BackdropImageTags[0]` (or `ParentBackdropImageTags[0]`). Including `tag` makes Emby return strong cache headers; omitting it still returns the image but without long-cache headers. Keep `tag` for cache hygiene (matches existing Primary pattern).
- `maxWidth`: 640 is too narrow for a full-bleed 16:9 hero. Recommend `1280` (good for ~720p-class handsets, balances bytes vs. crispness). For a 1080p-class hero use `1920`. This is a tunable; `quality=90` matches the existing Primary setting.

Exact URL builder pattern (mirrors `primaryImageUrl` at `EmbyRepository.kt:268-285`):
```kotlin
private fun backdropImageUrl(itemId: String, token: String, backdropTag: String?): String? {
    if (backdropTag.isNullOrBlank()) return null
    return baseUrl.toHttpUrl()
        .newBuilder()
        .addPathSegment("Items")
        .addPathSegment(itemId)
        .addPathSegment("Images")
        .addPathSegment("Backdrop")
        .addQueryParameter("maxWidth", "1280")
        .addQueryParameter("quality", "90")
        .addQueryParameter("tag", backdropTag)
        .apply { if (!EMBY_HEADER_AUTH_ENABLED) addQueryParameter("api_key", token) }
        .build()
        .toString()
}
```
(Use the non-indexed path; `imageIndex` defaults to 0 server-side. If you ever need the Nth backdrop, switch to `.addPathSegment("Backdrop").addPathSegment(index.toString())`.)

**Q4 — Series vs episode backdrops.**
For an **Episode**, the episode itself almost never has a backdrop; the DtoService walks up Episode → Season → Series and copies the Series backdrop onto the episode’s DTO as `ParentBackdropItemId` + `ParentBackdropImageTags` (DtoService L1642-1651, with the loop terminating at `parent is Series` per L1599). For a **Movie** or **Series**, the item has its own `BackdropImageTags` and `ParentBackdrop*` is left null.

**Recommendation — episode-vs-series backdrop fallback (in priority order):**
1. If `BackdropImageTags` is non-empty → use `BackdropImageTags[0]` with the item’s own `id` (Movie / Series case).
2. Else if `ParentBackdropImageTags` is non-empty → use `ParentBackdropImageTags[0]` with `ParentBackdropItemId` (Episode inherits Series backdrop — the common case).
3. Else if `SeriesId` is non-blank → fall back to building `/Items/{SeriesId}/Images/Backdrop` **without** a `tag` (no tag available; Emby still serves the image, just without strong cache headers). This is a last-resort fallback for incomplete Emby responses where `ParentBackdropImageTags` is missing but `SeriesId` is present.
4. Else → `backdropImageUrl = null`; the UI renders the gradient-scrim fallback (already required by PRD acceptance criterion: “falls back gracefully when no backdrop is available”).

Do NOT request a separate `/Users/{userId}/Items?ParentId=seriesId` round-trip just to get the series backdrop — the parent backdrop tags are already on the episode DTO when the server has them. The `SeriesId`-no-tag fallback (step 3) is cheaper than a network round-trip and still renders an image.

**Q5 — Logo / Thumb images.**
Both `Logo` and `Thumb` are `ImageType` enum members and use the same endpoint pattern `/Items/{id}/Images/{Logo|Thumb}`.
- **Thumb** (`ImageType.Thumb=5`) is a 16:9 landscape thumbnail Emby generates per item (episode thumb, series thumb, movie thumb). It is the **right surface for a 16:9 continue-watching card** — it’s a real frame / official still, already 16:9, no cropping needed. The PRD’s continue-watching 16:9 requirement should use `Thumb` (with `ParentThumbImageTag` + `ParentThumbItemId` fallback for episodes, exactly parallel to the backdrop fallback). The current code uses `Primary` (2:3 portrait) for continue-watching, which is the wrong aspect for a 16:9 card.
- **Backdrop** (`ImageType.Backdrop=2`) is also 16:9 but is a cinematic hero image (wide, often text-free, higher resolution). It is the **right surface for the detail hero** (PRD: “full-bleed 16:9 backdrop with gradient scrim”).
- **Logo** (`ImageType.Logo=4`) is a wide transparent PNG (series/studio logo). Use it as an **overlay on the hero** (title treatment), not as a card thumbnail. For episodes, `ParentLogoImageTag` + `ParentLogoItemId` (the Series) is populated the same way as parent backdrop (DtoService L1609-1618).

To fetch these later, add the same `Fields` values on Emby (`ThumbImageTags`, `LogoImageTags`) and the same DTO fields (`ThumbImageTags: List<String>?`, `ParentThumbItemId/Tag`, `ParentLogoItemId/Tag`). On Jellyfin they’re returned by default; the `Fields` additions are again harmless no-ops. **For this task’s MVP scope (backdrop hero only), only `BackdropImageTags` + the parent-backdrop fields are required.**

**Q6 — Auth.**
Confirmed: image endpoints carry `[Authorize]` (ImageController L552 region) and use the same Jellyfin/Emby auth middleware that reads `X-Emby-Token` header (preferred) or `api_key` query param (fallback). This matches the existing `EMBY_HEADER_AUTH_ENABLED` contract in `.trellis/spec/backend/emby-integration.md` § “Media URL Auth Header and Disk Cache Hygiene”:
- When `EMBY_HEADER_AUTH_ENABLED=true` (default): build the backdrop URL with **no** `api_key` query; `MediaAuthHeaderInterceptor` injects `X-Emby-Token` for the Emby origin. Coil `diskCacheKey = stripAuthQuery(url)` stays clean.
- When `EMBY_HEADER_AUTH_ENABLED=false` (smoke-test fallback): add `api_key=<token>` query (the `apply { if (!EMBY_HEADER_AUTH_ENABLED) addQueryParameter("api_key", token) }` line in the builder above). `stripAuthQuery` still keeps the cache key clean.

No new auth plumbing is needed — the existing `MediaAuthHeaderRegistry` registration in `EmbyRepository.session()` (lines 175-179) already covers image requests for the Emby origin.

### Code sketch — extending `EmbyItemDto.toVideoItem()` and `VideoItem`

`api/EmbyApi.kt` — add fields to `EmbyItemDto`:
```kotlin
data class EmbyItemDto(
    // ... existing fields ...
    @SerializedName("ImageTags") val imageTags: Map<String, String>? = emptyMap(),
    @SerializedName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerializedName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerializedName("ParentBackdropImageTags") val parentBackdropImageTags: List<String>? = null,
    @SerializedName("UserData") val userData: EmbyUserDataDto? = null
)
```

`api/EmbyApi.kt` — extend the `Fields` default:
```kotlin
@Query("Fields") fields: String =
    "Overview,ProductionYear,SeriesId,SeriesName,ParentIndexNumber,IndexNumber,RunTimeTicks,ChildCount,ImageTags,BackdropImageTags,CommunityRating,UserData",
```

`data/EmbyRepository.kt` — add `backdropImageUrl` to `VideoItem` and the builder + mapping:
```kotlin
data class VideoItem(
    // ... existing fields ...
    val imageUrl: String? = null,
    val backdropImageUrl: String? = null,   // NEW
    val streamUrl: String? = null
)

// New builder, mirrors primaryImageUrl(...)
private fun backdropImageUrl(itemId: String, token: String, backdropTag: String?): String? {
    if (backdropTag.isNullOrBlank()) return null
    return baseUrl.toHttpUrl()
        .newBuilder()
        .addPathSegment("Items")
        .addPathSegment(itemId)
        .addPathSegment("Images")
        .addPathSegment("Backdrop")
        .addQueryParameter("maxWidth", "1280")
        .addQueryParameter("quality", "90")
        .addQueryParameter("tag", backdropTag)
        .apply { if (!EMBY_HEADER_AUTH_ENABLED) addQueryParameter("api_key", token) }
        .build()
        .toString()
}

// Inside EmbyItemDto.toVideoItem(libraryId, token):
private fun EmbyItemDto.toVideoItem(libraryId: String, token: String): VideoItem? {
    val itemId = id?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val title = name?.trim()?.takeIf { it.isNotBlank() } ?: return null

    // Backdrop resolution: own -> parent (Series) -> SeriesId no-tag fallback.
    val ownBackdropTag = backdropImageTags.orEmpty().firstOrNull()?.takeIf { it.isNotBlank() }
    val parentBackdropTag = parentBackdropImageTags.orEmpty().firstOrNull()?.takeIf { it.isNotBlank() }
    val resolvedBackdrop: String? = when {
        ownBackdropTag != null -> backdropImageUrl(itemId, token, ownBackdropTag)
        parentBackdropTag != null && !parentBackdropItemId.isNullOrBlank() ->
            backdropImageUrl(parentBackdropItemId!!.trim(), token, parentBackdropTag)
        // Last-resort: Series backdrop with no tag (weak caching, but still renders).
        !seriesId.isNullOrBlank() && seriesId!!.trim().isNotBlank() ->
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("Items").addPathSegment(seriesId!!.trim())
                .addPathSegment("Images").addPathSegment("Backdrop")
                .addQueryParameter("maxWidth", "1280")
                .addQueryParameter("quality", "90")
                .apply { if (!EMBY_HEADER_AUTH_ENABLED) addQueryParameter("api_key", token) }
                .build().toString()
        else -> null
    }

    return VideoItem(
        // ... existing fields ...
        imageUrl = primaryImageUrl(itemId, token, imageTags.orEmpty()["Primary"]),
        backdropImageUrl = resolvedBackdrop,
        streamUrl = if (isDirectlyPlayableVideoType(type)) streamUrl(itemId, token) else null
    )
}
```

`ui/VideoDetailScreen.kt` — consume `video.backdropImageUrl` in the hero `CoverArt` (replace the 72%-width portrait `Surface` at lines 84-103 with a full-bleed 16:9 `CoverArt` + gradient scrim). The existing `CoverArt` already supports a nullable `imageUrl` with a gradient fallback, so no new component is needed — just pass `backdropImageUrl` and `aspectRatio(16f / 9f)` and layer a `Brush.verticalGradient` scrim + overlaid title/meta per the PRD.

### Test sketch (mirror existing `getCatalog_*` tests in `EmbyRepositoryTest.kt`)

```kotlin
// Backdrop tag present on the item itself (Movie/Series).
server.enqueueJson("""{"Items":[{"Id":"m1","Name":"Dune","Type":"Movie",
  "ImageTags":{"Primary":"p1"},"BackdropImageTags":["b1"]}],"TotalRecordCount":1}""")
// -> item.backdropImageUrl contains "/Items/m1/Images/Backdrop" and "tag=b1"

// Episode inherits Series backdrop via parent fields.
server.enqueueJson("""{"Items":[{"Id":"e1","Name":"Ep 2","Type":"Episode","SeriesId":"s1",
  "ParentBackdropItemId":"s1","ParentBackdropImageTags":["sb1"]}],"TotalRecordCount":1}""")
// -> item.backdropImageUrl contains "/Items/s1/Images/Backdrop" and "tag=sb1"

// No backdrop anywhere -> null (UI gradient fallback).
server.enqueueJson("""{"Items":[{"Id":"m2","Name":"Indie","Type":"Movie",
  "ImageTags":{"Primary":"p2"}}],"TotalRecordCount":1}""")
// -> item.backdropImageUrl == null
```

### Related Specs

- `.trellis/spec/backend/emby-integration.md` — § “Thumbnail URL” (Primary URL builder pattern), § “Media URL Auth Header and Disk Cache Hygiene” (`EMBY_HEADER_AUTH_ENABLED` / `MediaAuthHeaderRegistry` / `stripAuthQuery`). The backdrop builder must follow both: path+non-auth params in URL, token via header (or `api_key` fallback), cache key de-authed.
- `.trellis/spec/backend/quality-guidelines.md` — § “Performance-first persistent media chrome” (the hero backdrop is the first concrete application; cache hygiene matters because backdrop bytes are large).

## Caveats / Not Found

- **Emby-original source is closed.** The claim that `Fields=BackdropImageTags` is *required* on Emby (vs. a no-op on Jellyfin) is inferred from the PRD assumption + the fact that the user’s existing `Fields=ImageTags` works on their server. I could not inspect Emby’s `ItemFields` enum directly. The recommended change (add `BackdropImageTags` to `Fields`) is safe under both interpretations: required on Emby, silently dropped + returned-by-default on Jellyfin. If the user’s server is actually Jellyfin (not Emby-original), adding `BackdropImageTags` to `Fields` is harmless and the array will be returned regardless.
- **`maxWidth=1280` for backdrops is a recommendation, not a measured optimum.** Tune against actual handset pixel density and the performance-first spec. `640` (current Primary value) is too narrow for a full-bleed hero; `1920` is sharper but heavier. The PRD should confirm the target.
- **`ParentBackdropItemId` JSON type.** Jellyfin models it as `Guid?`, which serializes as a string. The existing `EmbyItemDto.id: String?` already treats all Emby ids as strings, so `parentBackdropItemId: String?` is consistent. If a server ever returns it as a non-string, Gson would leave it null — acceptable (falls through to the SeriesId-no-tag fallback).
- **Logo / Thumb fields are sketched but out of scope for this task’s MVP** (PRD: backdrop hero only). Listed here so the DTO extension can be done in one pass if desired; the `Fields` additions for `LogoImageTags` / `ThumbImageTags` follow the same Emby-required / Jellyfin-no-op pattern.
- Did not verify against the `demo.jellyfin.org` live server (the `/emby/OpenApi` path 404’d and the ReDoc SPA didn’t render via fetch). All conclusions are from source inspection of the `jellyfin/jellyfin` repo (master + tags v10.7.7 / v10.8.13), which is the canonical reference for the Emby-fork image API.
