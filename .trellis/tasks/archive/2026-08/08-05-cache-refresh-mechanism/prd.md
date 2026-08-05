# 完善缓存与刷新机制

## Goal

完善 Nordic 媒体中心的缓存与刷新机制，让各媒体域（Music / Audiobook / Video）在启动、配置变更、网络失败等场景下有一致、可靠的本地缓存与刷新体验。

## What I already know

* **Music (Navidrome)** 已有完整缓存：
  - `NavidromeMusicCacheRepository`：JSON 存于 DataStore，按 `config.cacheKey()`（url|user|schemaVersion）隔离，schema v3。
  - 启动流程：`applyCachedMusicData()` 先显示缓存 → `refreshMusicData()` 后台拉取并写回缓存。
  - 失败处理：有缓存时显示"正在显示上次缓存：..."，无缓存时显示"连接失败"。
  - 跟踪 `cacheUpdatedAtMillis` 并在 UI 展示缓存年龄。
* **Audiobook (AudiobookShelf)** 无缓存：
  - `refreshAudiobooks()` 直接拉 libraries + items；启动时只调用刷新，无缓存层。
  - 失败时只显示错误，无缓存兜底；无 `cacheUpdatedAtMillis`。
* **Video (Emby)** 无缓存：
  - `refreshVideo()` 直接拉 catalog；启动时只调用刷新，无缓存层。
  - 失败时只显示错误，无缓存兜底；无 `cacheUpdatedAtMillis`。
* 三个域都有 config 变更版本守卫（`xxxConfigStateVersion`）防止旧请求回写。
* 持久化约定（见 `database-guidelines.md`）：无 Room/ORM，用 DataStore Preferences + JSON；缓存字段语义变更需 bump schema version；缓存 key 需含 config 身份。

## Scope (confirmed)

* **补齐**：为 Audiobook (AudiobookShelf) 和 Video (Emby) 增加 cache-then-refresh + 失败兜底机制，对齐 Music 现有模式。
* **增强**：改进 Music 现有缓存机制（TTL/过期策略、缓存清理、统一缓存年龄展示）。
* **Detail 缓存**：Music 三类 detail + Audiobook 条目详情增加 cache-then-refresh。

## Open Questions

* （已全部解决，见下方 Decision）

## Requirements (evolving)

### 补齐 Audiobook/Video 缓存
* 为 Audiobook 增加 config-scoped 缓存（参照 `NavidromeMusicCacheRepository`），启动时 cache-then-refresh，失败时显示缓存兜底。
* 为 Video 增加 config-scoped 缓存，同上。
* 缓存字段（browse 级）：
  - Audiobook：`libraries` + `items`（`AudiobookLibrarySummary` + `AudiobookItemSummary`）
  - Video：catalog = `libraries` + `videos`（`EmbyLibrary` + `VideoItem`）

### 增强 Music 缓存
* **TTL/过期策略**：browse 缓存有效期 30 分钟；启动时若未过期则跳过后台刷新，过期则照常 cache-then-refresh。
* **缓存清理**：切换账号/服务器后清理旧 config 的缓存，避免 DataStore 堆积无用缓存。
* **统一缓存年龄展示**：Music 已有缓存年龄提示，扩展到 Audiobook/Video，三域 UI 一致。

### Detail 缓存
* 打开 detail 页时：先显示缓存 detail（若有）→ 后台拉取 → 写回缓存 → 更新 UI。
* 拉取失败且缓存存在：显示缓存 + 提示；无缓存：显示错误。
* 适用范围：
  - Music：专辑曲目（`albumDetailSongs`）、歌手专辑（`artistAlbums`）、歌单曲目（`playlistSongs`）
  - Audiobook：条目详情（`selectedItem` / `AudiobookItemDetail`）
  - Video：无需单独 detail 缓存（`relatedEpisodes` 由已缓存的 `videos` 派生）
* Detail 缓存 config-scoped（按 config + item id 隔离），无 TTL（打开始终后台刷新），随账号切换清理。

### 行为约定
* 手动刷新（↻）始终绕过 TTL 强制拉新。
* TTL 过期且网络失败：显示旧缓存 + 过期/离线提示。
* 搜索结果不缓存。

## Decision (ADR-lite)

**Context**: 三域缓存机制不对称（仅 Music 有缓存），且 Music 缓存无过期/清理策略，导致 Audiobook/Video 启动慢、离线无兜底，Music 缓存可能过期堆积。

**Decision**:
1. 补齐 Audiobook/Video 的 config-scoped JSON 缓存，复用 Music 的 cache-then-refresh + 失败兜底模式。
2. Music 增加 30 分钟 browse TTL；手动刷新绕过 TTL。
3. 三域统一缓存年龄展示；切换 config 时清理旧缓存。
4. 增加 detail 级缓存（Music 三类 + Audiobook 条目详情），打开 detail 时 cache-then-refresh；Video detail 由 browse 缓存派生。

