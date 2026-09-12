# 视频自动连播合同

## 1. 范围 / 触发条件

修改 Emby/WebDAV 下一项解析、自动连播、完成事件、播放器生命周期、异步切集或连播偏好时读取。与 Emby 进度/PiP、多来源、共享 UI 合同共同生效。

## 2. 关键签名

- `AppPreferences.videoAutoPlayNext: Boolean = false`；`ConfigRepository.videoAutoPlayNext: Flow<Boolean>` / `saveVideoAutoPlayNext(Boolean)`。
- `data/VideoEpisodeSequence.kt`：`VideoPlaybackIdentity(sourceType, sourceId, itemId)`、`VideoItem.playbackIdentity()`、`resolveVideoPlayerEpisodes`、`resolveNextVideoEpisode`、`resolveNextWebDavVideo`。
- `WebDavRepository.prepareEpisodeContext(siblings, history)` 返回已准备的可播条目；`WebDavBrowserViewModel.play` 的回调为 `(video, fromStart, episodeContext) -> Unit`。
- `VideoAutoPlayNextController(scope, nowMillis)`：`updatePlayback`、`setEnabled`、`setForeground`、`setPanelOpen`、`dismissCurrentPlayback`、`resetPlaybackCycle`、`playNow`、`claim`、`canStart`、`complete`。
- `VideoAutoPlayNextState`：Idle / Countdown(request, secondsRemaining) / Ready(request) / Switching(request) / Dismissed；`VideoAutoPlayNextRequest(token, previous, next)`。
- `VIDEO_AUTO_PLAY_NEXT_DELAY_MS = 5_000L`；实际时钟注入 `SystemClock.elapsedRealtime`，测试注入虚拟时钟。
- `runMediaHandoffCloseSteps(..., canProceed: () -> Boolean = { true })` 在每个关闭步骤及最终启动之前验证；自动关闭以 request 保留自身交接，普通关闭取消请求。

## 3. 可执行合同

### 偏好与候选

- 默认关闭，设置中心与播放器共用设备级偏好；写入经过现有加密 `updatePreferences`，成功前不留独立乐观开关。缺省/恢复默认均为 false，不新增旧 DataStore 迁移键，不升级媒体缓存 schema。
- 搜索 ID 为 `video_auto_play_next`，支持自动连播、下一集、Emby/WebDAV；关键词可能匹配多个页面，测试不能假定服务名只有一个结果。
- 所有选集共用 data 层解析，不在 Compose 或 ViewModel 复制排序。先按来源类型、来源 ID 和 libraryId 隔离，再去重；当前实时项优先于旧目录副本。
- Emby Episode 按季、集、标题、ID 排序，保持 seriesId 优先、缺失时非空 seriesName 忽略大小写匹配；不跳过紧邻但无流的集、不自动补库。
- WebDAV Video 仅同目录视频，名称使用 `compareNaturalNames`，相同名称以 ID 稳定排序。未载入当前项时先插入当前项作为顺序锚点，不回退最后一项；目录排序设置不改变连播自然顺序。
- 电影、最后一项、无可播放后继不连播；不跨目录、不循环。下一项沿用当前续播策略，不强制从头。
- WebDAV 必须在开播前从实际父目录完整 entries 与该来源 progress 构建上下文，再先发布队列、后调用播放。复用单项播放的 URL/认证/外挂字幕/contentVersion 映射，批量准备在 Default dispatcher；不能使用 `visibleEntries` 搜索结果或 `streamUrl = null` 占位条目作为播放队列。
- 从继续观看进入其他目录时使用实际视频父目录；不额外取其他目录或全库。文件版本变化、已完成记录沿用 `resumeWebDavVideo` 的重置规则。

### 计时、取消与生命周期

- 仅观察到同一播放项 `hasEnded: false → true` 才启动倒计时，不比较估算时长；初次收到 ended 快照、开关重开、重组或控制层显隐均不得补触发。
- 仅播放器可见、Activity RESUMED、非 PiP、无错误/缓冲且无打开面板时计时；结束时不满足条件则跳过本次，回前台或关面板不重新计时。
- 显示倒计时、下一项标题及取消/立即播放。结束提示置于播放器中心，复用现有黑底白字浮层，避免与顶部操作重叠；内容可滚动、按钮最小 48dp，手势锁不禁用按钮。
- 片尾手动提示保留，与自动提示互斥；关闭任一种下一项提示取消本次播放周期，不修改全局开关。重新播放才重置。
- 倒计时期间定位/开始拖动、重播、选集、打开面板、关闭开关、切源、退出、后台/PiP 都取消；切换全屏不取消或重新计时。
- token 每次计时递增；Ready 只能 claim 一次。取消/重播后的旧 token 无法启动；失败不跳过条目、不自动重试。

### 异步交接与进度

