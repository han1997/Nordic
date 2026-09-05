# 日间模式顶栏按钮阴影优化

## Goal

四个屏幕（音乐 MusicScreenV2 / 有声书 AudiobookScreen / 视频 VideoScreen / 设置 ServerConfigScreen）右上角的日夜间切换与刷新按钮组，在日间模式呈现一层明显阴影，与项目扁平设计语言（chip/卡片均为 border 无阴影）不符。去除该阴影。

## Root Cause

共享组件 `HeaderActionGroup`（`app/src/main/java/com/nordic/mediahub/ui/AnimatedComponents.kt:95-103`）的 Surface 声明了：
- `shadowElevation = 4.dp` — 真实投影，日间背景浅、阴影可见
- `tonalElevation = 3.dp` — 额外色调叠加，日间下也使按钮组比页面背景"浮起"

夜间模式下阴影在深色背景上几乎不可见，因此用户只在日间察觉。

## Requirements

1. `HeaderActionGroup` 的 Surface 移除 `shadowElevation`（设为 0.dp 或直接删除参数）
2. 移除 `tonalElevation`（保持与 MetaChip 等其它 header 元素一致的扁平观感）
3. 保留现有 border（`onSurface.copy(alpha = 0.06f)`）与内部渐变背景，形状/尺寸/交互不动

## Acceptance Criteria

* [ ] 四个屏幕右上角按钮组在日间模式无阴影
* [ ] 编译 + 单测 + lint 通过（纯视觉改动，预期无行为变化）

## Out of Scope

* `AnimatedIconButton`（独立使用场景，非本次反馈对象）
* Dock / 播放器按钮的阴影（有意的层级表达）
