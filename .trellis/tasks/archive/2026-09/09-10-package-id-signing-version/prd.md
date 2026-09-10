# 包名改为 fun.han1997.nordic、release 签名与版本管理规则

## 背景

1. Release APK 安装失败（OPPO X8 Ultra / Android 16 提示"安装包异常"）：根因是 APK 未签名（`app-release-unsigned.apk`，`apksigner verify` 报 `Missing META-INF/MANIFEST.MF`），项目从未配置 release signingConfig。
2. 用户要求软件包名改为 `fun.han1997.nordic`。
3. 用户确立版本管理规则：从 0.1.1 开始，每次修改 patch 位 +1；第二位/第一位只在用户通知时增加。

## 用户决策（已确认）

- release 签名复用 debug keystore（`~/.android/debug.keystore`，有效期至 2056）
- versionCode 简单递增（1, 2, 3…）
- 包名只改 `applicationId`，**不改 Kotlin namespace**（`com.nordic.mediahub` 保留为代码包根）——Android 官方推荐两者分离，改 namespace 需动 119 个文件且无功能收益

## 实施方案

### 1. app/build.gradle.kts

```kotlin
defaultConfig {
    applicationId = "fun.han1997.nordic"
    versionCode = 1
    versionName = "0.1.1"
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("debug")
        ... // R8 配置保留
    }
}
```

### 2. 验证

- `assembleRelease` 产出 `app-release.apk`（无 unsigned 后缀）
- `apksigner verify --print-certs` 通过
- `aapt dump badging` 确认 package='fun.han1997.nordic'、versionName='0.1.1'
- 快速门：compileDebugKotlin + testDebugUnitTest + lintDebug

### 3. Spec 同步

- directory-structure.md：包根说明补充 applicationId 与 namespace 分离的说明
- 新增/更新签名与版本规则条目（versionName 0.1.1 起 patch 递增、versionCode 简单递增、release 用 debug keystore）

## 保留不变

- Kotlin/资源 namespace `com.nordic.mediahub`（代码零改动）
- R8 keep 规则、proguard 配置
- Media3 session-activity meta-data（引用的是 activity 类名，与 applicationId 无关）

## 注意

- 包名变更后设备上会作为全新应用安装（旧 `com.nordic.mediahub` 包若装过需手动卸载，数据不迁移——此前未成功安装过 release，无实际影响）
- OPPO 侧载仍需允许"USB 安装/未知来源"；若提示异常可关闭"纯净模式"重试

## 验收记录（2026-09-10）

- [x] Gradle 中 applicationId、namespace、versionName、versionCode 与用户决策一致。
- [x] Debug/release 打包成功；release 使用有效签名，APK 元数据与启动类核验通过。
- [x] 编译、584 项单元测试实际重跑、Lint 通过（0 error，22 项既有 Warning）。
- [x] 预览 Activity 只进入 debug；README 使用新 applicationId 与完整类名。
- [x] Spec、README、CHANGELOG 与 JSONL 上下文已同步；详细证据见 `info.md`。

真机安装与覆盖升级未执行，作为验证边界单独披露，不计为已通过。
