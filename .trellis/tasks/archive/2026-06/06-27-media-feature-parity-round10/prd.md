# 媒体功能完善第十轮

## Goal

继续参考 Emby / Yamby 成熟视频客户端，完善 Nordic Media Hub 的视频体验。本轮聚焦 Tier 1 功能缺口：全局搜索、排序筛选、Next Up 剧集推荐、标记已看/未看、收藏。

## What I Already Know

- 视频模块已实现：认证、媒体库浏览、详情页、直接播放、进度上报、继续观看、单集进度、播放速度、音轨/字幕选择、字幕缩放、播放器手势控制。
- Emby API 端点未使用：`/Search/Hints`、`/Shows/NextUp`、`/Users/{userId}/FavoriteItems/{id}`、`/Users/{userId}/PlayedItems/{id}`。
- 当前 `getItems` 不支持 `SortBy`、`SortOrder`、`Filters` 等排序筛选参数。
- 视频首页只有媒体库选择 + 继续观看 + 项目列表，搜索、排序筛选、Next Up、收藏均缺失。
- 研究结果已保存在 `research/emby-yamby-features.md`，包含完整的 Tier 1/2/3 功能差距分析。

## Requirements (evolving)

- 全局视频搜索：搜索框、调用 Emby Search/Hints API、按类型分类显示结果。
- 排序筛选：在视频列表上方添加排序/筛选控件，支持 Emby 的 SortBy、SortOrder、Filters 参数。
- Next Up 剧集推荐：调用 `/Shows/NextUp`，在首页展示"下一集"横排卡片。
- 标记已看/未看：在详情页和列表项提供已看/未看切换，调用 PlayedItems API。
- 收藏：在详情页和列表项提供收藏按钮，调用 FavoriteItems API；支持按收藏筛选。

## Acceptance Criteria (evolving)

- [ ] 搜索可用：输入关键词后返回 Emby 搜索结果，按 Movie/Series/Episode 分组展示
- [ ] 排序筛选可用：支持至少 3 种排序方式 + 已看/未看/收藏筛选
- [ ] Next Up 展示：追剧用户能在首页看到下一个未看集推荐
- [ ] 标记已看/未看：详情页可切换已看状态，切换后 Emby 服务端同步更新
- [ ] 收藏功能：详情页可收藏/取消收藏，列表支持按收藏筛选
- [ ] Repository 单测覆盖新 API 映射
- [ ] compileDebugKotlin / testDebugUnitTest / lintDebug 通过

## Definition of Done

- Tests added or updated for repository mapping behavior
- Lint / type-check / test gates pass sequentially on Windows
- Specs updated if the new API contracts become reusable conventions
- Changes committed before finish-work

## Out of Scope (explicit)

- Tier 2/3 功能：Autoplay Next Episode、Skip Intro、Library Hiding、Similar Items、Latest Items、Cast/Crew、PIP、Chromecast、Download/Offline、Live TV、Transcoding、Parental Controls
- 搜索历史/建议
- Genre/Studio 独立浏览页

## Technical Notes

- 任务目录：`.trellis/tasks/06-27-media-feature-parity-round10`
- 关键文件：
  - `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`
  - `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`
  - `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`
  - `app/src/main/java/com/nordic/mediahub/ui/VideoDetailScreen.kt`
  - `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`
- 适用规范：`.trellis/spec/backend/emby-integration.md`
- 研究参考：`research/emby-yamby-features.md`
