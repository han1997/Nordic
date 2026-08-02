# Research: Prevent auth tokens in media URLs from persisting to disk (ExoPlayer/Media3 + Coil)

- **Query**: How to keep ExoPlayer SimpleCache + Coil disk cache working while ensuring token-bearing media URLs are not written to disk.
- **Scope**: mixed (internal codebase + external server-auth protocol docs)
- **Date**: 2026-08-02

## Findings

### Files Found (codebase)

| File Path | Relevance |
|---|---|
| `app/.../data/EmbyRepository.kt:264,276` | `?api_key=<token>` appended to image + stream URLs |
| `app/.../data/NavidromeAuth.kt:47-56` | `addNavidromeAuth` adds `u`,`t`,`s`,`v`,`c` query params (`t=md5(password+salt)`) |
| `app/.../data/NavidromeRepository.kt:87` | `addNavidromeAuth(config)` on stream/cover URLs |
| `app/.../data/AudiobookShelfRepository.kt:329-345` | `toAbsoluteAudioUrl` appends `?token=<bearer>` to audio URLs; cover URLs get NO token |
| `app/.../playback/MusicPlaybackService.kt:48-60` | `SimpleCache` (`cacheDir/exo_player_cache`, 100 MB LRU) + `CacheDataSource.Factory` over `OkHttpDataSource.Factory`; **no `CacheKeyFactory` set** → cache key = full token-bearing URL |
| `app/.../playback/VideoPlaybackEngine.kt:61` | Plain `ExoPlayer.Builder(appContext).build()` — **no disk cache** for video |
| `app/.../MainActivity.kt:203-213` | Coil `ImageLoader` with custom `okHttpClient` but **no `diskCache` configured** → Coil uses its default disk cache, key = URL (with token) |
| `app/.../api/EmbyApi.kt:100,106,112…` | Already uses `@Header("X-Emby-Token")` for all JSON API calls — proves Emby header auth works |
| `app/.../api/AudiobookShelfApi.kt:218` | Already uses `@Header("Authorization") bearerToken` for all `/api/*` calls |
| 13 `AsyncImage(model = <url>, …)` call sites (Music/Video/Audiobook UI) | Pass token-bearing URL strings as the Coil `model` |
| `app/build.gradle.kts:54,59-62` | `coil-compose:2.5.0`, `media3-*:1.3.1`, `okhttp:4.12.0` |

### Server header-auth support (external)

