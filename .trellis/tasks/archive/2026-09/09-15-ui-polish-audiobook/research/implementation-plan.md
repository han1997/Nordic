# 有声书域第三轮：源码审查与实施边界（2026-09-15）

## 审查结论总览

生产入口全部在 `AudiobookScreen.kt`（884 行，书库 Home + Detail）与 `AudiobookPlayerScreen.kt`（523 行，播放器 + 4 弹层）。共享组件复用度高于音乐域第二轮起点：`MediaPageHeader`、`MediaChoiceChip`、`CoverArt`、`resolveAudiobookCollectionLayout`（委托 `resolveMusicCollectionLayout`）、`MusicCollectionDescription`、`MediaPlayerSheet`、`MediaPlayerChoiceRow`、`MediaTransportRow`、`MediaPlayerTimeline`、`MediaAudioPlayerBody`、`MediaPlaybackSpeedSheet`。因此本轮**以修整与状态收口为主，不复制音乐轮的组件抽取阶段**——多数抽取已完成。

## 具体问题清单

### A. AudiobookScreen（书库 + 详情）

1. **列表间距不一致（A1）**：`AudiobookScreen.kt:419` LazyColumn `verticalArrangement = spacedBy(NordicSpacing.lg)`（16dp）。音乐合同要求列表 8dp（sm）。摘要卡列表、章节行列表同用 16dp，与音乐第二轮收敛后的节奏不一致。
2. **状态卡与内容叠加（A2）**：`standaloneError` 在页头下固定 item（467–476 行），占满短屏可用区后才轮到内容；无 MusicPageList 式"反馈在滚动区内 + suppressesEmptyState"结构。错误/空态/loading 三卡在短屏堆叠风险同音乐第二轮起点。`audiobookResetNotice` / `audiobookDetailInvalidationNotice` 同样在滚动区内但位置靠前，仍可接受（音乐轮把 notice 纳入 MusicLibraryFeedback 的 Column 内）。
3. **错误重试入口（A3）**：错误卡 hint 只有文案"检查配置或点击刷新重试"，无显式重试按钮；音乐域 `MusicLibraryFeedback` 有 `SecondaryActionButton("重试")`。有声书页头的刷新按钮可承担重试，但语义上应把重试显式化或保留页头刷新并验证可达。
4. **章节行间距与信息层级（A4）**：`AudiobookChapterRow`（863 行）无列表 key 间距收口（同 16dp）；时间文本未用 tnum（音乐/播放器时间线都用 `fontFeatureSettings = "tnum"`）；无"当前播放章节"高亮（详情页有 progress，可标注当前章节位置）。
5. **章节行无 heading 语义（A5）**：详情页"章节"节标题（660 行）未声明 `semantics { heading() }`，与详情标题不一致；简介卡内"简介"标题在 `MusicCollectionDescription` 外层，音乐合同未强制但同类做法应一致。
6. **loadingItemDetailId 无 UI 表达（A6）**：`openItemDetail` 设置 `loadingItemDetailId`，但 UI 层未消费（grep 仅赋值/比较），列表卡片打开详情时无行内加载指示；仅 `isLoading && item == null` 才显示加载卡。详情打开慢时用户无反馈。修整方案：保留状态但不新增假 UI——详情页在 item==null 且 isLoading 时已有加载卡；卡片点击后瞬时无反馈可接受（导航立即切换页面）。**实测确认后再定**，不在基线前定方案。
7. **摘要卡播放按钮语义（A7）**：`AudiobookSummaryCard` 播放按钮（750 行）用裸 `Surface + Box + clickable`，仅 `contentDescription = "播放有声书"`，未与卡片打开（`Role.Button` onOpen）区分角色/标签对；音乐域 `SongListRow` 已有对应规范。需确认卡片本身 clickable 的语义标签与播放按钮是否冲突（嵌套 clickable）。
8. **进度信息弱化（A8）**：详情头续播进度是 MetaChip（818 行），列表卡 meta 行无进度显示；用户返回书库时看不出"听到哪"。不加业务（进度仍来自服务端字段），但列表卡 meta 可显示已有 `item.progress`？——**数据面确认**：`AudiobookItemSummary` 是否含 progress 字段；若无则此项不动。

### B. AudiobookPlayerScreen（播放器 + 4 弹层）

