# 视频浏览、详情与搜索 UI 合同

## 1. 范围 / 触发

适用于 `VideoScreen.kt`（Home 与数据宿主）、`VideoBrowseComponents.kt`（媒体库选择、卡片、推荐架、搜索与筛选）和 `VideoDetailScreen.kt`（详情与分季选集）。播放器、PiP、横竖屏、连播与窗内面板遵循后续视频播放器任务及既有 Emby 合同。

## 2. 入口与组件

- `VideoHomeContent` 是无副作用生产内容层；Emby repository、缓存、刷新、配置切换与 Resume 数据流留在 `VideoScreen`。
- `VideoCard` 与 `ContinueWatchingCard` 整卡使用 `Role.Button` 和明确 `onClickLabel`，封面包裹 `clearAndSetSemantics`，避免与标题重复朗读。
- `VideoLibrarySelector`、类型筛选和详情分集筛选使用 `MediaChoiceChip` 与 `selectableGroup`。
- `VideoEpisodeRow.isCurrent` 使用 `primaryContainer` 背景、`onPrimaryContainer` 前景与 `selected` 语义；不可播放项保留 disabled 语义。
- `continueWatchingCardWidth(fontScale)` 以 240dp 为基准，按有效系统字号增长并钳制到 300dp。
- `resolveVideoDetailCurrentEpisode` 与详情主播放目标保持同一排序和可播放规则。

## 3. 实现合同

### Home、搜索与推荐架

- 推荐节头与「全部 N 项」使用 `headlineMedium`、`onBackground`、SemiBold 和 `heading()`。
- 海报推荐卡使用 `videoShelfCardSize(fontScale)`；续播横向卡使用 `continueWatchingCardWidth(fontScale)`，不写固定宽度。
- 卡片次级信息使用实色 `onSurfaceVariant`，不得在半透明 `surfaceVariant` 卡片上用 `onSurface.copy(alpha = ...)` 造成小字对比度低于 4.5:1。
- 续播卡中央播放图标是装饰，不创建第二个点击目标，实际视觉尺寸不小于 48dp。
- 连接错误在错误卡下提供 `SecondaryActionButton("重试")`，调用原刷新回调；错误且无缓存时不叠加成功空库或空内容文案。
- 搜索输入使用 `MediaSearchField`，保留清除、收起、Search IME 和原有本地过滤逻辑。

### 详情与分集

- 简介与分集标题声明 `heading()`；简介复用 `MusicCollectionDescription`，保留 itemId 隔离的展开状态。
- Series 当前分集由 `resolveVideoDetailCurrentEpisode` 解析；详情列表必须传入 `isCurrent`，与播放器选集面板使用同一视觉和语义语言。
- 当前分集的标签、标题、元信息使用 `onPrimaryContainer`；普通分集次级信息使用 `onSurfaceVariant`。
- 封面不承担重复标题朗读；详情 Hero 的白字叠黑渐变是视频播放/海报上下文中的既有例外，不用本合同改成普通页面文字角色。

### Debug 样板与边界

- `VideoCatalogSample` 只使用内存 `VideoItem`、本地 debug 封面和记录回调，不构造账号、不请求网络、不写真实偏好。
- 覆盖 `VideoHome`、`VideoSearch`、`VideoDetail`、`VideoSeries`；状态按实际实现覆盖正常、长文本、空/未配置、空库、加载、错误、缓存刷新、无封面和未看筛选为空。
- 本合同不代表真实 Emby、个人设备、TalkBack 全路径或播放器/PiP/横竖屏验收。

## 4. 边界矩阵

| 场景 | 预期 |
|------|------|
| 320/360/392/720dp，字号 1/1.5/2 | 卡片和节头不裁切；续播卡按字号增长且不超过 300dp |
| 普通卡片次级文字 | 使用实色 `onSurfaceVariant`，真实合成对比至少 4.5:1 |
| 当前分集 | `primaryContainer` + `onPrimaryContainer` + `selected` |
| 错误且无缓存 | 只有错误卡和重试按钮，不出现假空态 |
| 搜索无结果 | 显示匹配空态，保留筛选和清除路径 |
