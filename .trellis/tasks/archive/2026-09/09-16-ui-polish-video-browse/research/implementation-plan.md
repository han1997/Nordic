# 视频浏览第四轮：源码审查与实施边界（2026-09-16）

## 审查结论总览

生产入口：`VideoScreen.kt`（718，仓库/缓存/刷新 + LazyVerticalGrid Home，选中条目时改渲染 `VideoDetailScreen`）。浏览积木已拆到 `VideoBrowseComponents.kt`（384）。详情 `VideoDetailScreen.kt`（414）已是纯 UI。纯函数 `VideoScreenLogic.kt`（344）+ 单测 `VideoScreenTest.kt`。

共享复用度高：`MediaPageHeader`、`MediaChoiceChip`（库选择 + 类型筛选 + 分集筛选）、`MediaSearchField`、`CoverArt`、`MusicCollectionDescription`、`PrimaryActionButton`/`SecondaryActionButton`、`MediaStateCard`/`MediaLoadingCard`、`videoShelfCardSize`（推荐海报）。因此本轮**以内容层抽取 + 对比/语义/触控收口为主**，不复制音乐轮的组件抽取阶段。

09-07 已修：节头 heading+SemiBold、推荐卡 fontScale 增长、分集筛选迁 chip、简介展开。本轮回归这些点，不重做。

有声书第三轮对照：次级文字实色 `onSurfaceVariant`、整卡 `Role.Button`、封面 `clearAndSetSemantics`、错误显式重试、当前行 `primaryContainer`+`selected`。视频浏览多处仍是旧写法。

## 具体问题清单

### A. Home 栅格（VideoScreen + BrowseComponents）

1. **A1 次级文字合成对比**：`VideoCard` meta 用 `onSurface.copy(alpha = NordicAlpha.subtle=0.5)`，卡底 `surfaceVariant.copy(alpha=0.42)`。与有声书摘要卡同类，浅色约 3.2:1。`VideoSpotlightSections` 空态「暂无推荐」同样。改为实色 `onSurfaceVariant`。
2. **A2 整卡语义**：`VideoCard` / `ContinueWatchingCard` `clickable` 未声明 `Role.Button` / `onClickLabel`。封面 `contentDescription = title` 与下方标题重复朗读。封面包 `clearAndSetSemantics {}`，整卡 `Role.Button` + 打开详情标签。
3. **A3 续播卡触控与尺寸**：中心播放装饰 `Modifier.size(44.dp)` < 48dp。卡宽写死 `240.dp`，未走 fontScale 增长（推荐海报已有 `videoShelfCardSize` 132→176）。新增 `continueWatchingCardWidth(fontScale)`：基宽 240 × 钳制 scale，上限另行定（避免 2× 时 480dp 占满 720 仍可，但 360dp 设备会只露一张——上限建议 280 或 300）。播放装饰提到 ≥48dp，或取消独立按钮、整卡点击（当前就是整卡点击，中心 Icon 是装饰 → 保持 `contentDescription=null`，尺寸提到 48）。
4. **A4 错误重试**：`standaloneError` 只有 hint「检查配置或点击刷新重试」，无显式按钮。对齐有声书：卡下 `SecondaryActionButton("重试")` 调同一 `refreshVideo()`。
5. **A5 状态互斥**：错误/未配置/无库/空库/无匹配/loading 已是 `when` 互斥，优于有声书修前。保留；确认错误+无缓存不叠「没有内容」（当前 `standaloneError` 在 when 之前单独 item，**会与后续空态同时出现**）。把错误并入 when 或让空态分支要求 `standaloneError == null`（空库分支已要求，无匹配/else 未要求）。
6. **A6 海报间距**：栅格 `vertical=lg, horizontal=md`。不强制 8dp。横向架 `spacedBy(md)` 可保留。
7. **A7 搜索收起**：`VideoBrowserControls` 未展开时只剩一个搜索 IconButton，筛选 chip 仍在。短屏大字体下搜索+筛选+节头是否把海报顶出，实测。IME：搜索展开后 `MediaSearchField`，收起清除 query——回归即可。
8. **A8 页头副标题**：已按状态切换（刷新错误/缓存年龄/匹配数/已连接/未配置），符合「根页副标题只保留状态语义」。不改文案风格。

### B. 详情（VideoDetailScreen）

