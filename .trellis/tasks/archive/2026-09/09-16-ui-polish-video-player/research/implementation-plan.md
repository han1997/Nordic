# 视频播放器第五轮：源码审查与实施边界

## 已确认实现

- `VideoPlayerScreen` 已实现播放器主体、Surface、控制显隐、播放器错误/缓冲状态、跳过片头、下一集提示、手势锁、全屏与 PanelHost 调用。
- `VideoPlayerChrome` 已有 `resolveVideoPlayerToolLayout`、实际 48dp 工具按钮、72dp 主按钮、时间线和 safeDrawing inset。
- `VideoPlayerPanelHost` 已有单一 `VideoPlayerPanel` 状态、竖屏底部/横屏侧面板判定、7 个 panel 分支、paneTitle 和 BackHandler。
- `VideoPlayerPreviewActivity` 已提供离线 Surface 绘制、剧集列表、电影/空/未知时长/错误/缓冲/片尾/连播场景、横屏切换和选集回调。
- 纯函数测试已存在于 `VideoPlayerScreenTest.kt` 与 `VideoPlayerEpisodesTest.kt`，本轮补视觉合同对应的边界和交互回归。

## 优先审计项

1. 面板说明/元信息大量使用 `onSurface.copy(alpha = NordicAlpha.medium/subtle)`，在半透明或深色 surface 上需统一为经过合同验证的 `onSurfaceVariant`。
2. `VideoPlayerTracksContent` 当前 `LazyColumn(modifier.verticalScroll(...))` 同时使用两种滚动容器，改为单一 `LazyColumn` 或单一 Column+verticalScroll。
3. `VideoPlayerSettingRow`、影片信息、选集说明和清晰度说明需要检查大字体下的换行、右侧值可达性和 heading 语义。
4. `FilterChip` 季选择需要与共享 `MediaChoiceChip` 语义/高度合同对齐，保留窗内深色主题和当前季回调。
5. 章节行、选集行、字幕/音轨/清晰度选择行需逐一检查 48dp、selected 语义、当前项定位和不可播放 disabled 状态。
6. `VideoPlayerCenterMessage` 的关闭、重试、从头播放和「仍要关闭」操作需补 Role、点击标签和实际触控尺寸。
7. 播放器 Chrome 的白字是视频 overlay 例外，但说明文本、时间线标签和选中/未选中面板内容不能沿用未经合成验证的任意 alpha。

## 不变边界

- `VideoPlaybackEngine`、`MainActivity` handoff、方向控制、PiP bridge、自动连播状态机不改协议。
- 选集切换继续走原 `onPlayEpisode` / `onPlayNextEpisode`，不能直接调用 engine。
- 预览不访问网络、不写真实偏好、不写真实进度。
- 保留前四轮证据，使用 `r5-*` 新批次目录。
