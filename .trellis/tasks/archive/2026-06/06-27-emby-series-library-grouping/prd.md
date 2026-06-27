# Emby 电视剧按剧集聚合展示

## Goal

让 Emby 电视剧库在主列表按电视剧/剧集条目展示，而不是把每一集平铺成卡片。用户从电视剧卡片进入详情后，再按季和分集浏览，体验参考 Emby/Yamby 的层级浏览方式。

## Requirements

* 主库列表浏览 `Users/{userId}/Items` 时不展示独立 `Episode` 条目。
* 主库列表仍保留 `Movie`、`Series`、`Video`，避免影响电影库和家庭视频/混合视频。
* 搜索仍可返回 `Movie`、`Series`、`Episode`、`Video`。
* 继续观看和 Next Up 仍可展示分集，保留追剧场景。
* 电视剧卡片继续进入现有 `VideoDetailScreen`，由详情页加载季和分集。
* 收藏、已看/未看筛选、排序、用户状态字段在主库列表请求中继续按现有方式工作。

## Acceptance Criteria

* [ ] 电视剧库主列表请求的 `IncludeItemTypes` 不包含 `Episode`。
* [ ] 电视剧库主列表展示 `Series` 卡片，而不是每一集卡片。
* [ ] 电影库主列表仍展示 `Movie` 卡片。
* [ ] 搜索请求仍包含 `Episode`，可返回分集结果。
* [ ] 继续观看请求仍包含 `Episode`，可返回分集结果。
* [ ] Next Up 仍从 `/Shows/NextUp` 获取并展示分集。
* [ ] 电视剧详情页的季/分集请求仍使用 `Season` / `Episode`。
* [ ] 相关仓库测试更新并通过。

## Definition of Done

* Repository/API 行为符合上面的入口边界。
* 单元测试覆盖主库浏览不请求 `Episode`，并保护搜索/继续观看/分集详情不回退。
* 项目编译、测试、lint 按 Trellis 检查要求执行。
* 如果 Emby 集成契约变化，同步更新 `.trellis/spec/backend/emby-integration.md`。

## Technical Approach

推荐采用最小边界变更：只改变库浏览路径的 item types，使 `EmbyRepository.getLibraryItems(...)` 调用 `EmbyApi.getItems(...)` 时显式传入 `Movie,Series,Video`。保留 API 默认或其他调用点对 `Episode` 的显式使用，避免搜索、继续观看、Next Up、季分集钻取受到影响。

## Decision (ADR-lite)

**Context**: 当前 `EmbyApi.getItems` 默认 `IncludeItemTypes=Movie,Series,Episode,Video`，主库浏览会把电视剧分集平铺到列表中。已有 `VideoDetailScreen` 能处理 `Series -> Season -> Episode` 的层级浏览。

**Decision**: 主库浏览排除 `Episode`，把分集保留在搜索、继续观看、Next Up 和电视剧详情页中。

**Consequences**: 主列表更符合剧集浏览心智，同时不牺牲追剧和搜索入口。后续如果需要按库类型定制更多 Emby 行为，可以在仓库层扩展更明确的 include-type 常量或查询对象。

## Out of Scope

* 不做 UI 大改版或卡片视觉重设计。
* 不新增电视剧季/分集详情能力，沿用现有详情页能力。
* 不实现服务端分页、跨库聚合去重或本地 episode-to-series 聚合。
* 不改变 Plex/WebDAV 或其他视频源行为。

## Technical Notes

* `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt` 中 `getItems` 当前默认包含 `Episode`。
* `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt` 的 `getLibraryItems(...)` 直接映射 `getItems` 返回结果到 `VideoItem`。
* `app/src/main/java/com/nordic/mediahub/ui/VideoDetailScreen.kt` 已对 `Series` 加载 seasons/episodes。
* `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt` 已有主库请求、搜索、继续观看、Next Up、seasons/episodes 相关断言，需要更新主库浏览断言并保护其他入口。
* `.trellis/spec/backend/emby-integration.md` 的浏览 MVP 契约当前写明主库 item listing 包含 `Episode`，本任务完成后需要同步修订。
