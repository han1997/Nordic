---
name: Nordic Media Hub
description: 内容优先、保留紫青品牌的 Android 原生媒体客户端
colors:
  dusk-violet: "#B098FF"
  dusk-violet-deep: "#7B5FD3"
  fjord-cyan: "#7DD3FC"
  fjord-cyan-vivid: "#0EA5E9"
  polar-shadow: "#0A0A0F"
  frost-surface: "#DD1A1A24"
  twilight-slab: "#252530"
  snow-mist: "#F5F4F8"
  cloud-surface: "#DDFFFEFF"
  drift-variant: "#EBEAF0"
  ash-text: "#E8E8EE"
  slate-muted: "#9898A8"
  ink-text: "#1A1A20"
  iron-muted: "#666676"
rounded:
  none: "0dp"
  sm: "12dp"
  md: "16dp"
  lg: "20dp"
  xl: "24dp"
  full: "50%"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "20dp"
  xxl: "24dp"
  xxxl: "32dp"
  content: "16dp"
controls:
  touchTarget: "48dp"
  icon: "24dp"
  compactIcon: "20dp"
  choiceMaxWidth: "240dp"
---

# Nordic 设计系统

## 1. 方向：让内容成为主角

**The Listening Room · 聆听的房间**：封面、歌名、章节和视频内容是视觉中心；界面负责清晰组织，而非争夺注意力。

当前精修遵循用户确认的方向：保留紫色主色、青色辅助色、深浅主题、原生字体和导航结构，采用均衡密度。不替换品牌、不引入新字体或图标库，不用大片留白或过度紧凑制造变化。

- 普通列表和表单静止时无重阴影；仅播放器与底部 Dock 等浮层保留有限高度感。
- 颜色使用 Material 语义配对，紫青渐变只延续既有封面兜底／播放器背景，不新增循环动画或昂贵模糊。
- 间距、圆角、字体与动效以 `ui/theme/` 的 Kotlin 定义为唯一可执行来源；本文件用于说明，不再维护另一套旧数值。
- 全局共享实现改变不等于所有页面完成验收；首轮方向已确认，第二轮完成音乐域,第三轮完成有声书域，第四轮完成视频浏览与详情，其余媒体域仍需分批逐页验证。

## 2. 字体与颜色

`NordicTypography` 声明全部 15 个 Material 字体槽，统一 Android 原生字体、明确行高和 0sp 字距。

| 用途 | 字号 / 行高 | 字重 |
|---|---|---|
| displayLarge / Medium / Small | 40/48、36/44、32/40sp | Bold |
| headlineLarge / Medium / Small | 28/36、22/28、20/28sp | Bold / Bold / SemiBold |
| titleLarge / Medium / Small | 20/28、16/22、14/20sp | SemiBold |
| bodyLarge / Medium / Small | 16/24、14/20、12/18sp | Normal / Normal / Medium |
| labelLarge / Medium / Small | 13/18、12/16、11/16sp | SemiBold / Medium / Medium |

- 根页标题使用 titleLarge；子页使用 headlineMedium；不重复显示顶栏类别与集合长名称。
- 主文案使用 onSurface/onBackground，次要信息使用 onSurfaceVariant。选中、错误和强调容器使用对应 on…Container 前景。
- 普通正文以真实合成背景计算，最低对比度 4.5:1；禁用态单独表达，不以低透明度掩盖正常可读内容。
- 深色 primary 为浅紫，使用 DarkBackground 前景；浅色 primary 使用近白前景。青色强调使用已验证的深色前景，不一概写白字。
- 时间和时长使用等宽数字特性 tnum。完整歌词使用专用 18/24sp（紧凑 16/20sp）尺度与自然换行，不按普通副标题裁切。
- `NordicAlpha` 仅是既有次要文字透明度层级；新说明文字优先采用 onSurfaceVariant，不复制任意 alpha。

## 3. 布局与操作

### 统一尺度

顶部 YAML 与 `NordicSpacing`、`NordicShapes`、`NordicControlSizes` 对齐。普通屏幕内容边距 16dp；相邻元素按 4/8/12/16/20/24/32dp 组织，不引入近似但互不相同的尺度。