- 自动请求仍经过 `onPlayVideoRequest / runMediaHandoff`，不直接调用引擎绕过原项快照/关闭。普通媒体选择优先于未完成自动请求。
- 校验至少覆盖到期 claim、每一步关闭及 onReady、异步认证/清晰度握手完成后即将开播；取消也取消自动准备 job。
- Switching 可保留自身 stop 所产生的原项 IDLE/空引擎中间态，但不能忽略错误、后继变化、来源变化或明确取消。不能把自身停止误认成重播而清掉有效 token。
- 自动交接不瞬时隐藏播放器或重置全屏。等待准备时显示可关闭的加载播放器；取消后若已无引擎/准备任务且无错误，关闭空壳；真实准备失败保留错误与手动重试。
- Emby 关闭先捕获原 repository、位置和 PlaySessionId，再清旧会话、停引擎和触发 onClosed；onClosed 可同步启动新会话，不得在其后读取/清理旧会话 ID。
- WebDAV 使用原快照保存本机进度；异步保存完成后再次验证 token。进度同步失败保持既有 best-effort 语义，不伪装成解码失败。
- WebDAV 关闭时同步更新内存队列的实际时长和进度，完成项标为已看且续播点归零，避免手动切回即落在片尾；Emby 不在本地改服务器已看标记。
- PiP 自然结束仍关闭小窗，不连播；音乐/有声书后台行为不变。

## 4. 验证与错误矩阵

| 场景 | 预期 |
|---|---|
| 4999ms / 5000ms | 前者不切换，后者仅产生一次 Ready |
| 立即播放 / 到期与取消竞争 | 最多一次请求，取消后的 token 无效 |
| 重复结束、旧 UI 回调、全屏切换 | 不重复、不重置截止时间 |
| 默认关闭 / 结束后才开启 / 恢复 ended 快照 | 不追补连播 |
| Home、PiP、面板、切源、旧保存回调到达 | 取消本次，返回后不恢复，不启动旧来源 |
| 自身 stop 的原项 IDLE / 空状态 | 仅 Switching 保留，明确取消优先 |
| 同远程 ID 不同来源 / 当前项不在目录快照 | 不串源；按自然/季集顺序找真正后继 |
| 转码准备或播放失败 | 现有错误与手动重试，不自动跳集 |
| 新旧偏好 JSON、重建 store、恢复默认 | 缺省 false，持久化正确，连接和媒体数据不删 |

## 5. 正常、基线与错误案例

- Good：前台 E1 结束后等待 5 秒，经原项进度快照/关闭后续播 E2；退出后到达的保存回调被拒绝。
- Base：不启用开关，原手动下一集与 PiP 播完关闭不变。
- Bad：用 `position >= duration - 30` 自动切集；用 `remember(video.id)` 计时；取消后仅隐藏提示但未使 token 失效；把旧项目的停止回调当成用户重播。

## 6. 必须执行的检查

- `VideoAutoPlayNextControllerTest`：计时边界、取消/立即播放、一次 claim、恢复 ended、开关/面板/前台、目标变化、空状态/IDLE、准备错误、重播与过期 token。
- `VideoEpisodeSequenceTest` / 原有选集测试：跨季、自然排序、跨来源同 ID、当前项缺失、不可播放项、末项和稳定排序。
- `WebDavPlaybackContextTest`：真实 repository 准备出的目录队列 → 后继解析 → 结束事件 → 5 秒倒计时 → 一次 claim；同时验证非空来源 URL、外挂字幕、续播/版本失效及搜索不截断队列。不能只用手工制造的非空 URL 来证明浏览页连播可用。
- `MainActivityTest`：异步关闭后重新校验、先保存后启动、重复回调防重、无效请求不关闭当前播放。
- `VideoEpisodeProgressTest`：进度快照不替换其他来源同 ID；`SettingsCenterTest` / `MediaSourceTest`：搜索、JSON 缺省与读写/重建/重置。
- 完整编译、单测、Lint、Debug/Release 及 APK 身份/签名核验。真机横竖屏、大字体、错误重试、真实 Emby/WebDAV 续播和 PiP 单列验收。
- debug `VideoPlayerPreviewActivity --es scenario autoplay` 是固定 5 秒的布局/按钮 fixture，不模拟计时，不连接服务器，不能替代真实连播验收。

## 7. 错误与正确写法

```kotlin
// 错误：关闭回调可能已同步开启下一会话，这里取到/清掉的是新 PlaySessionId。
onClosed()
val oldSessionId = activePlaySessionId
activePlaySessionId = null

// 正确：在可重入回调之前快照并清理旧会话。
val oldSessionId = activePlaySessionId
activePlaySessionId = null
engine.stop()
onClosed()
// 后台报告只使用 oldRepository / oldVideo / oldPosition / oldSessionId 快照。
```
