# 全页面共享 UI 一致性合同

## 1. 范围 / 触发条件

修改主题、字体、标题导航、分段/选择控件、搜索输入和详情动作时必须读取。本合同约束共享实现，不能把共享组件通过检查当作全应用逐页视觉验收通过。服务协议、播放进度和导航回调继续遵循对应业务合同。

## 2. 关键入口

```kotlin
internal fun nordicColorScheme(darkTheme: Boolean): ColorScheme
internal fun resolveMediaHeaderActionLayout(availableWidth: Dp, showBack: Boolean, actionCount: Int, fontScale: Float = 1f): MediaHeaderActionLayout
internal fun shouldScrollMediaSegments(availableWidth: Dp, minimumWidths: List<Dp>): Boolean
```

- `MediaPageHeader` / `HeaderActionGroup` / `AnimatedIconButton` / `ScreenBackButton`：页头与导航。
- `MediaPlayerSheet(title, colors, onDismiss, subtitle?, skipPartiallyExpanded?, trailingAction?, content)`：音乐/有声书/视频播放器弹层（倍速、章节、睡眠定时、书签、均衡器、播放队列等）的统一容器，内部自带标题头、关闭按钮、86% 最大高度、导航栏安全区与共享水平边距。`trailingAction` 槽位承载头部右上动作（如队列的「清空后续」），不得在弹层内部另写标题 Row。
- `MediaPlayerChoiceRow(title, selected, colors, onClick, subtitle?)`：弹层内选择行的统一实现；`selected = null` 表示动作行（Role.Button），非空表示互斥选择（Role.RadioButton）。
- `MediaTransientPill(message, colors, detail?)`：播放器表面 transient 提示 pill（seek 反馈、收藏失败等）的统一实现：full 圆角、surface 0.94 alpha 容器、primary 0.24 alpha 描边、primary 主文案 + 可选次要详情。不得在单个播放器里复制近似 pill。
- `MediaChoiceChip`：有声书/视频库选择及视频类型选择；不是非交互 `MetaChip`。默认 `role = Role.Tab`，音乐音效预设可传 `Role.RadioButton`；真实宽高至少 48dp，保留选中/禁用和调用方的 selectableGroup。
- `MediaSegmentedControl<T>`：音乐发现/歌曲/歌单导航、歌曲排序和专辑排序；接收稳定 key、显示标签和原始回调。
- `MediaSearchField`：音乐远程搜索、歌曲本地过滤、视频本地搜索；数据与 debounce 仍由调用方管理。
- `PrimaryActionButton` / `SecondaryActionButton`：详情页主要/次要动作。

## 3. 可执行约束

### 字体与主题

- `NordicTypography` 明确声明 Material 的全部 15 槽，均使用 Android 原生字体、明确行高与 0sp 字距。常用尺寸保持 displaySmall 32、headlineMedium 22、titleMedium 16、bodyMedium 14、labelLarge 13、bodySmall 12sp。
- 原生输入框、弹窗、菜单不能依赖与应用不同的默认字体层级。查询输入与 placeholder 同为 bodyLarge。
- 强调底色上的文字使用配对 `onPrimary/onSecondary/onTertiary`；浅紫/亮青底色不意味着必须使用白字。
- 信息文字优先使用 `onSurfaceVariant`，选中色块上的文字使用 `onPrimaryContainer` 等配对角色；disabled 才允许故意降低对比度。
- 半透明容器需先合成到真实底色再计算对比度。共享主题正文角色及已覆盖的选中/未选中分段文字，测试下限为 4.5:1，不能降低阈值来让测试通过。
- `LightTextSecondary = #666676`；原 #6B6B7B 在 surfaceVariant #EBEAF0 上仅约 4.37:1，已修正。

### 标题与操作

