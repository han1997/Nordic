# 验收记录：包名、release 签名与版本规则

日期：2026-09-10。恢复已有实现提交 `f62cafa`，本轮只补齐文档及验收记录，应用版本保持 `0.1.1 / 1`。

## 本轮补齐

- 清理 directory-structure.md 重复的 Overview 与 applicationId 段落。
- 新增 build-release.md 的身份、版本、签名及实际 APK 验证合同，并同步 spec 索引。
- README 补充 JDK/SDK、已签名 release 路径、旧包数据隔离与证书连续性说明；修复 debug 预览 ADB 命令的包名/完整类名。
- CHANGELOG 记录包名、签名修复与版本规则；任务 JSONL 移除示例行并补齐已读取的规范。

## 自动验证

1. 增量质量门：`gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --console=plain`，BUILD SUCCESSFUL（2 秒）。
2. 最终质量门：`gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --rerun :app:lintDebug :app:assembleRelease :app:assembleDebug --console=plain`，BUILD SUCCESSFUL（1 分 9 秒）；`--rerun` 对单元测试任务生效，不只是复用旧报告。
3. 单元测试：584 项、37 个 suite，失败 0、错误 0、跳过 0。
4. Lint：0 error，22 项既有 Warning、18 项 Information；未新增警告抑制。Debug 打包保留既有原生库无法 strip 的提示，不影响成功产物。
5. Build Tools 34.0.0 的 `apksigner verify --verbose --print-certs` 对 debug/release 均通过（v2 有效），两者证书 SHA-256 一致：`16a2350bb63cc53f446ec3aeadbd08ccd9d9475db867c4564ea6b6c3372a545d`。
6. 两个 APK 的 `aapt dump badging` 均为 `fun.han1997.nordic`、versionName `0.1.1`、versionCode `1`，启动类为 `com.nordic.mediahub.MainActivity`。
7. `aapt dump xmltree ... AndroidManifest.xml` 确认预览 Activity 只存在于 debug；release 保留完整 MusicPlaybackService 类名与 Media3 metadata key，未开启 debuggable。
8. 文档 UTF-8、尾随空格、相对链接、7 段 spec 合同、重复标题及 README ADB 组件检查通过；`git diff --check` 与 `task.py validate` 通过。

## Release 产物

- 路径：`app/build/outputs/apk/release/app-release.apk`
- 大小：4343048 bytes
- SHA-256：`1575c36a7fdc6b5aabb7a6261678f2f21b1a4fe88968b3a7e9b335563c268635`（本次核验产物；后续重新打包可能变化）

## 验证边界

- 本轮没有执行真机安装、覆盖升级或 OPPO/ColorOS 播放体验验收，不能据自动检查宣称这些场景已通过。
- 不自动卸载旧包、不清除用户数据、不更换 keystore。当前 debug 证书只用于已确认的侧载方案，非商店发布方案。
- 没有修改应用代码、Gradle 配置、namespace 或 R8 规则；本轮纯文档不递增版本。

## 收尾记录

- 用户已确认文档提交计划，应用实现提交为 `f62cafa`，文档与规范提交为 `6430d62`。
- Phase 3.1 质量验证、3.3 规范同步和 3.4 工作提交均已完成。没有改动应用版本，没有推送远端。
- 任务归档状态与完成日期以 `task.json` 为准；会话日志记录两个工作提交，不混入归档提交。
