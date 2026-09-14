# 页面、弹层与组件覆盖清单

## 使用方式与边界

- 本轮先交付四组样板：歌曲列表（含导航/Dock）、专辑详情、音乐播放器（含歌词/队列/倍速）、模块显示与服务器表单。
- 下表按已实现的路由枚举、弹层分支和入口建立清单；“后续逐页审查”不代表本轮已经打开或完成该页面。
- 原始图片和 instrumentation 日志在 `app/build/reports/ui-polish/`；批次、哈希与视觉审查记录见 [evidence-index.md](./evidence-index.md)。
- [component-inventory.json](./component-inventory.json) 单独记录 169 个顶层 Composable 的源码定位；辅助函数/宿主层不冒充独立页面。
- 全应用统一规则仍需 320/360/392/720dp、字号 1/1.5/2、浅深主题；本轮只在样板进行实际渲染矩阵和交互检查。
- 每个页面后续分别检查适用的加载、刷新、空白、未配置、错误/重试、缓存、无封面、长文本；交互分别检查选中、禁用、按压、焦点、IME、提交中/失败。不能把不存在的状态当作已实现，也不靠重复静态图片计入覆盖。
- 本轮未连接任何真实服务器；无真实媒体播放/进度/PiP验收，不操作个人手机。

## 页面与弹层

### 音乐浏览

| 页面/弹层 | 源码入口 | 本轮范围 | 视觉方向/证据范围 |
|---|---|---|---|
| 发现 | `MusicScreenV2Pages.kt` | 后续，未逐页验收 | 未执行 |
| 专辑列表 | `MusicScreenV2Pages.kt` | 后续，未逐页验收 | 未执行 |
| 歌曲列表 | `MusicScreenV2Pages.kt` | 样板 `songs` | 首轮方向已确认；限已列证据 |
| 歌手列表 | `MusicScreenV2Pages.kt` | 后续，未逐页验收 | 未执行 |
| 歌手详情 | `MusicScreenV2Pages.kt` | 后续，未逐页验收 | 未执行 |
| 专辑详情 | `MusicScreenV2Pages.kt` | 样板 `album` | 首轮方向已确认；限已列证据 |
| 音乐搜索 | `MusicScreenV2Pages.kt` | 后续，未逐页验收 | 未执行 |
| 歌单列表 | `MusicScreenV2Pages.kt` | 后续，未逐页验收 | 未执行 |
| 歌单详情 | `MusicScreenV2Pages.kt` | 后续，未逐页验收 | 未执行 |

### 音乐播放与弹层

| 页面/弹层 | 源码入口 | 本轮范围 | 视觉方向/证据范围 |
|---|---|---|---|
| 完整音乐播放器 | `MusicPlayerScreen.kt` | 样板 `player` | 首轮方向已确认；限已列证据 |
| 完整/同步歌词 | `MusicLyricsDisplay.kt` | 样板 `lyrics` | 首轮方向已确认；限已列证据 |
| 播放队列与更多菜单 | `MusicQueueSheet.kt` | 样板 `queue` | 首轮方向已确认；限已列证据 |
| 音乐倍速 | `MusicPlayerScreen.kt` | 样板 `speed` | 首轮方向已确认；限已列证据 |
| 音乐均衡器 | `MusicEqualizerSheet.kt` | 后续，未逐页验收 | 未执行 |
| 收藏/下载操作层 | `MusicPlayerScreen.kt` | 样板 `player` | 未单独视检；仅所列交互回归 |
| 新建歌单 | `MusicScreenV2.kt` | 后续，未逐页验收 | 未执行 |
| 重命名歌单 | `MusicScreenV2.kt` | 后续，未逐页验收 | 未执行 |
| 删除歌单确认 | `MusicScreenV2.kt` | 后续，未逐页验收 | 未执行 |

### 有声书

