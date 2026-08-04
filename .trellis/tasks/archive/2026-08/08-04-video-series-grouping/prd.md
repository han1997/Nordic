# 视频电视剧整体展示逻辑

## Goal

调整视频浏览层的电视剧展示逻辑：电视剧在浏览页应作为一个整体展示，单集只应出现在“继续观看”和电视剧详情页的分集列表中，避免首页/全部列表/推荐区被单集拆散。

## What I already know

* 用户要求：视频的电视剧部分除了继续观看，不应该使用单集展示；一部电视剧是一个整体，除非点进电视剧详情。
* 当前 `VideoScreen.kt` 使用 `videos` 直接生成 `visibleVideos`、`topRatedVideos`、`unplayedVideos` 和网格列表，因此 `Episode` 会进入浏览层。
* 当前 `continueWatchingShelf(videos)` 会从全量 `videos` 中挑选续播项，适合继续保留单集级续播入口。
* 当前 `VideoDetailScreen.kt` 对 `Series` 使用 `relatedEpisodesFor(series)` 展示详情页分集列表。
* 当前 `VideoTypeFilter` 包含 `Episodes("单集")`，浏览层会出现单集筛选。
* 相关文件：`VideoScreen.kt`、`VideoScreenLogic.kt`、`VideoBrowseComponents.kt`、`VideoDetailScreen.kt`、`EmbyRepository.kt`、`VideoScreenTest.kt`。

## Assumptions

* 本任务只改变视频浏览 UI 的展示集合，不改变 Emby repository 拉取的数据；全量 `videos` 仍保留 Episode 以供继续观看和详情页分集使用。
* “继续观看”可以继续展示单集，因为用户明确允许该例外。
* 电影、独立视频、电视剧 Series 仍在浏览层展示。
* 搜索和类型筛选作用于浏览层展示集合，不返回单集；想找单集应先进入电视剧详情。

## Requirements

* 浏览层“全部 N 项”网格不展示 `Episode` 类型条目。
* 非继续观看推荐区（如“最受好评”“未播放的”）不展示 `Episode` 类型条目。
* “继续观看”继续允许展示可续播单集。
* 电视剧详情页继续展示对应分集列表，并可播放单集。
* 浏览层类型筛选不应提供“单集”筛选；若刷新后当前筛选为单集，应回到“全部”。
* 相关纯逻辑需有单元测试覆盖。

## Acceptance Criteria

* [ ] 全部网格列表不出现 Episode 条目。
* [ ] 搜索结果不出现 Episode 条目。
* [ ] “最受好评”“未播放的”等浏览推荐不出现 Episode 条目。
* [ ] “继续观看”仍可出现 Episode 条目。
* [ ] 点击电视剧 Series 进入详情后仍能看到分集列表。
* [ ] `VideoScreenTest` 覆盖浏览集合过滤和筛选回退逻辑。
* [ ] 更新 `CHANGELOG.md` 记录用户可见行为变化。

## Definition of Done

* 实现符合现有 Compose/token/spec 规范。
* 相关单元测试通过。
* `compileDebugKotlin` 通过；如仅改 UI 逻辑，运行聚焦测试即可，必要时运行 lint。
* 更新日志记录本次视频浏览行为变化。

## Out of Scope

* 不改变 Emby API 请求参数和数据层分页策略。
* 不新增季级页面或季分组 UI。
* 不重做播放器页面。
* 不删除 Episode 数据模型字段或详情页分集能力。

## Technical Approach

* 在 `VideoScreenLogic.kt` 增加浏览层过滤/筛选辅助函数，例如 `visibleVideoCatalogItems(...)` 或等价逻辑，只允许非 Episode 项进入浏览网格和非继续观看推荐。
* `continueWatchingShelf(...)` 继续从全量 `videos` 计算。
* `relatedEpisodesFor(series)` 继续从全量 `videos` 查找。
* `VideoTypeFilter` 的浏览可见 filters 排除 `Episodes`，刷新/过滤回退逻辑将 `Episodes` 视为不可用时回到 `All`。
* 更新 `VideoScreenTest.kt` 覆盖关键行为。

## Technical Notes

* Inspected: `VideoScreen.kt`, `VideoScreenLogic.kt`, `VideoBrowseComponents.kt`, `VideoDetailScreen.kt`, `EmbyRepository.kt`, `VideoScreenTest.kt`.