9. **B1 详情未传 isCurrent**：`VideoEpisodeRow(isCurrent)` 只在 `VideoPlayerPanels` 选集传入。详情 `items { VideoEpisodeRow(..., onClick) }` 默认 false，当前集无高亮。新增 `resolveVideoDetailCurrentEpisode(video, relatedEpisodes): VideoItem?`：Series 用 `resolveVideoDetailPlayTarget`；可播单集用自身 id。传入 `isCurrent = episode.id == current?.id`。
10. **B2 高亮行文字色**：`isCurrent` 只改了 Row 背景为 `primaryContainer`，标签/标题/meta 仍 `onSurface` / `onSurface.copy(alpha=subtle)`，未切 `onPrimaryContainer`。对照有声书章节行补配对色。
11. **B3 分集行语义**：`clickable` 无 `Role.Button`；封面 `contentDescription = title` 与标题重复；不可播时 `enabled=false` 已有。补角色、封面去重、禁用语义。
12. **B4 空筛选文案**：「没有未看的分集」用 `onSurface`+subtle。改 `onSurfaceVariant`。
13. **B5 Hero overlay**：白字+黑渐变，quality-guidelines 已记例外。本轮不改颜色。返回钮、主/次按钮已用共享组件。MetaChip 在黑底上的对比需样板里看一眼，不够再加 scrim，不加新业务。
14. **B6 简介**：已复用 `MusicCollectionDescription`。节头「简介」无 heading——分集节头有 heading，简介是 titleMedium。给简介节头补 `heading()` 以对齐有声书详情。

### C. 抽取与样板

15. **C1 VideoHomeContent**：从 `VideoScreen` LazyVerticalGrid 抽出，参数覆盖：libraries/selectedLibraryId/videos/visibleVideos/browseVideos、三架数据、search/filter 状态、loading/error/notices/ready、callbacks。`VideoScreen` 选中详情时仍 early-return `VideoDetailScreen`。
16. **C2 不在 debug 镜像 Home**：有声书 `AudiobookHomeContent` 只活在 debug，已开始漂移（错误卡无重试按钮）。本轮生产抽取。
17. **C3 UiSampleScreen**：`VideoHome("video_home")`、`VideoSearch("video_search")`、`VideoDetail("video_detail")`、`VideoSeries("video_series")`。搜索是 Home 的 expanded 状态，不是独立生产页。
18. **C4 状态矩阵（只做存在的）**：
    - home：normal / long / empty(未配置) / library_empty / loading / refreshing / cached_error / error / no_art
    - search：normal（有 query）/ empty（无匹配）/ long
    - detail（电影）：normal / long / no_art / 无简介
    - series：normal / long / 未看筛选空 / 当前集高亮
    - 不做：播放器状态、PiP、横屏 chrome。

### D. 数据/协议边界（不动）

- Emby 分库缓存、Resume 列表只来自服务器、stale-while-revalidate、请求版本、配置切换清选择，全部不动。
- `browseCatalogVideos` / `visibleBrowseVideos` / `topRatedVideoShelf` / `unplayedVideoShelf` / `resolveVideoDetailPlayTarget` 不改语义；只新增当前集解析若能委托 playTarget。
- 不连真实 Emby；preview/`example.test` 假地址不进网络层。

## Debug 样板计划

- 新 `VideoCatalogSamples.kt`：内存电影 + 剧集 + 分集列表，本地 debug 封面。
- 宿主页头对照真实调用方：根页「视频」+ 设置齿轮(fixed)+ 刷新；详情走 `VideoDetailScreen` 自带返回，不在样板外再套一层 Header。
- `CompositionLocalProvider` 仅在需要时；浏览页不读 skip 秒数。

## 执行顺序

1. 生产抽取 `VideoHomeContent` + 修 A/B 项 + 纯函数/单测；版本 0.1.11→0.1.12。
2. Debug 样板 4 条目 + instrumentation。
3. `r4-baseline-*` 严格基线（源码/APK 指纹 + 系统字号）；`app/build/reports/ui-polish/` 新目录，不覆盖 r1–r3。
4. 修整后同参数重跑 + 前后对比；正常/短屏交互。
5. 全量门禁 + `video-ui.md` + 文档 + 提交计划。

若样板必须先有才能拍基线：先抽内容层（视觉不变）→ 基线 → 再对比/语义修整。抽取 PR 不得夹带颜色改动，否则基线不是「修前」。

## 不变边界

- 只操作 nordic-ui-api34 / emulator-5580；ADB `-s`。
- 不改 playback engine、Emby repository、MainActivity 视频交接、PiP。
- 保留前三轮封存证据；失败批次另开新名。
