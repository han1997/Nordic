# Compose 性能快赢

- 优先级: P1
- 创建: 2026-08-02
- Assignee: hhy
- 关联审查: `.trellis/workspace/hhy/code-review-2026-08-02.md`

## 背景

近期提交聚焦 Compose 重组性能，但仍有数处高影响反模式：`searchJob` 用 `mutableStateOf` 致整屏重组（spec 已列为反例）；有声书章节每 tick 重新排序；播放位置在屏顶读取致整个播放器每 tick 重组；剧集行 eager 全量组合；状态 pill 用字符串相等选色；保存与 `LaunchedEffect(savedConfig)` 竞态。多为局部、低风险、高收益的快赢。

## 范围

### In scope
- `searchJob` 改 `AtomicReference<Job?>`（`MusicScreenV2.kt:146,1033`）
- 有声书章节预排序：`remember(chapters){ chapters.sortedBy{startSeconds} }` 派生当前章节（`AudiobookPlayerScreen.kt:151,453-455`）
- 播放位置隔离：`MusicPlayerScreen.kt:78,159`、`AudiobookPlayerScreen.kt:63` 以 `State<Int>`/lambda 下传，仅滑块/状态行读
- 剧集懒加载：`VideoScreen.kt:749` 改固定高 `LazyColumn` 或单 `LazyColumn` 跨项
- `VideoPlayerStatusPill` 传类型化 `VideoStatusTone` 枚举替代字符串相等（`VideoPlayerScreen.kt:285`）
- 保存与 effect 竞态：保存只持久化、让 `LaunchedEffect(savedConfig)` 刷新，或版本协调（`MusicScreenV2.kt:616`/`AudiobookScreen.kt:307`/`VideoScreen.kt:281`）
- `MusicEqualizerSheet`：预设 `LazyRow` 加 `key`（:141）；空 `catch(_:Exception){}` 至少 Log 或内联提示（:62,162,231）
- `playAlbum` 加 `requestVersion` 守卫 + `resetMusicStateAfterConfigChange` 清 `loadingAlbumId`（`MusicScreenV2.kt:350-373,375-399`）
- minor：`metaText()` 记忆（`VideoPlayerScreen.kt:135`/`VideoScreen.kt:506,1062`）、`MusicHomeSections.kt:122` AsyncImage `matchParentSize()`、`MusicEqualizerSheet` 频段本地态

### Out of scope
- MusicScreenV2/VideoScreen 物理拆分 → T7
- ViewModel/状态所有权重构 → T4

## 覆盖的审查发现

| ID | 严重度 | 位置 | 概述 |
|---|---|---|---|
| C3 | Critical | `MusicScreenV2.kt:146` | searchJob 用 mutableStateOf 整屏重组 |
| H12 | High | `AudiobookPlayerScreen.kt:151,453-455` | 章节每 tick 重排序 |
| H11 | High | `MusicScreenV2.kt:350-373` | playAlbum 缺版本守卫 |
| M-Compose | Medium | `MusicPlayerScreen.kt:78,159`/`AudiobookPlayerScreen.kt:63`/`VideoScreen.kt:749`/`VideoPlayerScreen.kt:285`/三屏保存竞态/`MusicEqualizerSheet.kt:141,62,162,231,196-251` | 位置隔离/eager/字符串色/竞态/key/吞异常 |
| M-守卫不一致 | Medium | `MusicScreenV2.kt:375-399` vs `350-358` | 专辑加载守卫不一 |

## 验收标准
- [ ] 防抖/播放 tick 不再触发整屏或顶部栏重组（可用 Compose 工具或手测验证）
- [ ] `playAlbum` 换号后不回调旧账号歌曲
- [ ] 均衡器预设变更组合稳定，频段/预设错误不静默
- [ ] 状态 pill 颜色由枚举驱动，文案改动不破外观
- [ ] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过

## 涉及 spec（待 1.3 jsonl 整理）
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/backend/directory-structure.md`
