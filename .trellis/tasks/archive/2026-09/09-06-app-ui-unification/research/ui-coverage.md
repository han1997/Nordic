# 全页面覆盖矩阵

原始目标覆盖所有页面；本表不是已完成声明。入口盘点基于当前代码，仍需逐项检查完整内容与运行状态。

状态：待审查 → 已审查 → 已改动 → 自动检查通过 → 渲染/交互通过。任何一列缺证据均不算页面完成。

| 域 | 页面/面板与子状态 | 入口（ui/，全局入口除外） | 源码审查/改动 | 浅色渲染 | 深色渲染 | 小屏/横屏/大字体 | 交互/状态 |
|---|---|---|---|---|---|---|---|
| 全局 | 底部导航 / 播放 Dock / 收起把手 | `MainActivity.kt, PlaybackDock.kt` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 公共 | 页面标题 / 返回 / 操作菜单 | `SharedComponents.kt, AnimatedComponents.kt` | 共享组件已改；占位矩阵通过，更多菜单/字体真机待验 | 待验证 | 待验证 | 待验证 | 待验证 |
| 公共 | 加载 / 空内容 / 配置缺失 / 失败与重试 | `MediaStateComponents.kt` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 发现首页 / 最近播放 / 推荐与快捷入口 | `MusicScreenV2Pages.kt:MusicHomePage, MusicHomeSections.kt` | 概览/卡片/中文文案已统一；待真机验证 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 歌曲列表 / 排序 / 全部播放 | `MusicScreenV2Pages.kt:MusicSongsPage` | 列表/时长自适应已统一；待真机验证 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 专辑列表 / 排序 | `MusicScreenV2Pages.kt:MusicAlbumsPage` | 列表/封面兜底已统一；待真机验证 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 歌手列表 | `MusicScreenV2Pages.kt:MusicArtistsPage` | 列表/头像/中文计数已统一；待真机验证 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 歌手详情 / 专辑列表 | `MusicScreenV2Pages.kt:MusicArtistDetailPage` | 概览/加载/计数/列表已统一；未新增不存在的热门歌曲功能 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 专辑详情 / 曲目 | `MusicScreenV2Pages.kt:MusicAlbumDetailPage` | 概览/加载/空错区分/列表已统一；待真机验证 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 搜索落地 / 搜索输入 / 分组结果 / 无结果 | `MusicScreenV2Pages.kt:MusicSearchPage, MusicBrowseComponents.kt` | 复用新列表/卡片与既有搜索组件；待组合交互验证 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 歌单列表 | `MusicScreenV2Pages.kt:MusicPlaylistsPage` | 列表/新建入口/元信息已统一；待真机验证 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 歌单详情 / 管理操作 / 曲目列表 | `MusicScreenV2Pages.kt:MusicPlaylistDetailPage` | 概览/简介/管理动作/列表已统一；不声明新增未有的排序功能 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 歌单创建 / 重命名 / 删除确认弹窗 | `MusicScreenV2.kt:AlertDialog` | 字段/提交/删除语义已改；原忙碌守卫保留，待真机验证 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 主播放器 / 歌词视图 / 缺失歌词 | `MusicPlayerScreen.kt` | 第三批已审查：transient pill 合并为 MediaTransientPill、歌词字号常量化并记录例外；渐变停靠点按先例 out-of-scope | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 队列面板 / 拖拽 / 当前定位 / 清空确认 | `MusicQueueSheet.kt` | 第三批已改动：迁移 MediaPlayerSheet + trailing 槽位、未知歌手文案、48dp 触达、未用参数与魔法数清理 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 播放速度面板 | `MusicPlayerScreen.kt:MusicPlaybackSpeedSheet` | 已共享化（第一批）；无进一步改动 | 待验证 | 待验证 | 待验证 | 待验证 |
| 音乐 | 均衡器面板 / 预设 / 自定义 | `MusicEqualizerSheet.kt` | 第三批已改动：预设 chip selectable/Role 语义、primaryContainer 选中语言、48dp、widthIn 标签 | 待验证 | 待验证 | 待验证 | 待验证 |
| 有声书 | 书库首页 / 库选择 / 书籍卡片 | `AudiobookScreen.kt` | 库选择已统一；第四批：summary 卡作者缺省文案「未知作者」 | 待验证 | 待验证 | 待验证 | 待验证 |
| 有声书 | 书籍详情 / 播放入口 / 章节 | `AudiobookScreen.kt:AudiobookDetailHeader` | 第四批已改动：概览复用共享 collection layout（堆叠/并列自适应）、简介展开收起、作者缺省文案、节头/边框微对齐 | 待验证 | 待验证 | 待验证 | 待验证 |
| 有声书 | 主播放器 / 封面信息切换 / 进度 | `AudiobookPlayerScreen.kt` | 第一批已统一容器与触达；无进一步改动 | 待验证 | 待验证 | 待验证 | 待验证 |
| 有声书 | 章节面板 / 当前定位 | `AudiobookPlayerScreen.kt:AudiobookChapterListSheet` | 第一批已统一（MediaPlayerSheet/ChoiceRow）；无进一步改动 | 待验证 | 待验证 | 待验证 | 待验证 |
| 有声书 | 倍速面板 | `AudiobookPlayerScreen.kt:AudiobookPlaybackSpeedSheet` | 第一批已共享化；无进一步改动 | 待验证 | 待验证 | 待验证 | 待验证 |
| 有声书 | 定时关闭面板 | `AudiobookPlayerScreen.kt:AudiobookSleepTimerSheet` | 第一批已统一；无进一步改动 | 待验证 | 待验证 | 待验证 | 待验证 |
| 有声书 | 书签面板 / 添加 / 删除 | `AudiobookPlayerScreen.kt:AudiobookBookmarkSheet` | 第一批已统一；第四批：书签行选中副标题对齐 0.78 alpha 规范 | 待验证 | 待验证 | 待验证 | 待验证 |
| 视频 | 媒体库 / 库切换 / 聚焦区 / 继续观看 | `VideoScreen.kt, VideoBrowseComponents.kt` | 库选择已统一；聚焦/卡片/继续观看仍待精查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 视频 | 搜索 / 类型筛选 / 无结果 | `VideoBrowseComponents.kt:VideoBrowserControls` | 搜索与类型选择已统一；组合交互/空态真机待验 | 待验证 | 待验证 | 待验证 | 待验证 |
| 视频 | 电影详情 / 简介 / 续播与从头播放 | `VideoDetailScreen.kt` | 共享主次动作已改；影片详情排版仍待精查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 视频 | 剧集详情 / 已看筛选 / 剧集行 | `VideoDetailScreen.kt:VideoEpisodeFilterRow, VideoEpisodeRow` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 视频 | 竖屏播放器 / 横屏全屏 / 手势反馈 / 锁定 | `VideoPlayerScreen.kt, VideoPlayerChrome.kt` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 视频 | 更多设置面板 | `VideoPlayerPanels.kt:Settings` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 视频 | 倍速面板 | `VideoPlayerPanels.kt:Speed` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 视频 | 影片信息面板 | `VideoPlayerPanels.kt:Info` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 视频 | 按季选集面板 | `VideoPlayerPanels.kt:Episodes` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 配置 | 配置首页 / 主题操作 / 保存反馈 | `ServerConfigScreen.kt` | 共享主题/页头已更新；表单与反馈仍待精查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 配置 | Navidrome 表单 / 测试连接 / 校验 | `ConfigCards.kt:NavidromeConfigCard` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 配置 | AudiobookShelf 表单 / 测试连接 / 校验 | `ConfigCards.kt:AudiobookConfigCard` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |
| 配置 | Emby 表单 / 认证方式 / 不支持服务说明 | `ConfigCards.kt:VideoConfigCard` | 入口已盘点，待逐项审查 | 待验证 | 待验证 | 待验证 | 待验证 |

