# 视频域 UI 精修

## Goal

延续全页面 UI 统一任务（09-06 / 09-07 两批 + 音乐第三批 + 有声书第四批），对视频域残余割裂点进行实现级精修：节头语义、海报卡自适应、分集筛选与播放器倍速面板的选中语言、简介展开与边框微对齐。

## What I already know

* 源码调研确认的割裂点：
  1. 「全部 N 项」节头缺 heading() 语义 + SemiBold（VideoScreen.kt:500-507）——对照 MusicHomeSections 节头规范。
  2. 继续观看/最受好评/未播放 节头同样缺（VideoBrowseComponents.kt:202-208）。
  3. 详情页「分集」节头缺（VideoDetailScreen.kt:116-121）。
  4. 视频推荐卡固定 width(132.dp)，大字体不增长（VideoBrowseComponents.kt:225）——对照 musicShelfArtworkSize 的 fontScale 增长策略。
  5. 分集筛选自绘 chip：裸 clickable、无 selectable/Role/selected 语义、primary 0.16 选中色、高度不足（VideoDetailScreen.kt:162-190）——对照 MediaChoiceChip。
  6. 播放器倍速面板选择行是裸 Row（无容器/选中背景/press scale）（VideoPlayerPanels.kt:176-191）——对照 MediaPlayerChoiceRow。
  7. 详情页简介超长无展开/收起（VideoDetailScreen.kt:101-106）——MusicCollectionDescription 第四批已 internal 化可复用。
  8. VideoEpisodeRow 缩略图边框 0.05f 与卡片家族 0.045f 不一致（VideoDetailScreen.kt:348）。
* 播放器 chrome white-on-black、面板 in-window modal、hero MetaChip 均为已确认的例外/架构决策，本批不动。

## Requirements (confirmed)

* 三处节头补 `Modifier.semantics { heading() }` + `fontWeight = FontWeight.SemiBold`。
* 新增 `internal fun videoShelfCardSize(fontScale: Float): Dp = (132.dp * scale).coerceAtMost(176.dp)`（scale 钳制 1..2，与其他域同一增长策略），推荐卡宽度改用该函数；补单测覆盖 scale 1/1.5/2 与上限。
* 分集筛选（全部/未看）迁移到 `MediaChoiceChip`，外层加 `selectableGroup()`。
* 播放器倍速面板选择行迁移到 `MediaPlayerChoiceRow`（保持面板强制深色主题不变，colors 参数兼容）。
* 详情简介复用 `MusicCollectionDescription`（itemId = video.id），删除 `lineHeight = 21.sp` 字面量。
* VideoEpisodeRow 边框 `0.05f` → `0.045f`。
* 不改变 Emby API、缓存、播放协议、面板容器架构与既有业务行为。

## Acceptance Criteria

* [ ] 节头有 heading 语义与统一字重；TalkBack 可按标题导航。
* [ ] 大字体下推荐卡与文字同步增长，不截断。
* [ ] 分集筛选与库选择/类型筛选同一 chip 语言；倍速面板与其他播放器弹层选择行一致（primaryContainer + 勾选）。
* [ ] 长简介可展开/收起。
* [ ] compile + testDebugUnitTest + lintDebug + assembleDebug 全绿；新增 videoShelfCardSize 单测。
* [ ] spec 更新：ui-consistency.md 业务页面扩展补视频域条目。
* [ ] 覆盖矩阵视频 9 行推进 + manual-checklist 第五批 + APK 交付。

## Out of Scope

* 播放器 chrome white-on-black 字面量（已记录例外）。
* 面板容器从 in-window modal 迁移（架构决策）。
* 配置域页面（第六批）。

## Current Progress

* 计划已确认（含倍速面板迁移决策），任务已创建。
