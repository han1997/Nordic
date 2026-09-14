# 音乐浏览与集合详情 UI 合同

## 1. 范围 / 触发

适用于 MusicHomeSections、MusicBrowseComponents、MusicScreenV2Pages 中的发现区、歌曲/专辑/歌手/歌单列表及对应详情，以及 MusicScreenV2 的歌单操作弹窗。遵循 ui-consistency.md；Navidrome API、缓存、播放队列与变更操作仍遵循原有业务合同。

## 2. 入口与签名

```kotlin
internal data class MusicCollectionLayout(val stacked: Boolean, val artworkSize: Dp)
internal fun resolveMusicCollectionLayout(availableWidth: Dp, fontScale: Float): MusicCollectionLayout
internal fun shouldInlineMusicRowTrailing(availableWidth: Dp, trailingWidth: Dp, fontScale: Float): Boolean
internal fun resolveMusicCollectionCount(reported: Int, loaded: Int, isLoading: Boolean, hasVisibleError: Boolean): Int
internal fun shouldShowMusicCollectionEmpty(isLoading: Boolean, itemCount: Int, hasVisibleError: Boolean): Boolean
internal fun musicShelfArtworkSize(fontScale: Float): Dp
```

- `MusicLibraryRow`：四类列表的共同内容层级、点击反馈与尾部信息布局。
- `MusicCollectionHeader`：专辑/歌手/歌单详情与首页集合概览。`onPlayAll` 为 null 时不显示播放动作，不能用空回调伪装播放入口。
- `MusicCollectionAction`：新建、重命名、删除等管理动作。
- `musicArtistLabel`、`musicSongCountLabel`、`musicAlbumCountLabel`、`musicTrackDurationLabel`：统一缺失信息与中文单位。

## 3. 实现合同

### 列表与封面

- 四类列表共用 52dp artwork、md 外轮廓、12dp 横向/8dp 纵向 padding，titleMedium / bodyMedium / bodySmall 信息层级；长标题可两行。
- 专辑/歌单/歌曲使用方封面，歌手保留圆形 initials/人物兜底。这是内容差异，不是另起视觉系统。
- 图片通过 `CoverArt` / `AuthedAsyncImage`，保留认证和加载失败兜底；不得在某个列表里另写无 error fallback 的直接图片分支。
- 列表邻接文字已经给出名称，装饰封面使用 clearAndSetSemantics 避免重复朗读；整行有 Role.Button 与对应打开/播放标签。
- 歌曲时长用 tnum；先测量尾部文字，再判断是否还给标题保留足够空间。空间不足时把时长放到元信息行首，不能让时长抢掉标题或被长专辑名挤没。
- `SongListRow(showAlbum = true)` 将歌手与非空专辑合并为次要信息；专辑详情传 `showAlbum = false`，不逐行重复当前专辑名。缺失歌手和时长的兜底不变。
- 歌曲列表与专辑曲目列表使用 8dp 项间距；其他音乐页面的间距保持原有规则，不能据此宣称全音乐页面已验收。
- 首页横向卡片随字体从 124dp 增长到最多 160dp，标题保留两行以对齐卡片后续信息。

### 集合详情

- 窄屏/大字体时竖向堆叠，空间足够时封面与文字并列；普通/宽屏 artwork 128/160dp，文字区至少预留 200dp × fontScale。
- metadata 使用 FlowRow，不把曲目数、年份、时长硬塞进单行。
- 集合文字区内使用 8dp 节奏组织标题、作者、metadata 与动作；保留原有堆叠/并列判断和 128/160dp 封面。
- 导航标题是“专辑/歌手/歌单”类别，集合真实名称集中在概览区域；不要让顶栏和内容同时重复长名称。
- 加载时保留已有集合信息，下面显示加载状态；数据成功返回再替换计数。
- 已知空集合才显示空态并禁用“播放全部”；加载失败且上层已有错误反馈时不再显示“暂无曲目/暂无专辑”误导用户。
- 计数优先使用已加载的真实条目数；加载中或失败且无条目时保留服务端报告值，不把未完成请求伪装成零内容。
- 歌单长简介可展开/收起，状态按 itemId/text 隔离，不扩散到另一歌单。