## 通用状态矩阵

- 每个数据页面：初次加载、有缓存刷新、空列表、搜索无结果、服务器错误、缺失图片、长标题。
- 每个操作：正常、选中、禁用、提交/连接中、成功、失败、返回/关闭。
- 每个播放器：暂停/播放、未知时长、拖动与取消、缓冲/错误、切换内容、弹层与返回优先级。
- 两种主题 + 320/360/392dp 紧凑宽度 + 720dp 宽屏 + fontScale 1.0/1.5/2.0。
- 避让状态/导航栏、刘海、键盘和底部 Dock；不能用静态源码检查替代真实布局或交互证据。

## 第一批共享基础

- 完整 Typography / 主题语义前景色；标题与操作响应式占位；48dp 实际操作目标；跨媒体库选择控件。
- 即使共享组件已改动，以上各页面仍须独立走完验收，不直接将所有行标记完成。

## 首批自动检查范围

- 新增字体/主题合成对比度、页头实际占位、分段真实宽度策略测试。
- 相关回归 307 个用例通过，compile/lint/assemble 通过；没有为任何页面勾选真机通过。
- 用户已选择自行真机调试，反馈清单见 `../manual-checklist.md`；不再尝试模拟器下载。

## 第二批覆盖说明

- 音乐浏览、集合详情、列表与歌单弹窗已做本批源码统一；真机各列仍未标记通过。
- 修正初始盘点中推测存在的“热门歌曲”和“歌单曲目重排”名称：现有入口是歌手专辑列表、歌单管理与曲目列表；未删除任何真实页面或缩小全页面目标，也未借 UI 统一新增这些独立业务功能。
