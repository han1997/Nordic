# 媒体库按库缓存与切库免同步

## 背景

视频 Emby 有三个"每次都同步"的路径：

| 路径 | 现状 | 痛点 |
|---|---|---|
| 切媒体库 chip | 立即清空 videos → loading → 全量分页拉取（100/页）→ 重写整个缓存 | 即使刚看过这个库也要重新等全量网络 |
| ON_RESUME（回到视频 tab） | 无条件全量 getCatalog（libraries + 选中库全部条目分页） | 每次切 tab 就全量同步，最大痛点 |
| 冷启动 | cache-then-network + 30min TTL | 正常 |

根因：`EmbyVideoCache.videos` 只存"当前选中库"的条目——切回上一个库时缓存里没有它的数据，只能重新拉。有声书切库同样清空+全量拉取。音乐无库切换概念，无此问题。

## 用户决策（已确认）

1. **ON_RESUME 策略**：Resume 列表每次刷（单请求，保跨设备进度）；全量 catalog 加 5 分钟 TTL 门控；↻ 手动刷新绕过 TTL
2. **有声书同步改造**：切库同样改为按库缓存 + cache-then-network，行为与视频一致

## 设计定案

**核心：每个媒体库的条目列表独立缓存，切库先显示缓存再后台刷新（stale-while-revalidate）**

```
┌─ 切库 chip 点击 ─────────────────────────────┐
│ 目标库有缓存 → 立即渲染（无 spinner）          │
│                → 后台 getLibraryItems 静默更新 │
│                → 成功后回写该库条目 + 时间戳    │
│ 目标库无缓存 → 现有 loading 全量拉取           │
└──────────────────────────────────────────────┘
```

## 实施方案

### 1. EmbyVideoCacheRepository.kt（schema v2→v3）

- `videos: List<VideoItem>` → `itemsByLibrary: Map<String, List<VideoItem>>` + `libraryFetchedAt: Map<String, Long>`
- `LIBRARY_CACHE_MAX_ENTRIES = 4`，写入时按 `libraryFetchedAt` LRU 逐出最久未用的库
- `load()` / `buildCache()` 签名适配；`saveResumeItems` 不受影响

### 2. AudiobookShelfCacheRepository.kt（schema v1→v2）

- `items: List<AudiobookItemSummary>` → `itemsByLibrary: Map<String, List<...>>` + `libraryFetchedAt`
- LRU 上限 4；`itemDetails` 合并语义保留

### 3. VideoScreen.kt

- `applyCachedVideo`：从 `itemsByLibrary[selectedLibraryId]` 恢复 videos
- 切库 chip：命中缓存 → 立即渲染 + 后台静默刷新（复用现有守卫，成功后回写该库条目）；未命中 → 现有 loading 全量拉取
- ON_RESUME：Resume 单请求每次刷；全量 catalog 加 5 分钟 TTL 门控；↻ 绕过
- 头部副标题缓存年龄改用当前库的 `libraryFetchedAt`

### 4. AudiobookScreen.kt

- 切库 chip 同样 cache-then-network；`applyCachedAudiobooks` 从 `itemsByLibrary` 恢复

### 5. CacheTtl.kt

- 增加通用 `isCacheFresh(updatedAtMillis, ttlMillis)` 重载（共享 TTL 辅助）

### 6. 测试

- `EmbyVideoCacheRepositoryTest`：按库 round-trip / LRU 逐出 / 跨库隔离 / v3
- `AudiobookShelfCacheRepositoryTest`：同样用例
- `CacheTtlTest`：新重载边界
- `CacheKeyTest`：`|v3` / `|v2`

### 7. Spec 同步

- database-guidelines.md Cross-Domain Media Cache Refresh 场景补按库缓存契约
- emby-integration.md 相应更新

## 保留不变

- `videoLibraryRequestVersion` / `configStateVersion` 全部竞态守卫
- 冷启动 30min TTL、手动 ↻ 绕过 TTL、错误分支 hasCachedContent 语义
- 详情页缓存语义、配置切换 clear(previousConfig)

## 验证

- `gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug`
