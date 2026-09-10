# Release Retrofit 泛型修复验收记录

## 修复内容

- R8 full mode 擦除了 Retrofit suspend 的 Continuation/Response 泛型信息，导致 `HttpServiceMethod` 将原始 Class 强转为 ParameterizedType 时崩溃。
- 补齐 Continuation、Response、Call 的精确泛型定义保留规则，继续启用 R8 与资源收缩；没有修改连接协议、账号处理、包名或签名。
- 版本递增为 `0.1.2 / 2`；新增 `RetrofitReleaseContractTest` 四项测试，并把 `proguard-rules.pro` 注册为 Gradle Test 输入，避免规则修改后复用旧测试缓存。
- CHANGELOG 和两份构建/R8 spec 已同步；README 使用方式不变，本次无需改动。

## 红灯到绿灯

- 基线：新增 4 项测试在旧规则下 3 项失败、1 项通过（25 秒）；失败均为缺少泛型 keep 定义，证据见 `research/baseline-retrofit-tests.xml`。
- 修复后全量：`gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebug --console=plain`，BUILD SUCCESSFUL（3 分 4 秒），实际重新执行 R8。
- 补齐测试缓存输入后再次执行同一全量命令：BUILD SUCCESSFUL（1 分 27 秒），单元测试实际重跑。
- 最终 588 项单元测试、38 个 suite，0 失败、0 错误、0 跳过；新增 4 项全绿。Lint 0 error、22 项既有 Warning、18 项 Information。

## 真实 release 产物检查

- `apkanalyzer dex code` 核验 Navidrome 19、有声书 9、Emby 8 个方法，共 36 个 suspend 方法均保留 `Continuation<Response<T>>` 嵌套泛型。
- Continuation、Response、Call 混淆后的类型本身均保留 `<T:...>` 类型定义。
- `apksigner verify --verbose --print-certs` 通过，v2 签名有效；证书 SHA-256 与旧包一致：`16a2350bb63cc53f446ec3aeadbd08ccd9d9475db867c4564ea6b6c3372a545d`。
- `aapt dump badging` 确認 `fun.han1997.nordic`、versionName `0.1.2`、versionCode `2`。
- 最终复核后 APK 的 SHA-256 与已检查泛型的产物相同，不存在检查后又换包的情况。

## APK

- 常规构建产物：`app/build/outputs/apk/release/app-release.apk`
- 便于识别的新包：`app/build/distributions/Nordic-0.1.2-release.apk`
- 文件大小：4359432 bytes
- SHA-256：`073d641210e8dfcf7cd4bf74ea9a63c8dcaa652bb69b4115bbf65b4dc16bf917`

## 根因与防复发

详见 `research/diagnosis.md`；属于测试覆盖缺口、隐含假设与跨编译层反射合同问题。之前验证签名成功只说明安装签名正确，不代表经过 R8 的 Retrofit 能解析泛型。规范现已要求检查实际 DEX，不仅依赖 debug/JVM 单测；项目没有需要同步的 spec 模板目录。

## 验证边界与收尾记录

- 用户已确认提交计划，工作提交为 `49bf0d2`（修复 release Retrofit 泛型解析崩溃）；六个工作文件已提交，版本保持 `0.1.2 / 2`。
- ADB 没有连接设备，未执行用户真机连接/播放验证；需要用户覆盖安装 `0.1.2` 后复测。不自动卸载应用或清除数据。
- Phase 3.1 质量验证、3.2 根因复盘、3.3 规范同步、3.4 工作提交均完成。任务归档状态以 `task.json` 为准，会话日志只记录工作提交。
- 本轮未推送远端；带版本号的 APK 与已验证产物的 SHA-256 一致。
