# Journal - hhy (Part 4)

> Continuation from `journal-3.md` (archived at ~2000 lines)
> Started: 2026-09-10

---



## Session 174: 包名、release 签名与版本规则收尾

**Date**: 2026-09-10
**Task**: 包名、release 签名与版本规则收尾
**Branch**: `main`

### Summary

完成 fun.han1997.nordic 包名、release 复用 debug 签名及 0.1.1 起版本规则任务；补齐发布文档、修正 ADB 组件名并归档。584 项单元测试实际重跑通过，Lint 0 error/22 warning，debug/release 打包与签名校验通过；真机安装与覆盖升级未验证。

### Main Changes

- 应用实现提交 `f62cafa`：applicationId 改为 `fun.han1997.nordic`，namespace 保持 `com.nordic.mediahub`，release 复用本机 debug keystore，版本从 `0.1.1 / 1` 开始。
- 文档提交 `6430d62`：新增构建身份/版本/签名规范，清理重复段落，补齐 README/CHANGELOG，并将 debug 预览 ADB 命令改为新 applicationId 与完整 Activity 类名。
- 记录证书连续性、旧包数据隔离、纯文档不递增版本和真机验收边界；本轮没有修改应用代码或构建配置。
- 任务归档至 `.trellis/tasks/archive/2026-09/09-10-package-id-signing-version/`，完整验收证据见其中 `info.md`。
- 未推送远端，未更换 keystore，未卸载应用或清除设备数据。


### Git Commits

| Hash | Message |
|------|---------|
| `f62cafa` | (see git log) |
| `6430d62` | (see git log) |

### Testing

- [OK] `compileDebugKotlin`、`lintDebug`、`assembleDebug`、`assembleRelease` 通过；最终 Gradle 验证耗时 1 分 9 秒。
- [OK] `testDebugUnitTest --rerun` 实际执行：584 项测试、37 个 suite，0 失败、0 错误、0 跳过。
- [OK] Lint：0 error、22 项既有 Warning、18 项 Information；没有新增警告抑制。
- [OK] 两个 APK 的 v2 签名有效且证书一致，包名/版本为 `fun.han1997.nordic / 0.1.1 / 1`；预览 Activity 只进入 debug，release 未开启 debuggable。
- [OK] 文档 UTF-8、相对链接、ADB 组件、spec 合同、`git diff --check` 及任务 JSONL 校验通过。
- [未验证] 真机安装、覆盖升级及 OPPO/ColorOS 体验；自动检查不替代真机验收。

### Status

[OK] **Completed**

### Next Steps

- 本任务已归档，无待完成开发事项；后续如进行真机安装或覆盖升级测试，应另行记录结果。


## Session 175: 修复 release Retrofit 泛型崩溃并交付 0.1.2

**Date**: 2026-09-10
**Task**: 修复 release Retrofit 泛型崩溃并交付 0.1.2
**Branch**: `main`

### Summary

定位 R8 full mode 擦除 Continuation/Response 泛型导致的 release 连接异常，补齐精确 keep 规则与测试缓存输入，版本升至 0.1.2/2。588 项单测通过，实际 release 36 个 suspend 方法的泛型恢复，签名未变；用户已确认提交，任务已归档，真机连接仍待复测。

### Main Changes

- 工作提交 `49bf0d2`：保留 Continuation、Response、Call 的泛型定义，继续启用 R8 混淆和资源收缩，不修改服务协议或账号处理。
- 新增四项 `RetrofitReleaseContractTest`，覆盖三类泛型规则及三个服务器 API 的 eager parsing；将 ProGuard 文件注册为 Gradle Test 输入，防止规则变化时复用旧测试缓存。
- applicationId 保持 `fun.han1997.nordic`，沿用原 debug 证书侧载签名，版本递增为 `0.1.2 / 2`。
- 更新 CHANGELOG、构建/签名合同和质量规范，明确 debug 单测与 APK 签名检查不能替代 R8 后的泛型验收。
- 任务归档：`.trellis/tasks/archive/2026-09/09-10-release-retrofit-generics/`；根因、红灯基线及实际 APK 证据分别记录于 `info.md` 和 `research/`。
- 交付 APK：`app/build/distributions/Nordic-0.1.2-release.apk`，SHA-256 `073d641210e8dfcf7cd4bf74ea9a63c8dcaa652bb69b4115bbf65b4dc16bf917`。