- 48dp 为实际布局占位的最小触控目标，而非透明越界点击区。装饰图标和封面可以更小。
- 正文和说明自然换行；必要操作不会因窄屏、大字体被删除、挤出屏幕或覆盖。
- 输入框、弹层、播放器和列表分别处理 IME、系统栏及短屏滚动；不通过缩小文字或按键规避适配。
- 状态栏与导航栏图标匹配当前窗口的实际背景；播放器 sheet 跟随应用主题，原生确认弹窗的全窗遮罩则按变暗后的背景检查，不能只依据应用浅／深主题。

### 导航、搜索与选择

- 使用 `MediaPageHeader`；固定设置齿轮保留内联位置，其余动作在空间不足时进入更多菜单。
- `MediaSearchField` 的输入和提示均用 bodyLarge，保留清空、收起、Search IME 和业务原有请求规则。
- `MediaSegmentedControl` 使用 outer md / inner sm、4dp 内间距、48dp 选项；按真实标签宽度选择等分或横向滚动。
- 选择和开关暴露 selected / checked / disabled 与正确角色，不能只靠颜色。

## 4. 样板组件规则

### 音乐浏览、集合详情与管理

- 复用 `MusicLibraryRow`：52dp 封面，16dp 外圆角，12dp 横向／8dp 纵向内边距；静止无阴影。
- 歌曲行合并歌手和专辑为次要信息，歌名最多两行，时长按实际空间决定是否移到下一行；专辑详情不逐行重复当前专辑名。
- 音乐列表项间距统一为 8dp；发现与搜索建议按“节头＋内容”成组，节内 8dp、节间 24dp，横向卡片仍独立滚动。
- 集合概览按可用宽度与字体选择并列／堆叠，封面仍为 128/160dp；标题、作者、metadata 和播放按钮通过 8dp 内节奏组织。
- 只有已确认的空数据才显示空态；加载与失败保留现有集合和计数，不伪装成暂无内容。
- 全局音乐反馈属于页面滚动内容，不在固定页头和 Dock 之间挤掉页面；未配置与已配置空库分开，缓存刷新保留条目，失败重试沿用原加载回调。
- 歌单数量与新建动作可自然换行；管理动作维持重命名／删除的原确认流程。名称与删除弹窗正文可滚动，确认／取消实际至少 48dp，按钮与 Done 共用提交保护。
- 原生弹窗聚焦标签使用 onPrimaryContainer，在实际 surfaceContainerHigh 上核对正文 4.5:1，不能拿白背景对比结果代替。


### 有声书浏览、详情与播放器

- 书库条目卡整卡点击打开详情,内层 48dp 播放按钮独立角色与朗读标签;列表项间距与音乐一致为 8dp,封面去重 TalkBack 朗读。
- 详情概览复用音乐集合布局策略(窄屏/大字体堆叠居中,宽屏并列 128/160dp);章节头声明 heading,当前章节用 primaryContainer 背景与 selected 语义高亮(详情页跳转列为 backlog,最近仍只有播放器面板可跳转)。
- 无配置/无书库/空书库/加载/错误等状态卡沿用共享卡片;错误卡下方固定显式重试入口,不依赖页头图标。
- 次级文字统一实色 onSurfaceVariant,不在半透明卡片上降低透明度;作者缺失统一「未知作者」。播放器与书签/睡眠定时/章节/倍速弹层复用共享面板与选择行。

### 视频浏览与详情

- 视频书库 Home 抽取无副作用内容层，生产宿主继续持有 Emby、缓存、刷新与 Resume 数据流；Debug 样板直接复用生产内容 Composable。
- 电影/剧集卡片整卡使用 `Role.Button` 与明确打开详情标签，封面使用 `clearAndSetSemantics` 去重；次级信息使用实色 `onSurfaceVariant`。
- 继续观看卡从 240dp 基准按系统字号增长并钳制到 300dp，中央播放装饰保持 48dp 实际尺寸；海报推荐卡继续使用 `videoShelfCardSize`。
- 视频详情简介与分集节头声明 heading；当前分集使用 `primaryContainer`、`onPrimaryContainer` 与 `selected` 语义表达，不依赖颜色 alone。
- 连接错误在滚动内容中提供显式重试，错误且无缓存时不同时显示成功空库文案；本轮不改变 Emby 协议或播放器窗口。

