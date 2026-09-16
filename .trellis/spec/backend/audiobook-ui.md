# 有声书浏览、详情与播放器 UI 合同

## 1. 范围 / 触发

适用于 `AudiobookScreen.kt`(书库 Home + 详情页)、`AudiobookPlayerScreen.kt`(播放器 + 书签/睡眠定时/章节/倍速 4 个弹层)以及 `app/src/debug` 下 `AudiobookCatalogSamples.kt` 样板宿主。遵循 ui-consistency.md;AudiobookShelf API、缓存、进度上报与播放会话仍遵循 audiobookshelf-integration.md 业务合同。

## 2. 入口与签名

```kotlin
internal enum class AudiobookLibraryPage { Home, Detail }

internal fun resolveAudiobookSelectedLibraryId(currentLibraryId: String?, libraries: List<AudiobookLibrarySummary>): String?
internal fun resolveAudiobookSelectedItemAfterLibraryRefresh(selectedItem: AudiobookItemDetail?, items: List<AudiobookItemSummary>): AudiobookItemDetail?
internal fun resolveAudiobookLibraryPageAfterRefresh(currentPage: AudiobookLibraryPage, previousSelectedItem: AudiobookItemDetail?, refreshedSelectedItem: AudiobookItemDetail?): AudiobookLibraryPage
internal fun resolveAudiobookLibraryPageAfterConfigChange(currentPage: AudiobookLibraryPage): AudiobookLibraryPage
internal fun shouldShowAudiobookConfigResetNotice(previousConfigChanged: Boolean, libraryPage: AudiobookLibraryPage, selectedItem: AudiobookItemDetail?): Boolean
internal fun shouldShowAudiobookDetailInvalidationNotice(currentPage: AudiobookLibraryPage, previousSelectedItem: AudiobookItemDetail?, refreshedSelectedItem: AudiobookItemDetail?): Boolean
internal fun sortAudiobookDetailChapters(chapters: List<AudiobookChapter>): List<AudiobookChapter>
internal fun resolveAudiobookCollectionLayout(availableWidth: Dp, fontScale: Float): MusicCollectionLayout
internal fun audiobookAuthorLabel(author: String?): String
internal fun resolveCurrentAudiobookChapter(chapters: List<AudiobookChapter>, positionSeconds: Int): AudiobookChapter?
internal fun resolveCurrentAudiobookChapterFromSorted(sortedChapters: List<AudiobookChapter>, positionSeconds: Int): AudiobookChapter?
internal fun sleepTimerRemainingLabel(sleepTimerRemainingSeconds: Int?, atChapterEnd: Boolean): String
```

- `AudiobookLibrarySelector`:书库选择行,复用 `MediaChoiceChip`,互斥选择 `selectableGroup` + `Role.Tab`。
- `AudiobookSummaryCard`:书库条目卡。整卡 `clickable(Role.Button, onClickLabel="打开详情")` 打开详情;内层 48dp 播放钮 `clickable(Role.Button, onClickLabel="播放有声书")`,Icon `contentDescription = null`;封面 `clearAndSetSemantics` 去重朗读。
- `AudiobookDetailHeader`:详情概览,复用 `resolveAudiobookCollectionLayout`(委托 `resolveMusicCollectionLayout`)与 `MusicCollectionDescription`,标题 `headlineMedium` + heading 语义。
- `AudiobookChapterRow`:详情页章节行,**纯展示不可点击**(行为决策,见 prd Out of Scope);`isCurrent` 高亮用 `primaryContainer`/`onPrimaryContainer` + `selected` 语义。
- `AudiobookChapterListSheet` / `AudiobookBookmarkSheet` / `AudiobookSleepTimerSheet` / `AudiobookPlaybackSpeedSheet`:播放器 4 弹层,统一 `MediaPlayerSheet` 容器。弹层复用 `MediaPlayerChoiceRow`,不另写近似选择行。
- `AUDIOBOOK_PLAYBACK_SPEED_OPTIONS`:倍速选项,经 `AudiobookPlaybackSpeedSheet` 委托 `MediaPlaybackSpeedSheet`。

## 3. 实现合同

### 书库 Home

- 列表复用统一品牌卡(非音乐四类列表),摘要卡间距与音乐第二轮收敛一致:LazyColumn `verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)`(8dp)。
- 书库选择复用 `MediaChoiceChip`,选中既有颜色又有 `selected` 语义(role 默认 `Role.Tab`,配 `selectableGroup`)。
- 状态卡按实际状态分支:未配置(empty)→「先接入你的有声书书库」、无书库(library_empty)→「没有可用书库」、空书库→「这个书库还没有内容」、加载中→`MediaLoadingCard`、错误→`MediaStateCard(tone=Error)`。错误态在卡片下方增加 `SecondaryActionButton("重试")`,与页头刷新同源(`refreshAudiobooks()`),使重试在短屏/大字体下不被页头图标隐藏。不叠加假空态:错误且无缓存内容时只显示错误卡,不显示「没有内容」。
- 状态卡、加载卡一律用共享 `MediaStateCard` / `MediaLoadingCard`,不复制近似 Surface。

