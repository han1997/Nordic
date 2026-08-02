# UI 拆分与共享组件提取

- 优先级: P2
- 创建: 2026-08-02
- Assignee: hhy
- 关联审查: `.trellis/workspace/hhy/code-review-2026-08-02.md`

## 背景

`MusicScreenV2.kt`(2092 行) 与 `VideoScreen.kt`(1246 行) 单文件承载主屏多分支、子组件与编排函数，无法维护。6 个近似 MetaChip、7+ 封面图框、4 个返回按钮、4 个主操作按钮跨屏复制，违反 spec "Shared Compose components must be internal"。本任务做机械拆分与组件提取，**依赖 T4** 先建立领域模型类型（UI 接领域类型）。

## 范围

### In scope
- 拆 `MusicScreenV2.kt`：按 `MusicLibraryPage.*` 分支抽独立 Composable；列表行/头部/排序控件移 `MusicBrowseComponents.kt`；纯辅助移 `MusicScreenLogic.kt`
- 拆 `VideoScreen.kt`：抽 `VideoDetailScreen.kt`/`VideoBrowseComponents.kt`/`VideoScreenLogic.kt`
- 提取 `internal` 共享组件：
  - `MetaChip`（plain / colored-tone 两变体）替代 6 个重复
  - `CoverArt(shape, fallbackGlyph, size)` 替代 7+ 封面图框
  - `ScreenBackButton(glyph, onClick)` 替代 4 个返回按钮
  - `PrimaryActionButton` 替代 4 个主操作按钮
- `ConfigCards.kt:129-150` 抽共享认证字段，仅类型字段动画
- `VideoPlayerScreen` 全屏时声明先 `onToggleFullscreen` 的 `BackHandler`
- 清理死代码：`MusicHomeSections.kt:192-263,266-314`（`AlbumShelfCard`/`ArtistRoundCard`）、`theme/Color.kt:11,21`（`DarkAccent`/`LightAccent`）
- 统一时长格式化（`VideoScreen.kt:1174` `formatVideoDuration` vs `MusicFormatters.kt:3` `formatDuration`）

### Out of scope
- ViewModel/状态所有权 → T4
- Compose 重组性能修复 → T6
- 测试补齐 → 后续

## 覆盖的审查发现

| ID | 严重度 | 位置 | 概述 |
|---|---|---|---|
| H13 | High | `MusicScreenV2.kt`(2092)/`VideoScreen.kt`(1246) | 巨型文件 |
| H14 | High | 6 处 MetaChip | 重复组件 |
| M-UI 重用 | Medium | 封面图×7/返回×4/主按钮×4/ConfigCards | 重复模式 |
| M-全屏 BackHandler | Medium | `VideoPlayerScreen.kt` | 全屏无 BackHandler |
| L-死代码 | Low | `MusicHomeSections.kt:192-314`/`Color.kt:11,21` | 未引用 |
| L-格式化重复 | Low | `VideoScreen.kt:1174` vs `MusicFormatters.kt:3` | 双格式化样式 |

## 验收标准
- [ ] `MusicScreenV2.kt`/`VideoScreen.kt` 行数大幅下降，单文件单一职责
- [ ] 新增 `internal` 共享组件被各屏复用，原重复定义删除
- [ ] 编译/测试/lint 通过：`.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon`
- [ ] 死代码已删，无未引用颜色/组件

## 涉及 spec（待 1.3 jsonl 整理）
- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/quality-guidelines.md`
