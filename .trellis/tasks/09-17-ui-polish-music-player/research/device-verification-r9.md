# 设备验证记录(第九轮:音乐播放器无障碍与弹层)

环境:API 34 专用 AVD `nordic-ui-api34` / `emulator-5580`,ADB 所有命令带 `-s emulator-5580`。

## 产物

- Debug / androidTest APK 安装于专用模拟器;r9 截图批次已落盘。
- r9 截图:`app/build/reports/ui-polish/r9/`(64 张 PNG + manifest.json),已复制到 `research/r9/`。

## 截图批次(phase=r9)

- screens:`player, lyrics, queue, speed`
- states:`normal, long, empty, error`
- fonts:`1, 2`(真实系统 font_scale),每张核对 `systemFontScale`
- themes:light / dark,状态栏图标像素断言(`statusBarInkVerified`)通过。

## 交互测试(UiCatalogInteractionTest 隔离运行)

通过(5):playerArtworkExposesLyricsToggleAsAccessibilityAction、
playerArtworkExposesSeekAsAccessibilityActions、emptyPlayerExposesNoCustomActions、
playerTransportAndSpeedSelectionProduceCallbacks、emptyPlayerStartsAtZeroWithDisabledTransport。

实现要点验证:
- 封面区(歌词隐藏)暴露「显示歌词」,歌词显示时暴露「显示封面」;触发回调与单击一致(`recordedEvents` 含 `lyrics`)。
- 封面区暴露「后退/前进 10 秒」;触发产生 `seek:*` 事件。
- 空播放器不暴露任何自定义动作(perform 抛 Key not present)。
- 顶栏标题 heading 语义由代码审查确认(未单独跑 TalkBack 朗读断言)。

## 未完成项 / 已知边界

- 像素级逐图人工视检**未执行**:当前会话模型无法读取图片,截图已封存待用户逐张确认。
- 未做 TalkBack 真机朗读走查;语义与回调已由 instrumentation 覆盖,真实 TalkBack 体验待用户验证。
- 无新增可见按钮或布局变化;Media3、歌词解析、队列拖拽、下载/收藏数据流均未改动。