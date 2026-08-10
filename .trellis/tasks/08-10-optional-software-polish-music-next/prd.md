# 继续打磨音乐体验

## Goal

打磨音乐搜索体验：在已有搜索框中提供一键清除入口，让用户不必手动删除整段关键词即可回到搜索落地页；清除时同时取消待执行的 debounce 搜索并清理旧结果、错误和加载状态，避免旧请求完成后重新污染页面。

## What I Already Know

* 用户要求继续打磨音乐部分。
* 音乐播放队列排序、音乐播放页进度拖动和音乐相对 seek 已在前几轮完成，本轮不重复这些方向。
* `MusicScreenV2` 的 `MusicLibraryPage.Search` 已有搜索输入和 300ms debounce，但 `OutlinedTextField` 当前没有 trailing clear action。
* 清空输入依赖用户手动删除文本；`onValueChange` 在空查询时会清空 `searchResult` 并停止 `isSearching`，但没有集中取消/清理语义，也缺少明确的一键清除入口。
* 搜索任务通过 `AtomicReference<Job?>` 保存，已有 config version 和 query 比较来防止部分陈旧请求写回。
* 项目使用 Compose Material 3 和现有 Nordic design tokens；搜索页面已有 `MusicSearchLanding`、`MediaLoadingCard` 和搜索结果区。

## Requirements

* 当搜索框内容非空时显示可识别的一键清除按钮，并提供无障碍 content description。
* 点击清除后将搜索文本设为空，并立即清理 `searchResult`、`searchError`、`isSearching` 等搜索状态。
* 点击清除时取消当前 debounce 或网络搜索 Job，避免清除后旧请求继续更新结果。
* 清除后保持搜索页面本身不变，显示现有 `MusicSearchLanding`，不自动切换到其他音乐 tab。
* 保持非空查询的现有 debounce、API 调用、结果展示、配置版本隔离和错误处理行为。
* 不改变音乐搜索 API、结果模型、结果排序、歌曲播放入口和其他音乐页面。
* 更新 `CHANGELOG.md` 记录用户可见的搜索体验改进。

## Acceptance Criteria

* [ ] 非空搜索关键词显示清除按钮，空关键词不显示该按钮。
* [ ] 点击清除按钮后搜索框为空，搜索落地内容立即恢复。
* [ ] 点击清除后待执行 debounce 被取消，不会发起不必要的搜索请求。
* [ ] 清除后旧搜索结果、错误和 loading 状态不会重新出现在页面。
* [ ] 非空查询仍保持 300ms debounce、正常结果展示和错误提示。
* [ ] 现有音乐播放、队列、收藏、歌词和其他音乐浏览行为不回归。
* [ ] `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug` 通过。

## Definition Of Done

* 代码改动聚焦 `MusicScreenV2` 搜索输入和搜索状态清理。
* 遵循现有 Material 3 / Nordic token 视觉和无障碍模式。
* 用户可见行为记录到 `CHANGELOG.md`。
* 补充或更新必要的搜索状态测试；至少通过完整 Gradle 门禁。

## Technical Approach

使用 Material 3 `OutlinedTextField` 的 trailing icon，在 `searchQuery.isNotBlank()` 时显示清除按钮。将清空输入和搜索状态重置收敛到搜索页内的小型状态清理逻辑：取消 `searchJob`，清空 job 引用、结果、错误和 loading，再把 query 设为空。保留现有非空输入分支和 request version 防陈旧写回机制。

## Decision (ADR-lite)

**Context**: 搜索功能可用，但清空关键词需要手动删除，且清空行为没有集中取消待执行搜索任务。

**Decision**: 采用 Material 3 输入框 trailing clear action，复用现有 `AtomicReference<Job?>` 和 Compose 状态，不引入新搜索 ViewModel 或搜索框组件。

**Consequences**: 改动范围集中在音乐搜索 UI 状态；用户能快速回到搜索首页，清除操作也更可靠。搜索状态仍由 `MusicScreenV2` 管理，后续若搜索能力扩大再考虑抽离。

## Out of Scope

* 不改变 Navidrome search API 或 `SearchMusicResult` 数据结构。
* 不新增搜索历史、热门搜索、过滤器或高级搜索语法。
* 不改变搜索结果排序、分组和播放行为。
* 不重做 Music 页面导航和视觉布局。

## Technical Notes

* 任务目录： `.trellis/tasks/08-10-optional-software-polish-music-next`
* 相关文件： `MusicScreenV2.kt`、相邻音乐 UI 测试、`CHANGELOG.md`
* 相关规格： `.trellis/spec/backend/index.md`、`.trellis/spec/backend/quality-guidelines.md`、`.trellis/spec/backend/navidrome-integration.md`、`.trellis/spec/backend/documentation-guidelines.md`