### 设置与服务器表单

- `SettingsRow` 最低 64dp，值的位置由真实标题／值测量、图标和箭头预算决定；大字体或长值放在标题下方，不再使用字符串长度阈值。
- 模块显示使用一组安静表面与分隔线，说明“至少保留一个”和“隐藏不删除数据”；开关仍由持久化结果驱动。
- 服务器表单分为连接信息与身份验证；复用 `ConfigTextField` 的字体、边框、密码安全键盘、可见性按钮和支持说明。
- 连接测试、保存、错误与草稿保护仍属于生产宿主；只读样板不能触发真实网络或存储。

### 播放器、队列与底部面板

- 播放器头部用实际 48dp 按钮加自适应标题列，避免对称空位把大字体标题挤成窄列；保持既有封面、歌词和运输控制关系。
- 队列行保留 42dp 封面与 48dp 拖动手柄／更多按钮，歌名最多两行，当前播放用容器与文字共同表达。
- 下一首、移除、上移、下移收进可访问菜单；保留原引擎回调和禁用条件，不在生产 UI 维护第二份播放队列。
- 拖动目标按已渲染行的中心位置计算，大字体变高也不靠固定行高猜测；回调读取最新拖动状态。
- `MediaPlayerSheet` 保持原有最大高度和底部安全区，窄屏／大字体时头部次要操作换行；倍速选项之间留 4dp 间隔。
- 均衡器组件完整展开并在内部滚动；预设复用选择控件的 RadioButton 角色，频率／分贝置于滑条上方，频段目标真实达到 48dp。为 Material3 滑条的横向语义扩展预留实际边距，不依赖被列表裁掉的越界目标。该组件仍未接入真实播放器入口。
- 音乐下载操作层保留原来源回调，可滚动；无曲目／地址、本地曲目、下载中与已下载状态禁止开始下载。
- 音频底部 sheet 与视频窗口内 panel 不互换，避免改变全屏和 PiP 所有权。
- Dock 的可见模块和迷你播放条是独立条件；迷你播放条至少 66dp、导航至少 58dp，并可随字体自然增高，禁止固定高度裁掉元信息。

## 5. 动效与状态

- 沿用 `NordicMotion` 的 150/200/300/450ms 与既有 easing；微交互不引入弹跳、列表逐项延迟或持续循环。
- 保留按压反馈、取消恢复与瞬时提示；高频播放状态只在必要的叶子节点订阅。
- 加载、刷新、空白、未配置、错误／重试、缺失封面和长文本分开验收；失败文案不抹掉已存在的内容。
- 保留来源隔离、模块即时更新、未保存退出保护、歌词跟随和播放进度等业务合同。

## 6. 验收方式

- 全量覆盖清单分开记录源码审查、模拟器渲染、交互测试和人工视觉结论，未覆盖项保持待验。
- 共享规则检查 320/360/392/720dp、字体倍率 1/1.5/2 和双主题；样板在独立 API 34 模拟器检查正常／大字体、异常状态、短屏／宽屏；大字体截图真实切换系统字号，不能只改页面 LocalDensity 而遗漏弹层窗口。
- Debug 的 `UiCatalogActivity` 复用生产内容 Composable，提供主题、字号与状态选择；仅改变预览内存，不连接账号或启动播放服务。Release 不含入口、样例封面或测试代码。
- 截图证明渲染被执行，不证明每个细节正确；仍需逐张视检与语义／回调断言。真实设置入口另做持久化冒烟，模拟器结果不等于真实服务器播放或个人手机验收。

细化合同见 `.trellis/spec/backend/ui-consistency.md`、`music-ui.md`、`audiobook-ui.md`、`video-ui.md`、`music-lyrics.md` 和 `quality-guidelines.md`。
