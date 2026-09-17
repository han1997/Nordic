# UI 精修第九轮:音乐播放器无障碍与弹层

## Goal

承接第八轮设置域,收口音乐播放器主体与弹层的无障碍与响应式细节。核心是补齐封面/歌词主显示区的 TalkBack 可达性(当前切换歌词与双击 &plusmn;10s 只通过手势,对无障碍用户不可达),并核对播放器主体、倍速/队列/均衡器弹层在短屏与大字体下的滚动与触控。保持 Media3 播放协议、歌词解析、下载/收藏数据流与弹层宿主协议不变;只做语义与可达性收敛,不新增可见视觉按钮。

基线为 `main / ea70566`,当前版本预期从 `0.1.16 / 16` 升至 `0.1.17 / 17`。沿用专用 AVD `nordic-ui-api34 / emulator-5580`,不操作个人设备。

## Requirements

- `MusicPlayerContent` 继续作为无副作用播放器内容层;生产 `MusicPlayerScreen` 保有下载、收藏与播放状态所有权;调试样板 `PlayerSample` 复用生产内容层并记录回调。
- 封面/歌词主显示区(`PlayerPrimaryDisplay`)补充自定义无障碍动作,手势路径(单击切换、双击 seek)保持不变:
  - 按当前 `showLyrics` 动态暴露「显示歌词」/「显示封面」动作,触发 `onToggleDisplay()`(即现有 `onToggleLyrics`)。
  - 暴露「后退 10 秒」/「前进 10 秒」动作,触发现有 `onSeekRelative(&plusmn;10)`(复用 `MUSIC_DOUBLE_TAP_SEEK_SECONDS`),让 TalkBack 用户获得与双击 seek 等价的语义路径。
  - 未就绪(`song == null` 或 `enabled == false`)时不暴露这些动作,保持禁用语义。
- `MediaPlayerTopBar` 标题声明 `heading()` 语义,与全应用页头规范一致;仅作用于标题文本,不影响布局与回调。
- 不加可见的新按钮;歌词切换与 seek 的无障碍入口只通过 `CustomAccessibilityAction` 暴露(用户已确认「仅语义动作」)。
- 倍速、队列(EQ 另行)、下载操作弹层在 320/392/720dp、fontScale 1/1.5/2、浅深主题下核对滚动与触控路径,发现问题才修正。
- `PlayerSample` 样板:`onToggleLyrics` 已有 `onEvent("lyrics")`;必要时补记录「切换显示」回调,确保自定义动作可被交互测试断言。
- 同步版本 `0.1.17 / 17`、`CHANGELOG.md`(未发布段)、`DESIGN.md`(播放器、队列与底部面板节)与相关 spec(`music-lyrics.md`、`ui-consistency.md` 若无则补新条);Release 不得包含 Debug 样板。

## Acceptance Criteria

- [ ] 封面区在歌词隐藏时暴露「显示歌词」自定义动作,歌词显示时暴露「显示封面」;触发后回调与单击一致。
- [ ] 封面区暴露「后退 10 秒」/「前进 10 秒」自定义动作,触发后回调与双击 seek 一致;空播放器不暴露。
- [ ] `MediaPlayerTopBar` 标题可被 TalkBack 按标题导航。
- [ ] 320/360/392/720dp、fontScale 1/1.5/2、浅深主题下,播放器与弹层无可达性裁切或低于 48dp 的操作目标回归。
- [ ] JVM、Lint、Debug/Release、AndroidTest 编译,以及 Release 签名/manifest/DEX 合同检查通过。
- [ ] 专用模拟器可用时产出 `r9-*` 截图与交互证据;不可用时如实记录未验证。
- [ ] 手动手势路径与回调全部保持;无新增视觉按钮。

## Definition of Done

- 更新必要的播放器纯函数、Compose/UI 交互测试与调试样板覆盖。
- `implement.jsonl` / `check.jsonl` 只包含本任务适用的规范或研究文件。
- 完成 `trellis-check`、`trellis-update-spec`、单次确认提交和任务归档。

## Technical Approach

在 `PlayerPrimaryDisplay` 的手势 `Modifier.pointerInput` 之上叠加 `Modifier.semantics { customActions = ... }`,将两个语义动作映射到既有回调(`showLyrics` 状态机与 `onSeekRelative`)。`MediaPlayerTopBar` 标题补 `Modifier.semantics { heading() }`。调试样板复用生产内容层,交互测试用 `performCustomAccessibilityActionWithLabel` 断言回调;截图批次沿用 UiCatalogScreenshotTest 参数流。

## Decision (ADR-lite)

**Context**: 封面/歌词切换与双击 seek 仅通过 `detectTapGestures` 手势暴露,Compose 手势不自动产出无障碍动作,TalkBack 用户无法切换歌词视图或跳转秒数。

**Decision**: 在该区域补 `CustomAccessibilityAction`,语义动作与手势共用同一回调;不新增可见按钮。顶栏标题补 `heading()`。

**Consequences**: 视觉布局零改动,无障碍与视觉双路径可达;新增测试需依赖 Compose 的 `performCustomAccessibilityAction`。不改变播放状态、歌词解析或弹层协议。

## Out of Scope

- Media3 播放、进度同步、歌词解析、收藏/下载持久化、均衡器 DSP。
- 视频播放器、有声书播放器、音乐浏览/列表的再次精修。
- 新增视觉入口或图标、导航重做、字体/依赖替换、真实设备测试。

## Technical Notes

- 生产入口:`app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`(`MusicPlayerScreen` / `MusicPlayerContent` / `PlayerPrimaryDisplay`)。
- 共享页头:`MediaPlayerComponents.kt`(`MediaPlayerTopBar` / `MediaPlayerIconAction` / `MediaTransportRow`)。
- 弹层:`MusicQueueSheet.kt`、`MusicEqualizerSheet.kt`、`MusicActionsSheet.kt`、`MediaPlayerSheets.kt`(`MediaPlayerSheet` / `MediaPlaybackSpeedSheet`)。
- 调试样板:`app/src/debug/java/com/nordic/mediahub/ui/UiCatalogSamples.kt`(`PlayerSample`);宿主 `UiCatalogActivity`。
- 版本:`app/build.gradle.kts` `versionCode = 16 / "0.1.16"` → `17 / "0.1.17"`。
- 文档:根 `CHANGELOG.md`(未发布段)、`DESIGN.md`(播放器、队列与底部面板节)、`.trellis/spec/backend/music-lyrics.md`/`ui-consistency.md`。