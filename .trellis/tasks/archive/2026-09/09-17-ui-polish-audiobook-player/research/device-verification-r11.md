# 设备验证记录(第十一轮:有声书播放器收口)

环境:API 34 专用 AVD `nordic-ui-api34` / `emulator-5580`,ADB 所有命令带 `-s emulator-5580`。

## 产物

- Release APK `app/build/outputs/apk/release/nordic-0.1.19.apk`,apksigner v2 验证通过,
  aapt badging 确认 `versionName=0.1.19 / versionCode=19`。
- Release DEX 不含 `UiCatalog*`/`UiSample*` Debug-only 类。
- r11 截图批次:`app/build/reports/ui-polish/r11/`(48 张 PNG + manifest.json),
  已复制到 `research/r11/`。

## 截图批次(phase=r11)

- screens:`ab_sleep`(睡眠定时面板), `ab_player`(播放器), `ab_bookmarks`(书签面板)
- states:`normal, empty, error, disabled`
- fonts:`1, 2`(真实系统 font_scale),每张核对 `systemFontScale`
- themes:light / dark,状态栏图标像素断言(`statusBarInkVerified`)通过。

## 交互测试(UiCatalogInteractionTest 隔离运行)

通过(1):audiobookSleepTimerDeduplicatesPresetEntry —— 预选=45 时「使用预选:45 分钟」存在、
「45 分钟后停止」不重复、「30 分钟后停止」/「关闭」/「本章结束」正常。

## 本轮额外修复的样板 bug

- `AudiobookCatalogSamples` 的 ab_sleep / ab_chapters / ab_bookmarks 直入入口原本只渲染播放器、
  不显示对应 sheet(sheet 条件用内部 `screen` 而非入口请求),现已改为按 `request.screen` 判定,
  使直达 sheet 可被截图与交互覆盖。

## 未完成项 / 已知边界

- 像素级逐图人工视检**未执行**:当前会话模型无法读取图片,截图已封存待用户逐张确认。
- 未做 TalkBack 真机朗读走查;预设去重与选中语义由 instrumentation 覆盖。