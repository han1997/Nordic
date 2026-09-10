# 构建身份、版本与签名

> 适用于 `app/build.gradle.kts`、Android manifest、侧载产物及安装/ADB 文档。用户决策日期：2026-09-10。

## 1. 范围与触发条件（Scope / Trigger）

- 修改应用身份、版本、签名、R8 或发布方式时遵守本合同；每个代码工作提交前检查版本递增。
- `applicationId` 是设备安装身份，`namespace` 是 Kotlin/资源所属包；两者不是同一概念。
- 签名验证和反射运行验证是不同边界；APK 可安装不代表 R8 后的 Retrofit 连接可用。不要迁移旧包数据、重命名源码包或用关闭混淆替代精确修复。

## 2. 配置与命令（Signatures）

起始配置位于 `app/build.gradle.kts`（版本后续按第 3 节递增，不得重置）：

```kotlin
android {
    namespace = "com.nordic.mediahub"
    defaultConfig {
        applicationId = "fun.han1997.nordic"
        versionCode = 1
        versionName = "0.1.1"
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            // 保留现有 proguardFiles 与 keep 规则。
        }
    }
}
```

Retrofit 2.9 的 consumer rules 尚未包含以下 full-mode 泛型定义保护，必须与既有 `Signature`/annotation、接口与 DTO keep 规则一起保留在 `app/proguard-rules.pro`：

