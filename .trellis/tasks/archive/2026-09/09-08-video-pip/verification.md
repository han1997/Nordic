# 视频画中画质量检查

日期：2026-09-08。阶段：2.2 / 3.1 已通过；真机验收未执行。

## 自动门禁

```powershell
.\gradlew.bat '-Pkotlin.compiler.execution.strategy=in-process' :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
.\gradlew.bat '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest --rerun --console=plain
```

- 完整门禁 BUILD SUCCESSFUL（2 分 27 秒），另一次完整单测强制执行 BUILD SUCCESSFUL（6 秒），不是把 UP-TO-DATE 当成测试执行。
- XML 结果：37 个测试类、546 项测试、0 失败、0 错误、0 跳过。相比任务前 532 项新增 14 项。
- PiP 策略测试 10 项；配置存储测试共 16 项，涵盖开关默认值/双向保存/新 store 恢复/非法字符串/注册时并发保存。
- lint：0 错误、22 警告；未新增警告抑制。编译仍提示加密库弃用、既有字幕回调弃用、未使用 onFailed 参数和测试 fake 的集合 cast。
- 合并 Manifest 中 MainActivity 的 `supportsPictureInPicture=true`，`configChanges=orientation|screenSize|smallestScreenSize|screenLayout`。
- 使用命令行 in-process Kotlin 编译策略复用 Gradle daemon，未改动用户的 `gradle.properties`；未并发运行多个 Gradle 进程。

## 代码与跨层检查

- 读：EncryptedConfigStore → ConfigRepository → VideoPlaybackViewModel → VideoPlayerLayer → Activity PiP 参数与设置面板。
- 写：单一 toggleable 操作 → setPipEnabled → saveVideoPipEnabled → IO commit → 配置 Flow；注册先于初始读取。
- 真实 STATE_ENDED 与 playWhenReady 从 Media3 发布，不以取整位置猜测结束；两种错误通道均禁止自动进入。
- Activity 模式 false 只更新 UI，不停止；关闭由 ON_STOP 触发，沿用本地停止与后台停止进度快照。自然结束使用 moveTaskToBack，不 finish 单 Activity。
- 小窗移除控制/面板/手势，保留视频与字幕节点；系统栏/方向不新增第二个控制器。
- 规范已更新：backend 的 emby-integration / database-guidelines / ui-consistency / index。不存在 `src/templates/markdown/spec`，无模板副本需要同步。

## 调试证据

完整检查曾停在 `EncryptedConfigStoreTest.navidromeConfig_emitsInitialValueAndUpdatesOnSave`。指定 Gradle test worker 的线程栈显示 runBlocking 等待通知，而非编译 daemon 卡死。

新增 `videoPipEnabled_observesWriteDuringListenerRegistration` 在旧顺序上确定性以 TimeoutCancellationException 失败（1 项执行、1 项失败）；改为先注册再读取后，该用例及完整测试两轮均通过。没有删除/跳过失败用例或只靠重启 daemon 掩盖问题。原始本地日志在本任务的 `*.log` 中，受仓库忽略规则排除。

## APK

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：23,053,398 字节
- SHA-256：`6D7F41544A6591E67134A12703C92D99EDF3C302143A36CC9BFAE872269190EA`

## 验收边界与保留内容

- `adb devices -l` 没有设备，不能声称已验证系统 Home 手势、PiP 动画、展开/关窗、TalkBack 或 Emby 真实停止上报。
- 真机清单见 `manual-checklist.md`，未通过的项目保持未勾选。
- `gradle.properties` 是任务开始前存在的用户本地配置，不纳入工作提交。任务/会话归档仍由后续 finish-work 处理。

## 工作提交

`ba039b2`：支持视频画中画并修复配置订阅竞态。17 个工作文件已提交；没有推送远端。剩余工作区仅用户本地 `gradle.properties` 与任务材料。