- `NordicControlSizes.touchTarget = 48dp`，为真实布局占位，不依赖越界扩大的隐藏点击范围。
- 顶部标题/返回/操作的布局必须由扣除页面边距后的宽度与 fontScale 决定。无法容纳的 action 进入更多菜单，不能直接删除、縮小或截掉；原顺序、disabled 与回调保留。
- 固定动作（如各媒体首页的设置齿轮）用 `HeaderAction(fixed = true)` 标记，始终内联渲染、不进入更多菜单；`resolveMediaHeaderActionLayout` 的 `fixedActionCount` 为固定动作预留槽位，其余动作在窄屏溢出。
- 根页头部使用单行紧凑布局（`MediaPageHeader` showBack=false 分支）：titleLarge SemiBold 左对齐 + 副标题降为 bodySmall 单行状态文本，Row 垂直居中；不使用 displaySmall 大字标题（与底部 dock 页签重复）。子页标题使用 headlineMedium，长子页标题允许两行，标题声明 heading 语义。根页副标题只保留状态语义（条目数/缓存年龄/刷新/错误），不写操作教学文案（如"点一下直接播放"）。
- 返回、搜索和页头图标采用同一尺寸/圆角/按压反馈；不为返回按钮单独增加常驻阴影。

### 选择与分段

- 互斥选择使用 `selectable`、`Role.Tab` 与 `selectableGroup`；选中不仅依赖颜色，也必须暴露 selected 语义。
- 播放器弹层选择行必须复用 `MediaPlayerChoiceRow`，不得在单个弹层里另写近似实现。选中态使用 `primaryContainer` 完整背景 + `onPrimaryContainer` 文字 + 勾选图标；未选中态使用 `surfaceVariant` 0.42 alpha 容器 + `onSurface` 文字；选中副标题用完整 `onPrimaryContainer`，未选中副标题用 `onSurfaceVariant`。深色合成背景下，0.78 alpha 的副标题只有约 3.78:1，不得恢复该透明度或降低 4.5:1 门槛。行最小高度 56dp，容器圆角 md。
- 弹层内的互斥选择 chip（如均衡器预设）同样使用 `selectable(selected, role = Role.RadioButton)` + 选中语义，选中样式对齐 `primaryContainer`/`onPrimaryContainer` 语言，最小高度 48dp；不得用裸 `clickable` + 仅颜色区分。
- 新弹层接入统一容器时，删除本地 `ModalBottomSheet` + 手写标题 Row 的重复实现（参考 `MusicEqualizerSheet`、`MusicQueueSheet` 的迁移），保持原有 `skipPartiallyExpanded` 与内容逻辑不变。
- 歌词展示面字号例外：`MusicLyricsDisplay` 歌词行使用 18sp/24sp（紧凑 16sp/20sp），位于 headlineMedium 与 titleMedium 之间，是专用展示面而非标准文本槽；以命名常量 `LYRIC_LINE_*` 显式声明。歌词长句自然换行，不用 maxLines/省略号截正文；跟随按钮按实际字体测量预留空间，不因出现/消失改变视口。其他新文本不得引用此字号例外。行为合同见 [音乐歌词](./music-lyrics.md)。
- 分段容器 outer md(16dp)、inner sm(12dp)，保留 4dp 内边距/间距；每个选项至少 48dp 高，容器至少 56dp，字体增大时自然增高。
- `rememberTextMeasurer` 测量真实 label，加入两侧 12dp padding 得到最小项宽；不能用字符数量或固定字宽代替。
- ≤4 项且等分宽度能完整容纳最宽标签时用等宽 Row；否则使用同一表面中的 LazyRow。>4 项始终使用 LazyRow。
- 横向列表使用稳定 key；所选项不在可见区时定位到它，已可见时不强制跳动。
- 选择芯片至少 48dp 高，保留最多 240dp 宽的长标签省略与完整无障碍文本；大字体不固定死高度。

### 播放器面板与窗口

- `MediaPlayerSheetHeader` 的 `trailingAction` 在字体 >1.3 或内容宽度 <320dp 时换到下一行；标题、关闭与次要操作都保持可达，不缩小触控目标。
- `MediaPlayerSheet` 只通过其 `DialogWindowProvider` 设置自己的系统栏图标；根据应用实际 surface 明暗选择前景，不依赖系统夜间模式，也不改宿主 Activity 的窗口所有权。
- `PolishedNowPlayingBar` 最小 66dp、`PolishedBottomNav` 最小 58dp，随字体自然增高；不能固定高度后把文字裁在容器外。
- 音乐播放器封面/歌词主显示区必须同时暴露手势与语义双路径:单击切换封面/歌词、双击左右半区快退/快进 ±10s,并配`CustomAccessibilityAction`(「显示歌词/显示封面」「后退/前进 10 秒」)映射到同一回调与`MUSIC_DOUBLE_TAP_SEEK_SECONDS`;空播放器不暴露动作。
- 播放器顶栏(`MediaPlayerTopBar`)标题声明`heading()`,与全应用页头规范一致。
- 音频 sheet 与视频窗口内 panel 继续使用各自宿主，样板修正不改变全屏/PiP 生命周期。