### 摘要卡与详情概览

- 摘要卡信息层级:`titleMedium` 标题(两行省略)、作者行 `labelLarge onSurfaceVariant`、meta 行 `bodySmall onSurfaceVariant`(tnum 时长)。封面 72dp、md 圆角、`MenuBook` 兜底。
- 作者文案统一 `audiobookAuthorLabel`:null / 空串 / 空白 → 「未知作者」,有值 trim 后返回。summary 卡作者行、详情作者行、页头副标题三处都用同一函数,不得静默省略作者行。
- summary 卡内层播放钮:48dp 实占位(`NordicControlSizes.touchTarget`),Icon `contentDescription=null`(由 onClickLabel 承担朗读);整卡点击与内层按钮点击必须分离(子级 clickable 优先消费指针)。不得让整卡 ripple 吸收播放钮点击。
- 默认非当前情境下的次级文字(作者/简介/时间戳)统一使用实色 `onSurfaceVariant`,不得改用 `onSurface.copy(alpha = NordicAlpha.medium/subtle)`:在 `surfaceVariant 0.42` 卡上该透明度合成后对比度浅色约 3.2:1、深色约 4:1,低于 4.5:1 小字下限(与 music-ui 对应收敛一致)。高亮/选中态文字用配对 `onPrimaryContainer`。

- 详情封面同级去重:相邻标题已命名封面时,封面用 `Box(Modifier.clearAndSetSemantics {})` 包裹,避免重复朗读;`contentDescription` 传入标题与 clearAndSetSemantics 并存属冗余但无害(与音乐行现状一致)。
- 详情章节头为 `titleMedium` + `Modifier.semantics { heading() }`;章节行 `isCurrent` 用 `primaryContainer` 完整背景 + `onPrimaryContainer` 文字 + `semantics { selected = true }`,不能只依赖颜色区分。

### 当前章节解析

- `resolveCurrentAudiobookChapter` 先按 `startSeconds` 排序再求「起点 ≤ 当前位置」的最后一个章节;`positionSeconds` 先 `coerceAtLeast(0)`,所以负位置落在首章起点为 0 时返回首章,首章起点非 0 时返回 null。
- **详情页与播放器章节面板必须用同一映射**:详情页高亮 = `resolveCurrentAudiobookChapter(sortAudiobookDetailChapters(chapters), progress.currentTimeSeconds)`;播放器 = `resolveCurrentAudiobookChapterFromSorted(sortedChapters, positionSeconds)`。两处 call site 的一致性由单测锁定(`resolveCurrentAudiobookChapter_matchesDetailProgressForHighlight`)。注意两者对「已排序输入」使用不同的内部函数:详情先 `sortAudiobookDetailChapters`(稳定排序,等 start 保序),播放器直接用服务端章节顺序;若未来播放器改用排序输入,须保持同样的等值语义。
- 章节时间戳 `"${formatDuration(start)} - ${formatDuration(end)}"` 使用 `bodySmall.copy(fontFeatureSettings = "tnum")`,等宽数字防跳动。

### 播放器弹层

- 播放器 + 4 弹层(书签/睡眠定时/章节/倍速)统一走 `MediaPlayerSheet` 容器;每弹层内容尽量复用共享选择控件,不手写标题 Row / 关闭钮(参照 MusicEqualizerSheet 迁移参考)。
- `AudiobookChapterListSheet`:章节选择行复用 `MediaPlayerChoiceRow`,点击回调 `onSeekTo(chapter.startSeconds)`;当前章节高亮沿用选中态语言。
- `AudiobookBookmarkSheet`:`PrimaryActionButton("在 MM:SS 添加书签")` 放在 LazyColumn 之外(固定在弹层顶部),下方书签列表;当前进度 ±2 秒的书签高亮为 primaryContainer;行右侧删除钮用 `MediaPlayerIconAction(destructive = true)`。当前书签行整行 `semantics { selected = isCurrent }` + `clickable(Role.Button, "跳转到书签")`。短屏/IME 下「添加书签」按钮必须保持可达(不在 LazyColumn 内被滚出)。
- `AudiobookSleepTimerSheet`:(含「使用预选」行)读取 `LocalAppPreferences.current.audiobookSleepMinutes` 等;样本宿主不写真实偏好。剩余标签统一 `sleepTimerRemainingLabel`(未开启/将在当前章节结束时停止/已停止/N 秒后停止/N分M秒后停止)。
- `resolveCurrentAudiobookChapter` 等纯函数以 `internal` 暴露并在 `AudiobookScreenTest.kt` 覆盖;sheet 内部以 `internal` 暴露供 debug 样板复用(不得仅因供样板调用就把生产私有函数改为 public)。

### Debug 样板宿主