9. **跳转秒数文本硬编码进语义（B1）**：287/290 行 `"后退 ${LocalAppPreferences.current.audiobookSkipBack} 秒"` 直接内联 CompositionLocal 读值进 description；功能正常但 `LocalAppPreferences` 是 staticCompositionLocalOf（预览/Debug 宿主可能读到默认值），Debug 样板需显式 provide。
10. **状态行单点（B2）**：statusText 已区分 error/buffering/playing/paused；`MediaPlayerTool("仍要关闭")` 错误时展示——与音乐播放器一致的既有实现，回归即可。
11. **睡眠定时弹层预选行（B3）**：417 行 "使用预选：N 分钟" 的 `selected = null`（动作行）与"关闭"行 `selected = !active` 语义混用——同一弹层里既有动作行又有选择行，音乐队列/倍速弹层的选择行互斥语义是 selected 非空。这是**既有产品语义**（预选是一个动作，关闭是一个选择），不动协议，只在样例中验证朗读顺序与选中态可辨识。
12. **书签弹层结构（B4）**：`PrimaryActionButton`（添加书签）在 LazyColumn 外、弹层 Column 内顶部，空态卡与列表在下方。短屏/IME 不涉及（无输入框），但弹层 86% 高度 + LazyColumn `weight(1f, fill = false)` 的行为要实测大字体：书签多时列表滚动可达、添加按钮始终可见。
13. **章节面板定位（B5）**：`rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))` 只按 index 定位，未滚到当前项（可能只露出底部一行）；音乐队列是否有同模式——回归时用 `performScrollToNode` 验证当前章节完整可见。
14. **章节面板 index 混乱（B6）**：`indexOfLast { it.startSeconds <= currentPosition }` 对未排序 chapters——`sortedChapters` 已排序所以 index 一致；title 显示 `index + 1`。无问题，仅回归。
15. **播放器标题重复（B7）**：`MediaPlayerTopBar("有声书")` + artwork fallback title "有声书播放" + "等待播放"——无 session 时三层重复文案。minor，样本里验证。

### C. 数据/协议边界（不动）

- `AudiobookItemSummary.progress` 字段存在性待确认（A8 数据面）。
- 缓存、请求版本、TTL 刷新、配置切换清理协议全部不动（audiobookshelf-integration.md）。
- 书签增删/跳转、睡眠定时、倍速、章节跳转回调全部保留原实现。
- 不新增"笔记"业务；详情页章节行不加点击跳转（用户已确认；记入 backlog）。

## Debug 样板计划

- `UiSampleScreen` 新增：`AudiobookHome("ab_home")`、`AudiobookDetail("ab_detail")`、`AudiobookPlayer("ab_player")`、`AudiobookChapters("ab_chapters")`、`AudiobookSpeed("ab_speed")`、`AudiobookSleep("ab_sleep")`、`AudiobookBookmarks("ab_bookmarks")`。
- 新 `AudiobookCatalogSample`（照 MusicCatalogSample 模式）：内存 `AudiobookItemSummary/Detail/Chapter/Bookmark` 数据 + 生产 Composable 宿主 + 本地资源封面；`UiCatalogActivity` 不需要 `LocalAppPreferences` provide 时，样板宿主内部 provide 内存偏好（B1）。
- 状态矩阵（只做实际存在的状态）：
  - home：normal / long / empty(未配置) / library_empty(无书库) / loading / refreshing / cached_error / error / no_art
  - detail：normal / long / loading / cached_error / error(无内容)
  - player：normal / long / loading(缓冲)/ error
  - bookmarks：normal / empty / long
  - chapters：normal（多章节滚动）；sleep：normal / active
  - speed：normal
- 不做笛卡尔积：搜索无缓存态（有声书无搜索）、歌手 initials 分支不存在（有声书用封面）、EQ 无调用方不涉及。

## 执行顺序

1. Debug 样板宿主扩展 + `r3-baseline-*` 严格基线（源码指纹 + Debug/测试 APK 指纹 + 系统字号核对；`app/build/reports/ui-polish/` 新批次目录，不覆盖 r1/r2）。
2. 生产修整（A1/A2/A4/A5/A7/B 弹层可达性），单测 + instrumentation 回归；版本 0.1.10/10 → 0.1.11/11。
3. 修整后重跑同参数批次 + 前后对比；正常/短屏交互、双主题、字号 1/1.5/2、宽度 320/360/392/720 按覆盖矩阵。
4. 全量门禁（compile/JVM/Lint/Debug/Release/签名/DEX 泛型）+ audiobook-ui.md + 文档 + 提交计划。

## 不变边界

- 只操作 nordic-ui-api34 / emulator-5580；ADB 带 `-s`。
- 不连接真实 AudiobookShelf；preview/`example.test` 假地址不交给网络层。
- 不改 playback engine、repository、缓存协议、MainActivity 的媒体交接逻辑。
- 保留首轮/第二轮封存证据不覆盖；失败批次保留另开新批次。