- **Emby** — `X-Emby-Token` header is the primary auth; `?api_key=` query is an alias accepted on **all** endpoints, including `Items/{id}/Images/Primary` and `Videos/{id}/stream`. Codebase already relies on the header for API calls. (Live Emby doc page was unreachable; grounded in codebase usage + Emby auth-filter behaviour. Recommend a 1-line runtime smoke-test on the stream/image endpoint.)
- **Subsonic / Navidrome** — [subsonic.org/pages/api.jsp](https://www.subsonic.org/pages/api.jsp) confirms auth is **strictly query-param based**: `u`, `t`, `v`, `c` plus `t`+`s` where `t = md5(password + salt)`. **No header option exists.** Important nuance: the leaked `t` is a **single-use salted one-way hash**, not the reusable password — so a persisted Navidrome URL exposes a replay token for that request only, not the credential. Severity is lower than Emby/ABS bearer tokens.
- **AudiobookShelf** — `server/Auth.js` (master) shows the JWT extractor:
  `jwtFromRequest: ExtractJwt.fromExtractors([fromAuthHeaderAsBearerToken(), fromUrlQueryParameter('token')])`
  → ABS accepts `Authorization: Bearer` **or** `?token=` on the same routes. Cover endpoints (`/items/{id}/cover`, `/authors/{id}/image`) are in `ignorePatterns` → **unauthenticated** (the repo correctly omits tokens for cover URLs).

### What Media3 1.3.1 actually persists to disk

`SimpleCache` writes cached byte spans plus a `cached_content_index` journal. The index stores **the cache key**, which defaults to `DataSpec.uri.toString()` (the full token-bearing URL). A custom `CacheDataSource.Factory.setCacheKeyFactory { … }` controls **exactly** which string becomes the key and is the only URL-like identifier persisted. No separate request-URL log is written by the progressive cache. → A cache key that strips auth params means **the token never reaches disk**; the upstream `OkHttpDataSource` request still carries the token over the network/in-memory only.

## Approaches

### 1. Disable disk cache for authed URLs
- **ExoPlayer**: drop `setCache(cache!!)` / remove `SimpleCache`. **Coil**: `ImageLoader.Builder.diskCache(null)`.
- **All three servers**: feasible, zero server changes. **Loses** the 100 MB LRU buffer (re-download on replay/seek, worse offline) and the image disk cache (re-fetch every launch). Migration is tiny but UX regression is high — defeats the `SimpleCache` the codebase intentionally built.

### 2. Local token-injecting proxy (NanoHTTPD / custom `HttpDataSource.Factory`)
- ExoPlayer/Coil request `http://127.0.0.1:PORT/<mediaId>` (token-free); the proxy maps `mediaId → (realUrl, token)` and forwards with token injected as header/query. Keeps disk cache perfectly clean (key = localhost URL).
- **Works for all three servers** (proxy can emit Emby `X-Emby-Token`, Subsonic `u/t/s`, ABS `Authorization`). **Complexity is high**: a mediaId→server registry, proxy lifecycle, port management, HTTP **range-request** forwarding (essential for ExoPlayer seeks), and a separate Coil `okHttpClient` pointing at localhost. Migration touches URL construction in all 3 repositories + playback + Coil. Over-engineered relative to the actual risk.

### 3. Custom cache key that strips auth from the key (token stays in the request)
- **ExoPlayer**: `CacheDataSource.Factory().setCacheKeyFactory { dataSpec -> stripAuthQuery(dataSpec.uri.toString()) }`. Index/bytes are keyed by the de-authed URL; `OkHttpDataSource` still fetches the full URL (network only). **What is persisted: the de-authed URL — not the token.**
- **Coil (2.5.0)**: `ImageRequest.Builder(ctx).data(urlWithToken).diskCacheKey(stripAuth(urlWithToken)).build()` passed as the `AsyncImage` `model` (also `.memoryCacheKey(...)`). Coil 2 supports per-request `diskCacheKey(String)`.
- **All three servers**: feasible, no server changes. Keeps disk cache **fully working** (same bytes, cleaner keys). Migration: moderate — one `CacheKeyFactory` in `MusicPlaybackService` (~10 lines) + switching ~13 `AsyncImage(model = string)` sites to an `ImageRequest` (eased by a small `AuthedAsyncImage(url)` helper).
- **Limitation**: token still travels in the URL over the network and in process memory; if OkHttp logging is ever raised above `NONE` (NavidromeRepository.kt:65 already pins `NONE`) the token can hit logcat. Combine with #4 where the server allows header auth.

### 4. Per-request auth via headers instead of URL params
- Move the token out of the URL into a header; the cache key is then naturally token-free.
- **Emby**: `X-Emby-Token` header (proven on API). ExoPlayer: `OkHttpDataSource.Factory(client).setDefaultRequestProperties(mapOf("X-Emby-Token" to token))` (shared-client = global; per-item tokens need a per-request OkHttp `Interceptor`). Coil: OkHttp `Interceptor` on the ImageLoader client adds the header + strips `api_key`.
- **ABS**: `Authorization: Bearer <token>` header (confirmed by `Auth.js` extractors). Drop `?token=` from `toAbsoluteAudioUrl`.
- **Navidrome**: **not supported** — Subsonic API requires `u/t/s/v/c` query params. Must fall back to #3.
- Keeps disk cache working (URL is clean so even the default cache key is token-free). Migration: moderate — OkHttp interceptors / `setDefaultRequestProperties`, drop `api_key` from `EmbyRepository.streamUrl`/`primaryImageUrl`, drop `?token` from ABS `toAbsoluteAudioUrl`.

## Recommendation

Combine **#3 (always, all servers)** with **#4 (Emby + ABS)**; leave Navidrome on #3-only.

- **ExoPlayer (MusicPlaybackService)**: add a `CacheKeyFactory` stripping `api_key`/`token`/`u`/`t`/`s`/`v`/`c` → removes tokens from `exo_player_cache` index immediately, zero server dependency. Then for Emby streams switch to `X-Emby-Token` header (drop `api_key` from `EmbyRepository.streamUrl`); for ABS drop `?token` from `toAbsoluteAudioUrl` and add `Authorization: Bearer` on the shared `OkHttpClient` used by `OkHttpDataSource`. Navidrome keeps query params but the clean cache key prevents disk persistence.
- **VideoPlaybackEngine**: no disk cache today, so only the in-memory URL matters — still worth moving Emby video streams to `X-Emby-Token` to keep the token out of `MediaItem.uri`/logcat.
- **Coil**: add an OkHttp `Interceptor` on the ImageLoader that injects `X-Emby-Token` / `Authorization: Bearer` and strips the matching query param; for Navidrome cover art (query param required) set `.diskCacheKey(stripAuth(url))`. Wrap `AsyncImage` call sites in a helper that builds the `ImageRequest` with the clean disk cache key.
- **Avoid** #1 (loses the intentionally-built cache) and #2 (over-engineered for the risk).

This keeps the existing `SimpleCache` design intact, removes tokens from disk for all three servers, and uses header auth only where the server supports it.

## Caveats / Not Found

- Emby stream/image endpoint accepting `X-Emby-Token` header is grounded in codebase API usage + known Emby auth-filter behaviour; the live `api.emby.media` doc was unreachable. A 1-line smoke test (`curl -H "X-Emby-Token: …" <stream url>`) should confirm before implementation.
- Media3 reference page (`CacheDataSource.CacheKeyFactory`) repeatedly timed out; the persistence description is based on Media3 1.3.1 source behaviour (`SimpleCache`/`CachedContentIndex`). The codebase already depends on this version.
- Coil 2's exact cache-key resolution after an `Interceptor` rewrites `data` was not byte-verified against source; the safe path is to set `diskCacheKey` explicitly rather than rely on interceptor-side URL stripping for the key.