**Consequences**:
- 三个域缓存体验一致，启动快、离线有兜底。
- Detail 缓存增加存储与复杂度，但范围可控（config-scoped + 随切号清理）。
- TTL 引入"数据可能滞后 30 分钟"的权衡，手动刷新作为逃生口。

## Technical Approach

### 缓存基础设施
* 复用 DataStore Preferences + Gson JSON 模式（不引入 Room，遵循 `database-guidelines.md`）。
* 每域一个 cache repository，config-scoped key（含 url|user|schemaVersion），独立 schema version。
* 抽取共享 TTL 常量 `CACHE_TTL_MILLIS = 30 * 60 * 1000L` 与 `isCacheFresh(updatedAtMillis)` 判定。
* 抽取 `formatCacheAge(updatedAtMillis)` 到共享位置（现 Music 私有），三域复用。

### 新增 cache repository
* `AudiobookCacheRepository`：browse（libraries + items）+ detail（item id → `AudiobookItemDetail`），schema v1。
* `EmbyVideoCacheRepository`：browse（libraries + videos），schema v1。
* 参照 `NavidromeMusicCacheRepository` 的 `load`/`save`/`buildCache` + config-scoped key 模式。

### Music 缓存扩展
* `NavidromeMusicCacheRepository` 增加 detail 缓存方法（album/artist/playlist songs 按 id 存取）。
* `MusicScreenV2` 启动流程：`applyCachedMusicData` 后判定 TTL，未过期则跳过 `refreshMusicData`。
* detail 打开流程：`openAlbumDetail`/`openArtistDetail`/`openPlaylistDetail` 改为 cache-then-refresh。

### Audiobook/Video 接入
* `AudiobookScreen` / `VideoScreen` 启动 `LaunchedEffect` 改为 cache-then-refresh（参照 Music）。
* `AudiobookScreen.openItemDetail` 改为 cache-then-refresh。
- 三域 header subtitle 增加缓存年龄提示（参照 Music 现有 `cacheAgeLabel`）。

### 缓存清理
* config 变更且新 config 与旧 config 的 cacheKey 不同时，清除旧 cacheKey 对应的缓存 JSON。
- 各 cache repository 提供 `clear(config)` 方法。

## Acceptance Criteria

* [ ] Audiobook 启动时先显示缓存（若有）再后台刷新；网络失败时有缓存则显示缓存兜底。
* [ ] Video 启动时先显示缓存（若有）再后台刷新；网络失败时有缓存则显示缓存兜底。
* [ ] Music 启动时若缓存未过期（< 30 分钟）跳过后台刷新；过期则 cache-then-refresh。
* [ ] 手动刷新（↻）在所有域始终绕过 TTL 强制拉新。
* [ ] TTL 过期且网络失败时，显示旧缓存 + 过期/离线提示（非空错误态）。
* [ ] 切换账号/服务器后，旧 config 的缓存被清理，不堆积。
* [ ] Music 打开专辑/歌手/歌单详情时先显示缓存再后台刷新；Audiobook 打开条目详情同上。
* [ ] Video 详情由已缓存 `videos` 派生，无需额外请求。
* [ ] 三域 header 一致展示缓存年龄（如"3 分钟前更新"）。
* [ ] 各域缓存按 config（url|user|schemaVersion）隔离，换号不串数据。
* [ ] 缓存 schema 变更时 bump version，旧缓存自动失效。
* [ ] `gradlew.bat :app:compileDebugKotlin` / `:app:testDebugUnitTest` / `:app:lintDebug` 通过。
* [ ] 新增 cache repository 与 TTL/cleanup 逻辑有单元测试。

## Out of Scope (explicit)

* 搜索结果缓存（三域搜索均不缓存）。
* Video detail 独立缓存（由 browse `videos` 派生）。
* 后台定时刷新 / WorkManager 周期同步。
* Room/ORM 引入。
* 缓存大小配额 / LRU 淘汰（仅做切号清理）。
* 跨设备缓存同步。

## Definition of Done (team quality bar)

* Tests added/updated (unit/integration where appropriate)
* Lint / typecheck / CI green（`gradlew.bat :app:compileDebugKotlin` / `:app:testDebugUnitTest` / `:app:lintDebug`）
* Docs/notes 更新 if behavior changes
* 遵循 `database-guidelines.md` 缓存约定（schema version、config-scoped key、不引入 Room）

## Technical Notes

* 参考实现：`app/src/main/java/com/nordic/mediahub/data/NavidromeMusicCacheRepository.kt`
* 刷新编排：`app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`（`applyCachedMusicData` / `refreshMusicData`）
* 三域刷新函数：`MusicScreenV2.refreshMusicData`、`AudiobookScreen.refreshAudiobooks`、`VideoScreen.refreshVideo`
* 持久化规范：`.trellis/spec/backend/database-guidelines.md`（Cache Storage 一节）
* 质量规范：`.trellis/spec/backend/quality-guidelines.md`
