# 有声书域 UI 精修

## Goal

延续全页面 UI 统一任务（09-06 / 09-07 两批 + 音乐域第三批），对有声书域残余割裂点进行实现级精修：书籍详情头部自适应、简介展开/收起、作者缺省文案与微对齐。弹层容器、触达目标、状态卡已在第一批共享化，本批不重复改动。

## What I already know

* 源码调研确认的割裂点（对照音乐域已统一实现）：
  1. `AudiobookDetailHeader` 封面固定 128dp + 固定 Row，无窄屏/大字体堆叠适配（AudiobookScreen.kt:714）——对照 `MusicCollectionHeader` 的 `resolveMusicCollectionLayout`。
  2. 简介卡片长文本无展开/收起（AudiobookScreen.kt:753）——对照 `MusicCollectionDescription`（3 行截断 + 展开/收起，按 itemId/text 隔离状态）。
  3. 作者空白时直接省略该行：summary 卡（671）、详情作者行（735）、页头副标题（395）——音乐域统一「未知歌手」缺省文案规范。
  4. 详情页「章节」节头用 `onBackground`，与其他节头 `onSurface` 不一致（595）。
  5. 简介卡边框 `0.05f` 与卡片家族 `0.045f` 不一致（757）。
  6. 书签行选中副标题 `onPrimaryContainer` 全色，选择行规范为 0.78 alpha（AudiobookPlayerScreen.kt:492）。
* 弹层（章节/倍速/定时/书签）已用 MediaPlayerSheet/MediaPlayerChoiceRow；工具行、拖拽关闭、错误「仍要关闭」入口已合规。

## Requirements (confirmed)

* 新增 `internal fun resolveAudiobookCollectionLayout(...)` 薄委托到 `resolveMusicCollectionLayout`（复用已测逻辑，不复制实现）；详情头部 stacked 时内容居中、封面在文字上方，宽屏维持并列 + 160dp。
* `MusicCollectionDescription` 从 `private` 提升为 `internal`，有声书简介卡片复用其展开/收起行为，保留「简介」标题与卡片容器。
* 新增 `audiobookAuthorLabel`（空白 → 「未知作者」），应用到 summary 卡、详情作者行、页头副标题。
* 「章节」节头 `onBackground` → `onSurface`；简介卡边框 `0.05f` → `0.045f`；书签行选中副标题 → `onPrimaryContainer` 0.78 alpha。
* 不改变 AudiobookShelf API、缓存、播放协议与既有业务行为。

## Acceptance Criteria

* [ ] 详情头部窄屏/大字体堆叠、宽屏并列 160dp，与音乐集合概览同一布局语言。
* [ ] 长简介可展开/收起，状态按条目隔离。
* [ ] 作者缺失显示「未知作者」，无静默省略。
* [ ] compile + testDebugUnitTest + lintDebug + assembleDebug 全绿；audiobook 既有 resolver 测试回归通过。
* [ ] spec 更新：ui-consistency.md「业务页面扩展」补有声书域条目。
* [ ] 覆盖矩阵有声书 7 行推进 + manual-checklist 第四批 + APK 交付。

## Out of Scope

* AudiobookShelf 集成协议、缓存 TTL、播放引擎行为。
* 视频域、配置域页面（第五/六批）。
* 播放器表面（已在第一批统一）。

## Current Progress

* 计划已确认，任务已创建。