| 页面/弹层 | 源码入口 | 本轮范围 | 视觉方向/证据范围 |
|---|---|---|---|
| 书库/来源选择/筛选 | `AudiobookScreen.kt` | 后续，未逐页验收 | 未执行 |
| 书籍详情/章节列表 | `AudiobookScreen.kt` | 后续，未逐页验收 | 未执行 |
| 有声书播放器 | `AudiobookPlayerScreen.kt` | 后续，未逐页验收 | 未执行 |
| 播放器章节面板 | `AudiobookPlayerScreen.kt` | 后续，未逐页验收 | 未执行 |
| 有声书倍速 | `AudiobookPlayerScreen.kt` | 后续，未逐页验收 | 未执行 |
| 睡眠定时 | `AudiobookPlayerScreen.kt` | 后续，未逐页验收 | 未执行 |
| 书签与笔记 | `AudiobookPlayerScreen.kt` | 后续，未逐页验收 | 未执行 |

### 视频

| 页面/弹层 | 源码入口 | 本轮范围 | 视觉方向/证据范围 |
|---|---|---|---|
| 视频媒体库/筛选/排序 | `VideoScreen.kt` | 后续，未逐页验收 | 未执行 |
| 视频搜索 | `VideoScreen.kt` | 后续，未逐页验收 | 未执行 |
| 继续观看 | `VideoScreen.kt` | 后续，未逐页验收 | 未执行 |
| 视频详情/分季选集 | `VideoDetailScreen.kt` | 后续，未逐页验收 | 未执行 |
| 视频播放器（横/竖屏） | `VideoPlayerScreen.kt` | 后续，未逐页验收 | 未执行 |
| 画中画 | `VideoPlayerScreen.kt` | 后续，未逐页验收 | 未执行 |
| 连播倒计时/立即播放/取消 | `VideoPlayerScreen.kt` | 后续，未逐页验收 | 未执行 |

### 视频面板

| 页面/弹层 | 源码入口 | 本轮范围 | 视觉方向/证据范围 |
|---|---|---|---|
| 播放设置 | `VideoPlayerPanels.kt` | 后续，未逐页验收 | 未执行 |
| 播放速度 | `VideoPlayerPanels.kt` | 后续，未逐页验收 | 未执行 |
| 影片信息 | `VideoPlayerPanels.kt` | 后续，未逐页验收 | 未执行 |
| 选集 | `VideoPlayerPanels.kt` | 后续，未逐页验收 | 未执行 |
| 字幕与音轨 | `VideoPlayerPanels.kt` | 后续，未逐页验收 | 未执行 |
| 章节 | `VideoPlayerPanels.kt` | 后续，未逐页验收 | 未执行 |
| 清晰度 | `VideoPlayerPanels.kt` | 后续，未逐页验收 | 未执行 |

### WebDAV

| 页面/弹层 | 源码入口 | 本轮范围 | 视觉方向/证据范围 |
|---|---|---|---|
| 网盘目录/路径 | `WebDavScreen.kt` | 后续，未逐页验收 | 未执行 |
| 收藏文件夹 | `WebDavScreen.kt` | 后续，未逐页验收 | 未执行 |
| 文件信息/播放操作 | `WebDavScreen.kt` | 后续，未逐页验收 | 未执行 |
| 网盘筛选/排序 | `WebDavScreen.kt` | 后续，未逐页验收 | 未执行 |

### 设置（16 页）

