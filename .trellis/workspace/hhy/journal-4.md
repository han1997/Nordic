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
