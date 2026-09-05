# 音乐库滑动卡顿优化

## Goal

音乐库各列表页（专辑/歌曲/歌手/歌单/详情/搜索）滑动时出现卡顿（掉帧）。定位滑动路径上的性能热点并修复，提升滚动流畅度。

## Root Cause 分析（代码盘点结论）

滑动路径上的主要开销，按影响排序：

1. **Coil 全局 ImageLoader 未配置内存缓存与 Bitmap 复用策略**（MainActivity.kt onCreate）：
   - 只设置了 crossfade + OkHttp，未显式设置 `memoryCache`（Coil 默认有内存缓存但按默认比例），未设置 `allowRgb565`（半透明不重要的封面可用 RGB_565 减半内存带宽），未设置 `bitmapFactoryMaxParallelism`/`crossfade(false)` for list。
   - 列表快速滑动时大量 52dp 小图解码 + crossfade 动画叠加，造成掉帧。
2. **AuthedAsyncImage 每次组合都 crossfade(200)**：列表行滚动进入时每张图都跑 200ms crossfade 动画，快速滑动时动画叠加导致 GPU/渲染压力。列表场景应禁用 crossfade（仅详情/大图保留）。
3. **MusicScrollbar 的 derivedStateOf 读 `state.layoutInfo`**（MusicScrollbar.kt:51）：`layoutInfo` 是快照对象，每次滚动帧都变化，4 个 derivedStateOf 都依赖它 → 每帧触发 4 次状态读 + thumb 重组。可合并为单个 derivedStateOf，减少每帧 recompose 次数。
4. **列表行无固定高度提示**：LazyColumn 行高由内容决定（正常），但 `SongListRow` 的 press-scale（`rememberPressScale` + `scale(scale)` modifier）在每行创建 `MutableInteractionSource` + 动画状态，滚动时每行都分配。影响小但可优化（AlbumListRow/ArtistListRow/PlaylistListRow 无 press-scale，仅 SongListRow 有——不一致且增加开销）。
5. **`MusicPageList` 的 `verticalArrangement.spacedBy` + 每行 Surface border**：border 绘制开销可接受，不动。

## Requirements

1. **MainActivity Coil ImageLoader**：
   - 显式配置 `memoryCache`（比例 0.25，与默认一致但显式化）与 `bitmapConfig`（RGB_565 用于列表小图不合适——Coil 全局配置会影响大图质量；改为在请求级处理）
   - `components { add(OkHttpNetworkFetcherFactory(callFactory = ...)) }` 保持现状即可
2. **AuthedAsyncImage 增加 `crossfadeEnabled` 参数**（默认 true 保持现状）；列表行封面（CoverArt / AlbumListRow / PlaylistListRow / CompactMusicShelfItem 内的 AuthedAsyncImage）传 false，禁用列表 crossfade
3. **MusicScrollbar 合并 4 个 derivedStateOf 为 1 个**（返回 data class），每帧只触发一次 thumb 重组
4. **SongListRow 移除 press-scale**（与 AlbumListRow/ArtistListRow/PlaylistListRow 一致，去掉每行 InteractionSource + 动画分配）

## Acceptance Criteria

* [ ] 列表滑动流畅度提升（无 crossfade 动画叠加、scrollbar 每帧单次重组）
* [ ] compile + test + lint 通过
* [ ] 图片加载行为不变（详情页 crossfade 保留）

## Out of Scope

* 分页加载（当前全量缓存已够用）
* 换图片库
