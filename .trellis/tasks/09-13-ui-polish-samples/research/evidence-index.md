# 首轮 UI 样板：最终证据与视觉确认入口

## 当前结论

- **代码/自动质量门禁通过；2026-09-14 用户确认首轮样板方向及 55 个文件的单次提交范围。** 未展开其余页面的逐页迁移，不能称作全应用 UI 打磨完成。
- 本轮承接未提交样板，补齐了短屏测试、真实键盘窗口同步、同帧几何检查、截图稳定性和空播放器样例；详细复盘见 [debug-retrospective.md](./debug-retrospective.md)。
- 最终白名单共 **12 批次、198 张初始视口截图**，另有 **4 张真实交互后的播放器控制区截图**；不是 198 个独立页面。正常/短屏各 17 项 UI 测试、真实设置 1 项，均对应同一源码、Debug APK 和测试 APK。
- 清单含 68 个页面/弹层条目与 169 个顶层 Composable 定位；未验收条目仍保留待办，不以共享组件影响范围冒充逐页完成。

## 先看这些样板

| 材料 | 用途 |
|---|---|
| [浅色样板总览](../../../../app/build/reports/ui-polish/review/final/overview-light-1_0.png) | 歌曲、专辑、播放器、队列、模块与表单 |
| [深色样板总览](../../../../app/build/reports/ui-polish/review/final/overview-dark-1_0.png) | 同样六个入口、相同内容 |
| [2× 字号总览](../../../../app/build/reports/ui-polish/review/final/overview-light-2_0.png) | 实际系统大字体，不是缩放截图 |
| [正常/短屏完整控制区](../../../../app/build/reports/ui-polish/review/final/player-controls-large.png) | 真实滚动、点击后播放按钮完整可见 |
| [浅色前后参考](../../../../app/build/reports/ui-polish/review/final/comparison-light.png) / [深色前后参考](../../../../app/build/reports/ui-polish/review/final/comparison-dark.png) | 使用历史原图，限制见下节 |

拼图由 `make-review.py` 从原始 PNG 缩放排列，不重绘、不抹除缺陷；每个拼图的输入路径与 SHA-256 保存在 `review/final/*-review.json`。大图和 APK 均留在被忽略的 build 目录，不进入工作提交。

## 历史基线的限制（未补造）

原 `baseline/` 在样式修改前保存了 36 张图，包含九个入口、双主题及内容字号 1/2。历史 manifest 只有 APK/图片哈希，**没有系统字号、测试 APK 和源码指纹**。因此：

- 同尺寸、主题与 1× 内容字号的原图可作为视觉参考；不能宣称整套旧图均符合后来补强的窗口/系统字号合同。
- 大字体 Dialog 不能当作严格同条件前后对照。PRD 的完整严格前后证据项仍未全勾选，不能用当前样板或重命名图片补造旧基线。
- 中间 `after/`、`samples-*`、`verified-*`、`checked-*`、`accepted-*` 以及失败批次保留供排查，不计入最终通过数量。最终只认本页白名单。

## 构建与运行检查

| 检查 | 实际结果 |
|---|---|
| compileDebugKotlin / testDebugUnitTest / lintDebug / assembleDebug / assembleRelease / assembleDebugAndroidTest | `final-stable-verification.log` BUILD SUCCESSFUL；未改动的任务命中缓存，最近实际全量 JVM 执行见 `delivery-verification.log` |
| JVM | 54 suites / **712 tests** / 0 failures / 0 errors / 0 skipped（基线 704，新增 8） |
| Lint | **0 Error/Fatal，27 Warning，18 Information**；不是零告警。新增 instrumentation 依赖的更新提示不通过混搭 Compose 版本处理 |
| Debug/Release | **0.1.9 / 9**，applicationId `fun.han1997.nordic`，实际启动类 `com.nordic.mediahub.MainActivity` |
| 签名 | 两个 APK 的 v2 签名通过，证书与 0.1.8 一致；未覆盖安装到个人手机 |
| Release 隔离 | manifest、resources、mapping 中不含 UiCatalogActivity/样板类/三张样例封面 |
| Retrofit 产物 | 实际 Release DEX 的 **36** 个 suspend 方法及 Continuation/Response/Call 泛型定义保留 |
| 正常屏交互 | `final-interaction`，360×800dp，**17/17** |
| 短屏交互 | `final-interaction-short`，320×480dp，**17/17**；包含真实键盘、完整播放/保存目标、菜单/拖动回调 |
| 真实设置 | `delivery-live`，**1/1**：打开设置、视频开关即时通知、恢复开关、Activity 重建后仍可见；不是三域真实播放验证 |
| 设备 | 仅 `emulator-5580` / AVD `nordic-ui-api34` / API 34；捕获后 size/density 无 override，font_scale 恢复 1；软件键盘设置恢复调用前值 |

原始构建/签名/DEX 报告见 `app/build/reports/ui-polish/artifacts/`。复核命令：