```proguard
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response
-keep,allowoptimization,allowshrinking,allowobfuscation interface retrofit2.Call
```

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebug
$tools = Join-Path $env:LOCALAPPDATA "Android/Sdk/build-tools/34.0.0"
$apk = ".\app\build\outputs\apk\release\app-release.apk"
& "$tools/apksigner.bat" verify --verbose --print-certs $apk
& "$tools/aapt.exe" dump badging $apk
$analyzer = Join-Path $env:LOCALAPPDATA "Android/Sdk/cmdline-tools/latest/bin/apkanalyzer.bat"
foreach ($api in @("NavidromeApi", "AudiobookShelfApi", "EmbyApi")) {
    & $analyzer dex code --class "com.nordic.mediahub.api.$api" $apk
}
```

示例使用 Windows 默认 SDK 路径及已安装的 Build Tools 34.0.0；其他环境按实际 SDK 路径调整。每条命令都必须检查退出码，不能只运行最后一条后声称全部通过。

## 3. 身份、环境与版本合同（Contracts）

- `JAVA_HOME` 使用 JDK 17；Android SDK 由本机 `local.properties` 的 `sdk.dir` 配置。不要提交本机配置或 keystore。
- `applicationId = "fun.han1997.nordic"`，`namespace = "com.nordic.mediahub"`。改包名后与旧应用并存，不会自动迁移数据；卸载旧应用会删除其本地数据。
- `versionName` 从 `0.1.1` 开始，每个修改代码的工作提交 patch +1；`versionCode` 从 `1` 开始，同步 +1。minor/major 仅在用户明确要求时调整。纯文档、任务归档、会话日志及重跑验证不递增。
- Release 复用 `~/.android/debug.keystore`，直接输出 `app/build/outputs/apk/release/app-release.apk`；使用 debug 证书不等于开启 release 的 `debuggable`。
- 覆盖安装必须保持 applicationId 和签名证书一致，versionCode 不回退。妥善备份 keystore；换机器或重新生成 debug keystore 后不能假定仍可覆盖安装。商店发布需另行规划专用签名及升级路径。
- Manifest 中 Activity/Service 的类名仍指向 `com.nordic.mediahub`；Media3 的 `com.nordic.mediahub.session-activity` key 与对应 Activity 值保持一致。ADB 显式组件使用 `<applicationId>/<完整类名>`，不能把新包名当成类所在的包。
- Retrofit suspend 方法经编译后最后一个参数为 `Continuation<? super Response<DTO>>`。R8 后必须保留其嵌套泛型签名，以及 Continuation、Response、Call 的泛型类型定义；仅保留 API/DTO 和 `-keepattributes Signature` 不够。允许类型重命名、优化和收缩，不需要保留整个 Kotlin/Retrofit 库的所有成员。

## 4. 验证与错误矩阵（Validation & Error Matrix）

| 条件 | 预期结果 / 处理 |
|------|-----------------|
| 已签名 APK，包名及版本符合 Gradle 配置 | `apksigner verify` 退出码 0；`aapt dump badging` 显示预期 applicationId、versionCode、versionName |
| `v1 = false`、`v2 = true` 且校验退出码为 0 | 当前 minSdk 26 可使用 v2 签名；不能仅凭缺少 v1 签名判定 APK 未签名 |
| 未签名旧产物 | 不可分发；本次故障中 `apksigner` 报 `Missing META-INF/MANIFEST.MF`，ColorOS/Android 16 提示“安装包异常” |
| 同包名但证书变更 / versionCode 回退 | 不能按正常覆盖升级处理；先核查证书与版本，不自动卸载用户应用 |
| Debug 连接正常，release 报 `Class cannot be cast to ParameterizedType` | 检查实际 DEX 的 Continuation/Response 泛型签名及其类型定义；不要直接归因于账号、网络或签名证书 |
| API 的 Signature 中 Continuation 只有原始类型，没有 `<...>` | Retrofit 2.9 的 `HttpServiceMethod` 强转最后一个参数时会失败；补齐 full-mode 泛型定义 keep 规则 |
| 新 applicationId 配合 `.VideoPlayerPreviewActivity` 简写 | 类名被错误推导为 `fun.han1997.nordic.VideoPlayerPreviewActivity`；改用完整类名 |
| 构建与签名验证通过，但没有真机安装记录 | 仅标记自动验证通过；不能声称 OPPO 安装或播放体验已验证 |

## 5. 正常、基线与错误案例（Good / Base / Bad Cases）

- Good：代码工作提交同步递增 patch 与 versionCode（例如从 `0.1.1 / 1` 到 `0.1.2 / 2`），沿用同一证书并核验实际 APK；下一次递增以 Gradle 中当前值为准。
- Base：仅补文档或重跑检查，保持应用版本不变；旧包与新包的数据边界明确告知用户。
- Good：R8 产物保留三类 API 的 `Continuation<Response<T>>` 嵌套签名，即使对应类型已混淆重命名也能被正确反射。
- Bad：为“统一包名”重命名 Kotlin 源码包、分发 unsigned APK、只改 versionName、不经用户要求递增 minor/major，或换证书后假设可覆盖升级。

## 6. 必须执行的检查（Tests Required）

- 编译、单元测试和 Lint 通过；保留真实测试数量、失败数和既有 Lint 警告，不把有警告说成零警告。
- `assembleRelease` 成功；对实际 APK 执行 `apksigner verify --verbose --print-certs`，检查退出码和证书身份，并在覆盖升级时与上次分发的证书摘要比较。
- `aapt dump badging` 的包名、versionCode、versionName 与 Gradle 配置一致，启动类为 `com.nordic.mediahub.MainActivity`。
- 修改包名或 ADB 文档时同时构建 debug，检查打包后的 manifest 包含完整的 `com.nordic.mediahub.VideoPlayerPreviewActivity`；release 不包含此调试 Activity。
- `RetrofitReleaseContractTest` 检查三类泛型定义的 app-owned keep 规则和三类服务器 API 的 eager parsing；`proguard-rules.pro` 必须注册为 Gradle `Test` 任务输入，避免仅改规则时测试仍命中旧缓存。但该 JVM 测试不经过 R8，不能替代下面的产物检查。
- 用上述 `apkanalyzer dex code --class` 命令检查三个 API：按 `app/build/outputs/mapping/release/mapping.txt` 解析混淆后的类型名，每个 suspend 方法的 `dalvik.annotation.Signature` 必须仍包含 Continuation 和 Response 的嵌套类型实参；三个泛型类型本身也应保留 `<T:...>` 类型定义。方法描述符本身只含原始类型是正常的，应检查 Signature 注解。
- 真机安装与覆盖升级单独记录；未执行时明确标为未验证，不自动卸载旧包或清除数据。

## 7. 错误与正确写法（Wrong vs Correct）

- 错误：只确认 `assembleRelease` / `apksigner verify` 成功、debug 单测通过，就认定 release 能连接服务；或只加 `-keepattributes Signature`。
- 正确：同时保护泛型类型定义并检查实际 release DEX 的 Signature。2026-09-10 的旧包证据是 `Continuation` 仅剩原始类型；修复后才恢复 `Continuation<Response<T>>`，见第 6 节回归要求。

```powershell
# 错误：相对类名会按新的 applicationId 展开，但类仍在旧 namespace 下。
adb shell am start -n fun.han1997.nordic/.VideoPlayerPreviewActivity

# 正确：安装身份与完整类名分别填写（仅 debug 包提供该入口）。
adb shell am start -n fun.han1997.nordic/com.nordic.mediahub.VideoPlayerPreviewActivity
```
