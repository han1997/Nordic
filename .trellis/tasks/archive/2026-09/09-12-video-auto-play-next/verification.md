# 视频自动连播验收记录

## 最终结论

实现与自动验收完成，用户已确认并完成工作提交 `8d7f19f`，按请求归档。真机/真实服务器验收未执行，不能将以下构建与单测结果等同于设备通过。

- 最终完整命令：`.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`。
- 最后一轮 BUILD SUCCESSFUL，3 分 13 秒，实际执行 Debug/Release 编译、完整单测、Lint、R8 及两种打包。
- 692 项单测、50 个 suite；0 失败、0 错误、0 跳过。
- Lint 0 error/fatal，25 Warning、18 Information。警告位于既有加密存储/迁移、旧均衡器/共享控件、Manifest/资源和未修改的依赖/SDK 声明，不指向新增连播逻辑；没有降低规则或增加 suppress。
- `git diff --check`、任务上下文路径与 UTF-8 检查通过。

## 覆盖范围

- 默认关闭；偏好 JSON 缺省、持久化、重建、恢复默认、设置搜索；两个 UI 入口使用同一存储投影。
- 4999/5000ms、立即播放、取消同刻竞争、重复结束、一次 claim、重播、过期 token、后台/PiP/面板、错误、候选失效与自身停止中间态。
- Emby 同来源/同剧集、跨季、无效后继、末集；WebDAV 来源/目录隔离、自然排序、当前项缺失、不循环。
- WebDAV 真实 repository 准备工厂 → 后继解析 → 结束事件 → 5 秒计时 → claim；断言可播地址、字幕、contentVersion、续播与搜索不截断队列。
- 异步原项保存后再检查请求，保存前不启动、取消后不迟到启动、重复回调只处理一次；本地进度不替换其他来源同 ID，WebDAV 已完成项切回从头播放。

## APK 与发布合同

- 应用包名 `fun.han1997.nordic`，版本 `0.1.6 / 6`，启动类 `com.nordic.mediahub.MainActivity`。
- Release：`app/build/outputs/apk/release/nordic-0.1.6.apk`，4523272 字节，SHA-256 `8d31a362b1ed05217ce361b32afe50a72814813b2c4e8e650fdda6aa838bac65`。
- Debug：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `f91dac2e2fe5127d0a4b74357c86491073f6955c344df5b6ca9434dc3bf2beb6`。
- 两个 APK 的 v2 签名有效，证书 SHA-256 `16a2350bb63cc53f446ec3aeadbd08ccd9d9475db867c4564ea6b6c3372a545d`；与已有 0.1.3 分发 APK 实测一致。没有修改 keystore，也未执行覆盖安装。
- Debug 保留合成预览入口；Release 不含该入口且不启用 debuggable。文件名从本次 output-metadata.json 读取，与 manifest 一致。
- 实际 Release DEX：Navidrome 19、AudiobookShelf 9、Emby 8 个 suspend 方法均保留 Continuation<Response<T>> 嵌套签名；Continuation/Response/Call 三个泛型类型定义均保留 T。
- 可复核命令：`py -3 ./.trellis/tasks/archive/2026-09/09-12-video-auto-play-next/research/verify-build-artifacts.py`；只读取构建/源码，证据输出到本任务 research。

## 迭代记录

1. 首轮定向编译与选集/计时测试通过（1 分 28 秒）。
2. 第一次完整单测 685 项有 1 失败：旧测试假定 WebDAV 搜索只有一个结果；改为验证服务器入口保留并新增精准连播关键词断言。
3. 完整 compile/test/lint/Debug/Release 通过（5 分 25 秒）；再补强准备期间候选失效取消任务，687 项复验通过（3 分 28 秒）。
4. 跨层检查发现 WebDAV UI 发布空地址占位项；补齐真实目录播放上下文与字幕/进度、增加工厂串接回归，最终 692 项及完整发布门禁通过（3 分 13 秒）。之前的中间 APK 证据已由最终产物覆盖。

## 验证限制

- `adb devices -l` 无连接设备，未发现可用 AVD；没有安装、操作手机或访问真实媒体服务器。
- 横竖屏、大字体、手势锁、真实 Emby/WebDAV 续播/字幕/转码错误、网络迟缓及 PiP 生命周期仍按 manual-checklist.md 待测。
- debug `autoplay` 场景是固定 5 秒的静态布局/按钮预览，不能代替真实计时与服务器验收。

## 提交前复核

30 个工作文件原始哈希和两个 APK 哈希均与验收快照一致，无额外修改。Git 暂存会规范化 CRLF/LF，另以 git hash-object --path 与暂存区 blob ID 逐一比较，全部一致后提交；未因此重跑或改变应用构建。
