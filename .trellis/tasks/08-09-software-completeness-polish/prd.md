# 软件完整度完善

## Goal

继续提升 Nordic Media Hub 的完成度，优先处理用户能直接感知、风险低、能让现有功能更像可交付产品的缺口。本轮先从代码和已有任务中识别候选方向，再和用户确认 MVP 范围后实施。

## What I already know

* 用户提出“软件完整度完善”，未指定具体模块。
* 项目是 Kotlin/Jetpack Compose Android 媒体中心，已有音乐 Navidrome、有声书 AudiobookShelf、视频 Emby、集中配置页、缓存、播放层和较多单元测试。
* 近期已完成配置 tab 抽取、视频浏览/播放页、音乐播放页、底部 Dock、歌词/封面切换、同步性能和两轮 software polish。
* 上一轮 `08-08-software-polish-2` 主要收敛 motion token 和 micro-interaction magic number。
* `app/src/main/java/com/nordic/mediahub/ui/ConfigCards.kt` 通过 `rg` 检查显示为正常中文；PowerShell `Get-Content` 的乱码是终端编码显示问题，不是源码损坏。现有 `UiCopyEncodingTest` 覆盖 UI 文案常见乱码标记。
* `README.md`、`PRODUCT.md`、`CHANGELOG.md` 通过 `rg` 检查显示为正常中文；PowerShell `Get-Content` 的乱码同样不作为源码/文档损坏处理。
* 代码中视频配置仍保留 `PLEX` / `WEBDAV` 类型枚举和 UI 入口，但当前同步与播放实现以 Emby 为主，`isReadyForVideoSync()` 对非 Emby 返回 false。这属于更大的产品范围问题，可能不适合和文案修复混在同一小任务里。

## Assumptions (temporary)

* “完整度完善”优先指向可感知 polish 和收尾缺口，而不是新增大型服务集成。
* 本轮应该选择一个明确、可验收、低风险的范围，避免把 Plex/WebDAV 完整实现、文档重写、UI 大改混成一个任务。

## Open Questions

* None. 用户已确认本轮同时包含候选方向 A、B、C。

## Requirements (evolving)

* 验证配置页用户可见文案未损坏，保留现有 UI 文案编码测试作为防线。
* 收敛视频服务入口与实际能力的一致性：当前视频同步/播放实现以 Emby 为主，Plex/WebDAV 不应表现为已可用完整入口。
* 视频配置仍保留未来扩展空间，但用户界面必须明确当前仅支持 Emby；若保留 Plex/WebDAV 选项，需要显示“暂不可用/后续支持”的状态，并禁止保存/测试造成误导。
* 更新 README/PRODUCT/CHANGELOG 中与服务能力、配置页、视频服务支持范围相关的描述，使其与当前实现一致。
* 文档和 UI 文案使用中文优先；Navidrome、AudiobookShelf、Emby、Plex、WebDAV、API Key 等技术名保留原名。
* 追加打磨：共享加载状态卡片需要有明确的进行中反馈，覆盖同步、搜索、详情加载等路径。

## Acceptance Criteria (evolving)

* [ ] `ConfigCards.kt` 中配置卡片的用户可见中文已验证无源码乱码，现有 `UiCopyEncodingTest` 通过。
* [ ] 视频配置 UI 不再暗示 Plex/WebDAV 已完整可用；当前可用能力明确为 Emby。
* [ ] 若 Plex/WebDAV 仍显示在 UI 中，必须有不可用说明，且保存/测试逻辑不会把它们当作可同步视频服务。
* [ ] `README.md` 不再把 Plex/WebDAV 描述为当前已支持的视频能力，除非同时说明是后续计划或暂不可用。
* [ ] `PRODUCT.md` 的产品能力描述与当前实现一致。
* [ ] `CHANGELOG.md` 在 `[未发布]` 中记录本次配置页文案修复、视频入口收敛和文档说明更新。
* [ ] 共享 `MediaLoadingCard` 在所有使用场景下呈现轻量进度反馈，不改变各页面加载条件或业务逻辑。
* [ ] 用户可见文案符合中文优先规范，技术名如 Navidrome、AudiobookShelf、Emby、API Key 保持原名。
* [ ] 相关单元测试、编译和 lint 按项目规范验证。

## Definition of Done

* 修复配置页文案和视频服务入口状态，不改变 Emby 现有同步/播放行为。
* 更新 README、PRODUCT、CHANGELOG 中相关能力描述。
* 运行必要 Gradle 检查，至少包含 `:app:compileDebugKotlin`；如涉及 UI 逻辑或文案测试，运行相关 unit test。
* 说明是否需要新增/更新测试，以及未覆盖风险。

## Out of Scope (explicit)

* 不在本轮完整实现 Plex 或 WebDAV。
* 不重构播放引擎、缓存、认证、Media3 架构。
* 不做大范围视觉重设计。
* 不把所有硬编码中文迁移到 Android string resources，除非确认这是本轮目标。

## Decision (ADR-lite)

**Context**: 配置页是三类媒体服务的核心入口，但 `ConfigCards.kt` 仍有用户可见乱码；视频配置模型保留 Plex/WebDAV 类型，README 也提到 Plex/WebDAV，但当前视频同步、缓存、播放和测试逻辑均以 Emby 为主。

**Decision**: 本轮同时处理配置页文案完整度、视频服务入口一致性和文档能力说明一致性。优先让现有 Emby 能力清晰可用，并把 Plex/WebDAV 定位为暂不可用/后续支持，而不是伪装成已完成集成。

**Consequences**: 变更会触及 UI 文案、配置入口行为和项目文档，但不新增服务集成、不改变 Emby 播放链路；风险主要在 UI 状态逻辑和文档描述一致性。

## Technical Notes

* 任务目录： `.trellis/tasks/08-09-software-completeness-polish`
* 相关规格： `.trellis/spec/backend/index.md`、`.trellis/spec/backend/documentation-guidelines.md`、`.trellis/spec/backend/quality-guidelines.md`
* 主要候选文件： `app/src/main/java/com/nordic/mediahub/ui/ConfigCards.kt`、`app/src/main/java/com/nordic/mediahub/ui/ServerConfigScreen.kt`、`app/src/main/java/com/nordic/mediahub/data/ServerConfig.kt`
* 实现后补充规格：`.trellis/spec/backend/quality-guidelines.md` 新增 PowerShell `Get-Content` 显示乱码与源码损坏的误判防线。
* 验证命令参考：
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`
