# 调试复盘：窗口真实可用性不能由单个按钮或 Compose idle 推断

## 1. 根因分类

- **E 隐含假设**：把应用浅主题等同于浅色系统栏；忽略原生 Dialog 全窗遮罩。首个基线失败是测试预期错误，真实暗背景上的白图标正确。
- **B/D 边界与测试缺口**：Material3 1.3 Slider 在内部把语义水平扩展各 10dp，默认 thumb 高 44dp；外层 heightIn(48dp) 没有使真实语义边界达到合同。
- **D/E 表单检查不完整**：原生浮动弹窗在短屏/IME 下被平移；仅给正文滚动或加 inset 后，确认按钮能露出但输入区会归零。“保存可见”不等于“表单可用”。
- **E 宿主与滚动假设**：Debug 子页多放根导航，额外消耗视口；嵌套 LazyRow 的 performScrollTo 只处理最近滚动祖先，不能据此认定纵向标题已可见。
- 一次失败诊断触发 SnapshotStateObserver 跨线程语义树读取异常，未发现生产代码新增跨线程布局。保留为测试场景切换时序风险，不宣称已证实 SDK 根因。

## 2. 为什么前一次修正不充分

1. 初次截图守卫没有区分普通页面、播放器 sheet 与原生 Dialog 遮罩；保留阈值，改为按真实窗口类型检查，原失败批次不计通过。
2. EQ 只加滚动仍不足：实测 `visible.width=984px`、`size.width=1044px`，左右语义各被裁 30px；thumb 实测高 132px=44dp。改为实际 12dp 预留和 48dp thumb，完整可见与最小目标断言都保留。
3. 原生弹窗仅给正文滚动/显式 inset，曾让保存断言通过，但原图显示输入框消失；拒收这批“按钮通过”的结果。
4. 最终采用全窗 inset 约束的 Dialog，标题/正文同一滚动区、底部动作固定；紧凑高度保留原字号、完整输入和字段语义，不占额外浮动标签行。真实 IME 屏幕边界同时约束输入框与保存按钮。
5. Debug 导航对齐真实 MusicScreenV2；该宿主修正单独标注，不冒充生产导航改动。横向卡片先横向定位，再沿竖向祖先按同帧实际几何滚动，最终仍真点击完整可见的标题。
6. 场景替换后复用稳定窗口同步，不吞测试异常或删断言；保留失败日志，完整重跑正常/短屏与旧回归。

## 3. 预防机制

| 优先级 | 机制 | 落地 |
|---|---|---|
| P0 | 同时验输入和动作，不把可见按钮当作表单可用 | MusicCatalogInteractionTest 的真实 IME + 原图 |
| P0 | 用真实语义尺寸/未裁边界，不用外层 Modifier 猜测 | UiTestAssertions + EQ 最后一频段回归 |
| P0 | 原始窗口图和像素守卫共同检查 | UiTestDevice / UiCatalogScreenshotTest；原生遮罩独立预期 |
| P0 | 源码/APK/每张图指纹；失败批次永不覆盖 | run-ui-checks.py + summarize-evidence.py |
| P1 | 复用真实页面/原回调，先审查 Debug 宿主 | MusicCatalogSamples 与范围说明 |
| P1 | 连续场景切换等待 Android 窗口稳定 | waitForStableWindow；失败诊断带可见/真实边界 |
| P1 | 点外部/返回/取消与提交中关闭保护 | 同一受保护关闭回调及设备回归 |

## 4. 扩展检查

- 原有播放器、歌词、队列、倍速、Dock、设置行和服务器表单均保留独立回归；没有为了音乐表单改写公共播放器窗口或媒体协议。
- 其他媒体域的输入弹窗、外接键盘/TalkBack/横屏仍需另行实测，不把本轮自动化通过扩成全应用确认。
- 新增正文对比测试使用真实弹窗 surfaceContainerHigh；聚焦 primary 约 3.88:1 不合格，改用 onPrimaryContainer，不能降低 4.5:1 阈值。

## 5. 知识落盘

- music-ui.md：滚动反馈、互斥空态、歌单 inset/输入/动作与 Slider 实测合同。
- ui-catalog-verification.md：原生遮罩、嵌套滚动、IME 全表单、稳定窗口和宿主真实性。
- ui-consistency.md：选择控件可指定 RadioButton，默认 Tab 不变。

全部失败/中间批次保留在 app/build/reports/ui-polish/r2-*；最终只认 verification-matrix.json 的白名单和当前指纹。