### Git Commits

| Hash | Message |
|------|---------|
| `49bf0d2` | (see git log) |

### Testing

- [OK] 红灯基线：旧规则下新增测试 4 项中 3 项按预期失败；修复后 4 项全部通过。
- [OK] 全量编译、单元测试、Lint、debug/release 打包通过；R8 重建耗时 3 分 4 秒，补测试输入后再次全量复核耗时 1 分 27 秒。
- [OK] 588 项单元测试、38 个 suite，0 失败、0 错误、0 跳过；Lint 0 error、22 项既有 Warning、18 项 Information。
- [OK] 真实 release DEX：Navidrome 19、有声书 9、Emby 8 个 suspend 方法均保留嵌套泛型，Continuation/Response/Call 类型定义保留泛型参数。
- [OK] APK v2 签名有效且证书未变，包名/版本为 `fun.han1997.nordic / 0.1.2 / 2`；带版本号副本与已验收 APK 哈希一致。
- [未验证] 当前无 ADB 设备，未执行真机连接、播放或覆盖安装测试。

### Status

[OK] **Completed**

### Next Steps

- 开发与自动验收已完成并归档；等待用户覆盖安装 0.1.2 后反馈真机连接结果。


## Session 176: 音乐歌词完整显示与2秒跟随优化

**Date**: 2026-09-10
**Task**: 音乐歌词完整显示与2秒跟随优化
**Branch**: `main`

### Summary

落实已确认的歌词方案：完整文本与同时间句组、播放中停止手动滚动 2 秒后跟随、暂停保留浏览、会话内记住歌词/封面选择。加载原子状态、取消及迟到隔离、错误重试均接入；609 项测试通过，0.1.3/3 两种构建及签名/R8 泛型核验通过。用户已确认提交并完成任务归档，真机交互仍待验收。

### Main Changes

- 工作提交 `2faadbc`，包含 21 个已确认文件；提交前逐一核验工作文件与验收时的 SHA-256 一致。
- 领域层统一规范化、稳定排序、定时去重、同时间句组及毫秒/offset 处理；普通歌词和长句完整显示，取消旧的窗口裁剪逻辑。
- MusicLyricsController 以仓库/歌曲/查询元数据为键，取消旧任务并用请求序号防迟到覆盖；MusicLyricsUiState 区分 Loading/Content/Empty/Error，支持受控重试。
- 跟随状态机分离自动与手动滚动，覆盖 1999/2000ms、拖动与惯性、暂停/继续、立即返回、销毁；同步进度保留叶子订阅，按实测视口/行高定位。
- 视图选择留在 ViewModel 会话内；长句、按钮大字体空间及歌词区域防误关闭已接入；README、CHANGELOG 与相关规范同步。
- 任务归档：`.trellis/tasks/archive/2026-09/09-10-music-lyrics-display/`，详细证据见 `info.md` 与 `research/verification.json`。
- 交付：`app/build/distributions/Nordic-0.1.3-release.apk`；包名、签名、R8 配置不变，未推送远端、未覆盖安装或操作手机。


### Git Commits

| Hash | Message |
|------|---------|
| `2faadbc` | (see git log) |

### Testing