- `AudiobookCatalogSample` 复用生产 Composable(`AudiobookHomeContent` 镜像生产 LazyColumn 状态分支、`AudiobookPlayerScreen`、4 个 sheet 函数、`AudiobookDetailHeader`、`AudiobookSummaryCard`、`AudiobookChapterRow`),只提供确定性内存数据与事件回调。
- 宿主 `CompositionLocalProvider(LocalAppPreferences provides AppPreferences())` 显式提供默认偏好,使 skip 间隔标签确定;不写真实偏好。
- 样例 detail 章节列表**必须**与生产一致地解析并传入 `isCurrent`(样例当前进度 4211 → 第 3 章高亮)+ 章节头 heading 语义,否则本轮新增的章节高亮在样板中不可见、样例与生产行为漂移。
- 宿主页头/导航对照真实调用方:有声书 Home 根页「有声书」+ 设置齿轮(固定)+ 刷新;详情页用返回按钮回详情层级 Home,不挤掉视口。

## 4. 边界矩阵

| 场景 | 预期 |
|---|---|
| 320/360/392/720dp,字体 1/1.5/2 | 摘要卡/详情按 `resolveMusicCollectionLayout` 堆叠或并列;封面不为挤文字无限缩小 |
| isCurrent 章节 | `primaryContainer` 背景 + `onPrimaryContainer` 文字 + `selected` 语义,缺一不可 |
| 非当前章节时间戳 / 摘要卡 meta / 详情作者行 | 实色 `onSurfaceVariant`,合成对比 ≥ 4.5:1 |
| 错误且无缓存 | 只有错误卡 + 重试按钮,不叠加「没有内容」假空态 |
| 书签为空 / 当前书签高亮 | 「暂无书签」compact 卡;±2 秒书签高亮 |
| 短屏 / IME 播放器弹层 | 添加书签按钮固定可达;弹层内容可滚动 |
| 负进度 / 首章前 | `resolveCurrentAudiobookChapter` 不崩:钳到 0,首章起点为 0 时返回首章 |
| 乱序章节列表 | 详情页先稳定排序再解析当前章节;播放器用服务端顺序 |

## 5. 正反案例

- 好:摘要卡与音乐列表同用 `clearAndSetSemantics` 去重封面、同用实色 `onSurfaceVariant` 次级文字,meta 时间戳 tnum。
- 好:错误态在卡下补显式重试按钮,短屏大字体的重试不依赖页头图标。
- 差:`onSurface.copy(alpha = NordicAlpha.subtle)` 用在 0.42 surfaceVariant 卡上,合成对比不足却当成已收敛的次级文字。
- 差:详情章节行没有当前章节高亮,与播放器章节面板行为不一致(本轮只修展示;点击跳转业务补 onPlayChapter 回调 + instrumentation 另行任务)。

## 6. 验证

- `AudiobookScreenTest`:`resolveCurrentAudiobookChapter`(乱序/首章前 null/负值钳制/末章/等值边界/空列表)、`resolveCurrentAudiobookChapterFromSorted`(等值/空/负值/不重排)、`sortAudiobookDetailChapters`(乱序/等 start 保序/空)、`audiobookAuthorLabel`(空/空白/null/trim)、`sleepTimerRemainingLabel`(未开启/章节结束/已停止/倒计时)、配置变更/详情失效通知判定。
- 详情页与播放器章节高亮一致性由 `resolveCurrentAudiobookChapter_matchesDetailProgressForHighlight` 锁定。
- instrumentation:`UiCatalogScreenshotTest` / `UiCatalogInteractionTest` 按 7 个有声书条目(书库、详情/章节列表、播放器、章节面板、倍速、睡眠定时、书签)覆盖双主题 × 字号矩阵;短屏/IME 播放入口、书签添加按钮与章节完整可见。
- 编译、相关测试、lint、assemble 后,由用户真机检查浅深主题、大字体、长名称、无封面兜底与弹层交互。
- 自动化不等于整个有声书模块或全应用已通过视觉/交互验收。

## 7. 错误与正确示例

```kotlin
// 错误:0.42 surfaceVariant 卡上叠 0.5 alpha 的 onSurface 次级文字,对比约 3.2:1。
color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle)

// 正确:与音乐行一致的实色 onSurfaceVariant,合成对比 ≥ 4.5:1。
color = colorScheme.onSurfaceVariant
```

```kotlin
// 错误:整卡可点击 + 内层播放钮共用 ripple,无点击分离标签。
Surface(Modifier.clickable(onOpen)) { Play(cd = "播放有声书") }

// 正确:整卡与内层按钮各自 Role.Button + onClickLabel;内层 Icon contentDescription = null。
Surface(Modifier.clickable(role = Role.Button, onClickLabel = "打开详情", onClick = onOpen)) {
    Box(Modifier.clearAndSetSemantics {}) { CoverArt(...) }
    Column { ... }
    Surface(Modifier.size(NordicControlSizes.touchTarget)
        .clickable(role = Role.Button, onClickLabel = "播放有声书", onClick = onPlay)) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
    }
}
```

```kotlin
// 错误:详情章节行只靠颜色区分当前章节,无 selected 语义、未传 isCurrent。
AudiobookChapterRow(chapter, colors)

// 正确:详情页解析当前章节并传入高亮,行内声明 selected。
val currentChapter = item.progress?.let {
    resolveCurrentAudiobookChapter(sortAudiobookDetailChapters(detailChapters), it.currentTimeSeconds)
}
AudiobookChapterRow(chapter, colors, isCurrent = chapter.id == currentChapter?.id)
```