### 操作与文案

- “新建/重命名/删除”实际目标至少 48dp，窄屏管理动作使用 FlowRow；删除使用 errorContainer/onErrorContainer，并保留原有确认弹窗。
- 创建与重命名的按钮/键盘 Done 共用同一提交函数；空白名称或已有请求进行中不发起请求，仍调用原 create/rename 实现。
- 删除确认文字使用 error 角色，不能因样式重构绕过确认或并发保护。
- 统一“未知歌手”“N 首歌曲”“N 张专辑”；未知时长使用 --:--，不假装 0:00。
- `artist` 列表只是曲库数据，不能无听歌统计依据标成“常听歌手”。

## 4. 边界矩阵

| 场景 | 预期 |
|---|---|
| 320/360/392/720dp，字体 1/1.5/2 | 根据实际宽度堆叠或并列，封面不为挤文字无限缩小 |
| 不可用宽度 | artwork 不为负，不发生异常 |
| 长歌曲名 + 宽时长 + 大字体 | 时长换到元信息行，标题仍保留两行空间 |
| 初次加载 / 缓存刷新 / 失败无内容 / 成功空内容 | 已选集合不消失，计数和状态不互相矛盾 |
| 无封面 / 空 URL / 加载失败 | 使用一致兜底，不残留空白图片分支 |
| 长歌单简介，切换歌单 | 展开可读；另一歌单从自己的状态开始 |
| 空白名称 / 请求进行中 + Done/按钮 | 不重复请求；原业务验证与错误反馈保留 |
| 删除入口 | 仍然先确认，取消不调用删除 API |

## 5. 正反案例

- 好：专辑、歌手、歌单使用同一信息顺序，但歌手头像与专辑封面形状表达各自类型。
- 好：真实请求失败时只保留已有错误，不同时出现“暂无曲目”。
- 差：固定 120dp 左封面，再把标题和多个 chip 挤进剩余的窄列。
- 差：列表英文 tracks/albums 与详情中文单位混杂，或缺失时长固定显示 0:00。
- 差：为了改按钮样式而直接在点击处理里重写 Navidrome 请求。

## 6. 验证

- `MusicLibraryLayoutTest` 覆盖详情空间策略、最小宽度、时长位置、计数/空态判定、中文文案与横向卡片尺度。
- 保留原音乐路由/排序/搜索/播放队列回归，扩展检查 NavidromeRepositoryTest 的既有协议用例。
- 编译、相关测试、lint、assemble 后，由用户真机检查浅深主题、大字体、长名称、图片失败和歌单操作。
- 自动化不等于整个音乐模块或全应用已通过视觉/交互验收。

## 7. 错误与正确示例

```kotlin
// 错误：错误和空列表同时给用户两个矛盾结论。
if (songs.isEmpty()) EmptyState()
// 正确：错误仍归原持有者呈现，只有明确空结果才显示空态。
if (shouldShowMusicCollectionEmpty(isLoading, songs.size, hasVisibleError)) EmptyState()
```

```kotlin
// 错误：每种集合单独画固定左封面/窄文字列。
Row { Artwork(120.dp); FixedMetadataColumn() }
// 正确：使用共同的自适应概览组件，原播放回调不变。
MusicCollectionHeader(
    itemId = album.id,
    title = album.name,
    subtitle = musicArtistLabel(album.artist),
    metadata = listOf(musicSongCountLabel(songs.size)),
    artworkUrl = album.coverArt,
    fallbackIcon = Icons.Filled.Album,
    colorScheme = colorScheme,
    onPlayAll = onPlayAll,
    playEnabled = !isLoading && songs.isNotEmpty()
)
```
