# PiP 生命周期与配置订阅排查

## 平台依据

PiP 进入只使 Activity 暂停而保持 STARTED；展开与关闭都可能发生模式 false 回调，因此不能把 false 本身当作停止请求。实际 ON_STOP 才代表播放器不再可见。

自然结束使用 moveTaskToBack(true) 退出 pinned 状态而不主动销毁单 Activity，保留 ViewModel 的 best-effort 上报协程。已查看 AOSP 两端实现：

- Android 8：`ActivityStack.moveTaskToBackLocked` 的 PINNED_STACK_ID 分支调用 `removeStackLocked`（4659–4661 行）。https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/services/core/java/com/android/server/am/ActivityStack.java
- Android 14：`Task.moveTaskToBackInner` 的 `inPinnedWindowingMode()` 分支调用 `removeRootTask`（5706–5708 行）。https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/services/core/java/com/android/server/wm/Task.java

源码依据不等于 OEM 真机验收；系统动画、权限拒绝、上报网络路径仍在 manual-checklist 中。

## Bug Analysis：配置订阅漏通知

### 1. 根因分类

- **E 隐含假设 + B 跨层合同 + D 测试覆盖缺口**：共享 configFlow 假定初始 read 与 listener 注册之间不会保存。save 在 IO，读取/注册并非原子；窗口内保存不会通知尚不存在的 listener。
- 完整测试栈定位到旧 Navidrome 配置测试 runBlocking 等待，不是 PiP 比例算法或 Gradle 编译卡住。此 helper 同样服务 PiP 和所有服务器配置。

### 2. 为何不能只重跑

历史日志曾有测试长时间不结束，但没有足够证据断言都来自同一根因。本次先抓指定 worker 栈，再用注入注册时写入的 SharedPreferences 做确定性红测；旧顺序稳定超时。终止已确认卡住的本任务测试 worker 只是恢复执行环境，不是修复。

### 3. 预防机制

| 优先级 | 机制 | 动作 | 状态 |
|---|---|---|---|
| P0 | 顺序合同 | 先注册 listener，再发初始快照 | 已完成 |
| P0 | 回归测试 | 注册过程中保存 false，旧代码超时、新代码收到 false | 已完成 |
| P1 | 清理保证 | try/finally 注销，覆盖初始读取失败 | 已完成 |
| P1 | 测试有界 | 配置测试类 5 秒超时，专门竞态用例 1 秒 | 已完成 |
| P1 | 全域回归 | 完整 546 测试两轮执行通过 | 已完成 |

### 4. 系统性扩展

搜索确认 app 的 SharedPreferences callbackFlow 只有这一处；统一修复可覆盖所有配置消费者。调度型失败优先使用可控 fake/线程栈，不添加 sleep 或静默重试来掩盖丢通知，也不并发启动 Gradle。

### 5. 知识沉淀

- [x] database-guidelines：播放偏好、监听顺序、清理与确定性测试合同。
- [x] emby-integration：PiP 进入时机、ON_STOP/展开区别、真实结束与非销毁关窗。
- [x] ui-consistency：单一开关语义和纯视频小窗。
- 本仓库没有 Trellis 模板源码副本，不适用模板同步。
