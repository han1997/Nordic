# UI 精修第六轮：WebDAV 网盘浏览

## Goal

承接视频浏览/详情与播放器两轮 UI 精修，收口 WebDAV 网盘目录、路径、收藏、继续观看、搜索、排序显示、文件信息和播放操作。保持 WebDAV XML、认证、Range、重定向、缓存、来源隔离和播放准备协议不变，只修内容层结构、语义、对比度、触控目标、短屏/大字体可达性，并新增 Debug-only 离线样板。

基线 main / `6d385f2`，应用 `0.1.13 / 13`；沿用专用 AVD `nordic-ui-api34` / `emulator-5580`，不操作个人设备。

## Scope

- `WebDavScreen.kt`：目录页头、路径面包屑、搜索、错误/加载、继续观看、收藏目录、文件列表、排序显示面板、文件信息 Dialog。
- `WebDavBrowserViewModel.kt`：只抽 UI 所需的纯状态/显示函数，不改变 repository、请求取消、缓存和播放准备协议。
- 新增生产无副作用 `WebDavBrowserContent`，生产宿主持有 ViewModel、偏好和回调，Debug 样板复用内容层。
- Debug-only `WebDavCatalogSamples.kt`：目录、搜索、收藏、继续观看、排序、文件信息、错误和空态。
- 版本 `0.1.13 / 13 → 0.1.14 / 14`，同步 `video-ui.md`、DESIGN、CHANGELOG。

## Requirements

- 目录内容层与 WebDAV 宿主分离；来源、请求、缓存、偏好和播放回调仍由生产宿主管理。
- 路径返回、根目录、面包屑和收藏当前目录均为至少 48dp；长路径可水平滚动，当前路径展示不丢失编码/大小写语义。
- 文件/文件夹行保持至少 72dp，自然适应 fontScale 1/1.5/2；整行 `Role.Button`，右侧菜单保持独立且不造成重复朗读。
- 文件名、类型、大小和本机进度次级文字使用实色 `onSurfaceVariant`；时间/进度数字使用 tnum。
- 继续观看卡按字号增长：220dp 基准，钳制到 300dp；整卡播放与「移除进度」动作隔离，两个目标均至少 48dp；明确「仅本机」语义。
- 错误态使用共享 `MediaStateCard` + 显式 `SecondaryActionButton("重试")`；错误且无缓存时不叠加空目录；准备播放错误不抹掉当前目录。
- 搜索使用 `MediaSearchField`，清除、收起和 BackHandler 行为保留；搜索空态与空目录空态区分。
- 排序/显示面板保持 WebDAV 专属 `ModalBottomSheet` 宿主，不迁移到视频播放器 panel；标题声明 `heading()`，排序使用共享 `MediaPlayerChoiceRow`，显示选项使用唯一 `Role.Switch`。
- 文件信息 Dialog 的长路径可换行/滚动，关闭目标至少 48dp；不新增播放或编辑操作。
- 保留真实回调：目录打开、返回上级、搜索、刷新、收藏、移除进度、文件信息、播放、从头播放、来源设置。
- 不修改 WebDAV 协议、认证、路径安全、Range、重定向、缓存上限、同目录选集或本机进度存储。

## Acceptance Criteria

- [ ] 目录/路径、收藏、继续观看、文件信息/播放、搜索、排序显示 6 个页面/面板条目逐项有结论。
- [ ] 320/360/392/720dp、字号 1/1.5/2、双主题按覆盖矩阵检查。
- [ ] 路径、收藏、文件行、播放、从头播放、移除进度、重试、关闭均满足真实触控目标。
- [ ] 错误、缓存、空目录和准备播放状态不互相覆盖。
- [ ] WebDAV 相关 JVM、Lint、Debug/Release、AndroidTest 编译通过；既有协议测试不回归。
- [ ] 版本 0.1.14 / 14，Release 排除 Debug 样板；文档和 `video-ui.md` 同步。
- [ ] 专用模拟器可用时产出 `r6-*` 证据；不可用时明确记录未验证，不伪造视觉通过。
- [ ] 单次确认提交，不 push。

## Definition of Done

- 新增/更新 WebDAV UI 纯函数测试和 Debug 交互覆盖。
- `implement.jsonl` / `check.jsonl` 只包含本轮 spec 与 research 文件。
- 完成 `trellis-check`、`trellis-update-spec`、提交和任务归档。

## Decision (ADR-lite)

**内容层抽取**：从 `WebDavScreen` 抽出 `WebDavBrowserContent`，但保留生产宿主对 ViewModel、`ConfigRepository`、偏好更新、播放准备和来源上下文的所有权。这样 Debug 样板能复用真实目录 UI，避免复制一套展示版。

**排序面板宿主**：继续使用 WebDAV 自己的 `ModalBottomSheet`，不复用视频播放器窗内 panel；只复用选择行和设置行的内容语义，避免改变窗口所有权。

**本机继续观看**：继续观看卡明确标注「仅本机」，只调用 `WebDavLocalRepository` 现有进度删除/播放路径，不新增远端同步。

## Out of Scope

- WebDAV XML、认证、Range、重定向、路径安全和缓存协议实现。
- Emby、播放器、PiP、自动连播、设置域和导航重做。
- 上传、删除、重命名、下载、云同步、备份、投屏等新能力。
- 真实 NAS、AList/OpenList、个人设备、完整 TalkBack 路径。
- 更换字体、依赖或 WebDAV 数据模型。

## Technical Notes

- 主要合同：`media-sources-webdav-settings.md`、`ui-consistency.md`、`ui-catalog-verification.md`、`quality-guidelines.md`、`build-release.md`、`video-auto-play-next.md`。
- 前轮参考：`.trellis/tasks/archive/2026-09/09-16-ui-polish-video-player/`、`.trellis/tasks/archive/2026-09/09-16-ui-polish-video-browse/`。
- 现有宿主：`WebDavScreen.kt`、`WebDavBrowserViewModel.kt`；现有协议测试不应被 UI 抽取改变。