| 页面/弹层 | 源码入口 | 本轮范围 | 视觉方向/证据范围 |
|---|---|---|---|
| 设置 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 媒体服务器 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 外观与启动 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 模块显示 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 样板 `modules` | 首轮方向已确认；限已列证据 |
| 音乐播放 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 有声书播放 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 视频播放 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 存储与下载 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 隐私与数据 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 关于与帮助 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 添加服务器 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 编辑服务器 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 样板 `server/server_emby/server_webdav` | 首轮方向已确认；限已列证据 |
| 已下载音乐 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 待归属旧数据 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 连接帮助 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |
| 开源声明 | `SettingsScreen.kt / SettingsPreferences.kt / SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |

### 设置与来源弹层

| 页面/弹层 | 源码入口 | 本轮范围 | 视觉方向/证据范围 |
|---|---|---|---|
| 设置搜索及定位 | `SettingsScreen.kt` | 后续，未逐页验收 | 未执行 |
| 设置选项对话框 | `SettingsComponents.kt` | 后续，未逐页验收 | 未执行 |
| 未保存草稿退出确认 | `ServerEditorScreen.kt` | 后续，未逐页验收 | 未执行 |
| 来源选择/管理菜单 | `MediaSourceControls.kt` | 后续，未逐页验收 | 未执行 |
| 播放中切换来源确认 | `MediaSourceControls.kt` | 后续，未逐页验收 | 未执行 |
| 删除来源确认 | `MediaSourceControls.kt` | 后续，未逐页验收 | 未执行 |
| 隐藏正在播放模块确认 | `SettingsPreferences.kt` | 后续，未逐页验收 | 未执行 |
| 清理/恢复/旧数据归属确认 | `SettingsDataPages.kt` | 后续，未逐页验收 | 未执行 |

### 共享组件样板

| 页面/弹层 | 源码入口 | 本轮范围 | 视觉方向/证据范围 |
|---|---|---|---|
| 设置值、长路径、开关与禁用项 | `SettingsComponents.kt` | 样板 `settings_rows` | 首轮方向已确认；限已列证据 |

## 共享实现影响面（不计作逐页完成）

| 共享实现 | 影响范围 | 本轮证据边界 |
|---|---|---|
| `SongListRow` / `MusicCollectionHeader` | 音乐首页、集合与列表调用方 | 歌曲与专辑详情样板；其他音乐页面未逐页验收 |
| `MediaPlayerTopBar` | 音乐/有声书播放器 | 音乐样板；有声书未逐页验收 |
| `MediaPlayerSheet` / `MediaPlayerChoiceRow` | 音频弹层、复用的 WebDAV/视频选择内容 | 音乐队列/倍速与合成对比测试；视频 panel 宿主未改、未新增 PiP 验收 |
| `PolishedPlaybackDock` | 根媒体导航及各媒体迷你条 | 歌曲样板与真实设置根入口；真实三域播放切换未验 |
| `SettingsRow` / `ConfigTextField` | 设置页面、服务器表单、复用的操作层 | 组件样板、模块显示、Navidrome/Emby/WebDAV 表单；其他设置页及 ABS 实际表单未逐页验 |

## 本轮宿主与数据流审查

- `MusicPlayerScreen` 仍管理下载订阅、恢复和取消，`MusicPlayerContent` 只消费状态/回调；预览不会构造下载管理器。
- `ServerEditorScreen` 仍管理连接测试、取消/请求序号、显式保存和未保存退出保护；`ServerEditorContent` 只渲染草稿并回传事件。
- 队列生产数据继续由调用方/引擎持有；菜单与拖动只调用原回调，不建立第二份真实播放队列。
- ModuleVisibilityPage 的真实偏好更新、拒绝全关和活动媒体确认分支未改业务协议；真实主应用自动化仅覆盖开关更新与重建后的持久化。
- 调试歌曲的 `preview://` 地址和 `example.test` 服务器不会交给真实播放/网络层；本地封面仅存在 debug source set。

## 最终验证状态

- 当前代码的最终门禁与 12 个白名单批次已完成；198 张截图、正常/短屏各 17 项交互、真实设置 1 项，详见 [evidence-index.md](./evidence-index.md)。
- 历史基线缺系统字号与源码指纹，严格大字体前后对照仍未补齐；初始视口图不替代整页/反馈视检。收藏下载层只有打开/下载预览隔离回归，更多菜单仅验证所测回调。
- 本清单仍有 68 个页面/弹层条目；样板以外的未验项目状态不变。

## 下一关卡

2026-09-14 用户已确认首轮样板方向并批准单次工作提交；工作提交 `a889a1b` 已完成，本轮按样板范围归档并记录日志。其余页面的未验状态保持不变，后续分批打磨另行推进，不把全应用标记完成。方向确认不代表未独立截图的操作层或全部菜单条目已视检。
