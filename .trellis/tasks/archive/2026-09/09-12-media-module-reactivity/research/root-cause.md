# 模块显示开关即时更新：根因与修复方案

## 实际数据链路

`ModuleVisibilityPage.toggle` → `SettingsScreen.update` → 设置页自己的 `ConfigRepository.updatePreferences` → `EncryptedConfigStore.updatePreferences` → `EncryptedSharedPreferences.Editor.commit()`。

主界面由 `MainActivity` 的另一个 `ConfigRepository.preferences` Flow 提供 `LocalAppPreferences`；`MainScreen`、`SettingsScreen` 再使用其中的 showMusic/showAudiobook/showVideo 计算开关、可见媒体、启动回退、设置分类和搜索。

## 根因证据

- `EncryptedConfigStore.kt` 的默认 prefsProvider 每次为一个 store 新建 EncryptedSharedPreferences 包装器。
- 本机解析到的 `androidx.security:security-crypto:1.1.0` 源码：`EncryptedSharedPreferences.java:93,110` 每个包装器持有自己的 mListeners；create 在 179 行返回新包装器；479–487 行注册/注销仅操作本实例列表；Editor 在 395–402 行仅通知该包装器的监听者。
- 多个包装器读写同一个 secret_prefs 文件，但不共享变更通知。设置页成功提交后主界面 Flow 仍停留在旧值；重启重新读取才生效。不是 Switch 点击事件或 Compose remember 缺少 key。
- 既有 FakeSharedPreferences 测试通常把同一个对象注入读写 store；只验证 round-trip/重建读取，未模拟同文件不同包装器的监听隔离，因此漏掉跨页面即时更新。

## 方案与范围

- 在加密存储默认入口共享一个进程级、线程安全、惰性创建的 SharedPreferences 包装器；只用 applicationContext 创建。
- 新建失败不缓存异常或空实现，后续正常重试；不绕过 Keystore、不降级明文。
- 保留 store 的 Flow 注册顺序、distinctUntilChanged、finally 注销、IO commit 和失败回滚；不以 configurationReload、重启 Activity 或 UI 假状态代替修复。
- 除模块显示外，同一底层合同也服务服务器选择、主题/播放偏好和恢复默认值，因此增加跨 store 回归。

## 回归设计

- 复用 FakeSharedPreferences，仅允许注入共享数据 Map；每个新包装器保留独立 listener 列表，模拟 AndroidX 的真实语义。
- 先提取不缓存的实例入口（保持缺陷行为），用持续订阅的不同 store 与连续模块切换触发失败，再加入共享实例使其通过。
- 验证七种有效模块组合、稳定媒体 ID/回退、设置分类与搜索过滤、重建持久化、恢复默认、拒绝全关。
- 验证共享实例并发只创建一次、初始化失败可重试；跨 store 来源与播放偏好仍即时传播。
- 不把 JVM 的 Flow/推导验证称为真实 Android UI 验收；设备可用性与真机结果单独记录。

## Bug Analysis: 跨实例设置变更通知丢失

### 1. Root Cause Category
- **E / B / D**：隐含假设错误、跨层合同缺失和测试覆盖空白。同文件读写被误认为等同于同一监听注册表。

### 2. Why Fixes Failed
- 本轮没有反复打补丁；首次先保留旧行为做红灯验证，7 项中 6 项失败（4 项跨实例 Flow 等待超时、2 项实例复用合同失败）。
- 上次实现只检查偏好序列化和静态列表推导，未覆盖设置页写入与根界面持续订阅分属不同 store 的真实拓扑。

### 3. Prevention Mechanisms
| 优先级 | 机制 | 具体动作 | 状态 |
|---|---|---|---|
| P0 | 结构约束 | 默认入口共享一个线程安全、成功后缓存的包装器 | 已实现 |
| P0 | 红绿回归 | 独立 listener + 共享数据的 Fake，持续订阅跨 store 更新 | 红绿均已确认；新增 7 项与全量 704 项通过 |
| P1 | 可执行规范 | 持久化合同写明同文件不等于共享监听、并发和重试保证 | 已更新 |

### 4. Systematic Expansion
- 相同入口还服务主题、播放偏好、服务器选择和恢复默认，已加入跨实例回归，而非逐个设置按钮加刷新。
- 复用现有 `configFlow` 的先监听后读取与 finally 注销，不引入第二套内存状态或依赖重启。

### 5. Knowledge Capture
- [x] 更新 `.trellis/spec/backend/database-guidelines.md` 的完整七段订阅合同。
- [x] 更新 `.trellis/spec/backend/media-sources-webdav-settings.md` 的即时生效约束与规范链接。
- [x] 更新 CHANGELOG；README 的功能入口和使用方式未改变，不重复改写。
- 本项目无 `src/templates/markdown/spec/` 模板副本；规范随工作提交一并提交，仍等待用户确认。
