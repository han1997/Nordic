# 悬浮导航栏隐藏后只保留小白条

## Goal

底部导航栏滚动隐藏后，当前整个底部区域会被 Scaffold 的 `containerColor` 填充为背景色（白色/浅色），形成一条全宽的色条。用户希望隐藏后只保留悬浮的 `BottomDockHandle` 小白条，底部区域透明，不出现全宽色条。

## What I already know

- `Scaffold(containerColor = colorScheme.background, bottomBar = { ... })` — Scaffold 的 `containerColor` 会填充整个 Scaffold 表面，包括 `bottomBar` 区域
- `bottomBar` 中有两个 `AnimatedBottomDock`：一个显示完整 dock，一个显示 handle
- 当 dock 隐藏时，handle 的 `AnimatedBottomDock` 可见，但 Scaffold 的背景色仍然填充 bottomBar 区域
- `BottomDockHandle` 本身的外层 Box 是 `fillMaxWidth()` 但无背景，Surface 只有 64dp 宽
- 问题根因：Scaffold 的 `containerColor` 在 bottomBar 区域可见，形成全宽色条

## Requirements

- 滚动隐藏 dock 后，底部区域透明，只保留悬浮的 `BottomDockHandle`
- dock 可见时，正常显示完整导航栏
- 不影响 dock 的显示/隐藏动画
- 不影响内容区域与 dock 的间距（内容不被 dock 遮挡）

## Acceptance Criteria

- [ ] 滚动隐藏 dock 后，底部无全宽色条，只看到悬浮小白条
- [ ] 点击小白条能恢复完整 dock
- [ ] dock 可见时内容不被遮挡
- [ ] 切换 tab / 打开 player 时 dock 正常显示/隐藏

## Technical Approach

将 dock 和 handle 从 `Scaffold.bottomBar` 移出，改为在内容 `Box` 中以 `Alignment.BottomCenter` 叠加。内容区域添加动态 bottom padding（dock 可见时预留空间，隐藏时不预留）。

这样 bottomBar 区域不再存在，Scaffold 的 `containerColor` 不会在底部填充，handle 真正悬浮在内容之上。

## Decision (ADR-lite)

**Context**: Scaffold 的 `containerColor` 填充 bottomBar 区域，导致 dock 隐藏后底部仍有全宽色条
**Decision**: 方案 A — 将 dock 从 `Scaffold.bottomBar` 移出，改为在内容 `Box` 中以 `Alignment.BottomCenter` 叠加
**Consequences**: 内容区域需要动态 bottom padding；handle 真正悬浮在内容之上

## Out of Scope

- BottomDockHandle 的视觉样式调整
- dock 的动画时序调整
- 滚动隐藏逻辑的改动

## Technical Notes

- 文件：`app/src/main/java/com/nordic/mediahub/MainActivity.kt`（约 437-460 行的 Scaffold + bottomBar）
- `AnimatedBottomDock` 使用 `AnimatedVisibility` + `expandVertically`/`shrinkVertically`，移出 bottomBar 后仍可正常工作
- 需要计算 dock 高度作为内容区域的 bottom padding（dock 可见时）
- `resolveBottomDockPresentation` 逻辑不变
