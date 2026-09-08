# 视频画中画支持

## Goal

视频功能补全第一批 B：为视频播放器添加画中画（PiP）支持。用户上滑/返回主页时自动进入 PiP 继续播放；关闭 PiP 退出播放（保留「关窗即停」语义）；设置面板提供开关（默认开启、可持久化关闭）。

## What I already know

* 用户决策：PiP 设置开关默认打开；开启 PiP 时不执行「关闭即停止」；关闭 PiP 退出播放即停止。
* `minSdk 26`，PiP API 可调用，但仍需检查设备的 `FEATURE_PICTURE_IN_PICTURE` 与系统允许状态；MainActivity 是 `ComponentActivity`。
* 「关闭即停止」在 `closeVideoPlaybackInternal`（engine.stop + 后台 stop 上报）；PiP 退出复用该路径。
* 持久化沿用 `EncryptedConfigStore` key + configFlow + save 模式（`videoPlaybackSpeed` 先例）。
* 应用内无 Switch 先例；设置面板现有 `VideoPlayerSettingRow`（64dp 点击行）。

## Requirements (confirmed)

### B1 持久化开关
* `EncryptedConfigKeys.VIDEO_PIP_ENABLED`；`videoPipEnabled: Flow<Boolean>`（key 缺失默认 true）+ `saveVideoPipEnabled`；`ConfigRepository` 透传。

### B2 ViewModel
* `VideoPlaybackViewModel` 暴露 `pipEnabled: StateFlow<Boolean>`（默认 true）+ `setPipEnabled(Boolean)` 写回。

### B3 UI
* Settings 面板新增「画中画」行：`PictureInPicture` 图标 + M3 `Switch`（新增 `VideoPlayerSettingToggleRow`，与现有行同一 64dp 语言）。
* 参数链：MainActivity → VideoPlayerScreen(pipEnabled, onTogglePip) → VideoPlayerPanelHost → Settings；`isInPipMode` 由 Activity 可观察状态传给屏幕，小窗只保留视频和字幕，不显示面板、控制、提示或手势。
* 开关整行使用 `toggleable(Role.Switch)` 承载状态与操作，内部 M3 `Switch` 不重复注册点击事件。

### B4 Activity 集成
* Manifest：MainActivity 加 `android:supportsPictureInPicture="true"`，配置变化覆盖 orientation/screenSize/smallestScreenSize/screenLayout。
* Android 8–11 在 `onUserLeaveHint()` 中按资格进入；Android 12+ 在 Home 手势前通过 `setPictureInPictureParams` 设置 `setAutoEnterEnabled(资格)`，关闭/暂停/出错后同步撤销，不能仅在离开时设为 true。
* 资格：播放器可见、开关开启、有播放地址、无播放器或外部错误、未结束，且正在播放或 `isBuffering && playWhenReady`；不重复进入已有 PiP。
* Compose 通过 `LaunchedEffect` 同步低频资格和视频比例；Activity 通过 `mutableStateOf` 发布实际 PiP 模式，比例安全限制为 `1:2.39..2.39:1`，非法值回退 16:9。
* `onPictureInPictureModeChanged(false)` 也代表展开回应用，不得据此停止；可见 PiP 保持 STARTED，真正的 `ON_STOP` 且非配置重建时复用 `closeVideoPlayback`，立即停止并提交 Stopped 进度快照。配置重建仍走 `syncNow` 安全网。
* PiP 中播放结束使用 Media3 的 `STATE_ENDED` 状态，不以整数秒或服务器时长猜测；先 `closeVideoPlayback`，再 `moveTaskToBack(true)` 退出小窗，保留 ViewModel 以完成后台上报，不调用 `finish()`。

### B5 边界
* 错误态不自动进 PiP；仍请求播放的缓冲态允许，暂停中的缓冲不自动进入。
* 音乐/有声书不受影响。
* 开关关闭、设备不支持或系统拒绝后，退回「后台即停 + ON_STOP 即时停止进度上报」，避免并发发起 Progress/Stopped 导致顺序竞争。

## Acceptance Criteria

* [ ] 播放中按 Home/上滑进入 PiP 继续播放；PiP 中关闭小窗后停止播放并上报进度。
* [ ] 设置面板开关可切换并持久化；关闭后不再自动进入 PiP。
* [ ] PiP 中播放结束自动关闭。
* [x] 单测：pipEnabled 默认 true / 存取 round-trip。
* [x] compile + test + lint + assemble 全绿；CHANGELOG 条目；manual-checklist + APK（真机项目另列待验收）。

## Out of Scope

* 章节跳转 / PlaybackInfo 转码 / 片头跳过（第二批）。
* Cast、外部播放器、离线下载。
* PiP 内自定义菜单/操作按钮（系统默认关闭按钮即可）。

## Current Progress

* 已恢复上一会话的未提交实现，并完成数据、ViewModel、UI 与 Activity 的贯通检查。
* 检查发现原 B4 的退出回调判定会误停“展开回应用”；已按 Android 生命周期修正为 ON_STOP 关窗语义，并补齐自动进入时机、真实结束状态、比例边界和纯视频小窗。
* 已补充 PiP 资格/比例与偏好恢复测试；完整 compile/test/lint/assemble 通过，546 单测无失败/跳过，lint 0 错误（22 警告）；另一次完整单测强制重跑同样通过。
* 完整检查定位到配置共享订阅的“先读取后注册”通知丢失竞态，已修复并用确定性用例验证修复前超时、修复后通过；配置流测试增加 5 秒超时保护。
* Phase 3.1 质量检查、3.2 调试复盘、3.3 规范更新已完成；APK 校验值与证据见 `manual-checklist.md`、`verification.md`。
* 真机验收待执行；当前 `adb devices -l` 无连接设备，不将自动检查当作真机通过。

* Phase 3.4 已完成：工作提交 `ba039b2`（未推送）；保留任务待真机验收与归档，不将自动检查当作全部验收通过。