```powershell
py -3 .trellis/tasks/09-13-ui-polish-samples/research/summarize-evidence.py
```

该命令重新核对所有白名单 manifest、源码/产物指纹、每张图片 SHA-256、实际像素尺寸、系统字号和通过状态；缺少批次或源码改变会失败。

## 最终批次

所有截图批次均含浅/深主题；屏幕尺寸是整个模拟器窗口，不等于扣除系统栏后的内容高度。

| 批次 | 设备尺寸 | 通过测试数 | 截图数 | 实际范围 |
|---|---|---:|---:|---|
| `final-interaction` | 360×800dp | 17 | 0 | 17 项交互，含系统字号 1/2 |
| `final-interaction-short` | 320×480dp | 17 | 0 | 17 项交互，含系统字号 1/2 |
| `delivery-live` | 360×800dp | 1 | 0 | 真实设置开关、恢复与 Activity 重建 |
| `delivery-samples` | 360×800dp | 1 | 44 | songs,album,player,lyrics,queue,speed,modules,server,server_emby,server_webdav,settings_rows / normal / 字号 1,2 |
| `delivery-music-states` | 360×800dp | 1 | 64 | songs,album,player,lyrics / empty,loading,error,no_art / 字号 1,2 |
| `delivery-long` | 360×800dp | 1 | 20 | songs,album,player,queue,server / long / 字号 1,2 |
| `delivery-disabled` | 360×800dp | 1 | 8 | server,settings_rows / disabled / 字号 1,2 |
| `delivery-server-states` | 360×800dp | 1 | 8 | server / loading,error / 字号 1,2 |
| `delivery-queue-empty` | 360×800dp | 1 | 4 | queue / empty / 字号 1,2 |
| `delivery-short` | 320×480dp | 1 | 18 | songs,album,player,lyrics,queue,speed,modules,server,settings_rows / normal / 字号 2 |
| `delivery-wide` | 720×960dp | 1 | 22 | songs,album,player,lyrics,queue,speed,modules,server,server_emby,server_webdav,settings_rows / normal / 字号 2 |
| `delivery-mid` | 392×800dp | 1 | 10 | songs,album,player,queue,settings_rows / normal / 字号 1.5 |

- `delivery-samples` 的 44 张正常样板经拼图视检。重新封存后，其正文与此前逐张审阅版本完全一致；像素比对仅排除顶部 108px 系统时钟区和底部 96px 导航区，且只用作辅助追踪，**不是放宽截图通过条件**。
- 本轮逐组查看了状态、长文本、禁用、空队列、短/宽屏及 1.5× 的最终拼图，并复核了关键控制区原图。代理视检不代替用户审美确认。
- 初始视口图不是整页长截图：长表单、2× 设置、短屏播放器须滚动；队列保留原半展开行为。真实滚动/点击另由交互测试覆盖。2× 表单错误详情/禁用缓存项在初始视口以下，不将其初始图当作完整反馈视检。
- 宽屏会出现 Android 14 系统任务栏，这是系统表面，不是应用新导航；应用底部仍遵守安全区。
- `settings_rows` 正常样例已含长值与路径，不额外复制 `long` 状态凑数量；只有确实表达状态的页面进入各批次。

## 可追溯指纹

完整结构化结果与每批 manifest SHA-256 见 [evidence-summary.json](./evidence-summary.json)。

- 源码：`8b4b2b02e8fe355637e368d4320964b379f5ec1f29f9e216fd3164dec642b850`
- Debug APK：`6c3fca6de2e0cf9dd0ed52589400dac66a981a851df66d8ffd7971fde089aa94`
- 测试 APK：`d0cf0372e2ce27fd5f7cba066c2c7d4b0496e210b350552609af1b0e6a588cd7`
- 运行脚本：`b5aa74946e6dcaec24ac39f2c053324f977cb59ef0bfe402076e2a72eda29c05`
- Release APK：`33e3bf62a07e50cb0e187fe364987006a41057bafef162d4d2c78e160c5c6474`

## 未验证与下一步

- **用户已确认首轮视觉方向；严格大字体前后基线仍有缺口，方向确认不等于逐页/逐状态穷尽验收。** 其余音乐、有声书、视频/WebDAV、设置页面继续按 [coverage.md](./coverage.md) 待验。
- 未连接真实 Navidrome/ABS/Emby/WebDAV；未验证真实媒体下载/播放/上报、PiP、横竖屏切换、个人手机或升级覆盖；未测试 TalkBack 朗读和外接键盘全路径。
- 收藏/下载操作层仅有打开及下载预览无副作用回归，不存在独立最终截图；队列更多菜单有回调测试，不宣称菜单所有条目均逐项截图。
- 源码/指纹不变时，下次继续应先读本索引，无需又从构建和截图起点重跑。用户已批准 [commit-plan.md](./commit-plan.md) 的完整范围；完成工作提交后仅归档本轮样板并记录日志。其余页面待后续任务，不自动 push、不自动扩大到全量。
