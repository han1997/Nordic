# Emby 章节与片头数据源调研（真机验证完成）

日期：2026-09-09 · 方式：官方 REST 参考（dev/betadev emby.media）+ openapi.json + 真实服务器 curl 验证

## 真机验证结果（服务器 4.9.5.0）

### 章节 + intro 数据源：同一个 `Chapters` 数组（无需独立端点）

* `GET /Users/{UserId}/Items` 请求 `Fields=Chapters` 后，每个 item 返回 `Chapters` 数组。
* 每个条目：`StartPositionTicks`（long）、`Name`、`ImageTag`、`MarkerType`、`ChapterIndex`。
* **`MarkerType` 枚举（openapi.json 确认）**：`Chapter`、`IntroStart`、`IntroEnd`、`CreditsStart`。
* Emby 4.9 的 intro detection 把片头区间直接写成 `IntroStart`/`IntroEnd` 两个 marker 混在章节列表里：
  * 验证样本：50 集 Episode 全部带 Chapters；48 个 `IntroStart` + 48 个 `IntroEnd` 成对出现；`CreditsStart` 0 个。
  * `IntroStart` 命名「片头」，`IntroEnd` 命名「片尾」（服务器命名惯例，不能依赖名称，只依赖 MarkerType）。
  * 25/48 的 IntroStart 在 0，其余在正数位置（OP 在正片数秒后）；区间长度 59s–113s，全部 end > start。
* `MarkerType` 字段在老服务器（4.8-）可能缺失——DTO 必须可空，缺失时按普通章节处理。

### 排除的端点（全部 404/不存在）

* `/MediaSegments/{itemId}`、`/MediaSegments?ItemId=`、`/Items/{id}/MediaSegments`：Emby 不提供（Jellyfin 专属）。
* `/Items/Intros`：存在但为**管理员 debug 端点**（返回全库 intro 的 Path 列表，按文件路径而非 itemId），不可用于客户端播放。
* `/Users/{UserId}/Items/{Id}/Intros`：是「片前推荐预告片」不是 intro 检测。

## 实现决策

1. `EmbyChapterDto` 增加 `MarkerType: String?` 可空字段；`StartPositionTicks` 已有。
2. 章节面板只显示 `MarkerType == null || "Chapter"` 的条目（IntroStart/IntroEnd 是标记不是章节）。
3. intro 区间解析：在同一 item 的 Chapters 里找第一个 `IntroStart`，取其后第一个 `IntroEnd`（ChapterIndex 顺序），tick→秒。找不到 start 或 end 则无 intro。
4. 映射纯函数 + 单测；请求失败/字段缺失一律静默降级为无章节/无 intro，不影响播放主链路。

## 任务 B 相关端点（已确认存在，仅记录）

* `GET/POST /Items/{Id}/PlaybackInfo`（MediaInfoService）
* `GET /Videos/{Id}/master.m3u8`、`live.m3u8`（DynamicHlsService）
* `POST /Sessions/Playing`、`/Progress`、`/Stopped`（PlaystateService）
