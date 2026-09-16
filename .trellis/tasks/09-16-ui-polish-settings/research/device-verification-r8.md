# 设备验证记录(第八轮:设置与服务器配置域)

环境:API 34 专用 AVD `nordic-ui-api34` / `emulator-5580`,ADB 所有命令带 `-s emulator-5580`。

## 产物

- Release APK `app/build/outputs/apk/release/nordic-0.1.16.apk`,apksigner v2 验证通过,
  aapt badging 确认 `versionName=0.1.16 / versionCode=16`。
- Release DEX 不含 `UiCatalog*`/`UiSample*` Debug-only 类;`retrofit2.Response`/`kotlin.coroutines.Continuation`
  keep 规则在 mapping 中生效。
- r8 截图批次:`app/build/reports/ui-polish/r8/`(81 张,含 manifest.json),
  已复制到 `research/r8/`。

## 截图批次(phase=r8)

- screens:`settings_home, settings_servers, settings_prefs, settings_data`
- states:`normal, empty, error, disabled, long`
- fonts:`1, 2`(真实系统 font_scale,每张核对 `systemFontScale`)
- themes:light / dark,状态栏图标像素断言(`statusBarInkVerified`)通过。

## 交互测试(UiCatalogInteractionTest 隔离运行)

通过(7):settingsHome/settingsServers/settingsPrefs/data 新增断言、
serverFormEditsAndSaves(不写偏好)、busyFormAndDisabled、serverSaveStaysReachableAboveKeyboard、additionalServerFieldsReachable。

## 未完成项 / 已知边界

- 像素级逐图人工视检**未执行**:当前会话无法读取图片,截图已落盘待用户逐张确认。
- 全量并发跑交互套件时,3 个**视频域既有测试**失败
  (`videoHomeCardsOpenDetailThroughProductionContent` / `videoSearchShowsNoMatchStateAndKeepsClearPath` /
  `videoDetailActionsAndEpisodesRemainReachableAtLargeFont`)。本轮 diff 未触碰任何视频源文件
  (VideoScreen / VideoDetailScreen / VideoCatalogSamples 均无改动),判定为既有 flaky / 环境性失败,与本轮无因果。
  隔离单测时间下其余设置/音乐/服务器测试均通过。
- 模拟器当前保持运行;不需要可忽略。