- [OK] 首轮定向与既有解析测试通过（1 分 35 秒）；恢复提取时误删的委托导入后完整 compile/test/lint 通过（1 分 53 秒）。
- [OK] 最终 compileDebugKotlin、testDebugUnitTest、lintDebug、assembleDebug、assembleRelease 全部通过（3 分 25 秒），实际执行 R8。
- [OK] 609 项测试、41 个 suite，0 失败、0 错误、0 跳过；Lint 0 error、22 项既有 Warning、18 项 Information。
- [OK] 两个 APK 的包名/版本为 fun.han1997.nordic / 0.1.3 / 3，v2 签名有效且证书不变；release 未包含 debug 入口、未开启 debuggable。
- [OK] release 的 36 个 Retrofit suspend 方法及 Continuation/Response/Call 泛型定义保留正常。
- [OK] APK SHA-256：`49288341706c56c4ad5106c2b30a185763afb3cec49d878193806e62c14d824d`；UTF-8、文档链接、任务上下文及 diff 检查通过。
- [未验证] 真机滚动/手势、主题与大字体排版；虽检测到已连接设备，本轮未操作手机。

### Status

[OK] **Completed**

### Next Steps

- 开发与自动验收已完成并归档；后续覆盖安装 0.1.3，按任务记录检查真机交互与排版。


## Session 177: 真机复测歌词交互（未发现问题）

**Date**: 2026-09-10
**Task**: 真机复测歌词交互（未发现问题）
**Branch**: `main`

### Summary

OPPO 真机复测 0.1.3 歌词交互：显示/切歌/手动滚动 2 秒恢复/惯性不抢占均正常，未发现需修复问题，未改代码，版本保持 0.1.3/3。剩余限制：视觉手感与大字体/浅色布局未复测。

### Main Changes

(Add details)

### Git Commits

(No commits - planning session)

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 178: WebDAV、多服务器与设置中心实现及收尾

**Date**: 2026-09-11
**Task**: WebDAV、多服务器与设置中心实现及收尾
**Branch**: `main`

### Summary

完成 WebDAV 文件夹点播、字幕与本机续播，三类媒体多服务器和数据隔离，以及八分类设置中心与搜索。646 项单测通过，Lint 无错误，debug/release 0.1.4 构建、签名、manifest 和 Retrofit 泛型签名核验通过。工作提交已获用户确认，本次按用户 finish-work 请求归档。因无 ADB 设备或可用 AVD，真实 NAS/AList/OpenList、真机 UI、解码播放和画中画验收仍未执行，需后续实测；未 push。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `1d62e1a` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 179: 视频页旧缓存闪退修复 + 音乐/有声书缓冲进度条

**Date**: 2026-09-11
**Task**: 视频页旧缓存闪退修复 + 音乐/有声书缓冲进度条
**Branch**: `main`

### Summary

1)视频页点击闪退：logcat 定位 VideoItem.copy sourceType NPE——WebDAV 版给 VideoItem 加非空字段但缓存 schema 未升版，旧 JSON 经 Gson 反序列化 sourceType 为 null；修复 VIDEO_CACHE_SCHEMA_VERSION 3→4 + parseOrNull 拒载含 null sourceType 行的缓存（降级为缓存未命中），补 2 个回归测试，CacheKeyTest 同步 v4；真机 PKJ110 验证 Emby/WebDAV 两来源均正常。2)音乐/有声书播放器补缓冲进度条：MusicPlaybackState/AudiobookPlaybackState 加 bufferedPositionSeconds，引擎发布 controller.bufferedPosition（TIME_UNSET 守卫），有声书轨内缓冲经 resolveAudiobookAbsolutePositionSeconds 映射全书绝对秒并 coerceAtLeast(position)；MediaPlayerTimeline 加可选 bufferedPosition 参数透传 PlayerThinSlider；补轨内缓冲映射单测。compile+649 tests+lint 全绿；真机交互验证因设备断开未完成，待复测。3)提交上一 session 遗留的 Release APK 自动命名工作（build.gradle.kts ApkVariantOutput + README/spec/CHANGELOG 同步）。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `cd8fb50` | (see git log) |
| `9bf898b` | (see git log) |
| `f68afcf` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
