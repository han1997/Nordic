# 设备验证记录(第十轮:底部 Dock 全局收口)

环境:API 34 专用 AVD `nordic-ui-api34` / `emulator-5580`,ADB 所有命令带 `-s emulator-5580`。

## 产物

- Release APK `app/build/outputs/apk/release/nordic-0.1.18.apk`,apksigner v2 验证通过,
  aapt badging 确认 `versionName=0.1.18 / versionCode=18`。
- Release DEX 不含 `UiCatalog*`/`UiSample*` Debug-only 类。
- r10 截图批次:`app/build/reports/ui-polish/r10/`(40 张 PNG + manifest.json),
  已复制到 `research/r10/`。

## 截图批次(phase=r10)

- screens:`songs`(音乐库 + Dock),`modules`(模块显示)
- states:`normal, loading, error, empty, disabled`
- fonts:`1, 2`(真实系统 font_scale),每张核对 `systemFontScale`
- themes:light / dark,状态栏图标像素断言(`statusBarInkVerified`)通过。
- Dock 侧通过 songs 屏幕的 loading/error 状态呈现缓冲/错误色。

## 交互测试(UiCatalogInteractionTest 隔离运行)

通过(3):dockShowsErrorAndBufferingStatusWithButtonRole、
nowPlayingBarGrowsForLargeTextInsteadOfClippingMetadata、sampleControlsExposeRolesSelectionAndMinimumTouchTargets。

JVM 单测:`PlaybackDockStatusTest` 验证 resolveDockStatusSubtitleColor 三分支(onSurfaceVariant / primary / error)。

## 未完成项 / 已知边界

- 像素级逐图人工视检**未执行**:当前会话模型无法读取图片,截图已封存待用户逐张确认。
- `BottomDockHandle` 的 Role.Button 属生产 MainActivity 收起态,样板含 Dock 展开态未展示 Handle;可由真机收起后走查。
- 未做 TalkBack 真机朗读走查;语义由 instrumentation 覆盖(按钮 Role、状态文案),真实朗读体验待用户验证。