# WebDAV 第六轮：源码审查与实施边界

## 已确认实现

- `WebDavScreen` 目前同时持有 ViewModel、ConfigRepository、偏好写入、目录列表、路径面包屑、继续观看、收藏、文件菜单、排序面板和信息 Dialog。
- `WebDavBrowserViewModel` 已负责来源配置、目录缓存/刷新、请求版本取消、收藏/进度流和播放准备；UI 抽取不得移动这些边界。
- `MediaSearchField`、`MediaStateCard`、`SecondaryActionButton`、`MediaPlayerChoiceRow`、`SettingsRow`、`MediaPageHeader` 可直接复用。

## 优先问题

1. 页面内容未抽取，Debug 无法复用生产目录 UI。
2. 面包屑、当前目录收藏和页头动作在大字体/窄屏下需要实测，需确保动作不被挤出。
3. 继续观看卡固定 220dp，移除进度按钮嵌套在播放卡中；需要按字号增长并明确两个动作的语义隔离。
4. 错误态使用裸 `TextButton("重试")`，应改为共享显式次级动作，并保持缓存目录可见。
5. 文件行整行播放/打开信息/打开目录，右侧菜单又有动作；需去除图标与标题重复朗读，保持单一整行角色与独立菜单角色。
6. 文件信息 Dialog 的长路径和排序面板大字体可达性需要覆盖。
7. 排序选择行已使用 `MediaPlayerChoiceRow`，但排序 panel 标题、开关、底部安全区和保存失败反馈需统一。

## 状态矩阵

- home：normal / long / loading / refreshing(cache) / error(no cache) / cached_error / empty / search_empty / no_favorites / no_resume。
- file：directory / video / non_video / has_progress / preparing / disabled。
- dialog/panel：file_info_long / sort_normal / sort_large / preference_error。

## 不变边界

- WebDAV repository、认证上下文、路径编码、Range、缓存限制、同目录 episode context 和本机 progress 均不改。
- 不连接真实 NAS/AList/OpenList；样板只使用内存 `WebDavEntry`、`WebDavFolder`、`WebDavProgress`。
- 保留前五轮证据，使用 `r6-*` 新批次目录。
