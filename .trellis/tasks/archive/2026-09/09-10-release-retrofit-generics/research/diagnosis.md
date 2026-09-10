# Release Retrofit 泛型丢失诊断

## 证据链

1. 用户报告 debug 连接成功，release 连接时报 `Class cannot be cast to ParameterizedType`。
2. 本地 Retrofit 2.9.0 sources.jar 的 `retrofit2/HttpServiceMethod.java:45-51` 调用 `method.getGenericParameterTypes()`，随后将最后一个 suspend 参数强转为 `ParameterizedType`，并从 `Response<T>` 中继续展开 DTO。
3. 修复前 APK 用 Android SDK `apkanalyzer dex code --class com.nordic.mediahub.api.NavidromeApi` 读取；19 个方法中有 19 个方法的 Signature 仅包含 `Lx2/d;`，没有 Continuation 的类型实参。原始反汇编见 `old-release-navidrome.smali`。
4. 修复前 mapping：`kotlin.coroutines.Continuation -> x2.d`、`retrofit2.Response -> q3.M`、`retrofit2.Call -> q3.c`。因此不是改包名或账号/网络错误，而是 R8 产物中的反射合同断裂。
5. 新增 JVM 配置回归测试在旧规则下为 4 项执行、3 项失败：Continuation/Response/Call 的泛型定义保留缺失；三类 API eager parsing 在非混淆字节码下成功。原始结果见 `baseline-retrofit-tests.xml`。

## 依据与方案

- 本地 Retrofit 2.9 的 consumer rules 只有 Signature/annotation、接口代理相关规则，缺少后续补充的 full-mode 泛型保留。
- 上游规则来源：https://raw.githubusercontent.com/square/retrofit/trunk/retrofit/src/main/resources/META-INF/proguard/retrofit2.pro ，2026-09-10 获取的快照见 `upstream-retrofit2.pro`。
- 采用 `keep,allowoptimization,allowshrinking,allowobfuscation` 保留 Continuation/Response 的泛型定义；对内部 Call 包装也显式保留泛型定义，不大范围保留 Retrofit/Kotlin 全库。
- 不升级依赖、不关闭 R8、不改网络实现；版本递增至 `0.1.2 / 2`，签名和 applicationId 不变。

## 为什么原先质量门没有发现

- 根因分类：D（测试覆盖缺口）+ E（隐含假设：keepattributes Signature 足够）+ B（Kotlin suspend ABI / R8 / Retrofit 反射合同）。
- `testDebugUnitTest` 和 `validateEagerly(true)` 检查的是未经 R8 的 JVM 字节码，不能代替 release 运行验证。
- `assembleRelease`、签名校验、manifest 包名验证都不执行 Retrofit 的方法泛型解析，因此之前成功不能证明 release 连接可用。
- 防线：保留规则回归测试必须先红后绿，另需核验真正 release DEX 中三个 API 的 Continuation/Response 嵌套签名和类型定义。真机连接结果单独记录。
