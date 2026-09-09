# 继续观看使用服务端数据并持久化

## 背景

"继续观看"当前有三层数据来源（VideoScreen.kt:91-100）：

1. **服务端列表**（权威）：`GET /Users/{id}/Items/Resume`，每次进屏/ON_RESUME 刷新，仅存内存 UI 状态
2. **本地推导 fallback**：`resumeVideos == null` 时（首次加载中 / 请求失败），用 `continueWatchingShelf(videos)` 从本机缓存的目录推导——位置可能严重过期（如在电视上看了一半，手机缓存还是旧的）
3. **缓存**：`EmbyVideoCache` 只存目录（libraries/videos），不存 Resume 列表

问题：冷启动和请求失败时展示的是本机缓存推导的过期进度，而非服务端数据。

## 用户决策（已确认）

- 继续观看**不使用本机缓存推导**，保持使用服务端数据
- **缓存也缓存服务端数据**：把服务端 Resume 响应持久化，冷启动 cache-then-network（先显示缓存的服务端列表，随后静默刷新）

## 实施方案

### 1. EmbyVideoCacheRepository.kt

- `EmbyVideoCache` 增加 `resumeVideos: List<VideoItem>` 字段
- `VIDEO_CACHE_SCHEMA_VERSION` 1→2（旧缓存整体失效一次，避免 Gson 反序列化缺字段 null 隐患）
- 新增 `saveResumeItems(config, items)`：成功拉取后把服务端 Resume 响应写入缓存
- `load()` 有效条件加上 `resumeVideos.isNotEmpty()`

### 2. VideoScreen.kt

- `applyCachedVideo`：冷启动从缓存恢复 `resumeVideos`（上次的服务端数据，cache-then-network）
- `continueWatchingVideos`：删除 `continueWatchingShelf(videos)` fallback，只认服务端列表（缓存或新鲜拉取）
- `refreshResumeItems`：成功 → 更新状态 + 写缓存；失败 → **保留现有值**（不再置 null，避免把已显示的服务端数据打掉）

### 3. VideoScreenLogic.kt

- 删除 `continueWatchingShelf`（唯一调用方就是被删的 fallback）

### 4. 测试

- 删除 4 个 `continueWatchingShelf_*` 测试（VideoScreenTest.kt:22-129）
- `EmbyVideoCacheRepositoryTest` 增加 Resume 列表 round-trip 用例

### 5. Spec 同步（emby-integration.md）

- L103 "Resume 列表仅 UI 状态，never persisted" → "持久化服务端 Resume 响应到视频缓存，无本地推导 fallback"
- L106 "These shelves are view state only" 相应调整
- L178 "Missing UserData.LastPlayedDate → continue-watching shelf keeps..." 本地推导规则删除
- 测试清单中 continueWatchingShelf 相关条目更新

## 行为变化

- 离线且无缓存的服务端 Resume 数据时，"继续观看"区直接隐藏（不再显示本机推导的过期列表）
- 冷启动先显示缓存的服务端 Resume 列表，随后静默刷新

## 验证

- `gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug`
