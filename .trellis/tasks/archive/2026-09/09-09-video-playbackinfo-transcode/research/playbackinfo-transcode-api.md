# PlaybackInfo 与转码链路调研（真机验证完成）

日期：2026-09-09 · 服务器：Emby 4.9.5.0（openapi.json + curl 实测）

## 端点行为（真机验证）

### GET /Items/{Id}/PlaybackInfo（无 body）

* 200 返回 `{ MediaSources: [...] }`（无 PlaySessionId 顶层字段时 GET 也有）。
* MediaSource 关键字段：`Id`（`mediasource_<itemId>`）、`Protocol`（"File"）、`Container`（"mkv"）、`Path`、`Size`、`RunTimeTicks`、`SupportsDirectPlay`、`SupportsDirectStream`、`SupportsTranscoding`、`MediaStreams`（完整流信息含 Codec/BitRate/Height 等）。
* GET 形状足够做「探测」；POST 带 DeviceProfile 是官方客户端用法。

### POST /Items/{Id}/PlaybackInfo + DeviceProfile

* Body：`{"DeviceProfile": {...}}`；query 可带 `UserId=<guid>`（**必须带合法 guid，否则 500 "Unrecognized Guid format"**）。
* `AutoOpenLiveStream=true&MaxStreamingBitrate=N` 放 query 或 body 均可，但实测**服务器仍返回 SupportsDirectPlay=true**（mkv/H.265 在此 profile 下 DirectPlay 判定宽松）——**不能依赖服务器替我们做「不可直连」判定**。
* `PlaySessionId` 由**服务器生成**返回（32 hex）；客户端不应自己生成。
* `TranscodingUrl` 实测未返回（此服务器配置下）——**转码 URL 需客户端自己构造**。

### 转码 URL 构造（真机验证可用）

```
GET /Videos/{itemId}/master.m3u8?MediaSourceId={mediaSourceId}
    &VideoCodec=h264&AudioCodec=aac&VideoBitrate={bitrate}
    [&TranscodeReasons=...]           → 200 返回 master playlist
GET /Videos/{itemId}/main.m3u8?...&SegmentContainer=ts
                                     → 200 返回 VOD 媒体 playlist（3s 分段）
```

* `master.m3u8` 是单条目 wrapper，内部指向 `main.m3u8`；**ExoPlayer 直接吃 master.m3u8 即可**。
* 认证：`X-Emby-Token` header（与现有 `EMBY_HEADER_AUTH_ENABLED` 一致）；OkHttp 拦截器已覆盖（`MediaAuthHeaderRegistry`）。
* 分段 URL 相对路径，ExoPlayer/HLS datasource 自动解析。

## 关键实现结论

1. **转码决策在客户端**：依据清晰度档位 + 媒体信息（Container/Codec/BitRate 来自 MediaStreams）判定：
   * 「原始」→ Static 直连（现状不变）
   * 「自动」→ 直连优先（现有行为，MVP 可与原始等价，预留判定扩展）
   * 「限码率 N」→ 直接构造 master.m3u8 转码 URL
2. **PlaySessionId**：POST PlaybackInfo 获取（可带 UserId guid）；转码播放的 Progress/Stopped 上报可带 `PlaySessionId` 字段（扩展现有 request DTO，向后兼容）。
3. **DeviceProfile 最小集**：`MaxStreamingBitrate` + `DirectPlayProfiles` + `TranscodingProfiles` 已被服务器接受（4.9.5 实测 200）。
4. **UserId**：仓库已有 `session()` 拿 userId，无障碍。
5. **切清晰度 = 用新 URL 重新 play**（`shouldReplaceCurrentVideoItem` 因 streamUrl 不同自动重建），进度由 `playbackPositionSeconds` 或引擎 seek 恢复。

## 风险

* POST PlaybackInfo 携带 AutoOpenLiveStream 时服务器不强制转码 → 不依赖它，档位判定全在客户端。
* `SupportsDirectPlay` 字段是服务器对「此设备能否直接解码」的乐观判定，真机播放失败仍需回退——MVP 先按档位驱动，播放错误时提示用户切换清晰度。
