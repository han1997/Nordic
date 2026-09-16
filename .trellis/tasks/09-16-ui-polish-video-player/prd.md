# UI 精修第五轮：视频播放器与窗内面板

## Goal

承接第 4 轮视频浏览/详情精修，收口视频播放器主体、横竖屏控制栏和 7 个窗内面板。保持现有播放器架构、Media3、Emby 进度同步、PiP、自动连播和方向控制协议不变，只修布局、层级、语义、对比度、短屏/大字体可达性，并扩展 Debug-only 离线预览与测试覆盖。

基线 main / `912c0a3`，应用 `0.1.12 / 12`；沿用专用 AVD `nordic-ui-api34` / `emulator-5580`，不操作个人设备。

## Scope

- `VideoPlayerScreen.kt`：播放器状态表面、Surface、scrim、控制显隐、错误/缓冲/重试、片尾提示、跳过片头、手势锁与播放器交互回归。
- `VideoPlayerChrome.kt`：横竖屏工具布局、48dp 工具按钮、72dp 主播放按钮、时间线、字号溢出和安全区。
- `VideoPlayerPanels.kt`：播放设置、播放速度、影片信息、选集、字幕与音轨、章节、清晰度 7 个窗内面板。
- `VideoPlayerLayout.kt` / `VideoPlayerGestures.kt`：只修 UI 相关布局判定、可达性和回归测试，不改变播放协议。
- `VideoPlayerPreviewActivity.kt`：复用既有 Debug-only 离线预览，覆盖正常、电影、空状态、未知时长、缓冲、错误、片尾、连播、横屏和面板。
- 测试：`VideoPlayerScreenTest.kt`、`VideoPlayerEpisodesTest.kt`、相关 progress/布局测试；新增必要的 Debug interaction 条目。

## Requirements

- 所有可见播放器操作真实布局目标至少 48dp，主播放按钮 72dp；大字体下不缩小、不裁切，低频操作进入「播放设置」。
- `resolveVideoPlayerToolLayout` 依据实际宽高、fontScale、选集/下一集/状态预留空间；中央控制区不足 280dp 或缓冲/错误时，播放按钮移到底栏。
- 竖屏使用底部窗内面板，全屏横向且宽度至少 600dp 时使用侧面板；两者都避让 `WindowInsets.safeDrawing`，不改变系统栏所有权。
- 面板只能同时打开一个；面板打开时禁用底层播放手势，并保留正确 BackHandler 优先级。
- 播放设置、影片信息、字幕/音轨、章节、清晰度的正文和说明文字统一使用真实 `onSurfaceVariant`，选中行使用 `primaryContainer` / `onPrimaryContainer`，避免半透明小字低于 4.5:1。
- 面板选择行继续复用 `MediaPlayerChoiceRow`；章节、选集、字幕、音轨、清晰度不得另写裸 clickable 选择行。
- 设置开关行保持唯一 `Role.Switch`，内部 `Switch(onCheckedChange = null)` 只负责视觉；普通设置行使用 `Role.Button`。
- 字幕/音轨面板使用单一可滚动容器，不组合互相竞争的 `LazyColumn` 与 `verticalScroll`。
- 选集面板按季筛选、定位当前集并保持当前项完整可见；不可播放项禁用，当前集点击只关闭面板，不重新播放。
- 时间线未知时长显示 `--:--`，复用 `PlayerThinSlider`，保留取消拖动不 seek；时间数字使用 tnum。
- 保留真实回调和数据边界：播放、seek、倍速、比例、全屏、选集、字幕/音轨、清晰度、PiP、连播、进度快照均不绕过 MainActivity/PlaybackEngine。
- 版本 `0.1.12 / 12 → 0.1.13 / 13`；更新 CHANGELOG、DESIGN、视频播放器合同；Release 排除 Debug 预览。

## Acceptance Criteria

- [ ] 播放器主体、横屏/竖屏控制栏和 7 个面板均有逐项结论。
- [ ] 320/360/392/720dp、字号 1/1.5/2、双主题按覆盖矩阵检查；横屏至少验证 720dp 侧面板。
- [ ] 主要按钮 ≥48dp，主播放按钮 72dp，短屏/大字体/系统栏下完整可达。
- [ ] 面板选择、开关、当前集、不可播放项、错误/缓冲/未知时长均有正确语义。
- [ ] 播放器和面板自动化单测、Debug AndroidTest 编译/交互通过。
- [ ] JVM、Lint、Debug/Release/测试 APK、Release Debug 排除和版本/签名检查通过。
- [ ] 专用模拟器可用时产出 `r5-*` 截图与交互证据；不可用时明确记录未验证，不伪造通过。
- [ ] 单次确认提交，不 push。

## Definition of Done

- 测试覆盖 `VideoPlayerScreenTest`、`VideoPlayerEpisodesTest`、布局/进度相关纯函数及新增交互。
- 新增或同步 `.trellis/spec/backend/video-ui.md` 播放器章节与 DESIGN/CHANGELOG。
- 完成 `trellis-check`、`trellis-update-spec`、提交和任务归档。

## Decision (ADR-lite)

**播放器面板宿主**：继续使用窗内 `VideoPlayerPanelHost`，不迁移到音频 `MediaPlayerSheet`。视频全屏/PiP/系统栏由同一播放器窗口管理，迁移会改变窗口所有权和返回优先级。

**预览策略**：扩展既有 `VideoPlayerPreviewActivity`，不新建第二套视频播放器宿主。它只使用内存 `VideoItem`、绘制 Surface 和记录回调，不创建 repository、播放引擎或真实进度写入。

**范围切分**：PiP 和自动连播已有业务合同，本轮只验证其在播放器 UI 中的呈现、互斥和操作路径，不改连播状态机或 PiP 生命周期。

## Out of Scope

- Emby API、Media3 播放引擎、播放 URL、进度上报和缓存协议。
- PiP 生命周期重做、自动连播状态机重做、投屏、字幕下载和新媒体能力。
- WebDAV 目录 UI、设置域、浏览/详情页再次打磨。
- 将视频窗内 panel 迁移至 `MediaPlayerSheet`。
- 真实 Emby、个人设备、完整 TalkBack 路径；无设备时不伪造截图证据。
- 更换字体、依赖、导航结构或播放器视觉品牌。

## Technical Notes

- 主要合同：`.trellis/spec/backend/emby-integration.md` 视频播放章节、`video-auto-play-next.md`、`ui-consistency.md`、`ui-catalog-verification.md`、`quality-guidelines.md`、`build-release.md`。
- 前轮参考：`.trellis/tasks/archive/2026-09/09-16-ui-polish-video-browse/`、`.trellis/tasks/archive/2026-09/09-15-ui-polish-audiobook/`。
- 既有预览入口：`app/src/debug/java/com/nordic/mediahub/VideoPlayerPreviewActivity.kt`。
