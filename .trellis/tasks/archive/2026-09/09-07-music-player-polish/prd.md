# 音乐域播放器三面 UI 精修

## Goal

延续全页面 UI 统一任务（09-06 / 09-07 两批），对覆盖矩阵中音乐域尚未逐项审查的三个播放器表面——主播放器/歌词、队列面板、均衡器面板——进行实现级统一精修，消除与共享壳层契约的残留割裂点。

## What I already know

* 音乐浏览页/列表/集合详情/歌单弹窗已在 09-06 批统一（music-library-audit.md）。
* 第一批（09-07）已统一播放器弹层容器 `MediaPlayerSheet` 与选择行 `MediaPlayerChoiceRow`，倍速面板已共享化。
* 覆盖矩阵中音乐域剩余未逐项审查行：主播放器/歌词视图、队列面板、均衡器面板。
* 源码扫描确认的割裂点：
  - `MusicQueueSheet.kt:173` 仍用本地 `ModalBottomSheet`，未迁移共享容器。
  - `MusicQueueSheet.kt:575` `song.artist ?: "Unknown"` 违反中文缺省文案规范。
  - `QueueTextAction` 实际高度约 32dp、`QueueDragHandle` 28dp，低于 48dp 触达规范。
  - `onMoveUp`/`onMoveDown` 参数未使用（编译警告）；`520.dp` 魔法数出现两次。
  - `MusicPlayerScreen.kt` 的 `MusicSeekFeedbackChip` 与 `FavoriteErrorNotice` 是近似重复的 transient pill。
  - 歌词字号 `fontSize = 16.sp/18.sp` 字面量（18sp 无对应 Typography 槽位）。
  - `MusicEqualizerSheet.kt` 预设 chip 用裸 `clickable`，无 selectable/Role/selected 语义；选中样式 `primary 0.18` 与选择行 `primaryContainer` 语言不一致；chip 高度低于 48dp；频率/dB 标签固定宽度。

## Requirements (confirmed)

* 队列面板迁移到共享 `MediaPlayerSheet`；`MediaPlayerSheetHeader` 增加可选 trailing action 槽位承载「清空后续」按钮，保持原回调与禁用逻辑。
* 队列行歌手缺省文案统一为 `musicArtistLabel`（未知歌手）。
* `QueueTextAction` 最小高度 48dp（宽 58dp 保留）；`QueueDragHandle` 放大到 48dp 圆形（图标 20dp 不变）。
* 删除未使用的 `onMoveUp`/`onMoveDown` 参数；`520.dp` 提取为私有命名常量并去掉 Box/LazyColumn 双重 heightIn。
* `MusicSeekFeedbackChip` 与 `FavoriteErrorNotice` 合并为共享 internal `MediaTransientPill`（统一 0.94 alpha 容器与 border），置于 MediaPlayerSheets.kt。
* 歌词字号 16/18sp 改为私有命名常量；spec 记录「歌词展示面字号例外」。
* 背景渐变 alpha 停靠点不动（按 VideoPlayerScreen 先例记为 out-of-scope）。
* 均衡器预设 chip：`selectable(selected, role = Role.RadioButton)` + 容器语义；选中样式对齐 `primaryContainer` + `onPrimaryContainer`；chip 最小高度 48dp；频率/dB 标签 `widthIn(min = ...)` 允许大字体自然增宽。
* 不改变队列拖拽协议、播放回调、均衡器音频会话逻辑与既有业务行为。

## Acceptance Criteria

* [ ] 队列面板、倍速、均衡器弹层使用同一容器语言（MediaPlayerSheet）。
* [ ] 队列行、均衡器 chip 的触达目标 ≥48dp，选中态有完整背景/文字/语义反馈。
* [ ] 音乐域无 "Unknown" 英文缺省文案残留。
* [ ] transient pill 只有一份共享实现。
* [ ] compile + testDebugUnitTest + lintDebug + assembleDebug 全绿；队列拖拽/布局既有测试回归通过。
* [ ] spec 更新：弹层 header trailing 槽位、transient pill 契约、歌词字号例外。
* [ ] 覆盖矩阵音乐三行推进 + manual-checklist 第三批真机清单 + APK 交付。

## Out of Scope

* 播放器背景渐变 alpha 停靠点、封面比例、播放器控件布局重设计。
* 队列拖拽协议重写、均衡器音频处理逻辑变更。
* 有声书/视频/配置域页面（后续批次）。

## Current Progress

* 计划已确认（全部采用推荐方案），任务已创建。