### 输入与详情动作

- 搜索输入统一边框、背景、搜索图标、可读提示文字、清除入口和 Search IME；提交键只收键盘/焦点，不新增隐式请求。
- 清除与收起搜索分别是不同操作，保留调用方回调；音乐空白查询清除入口语义沿用既有规则。
- 主要/次要详情按钮使用最小高度而非固定高度，长文字最多两行并居中，保留 enabled 状态和 Role.Button。
- 所有共享控件使用既有 Nordic 间距、圆角和动效；不要复制一套近似但略有不同的实现。

## 4. 边界矩阵

| 条件 | 期望 |
|---|---|
| 320/360/392/720dp，fontScale 1/1.5/2，有/无返回，0–5 个 action | 实际 48dp 目标容纳，标题仍有空间，额外 action 可在菜单找到 |
| 零个 header action | 不生成空容器或假更多入口 |
| 分段标签长 / 字体大 / 六种排序 | 保持文字和点击尺寸，使用横向滚动，不粗暴截字 |
| 选中项不在横向可视区 | 定位；已可见时不抖动 |
| 无 query / 有 query / 纯空白 | 清除入口与调用方既有逻辑一致；没有重复 search 请求 |
| 浅色的次要文字、浅紫/亮青按钮、选中容器 | 对实际合成色执行对比度断言 |
| 主/次动作长文字、大字体、disabled | 可换行并自然增高，不发生假点击或丢失禁用语义 |

## 5. 正反案例

- 好：窄屏标题完整可读，刷新/主题切换在更多里仍可使用。
- 好：歌曲排序与主分段来自同一个组件，字体增大后自动滚动而不是变成另一种控件。
- 差：为通过对比测试把 4.5 降成 4.3；忽略实际的半透明背景合成。
- 差：列表 Tab 固定外框 48dp 再扣 8dp padding，使真实点击区只有 40dp。
- 差：搜索 placeholder 14sp、输入值落回默认 16sp，或不同页面使用不同 TextFieldDefaults。

## 6. 测试与验证证据

- `NordicDesignContractTest`：完整字体槽、主题配对前景、分段选中/非选中文字的合成对比度。
- `MediaHeaderLayoutTest`：屏宽/字体/返回/操作数量矩阵；实际操作宽度与更多可达策略。
- `MediaSegmentedControlTest`：标签宽度边界、最小点击区域、等分/滚动、空集合。
- 保留媒体搜索/过滤/排序/导航的既有测试，运行 compile、相关 test、lint、assemble。
- 这些测试不证明所有页面的渲染、键盘与点击正确。每个页面仍须有真机反馈；在覆盖矩阵里明确分开代码、自动检查和真机验收。

## 7. 错误与正确示例

```kotlin
// 错误：固定小高度、相互独立的样式分支。
Surface(Modifier.height(48.dp)) { Row(Modifier.padding(4.dp)) { /* 40dp target */ } }

// 正确：真实标签测量与共享布局决定等分还是滚动。
MediaSegmentedControl(options, selectedOption, label, optionKey, colorScheme, onOptionSelected)
```

```kotlin
// 错误：浅紫主按钮始终强制白字。
Text("播放", color = Color.White)
// 正确：使用与按钮底色成对、经过对比度验证的前景角色。
Text("播放", color = colorScheme.onPrimary)
```

## 业务页面扩展

