# UI 精修第七轮：视频详情页

## Goal

承接视频浏览、视频播放器和 WebDAV 三轮精修，收口视频详情页的 Hero、简介、分集筛选与分集列表。保持现有 Emby 数据、详情选择、主播放目标、从头播放、分集播放和播放器交接协议不变；只优化布局层级、语义、对比度、触控目标、短屏和大字体可达性，并完善 Debug-only 离线样板。

基线为 `main / 958dc64`，当前版本预期从 `0.1.14 / 14` 升至 `0.1.15 / 15`。沿用专用 AVD `nordic-ui-api34 / emulator-5580`，不操作个人设备。

## Requirements

- `VideoDetailScreen.kt` 继续作为无副作用详情内容层；`VideoScreen` 保有选择状态、媒体库、播放目标解析和所有真实回调。
- Hero 保持视频详情语境中白字叠黑色渐变的既有可读性例外；核对无 backdrop fallback、长标题、长续播目标文案与大字体下的换行、裁切和播放操作可达性。
- 主、次播放动作继续复用 `PrimaryActionButton` / `SecondaryActionButton`，最小实际目标 48dp，长标签至多两行、自然增高，且保留 enabled/disabled 语义与原回调。
- 简介与分集标题均声明 `heading()`；简介继续复用 `MusicCollectionDescription`，保留按视频 ID 隔离的展开状态。分集节头按视频域规范使用 `headlineMedium`、`onBackground` 和 SemiBold。
- 分集筛选继续复用 `MediaChoiceChip` + `selectableGroup`，不新增局部自绘选择控件；短屏、长标签和 fontScale 1/1.5/2 下保留完整的横向滚动路径与选中语义。
- 普通分集行确保真实点击目标至少 48dp，并自然适配字体增长；不可播放项同时保留 disabled 视觉和语义。
- 当前分集继续使用 `primaryContainer` 背景、`onPrimaryContainer` 前景和 `selected` 语义；普通分集的标签与元信息使用实色 `onSurfaceVariant`。封面保持去重朗读。
- “没有未看的分集”作为详情级空态，使用既有共享状态组件或等价的紧凑呈现；不得伪造刷新、错误或空媒体库状态。
- `VideoCatalogSamples.kt` 保持内存 `VideoItem`、本地 Debug 封面和记录回调的隔离；覆盖电影、剧集、长文本、无简介、无 backdrop、未看筛选为空及不可播放分集。
- 同步版本、`CHANGELOG.md`、`DESIGN.md` 和视频 UI 合同；Release 不得包含 Debug 样板 Activity、资源或类。

## Acceptance Criteria

- [ ] Hero、简介、分集筛选、普通/当前/不可播放分集行和未看筛选空态均完成逐项 UI 结论。
- [ ] 320/360/392/720dp、fontScale 1/1.5/2、浅深主题下，无标题/按钮裁切和低于 48dp 的真实操作目标。
- [ ] 主播放、从头播放和分集播放均调用原始回调；详情返回与浏览返回优先级不变。
- [ ] 分集当前项、不可播放项、封面去重朗读、筛选选中和空态语义符合合同。
- [ ] Debug 样板不访问网络、不创建账号、不写偏好、不启动真实播放。
- [ ] JVM、Lint、Debug/Release、AndroidTest 编译，以及 Release 签名/manifest/DEX 合同检查通过。
- [ ] 专用模拟器可用时产出 `r7-*` 截图与交互证据；不可用时如实记录未验证，不以编译通过替代视觉验收。

## Definition of Done

- 更新必要的详情页纯函数、Compose/UI 交互测试及 Debug 样板覆盖。
- `implement.jsonl` 和 `check.jsonl` 只包含本任务适用的规范或研究文件。
- 完成 `trellis-check`、`trellis-update-spec`、单次确认提交和任务归档。

## Technical Approach

优先在 `VideoDetailScreen` 收敛既有内容层，而非抽取第二套详情宿主。复用现有共享按钮、简介组件、选择芯片和设计 token；只在现有 Debug 样板上扩展确定性场景，避免引入新的网络、配置、缓存或播放依赖。

## Decision (ADR-lite)

**Context**：详情 UI 同时被生产 `VideoScreen` 和 Debug `VideoCatalogSamples` 使用，播放目标与当前集解析已有稳定测试。

**Decision**：保留 `VideoDetailScreen` 的接口和 `VideoScreen` 的状态/播放所有权，仅收敛内容呈现并扩展现有样板。

**Consequences**：生产与样板共享真实详情 UI，减少视觉漂移；不改变 Emby、Media3、进度同步、导航或播放器生命周期。

## Out of Scope

- Emby API、媒体库请求、缓存、认证、详情选择数据流和错误状态机。
- Media3 播放引擎、播放 URL、进度上报、PiP、自动连播、转码、字幕和音轨。
- 视频首页、视频播放器、WebDAV、音乐/有声书、设置页的再次精修。
- 新媒体能力、导航重做、字体/依赖替换、真实 Emby 或个人设备测试。

## Technical Notes

- 生产入口：`app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`。
- 内容层：`app/src/main/java/com/nordic/mediahub/ui/VideoDetailScreen.kt`。
- Debug 样板：`app/src/debug/java/com/nordic/mediahub/ui/VideoCatalogSamples.kt`。
- 既有详情目标解析测试：`app/src/test/java/com/nordic/mediahub/ui/VideoScreenTest.kt`。
- 主要合同：`video-ui.md`、`ui-consistency.md`、`ui-catalog-verification.md`、`quality-guidelines.md`、`build-release.md`、`documentation-guidelines.md`。
