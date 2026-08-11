# Navigation Audit Decisions

## Audit Artifact

* `research/navigation-audit.md` — 静态审计 Music / Audiobook / Video 的 back path、selection-after-refresh、first-connect、config-change reset。

## User Decision

用户确认本轮只修音乐相关 findings，视频/有声书 findings 暂不纳入实现。

## Included In This Task

* **M1 - Detail back path loses source context**：实现音乐详情页来源感知回退，让 AlbumDetail / ArtistDetail / PlaylistDetail 尽量回到进入前上下文。
* **M3 - Refresh can leave selected music detail objects dangling**：刷新 music albums/artists/playlists 后 reconcile selected detail objects，避免悬挂 state。
* **M5 - Config reset feedback is silent**：音乐配置变更导致非 Home/可见内容复位时，显示轻量说明。

## Deferred

* **V4 / V6**：视频 catalog/filter consistency 和配置 reset 反馈。
* **A4**：有声书配置 reset 反馈。
* **C1**：跨模块统一配置 reset 反馈模式。

## Implementation Constraints

* 保持当前 state-driven navigation，不引入 Jetpack Navigation Compose。
* 保持 `BackHandler` 和 `MediaPageHeader(onBack)` 共用同一回退函数。
* 不修改 Navidrome / AudiobookShelf / Emby 数据接口、播放引擎或缓存 schema。
* 用户可见行为更新 `CHANGELOG.md`。