- 音乐集合、列表、封面、缺失信息与管理动作的进一步约束见 [music-ui.md](./music-ui.md)，对应组件复用本合同的字体、主题、动作尺寸和无障碍原则。
- 有声书详情概览复用音乐集合布局策略：`resolveAudiobookCollectionLayout` 委托 `resolveMusicCollectionLayout`（窄屏/大字体堆叠居中，宽屏并列 128/160dp），不得另写固定 128dp Row。简介卡片复用 `MusicCollectionDescription`（3 行截断 + 展开/收起，按 itemId 隔离状态），保留域内标题容器。
- 有声书作者缺省文案统一 `audiobookAuthorLabel`（空白 → 「未知作者」），summary 卡、详情作者行、页头副标题三处均不得静默省略作者行。
- 有声书摘要卡整卡 `clickable(Role.Button)`(打开详情)与内层 48dp 播放钮 `clickable(Role.Button)` 分离,内层 Icon `contentDescription = null`;次级文字统一实色 `onSurfaceVariant`,不得用 `onSurface.copy(alpha = ...)` 叠 0.42 surfaceVariant 卡(合成对比不足 4.5:1);详情章节行 `isCurrent` 高亮必须 `primaryContainer`/`onPrimaryContainer` + `selected` 语义。完整合同与当前章节解析一致性见 [有声书页面 UI](./audiobook-ui.md)。
- 视频域节头（推荐区、全部 N 项、分集）使用 `headlineMedium` + `onBackground` + `Modifier.semantics { heading() }` + SemiBold，与音乐首页节头同一规范。
- 视频海报推荐卡宽度使用 `videoShelfCardSize(fontScale)`（132dp × 钳制 scale，上限 176dp），与音乐 `musicShelfArtworkSize` 同一增长策略；不得写固定宽度。
- 视频详情分集筛选、播放器倍速面板选择行分别复用 `MediaChoiceChip`（含 `selectableGroup`）与 `MediaPlayerChoiceRow`；不得另写裸 `clickable` 自绘选中样式。
- 视频设置开关行保持最小 64dp、主题字体与间距；整行使用 `toggleable(value, role = Role.Switch)` 暴露开关状态，内部 M3 `Switch(onCheckedChange = null)` 只负责视觉，避免嵌套两个操作目标。PiP 中不组合播放器面板与控制，保留视频/字幕节点。
- 视频浏览与详情的完整合同见 [视频页面 UI](./video-ui.md)：卡片 Button 语义、封面朗读去重、续播卡字号增长、当前分集高亮和错误重试均须逐页验收。
- 配置页表单遵循同一语言：`ConfigTextField` 用 TextField `label` 参数承载字段名（自带关联语义），placeholder/输入统一 `bodyLarge`；服务器卡片容器 `surfaceVariant` 0.5 alpha + md 圆角 + 标题 heading 语义；保存/测试连接使用 `PrimaryActionButton`/`SecondaryActionButton`；状态消息最多三行省略。服务器类型选择复用 `MediaChoiceChip`（enabled 表达支持状态），不另写自绘选中容器。

## 设置行与样板验收扩展

- `SettingsRow` 使用 `shouldInlineSettingsValue(availableWidth, titleWidth, valueWidth, fontScale, hasIcon, hasNavigation)`：实际文字测量连同图标、箭头和间距共同预算，不能再按字符串长度决定值的位置。空间不足时值独立放在标题下，说明另起一行。
- 设置行最小 64dp；整行暴露唯一的 Button/Switch 角色，内部 `Switch(onCheckedChange = null)` 不再重复接收操作。禁用视觉与禁用语义必须同时保留。
- `ServerEditorScreen` 仍拥有测试连接、保存、取消及草稿保护；共享 `ServerEditorContent` 不构建 repository，复用的 `ConfigTextField` 保留安全键盘、密码显隐与 enabled。
- 设置主页入口列表按「连接 / 体验与播放 / 数据与应用」分段,组内使用`SettingsRow` + 分隔线(0.5 alpha);页级空态(尚未添加服务器、暂无下载、无匹配设置)统一复用紧凑状态卡,不得用裸`Text`代替。
- 服务器列表行保留 Radio `selected`语义、名称/账号或测试状态两行与溢出菜单(编辑/测试/删除);测试状态行使用实色`onSurfaceVariant`,失败用`error`。
- 设置选择对话框(`SettingsChoiceDialog`)选中行使用`primaryContainer`背景 + `onPrimaryContainer`文案强调,与全应用选择语言一致;选项行最小 56dp。
- 已无生产引用的单服务器`Navidrome/Audiobook/VideoConfigCard`死亡组件已从`ConfigCards.kt`移除;仅保留被`ServerEditorContent`复用的`ConfigTextField`。
- 调试样板和真实设置流程的隔离、系统字号、全窗口截图与逐页证据合同见 [UI 样板验收](./ui-catalog-verification.md)。
