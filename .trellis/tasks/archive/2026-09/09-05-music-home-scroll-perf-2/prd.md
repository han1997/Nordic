# 音乐库发现页滑动二次优化

## Goal

首轮优化（8d3e00a：列表禁 crossfade、scrollbar 合并 derivedStateOf、移除 SongListRow press-scale、Coil 显式内存缓存）后，发现页（Home）上下滑动仍有轻微卡顿。本轮针对发现页特有路径继续优化。

## Root Cause 分析（第二轮盘点）

1. **MusicScrollbar 滚动期间每帧重组**（MusicScrollbar.kt）：
   - 当前结构：`BoxWithConstraints`（每次几何变化触发 subcomposition）+ thumb `Box` 的 `offset(y)`/`height` 参数在滚动时每帧变化 → 每帧 recompose + 一次 subcomposition。
   - 改为 `Modifier.drawBehind` 在绘制阶段直接读 `state.layoutInfo`（snapshot 状态读取只触发 redraw，不触发 recompose/layout）→ 滚动期间零重组、零 subcomposition。
2. **CompactMusicShelfItem 仍有 press-scale**（MusicHomeSections.kt:390-397）：发现页三组 LazyRow 的每张卡片在滚动组合时都分配 `MutableInteractionSource` + 动画状态。与上轮移除 SongListRow press-scale 的理由一致（且其他行组件均无 press-scale），移除。

## Requirements

1. `MusicScrollbar` 重写为 drawBehind 渲染：thumb 的位置/尺寸/可见性全部在 draw 阶段计算，滚动帧只重绘不重组
2. `CompactMusicShelfItem` 移除 press-scale（保留 clickable）
3. 清理随之失效的 import

## Acceptance Criteria

* [ ] 滚动期间 scrollbar 零 recomposition（draw-only 更新）
* [ ] 发现页卡片滚动组合无 InteractionSource/动画分配
* [ ] compile + test + lint 通过；scrollbar 视觉行为不变（位置/粗细/自动隐藏）

## Out of Scope

* 分页/虚拟化改造（LazyColumn 默认 prefetch 已够用）
* CoverArt URL 加 size 参数（改变缓存键，代价大于收益；Coil 已按目标尺寸降采样解码）
