# 最终验证记录

## 已通过
- compileDebugKotlin、testDebugUnitTest、lintDebug、assembleDebug、assembleRelease 同一 Gradle 调用通过。
- 646 项单测，0 失败、0 错误、0 跳过。
- Lint：0 错误、25 条非阻断警告、18 条信息；未为通过检查修改警告阈值或关闭检查。
- debug/release APK 均为 fun.han1997.nordic，0.1.4 / versionCode 4，minSdk 26、targetSdk 34。
- apksigner 验证均通过，两种 APK 使用同一项目约定的 debug 证书，v2 签名有效。
- release 的 NavidromeApi / AudiobookShelfApi / EmbyApi 分别保留 19 / 9 / 8 个泛型签名注解。
- git diff --check 通过。产物路径、大小和 SHA-256 见 verification.json。

## 未执行的设备与服务验收
当前没有连接的 ADB 设备，SDK 也没有可用 AVD。以下不能视为已通过：
- 浅深主题、大字体、窄屏、键盘遮挡、设置搜索定位、未保存退出提示的实际视觉/交互。
- 实际 NAS / 标准 WebDAV 和 AList / OpenList 账号连接、拖动、字幕、续播与临时链接更新。
- Android 实际解码能力、画中画、后台切换和覆盖安装迁移。
- 大目录和多来源条件下的实际帧率、内存及缓存性能。

自动化测试只验证协议、状态与数据合同，不替代上述验收。WebDAV 当前支持 Basic/匿名，不支持仅 Digest 的服务；不实现刮削、投屏、视频离线下载、云同步和远程写操作。

## 工作流状态
工作提交已完成：1d62e1a。用户随后显式请求 trellis-finish-work，现归档任务并记录开发日志。以上自动化验证结果保持有效；设备与真实服务验收仍未执行，归档不等同于这些验收已经通过。
