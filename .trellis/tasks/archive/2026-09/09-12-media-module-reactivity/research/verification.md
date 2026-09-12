# 模块开关即时生效修复：验收记录

日期：2026-09-13。工作基线：`8425885`，任务 `.trellis/tasks/archive/2026-09/09-12-media-module-reactivity/`。

## 修复与边界

- 默认加密配置入口现在通过 `EncryptedPreferencesInstance` 复用一个进程级包装器。同步首访只创建一次；仅使用 applicationContext；初始化失败原样抛出且下一次可重试。
- 未修改 UI 开关、播放确认/关闭策略、配置字段/格式、迁移方式或服务器/媒体数据；没有 Activity 重启、手动 reload 或 UI 局部偏好副本。
- 根因与 AndroidX 1.1.0 源码证据见 `root-cause.md`。原 `configFlow` 的先监听后读取、distinctUntilChanged 和 finally 注销保持不变。

## 红绿回归

- 先提取保持旧行为的入口（每次打开独立包装器）并运行新增 7 项测试：6 项失败，4 项跨 store 实时 Flow 等待超时，2 项实例共享/复用合同失败；见 `red-tests.json`。
- 加入缓存与同步后，新增 7 项全部通过，覆盖七种有效模块组合、稳定标签与回退、设置分类/搜索、多个已有订阅者、注销隔离、持久化、恢复默认、全关保护/无变化写入、播放投影/来源更新、八线程首访及异常重试。
- Fake 的共享数据 Map 与各包装器独立 listener 列表先有单独断言，不预先把同一个假对象注入所有 store 来掩盖缺陷。

## 全量质量门禁

命令：

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --console=plain
py -3 ./.trellis/tasks/archive/2026-09/09-12-media-module-reactivity/research/verify-build-artifacts.py
```

- Gradle `BUILD SUCCESSFUL`，4 分 22 秒；编译、单测、Lint、Debug 和 Release 打包全部通过。
- 52 个 suite、704 项单测：0 失败、0 错误、0 跳过。新增回归包含在全量执行中，见 `green-tests.json`。
- Lint：0 Error/Fatal、25 Warning、18 Information；未把已有弃用或静态检查警告描述为零警告。
- 应用身份 `fun.han1997.nordic`，版本 `0.1.8 / 8`；Debug/Release v2 签名通过且证书相同。
- Release 文件名与 metadata/manifest 一致、非 debuggable；启动类正确，调试预览 Activity 仅在 Debug 包中。
- 实际 Release DEX 的三类 API 共 36 个 suspend 方法保留 Continuation<Response<T>> 嵌套泛型，Continuation/Response/Call 三类泛型参数也保留。
- 详细产物、签名与 DEX 证据：`build-results.json`、`apk-signatures.txt`、`dex-contract.json` 和各 `.dex.txt`。

## 修复包

- 路径：`app/build/outputs/apk/release/nordic-0.1.8.apk`
- SHA-256：`4851dcd734728422ce6c500178fa817ba642b312d68f213fc81ca8b392258c4f`
- 证书 SHA-256：`16a2350bb63cc53f446ec3aeadbd08ccd9d9475db867c4564ea6b6c3372a545d`

## 真机验证范围

- 只读确认 PKJ110 在线且已安装 0.1.7 / 7；手机当时正在使用其他应用，因此没有接管屏幕或覆盖安装。
- 本轮未执行真实 Compose 点击、前后台/重启、播放中隐藏、小屏或大字体验收；不能把 JVM 的状态推导测试描述为真机交互通过。
- 后续步骤见 `manual-checklist.md`。手机仍是旧版，需安装修复包后才能验证效果；不得卸载或清数据。

## 收尾状态

- 阶段 2.1、2.2、3.1、3.2 根因复盘及 3.3 规范更新已完成。
- 持久化与设置合同、CHANGELOG 和版本已同步；README 的入口/使用方式未改变，因此不重复改写。
- 用户回复“行”后完成工作提交 `9270ae7`，严格只提交已批准的 8 个文件；暂存 blob 与验收快照一致，Debug/Release APK 哈希也未变化，本轮无需重跑构建。
- 工作提交之后执行任务归档与日志记录；未 push、未安装或操作手机。
