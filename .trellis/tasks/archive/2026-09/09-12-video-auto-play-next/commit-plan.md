# 提交计划（用户已确认并执行）

## 1. `feat: 支持视频自动连播并完善 WebDAV 播放队列`

本次一个完整功能工作提交，共 30 个文件，版本递增至 0.1.6 / 6；完整自动门禁和 APK 验证已通过。

- `.trellis/spec/backend/emby-integration.md`
- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/media-sources-webdav-settings.md`
- `.trellis/spec/backend/video-auto-play-next.md`
- `CHANGELOG.md`
- `README.md`
- `app/build.gradle.kts`
- `app/src/debug/java/com/nordic/mediahub/VideoPlayerPreviewActivity.kt`
- `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
- `app/src/main/java/com/nordic/mediahub/data/AppPreferences.kt`
- `app/src/main/java/com/nordic/mediahub/data/ConfigRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/VideoEpisodeSequence.kt`
- `app/src/main/java/com/nordic/mediahub/data/WebDavRepository.kt`
- `app/src/main/java/com/nordic/mediahub/playback/VideoAutoPlayNextController.kt`
- `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackViewModel.kt`
- `app/src/main/java/com/nordic/mediahub/ui/SettingsPreferences.kt`
- `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerPanels.kt`
- `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`
- `app/src/main/java/com/nordic/mediahub/ui/VideoScreenLogic.kt`
- `app/src/main/java/com/nordic/mediahub/ui/WebDavBrowserViewModel.kt`
- `app/src/main/java/com/nordic/mediahub/ui/WebDavScreen.kt`
- `app/src/test/java/com/nordic/mediahub/MainActivityTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/MediaSourceTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/VideoEpisodeSequenceTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/WebDavPlaybackContextTest.kt`
- `app/src/test/java/com/nordic/mediahub/playback/VideoAutoPlayNextControllerTest.kt`
- `app/src/test/java/com/nordic/mediahub/playback/VideoEpisodeProgressTest.kt`
- `app/src/test/java/com/nordic/mediahub/ui/SettingsCenterTest.kt`
- `app/src/test/java/com/nordic/mediahub/ui/VideoPlayerEpisodesTest.kt`
- `app/src/test/java/com/nordic/mediahub/ui/VideoScreenTest.kt`

## 未识别修改（不纳入提交）

无。所有工作文件均由本会话修改。

## 不纳入本次工作提交

用户回复“行并finishwork”确认本清单，工作提交 `8d7f19f` 已完成。本任务材料随后由 finish-work 单独归档，会话另行记录；不推送远端。

已逐一核验 30 个工作文件与原验收快照、Git 规范化后的暂存内容一致；未包含其他文件，未 amend 或 push。
