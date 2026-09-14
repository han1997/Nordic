# Debug UI 样板与设备验收合同

## 1. 范围 / 触发条件

修改 Debug UI 目录、内容层抽取、Compose instrumentation、系统字号/窗口截图或逐页覆盖记录时读取。样板用于确认视觉方向与共享控件，不代表全应用或真实服务器播放已验收。

## 2. 入口与命令

- Debug：`UiCatalogActivity.showSample(screen, state = "normal", dark = false, fontScale = 1f)`，实现位于 `app/src/debug/`，样例封面也只能放在该 source set。
- 无副作用内容层：`MusicPlayerContent`、`ServerEditorContent`；下载恢复、连接测试、存储、取消和草稿保护继续由生产宿主负责。
- instrumentation：`UiCatalogScreenshotTest`、`UiCatalogInteractionTest`、`MainSettingsUiTest`，共享 `UiTestDevice` 和 `UiTestAssertions`。
- 任务的 `research/run-ui-checks.py`：`--phase <唯一批次名>`、`--kind screenshots|interaction|live`、`--screens <逗号列表>`、`--states <逗号列表>`、`--fonts 1,1.5,2`、`--width <dp>`、`--height <dp>`。
- 任务的 `research/verify-build-artifacts.py`：读取 JVM/Lint 报告，检查 Debug/Release manifest、资源、签名以及实际 Release DEX 的 Retrofit 泛型合同。

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
# $task 为当前任务的实际目录；所有设备命令仍由脚本显式指定序列号。
py -3 "$task/research/run-ui-checks.py" --phase review-normal --kind screenshots --fonts 1,2
py -3 "$task/research/run-ui-checks.py" --phase review-short --kind interaction --width 320 --height 480
```

## 3. 可执行合同

### 设备、内容与宿主隔离

- 本任务固定 API 34 AVD `nordic-ui-api34` / `emulator-5580`；脚本与 instrumentation 都校验模拟器及 AVD 名称，所有 ADB 命令带 `-s`。禁止无差别执行 `connectedAndroidTest`，不安装、停止或清理个人手机应用。
- SDK 从 `%LOCALAPPDATA%/Android/Sdk` 定位，Gradle 使用现有 JDK 17。一个 checkout 只运行一个 Gradle 进程；同一 AVD 的测试、截图和显示设置变更必须串行。
- 样板仅消费确定性内存数据和本地资源，复用生产 Composable 与原回调接口，不构造真实账号/播放/下载依赖。假地址不得交给网络层。
- 真实设置冒烟和合成样板分开记录；前者可在专用空模拟器更新真实偏好并恢复原状态，不能拿合成开关证明持久化正确。
- Release 必须同时排除样板 Activity、资源和类，不能只检查 Debug 入口在源码中的位置。

### 字号、窗口与图像

- 手动目录的 LocalDensity/LocalConfiguration 仅用于内容预览。正式大字体截图必须真实设置系统 `font_scale`，并核对每张图的 `systemFontScale == fontScale`，确保 Dialog 和系统窗口也在同一条件下。
- Debug Activity 显式处理 `fontScale` 配置变化，保持样板请求；不能只等 application resources 更新，就假定异步重建后的 Activity 已显示目标页面。生产 Activity 不为测试改变配置合同。
- Compose idle 不等于 Android 窗口动画/Surface 已提交。`captureStableWindow()` 等待主线程 idle 和窗口稳定后使用 `UiAutomation.takeScreenshot()`；样板图与交互图必须走同一个捕获入口。
- 系统栏像素检查要同时核对背景明暗与相反色的图标像素；只累计白色/黑色像素会把错误主题背景当作图标。该检查不替代正文 4.5:1 合成对比测试，也不替代逐张看图。
- 原始 PNG 保存在 `app/build/reports/ui-polish/<phase>/`，不覆盖已封存批次、不提交 APK/大量截图。比较图只能从真实原图缩放/排列生成，不重绘成“理想效果”。

### 证据 schemaVersion 3

- 批次记录 `phase/kind/device/avd/apiLevel`、`widthDp/heightDp`、参数、UTC 时间、时长、`passed/testsRun/testNames`。
- `sourceSha256` 覆盖 `app/src` 与相关 Gradle/Proguard 输入；另记 `appSha256/testApkSha256/runnerSha256`。捕获期间源文件或 APK 改变必须失败；重新构建后不能把旧批次冒充当前结果。
- 每张图记录页面、状态、主题、内容/系统字号、实际像素尺寸、SHA-256、`statusBarInkVerified`；期望笛卡尔积、唯一性与尺寸必须匹配。
- `finally` 恢复调用前的 wm size/density、font_scale 和熄屏时间；IME 测试等待收起动画/视口稳定，再恢复软件键盘设置。失败证据保留，另用新批次重试。

## 4. 验证与错误矩阵

| 情况 | 必须结果 |
|---|---|
| 320/360/392/720dp、字号 1/1.5/2、双主题 | 共享布局单测覆盖规则；样板按证据索引列出实际渲染组合，不假装全矩阵逐页跑过 |
| LazyColumn + 内嵌 LazyRow | 竖向表单选择器包含 VerticalScrollAxisRange + ScrollToIndex，不依赖模糊动作匹配 |
| 清筛选后条目在屏外 | 先结束输入并滚动到目标，再断言；未合成的 lazy item 不等于数据不存在 |
| 短屏 / 大字体播放器 | 保留可滚动路径，按钮真实点击；同时核对完整可见与 >=48dp，不接受部分相交的 assertIsDisplayed 作为完整可用证明 |
| Android 14 字号 | 检查实测增高、文字布局 didOverflowHeight 和容器边界；父/子边界在同一个 UI frame 读取，不混用窗口动画前后的矩形，也不硬猜固定 dp 高度 |
| 禁用 TextField / 保存中 | 按字段 label 定位，断言 disabled 与无 SetText；保存/测试不执行回调 |
| IME 弹出 | 同时等待 IME bottom 和实际表单视口高度稳定后再滚动；仅 isVisible=true 可能早于动画/焦点定位完成。保存按钮在真实键盘上方完整可达，点击调用原保存事件 |
| 同应用主题与系统主题不同 / 打开关闭 sheet | Dialog 自己的系统栏和回到宿主后的系统栏均正确 |
| 老截图缺少系统字号/源码指纹 | 标为历史证据；尤其不能作为大字体 Dialog 的严格同条件前后比较 |
| Release / 真机 / 真实媒体服务器 | 分别核验；模拟器样板成功不能推导真实媒体播放、上报、PiP 或用户视觉确认通过 |

## 5. 正反案例

- 好：语义回调通过后检查实际 PNG，发现上一页面或半截按钮仍拒收，补齐稳定窗口/完整可见断言后另封存批次。
- 基础：代码、自动测试、代理视检、用户视觉确认分列记录，未覆盖页面继续待验。
- 差：减少断言、缩小文字、把对比度阈值降低，或将共享组件通过等同于全应用验收。

## 6. 必需测试与断言点

- `SettingsRowLayoutTest`、`QueueItemGeometryTest`、`MusicQueueSheetTest`、`NordicDesignContractTest`：实际宽度、可变行高、兼容行为与真实合成对比。
- `UiCatalogInteractionTest`：开关即时更新/拒绝全关、过滤/清空、空播放器零进度与禁用控制、播放/倍速回调、菜单/真实拖动、表单编辑/禁用/IME、字体下的布局/触控/完整可见、主题系统栏切换，以及不写偏好/不启动播放服务。
- `statusIconPixelGuardRejectsBlankAndOppositeThemeFrames`：空白帧和相反主题不能通过图标像素守卫。
- `MainSettingsUiTest`：真实设置入口、加密偏好通知及 Activity 重建后状态；保持与合成样板的结果分离。
- 截图测试完整矩阵与每张图的窗口/系统字号检查；原图还需视检，不能仅凭 instrumentation 的 OK。

## 7. 错误与正确写法

```kotlin
// 错误：屏外 lazy item 未合成，却判定筛选失败；半个按钮也满足“已显示”。
compose.onNodeWithText("目标歌曲").assertExists()
play.assertIsDisplayed().performClick()

// 正确：先通过对应滚动容器找到条目；存在滚动祖先时再对按钮 performScrollTo。
compose.onNode(hasVerticalLazyScrollAction()).performScrollToNode(hasText("目标歌曲"))
play.assertIsDisplayed().assertIsFullyVisible().assertMinimumTouchTarget()
    .performTouchInput { click() }
```
