# 移除音乐播放页快进快退按钮

## Goal

移除音乐播放页中用户认为没有意义的快进快退按钮（后退 10 秒 / 前进 30 秒），简化控制行布局。视频和有声书播放页的快进快退不受影响。

## What I already know

* `MusicPlayerScreen.kt` 控制行（`PlayerControls` composable）当前包含 6 个按钮：随机、上一首、后退 10 秒、播放/暂停、前进 30 秒、下一首、循环。
* 快进快退按钮在 `MusicPlayerScreen.kt:869-893`，使用 `Icons.Filled.FastRewind` / `Icons.Filled.FastForward`。
* `onSeekBack` / `onSeekForward` 参数从 `MusicPlayerScreen` → `PlayerPrimaryDisplay` → `PlayerControls` 传递。
* `MainActivity.kt:623-624` 接线 `onSeekBack = { musicVM.seekBackBy() }` / `onSeekForward = { musicVM.seekForwardBy() }`。
* `MusicPlaybackViewModel.seekBackBy` / `seekForwardBy` 方法存在于 ViewModel，本次不删除（保持 API 完整性，未来可能用于其他触发方式）。
* 视频和有声书播放页也有 `onSeekBack` / `onSeekForward`，本次不改。

## Requirements

* 移除 `MusicPlayerScreen.kt` 中 `PlayerControls` 的 `FastRewind` 和 `FastForward` 两个 `PlayerIconButton`。
* 移除 `MusicPlayerScreen` 和 `PlayerPrimaryDisplay` 的 `onSeekBack` / `onSeekForward` 参数。
* 移除 `MainActivity.kt` 中音乐播放器的 `onSeekBack` / `onSeekForward` 接线（仅音乐，视频/有声书不动）。
* 不删除 `MusicPlaybackViewModel.seekBackBy` / `seekForwardBy` 方法（保持 API 完整性）。
* 控制行剩余 5 个按钮：随机、上一首、播放/暂停、下一首、循环。布局保持 `Arrangement.SpaceEvenly` 或现有间距。
* 更新 `CHANGELOG.md` `[未发布]` 段。

## Acceptance Criteria

* [ ] 音乐播放页控制行不再显示快进快退按钮。
* [ ] `onSeekBack` / `onSeekForward` 参数从音乐播放页相关 composable 中移除。
* [ ] `MainActivity.kt` 中音乐播放器的 `onSeekBack` / `onSeekForward` 接线移除。
* [ ] 视频和有声书播放页的快进快退不受影响。
* [ ] `MusicPlaybackViewModel.seekBackBy` / `seekForwardBy` 方法保留。
* [ ] lint / typecheck / 构建通过。
* [ ] `CHANGELOG.md` `[未发布]` 段补充条目。

## Out of Scope

* 视频和有声书播放页的快进快退按钮。
* `MusicPlaybackViewModel.seekBackBy` / `seekForwardBy` 方法删除。
* 控制行布局重新设计（仅移除两个按钮，保持现有间距逻辑）。

## Technical Notes

* 控制行当前用 `Row` + `Arrangement.SpaceEvenly`，移除两个按钮后剩余 5 个按钮会自动重新均匀分布。
* `sideButtonSize` 变量仍被循环按钮使用，不移除。
