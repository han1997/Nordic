# 多服务器、WebDAV 与设置中心合同

## 范围与入口

适用于服务器管理、媒体认证/缓存、WebDAV 浏览播放、全局偏好及设置导航。本合同替代旧单服务器配置下的“切换即清掉旧服务器缓存”策略。

- `MediaSource` / `MediaSourceState` / `MediaSourceKind` / `data.MediaDomain`。
- `ConfigRepository.sourceState`、`preferences`、`saveSource`、`selectSource`、`deleteSource`、`updatePreferences`。
- `WebDavRepository.listDirectory` / `preparePlayback`；`WebDavLocalRepository`。
- `MediaSourceManagementHost`、`SettingsScreen`、`SettingsNavigationGuard`、`LocalAppPreferences`。
- `ScopedMediaRegistry`、`MediaAuthHeaderInterceptor`、`ScopedMediaNetworkInterceptor`。
- `playback.MediaDomain` 是既有共享音频播放器所有权枚举，与 `data.MediaDomain` 不同；同时引用时显式 alias，不能混用。

## 来源身份与持久化

1. 每个连接是稳定 UUID，音乐/有声书/视频分别保存 active ID，不合并列表或队列。
2. 名称、密码修改不改变 ID；类型、地址、账号、API Key 或 WebDAV 起始目录改变时创建新 ID，旧媒体数据保留供管理，不关联到新账号。
3. `ConfigRepository` 保留旧单配置 Flow 作为当前来源投影；来源级数据不能仅凭旧媒体 ID 判断身份。
4. 连接集合和偏好仍写入 `EncryptedConfigStore`，同步 `commit()` 在 IO 调用中完成。提交失败需恢复内存中的旧状态并报错，不降级明文。
5. 旧凭证迁移必须等提交成功后才移除 DataStore 的旧凭证键，不能删除整个共享 DataStore 文件。迁移失败不得留下已完成标记。
6. 多来源迁移一次写入列表与 active ID，并移除已迁移服务的旧加密凭证槽，避免删除连接后旧密码仍残留；未接入的旧 Plex 配置不伪装成 Emby。
7. 可确认账号的旧目录缓存迁到来源键，旧账号身份和认证 URL 参数必须清除。无法确认来源的目录缓存可丢弃；旧历史、书签和音乐下载只能手动归属或明确清理。
8. 恢复偏好不删除连接或媒体数据，清缓存不删除收藏文件夹、下载和进度。

## 播放与并发

- 来源切换与跨媒体播放共用 MainScreen 的交接门闩。正在播放或准备播放的同类来源切换需确认，先关闭旧会话再更新 active ID。
- Emby/有声书上报持有原会话的 repository，不读取后来切换的当前浏览 repository；书签异步结果同时检查 session ID 与 source ID。
- 下载目录、缓存键、历史、书签和继续观看按 source ID 隔离。旧请求不能更新新来源 UI；原来源后台任务的结果也不能污染当前列表。
- 同一媒体源生命周期中的请求使用来源认证上下文。删除/替换来源会撤销旧上下文，迟到的认证结果不能重新注册已撤销 ID。

## 媒体 URL 与重定向

- 无凭证媒体 URL 使用 `#nordic-source=<id>` 作为本机路由与缓存身份；该 fragment 在发网络请求前去除。不能把密码、API Key 或 bearer token 放入该标识。
- `MediaAuthHeaderInterceptor` 捕获具体来源上下文；`ScopedMediaNetworkInterceptor` 每一跳按 scheme、host、port、根路径校验，跨范围移除 Authorization / X-Emby-Token，并拒绝 HTTPS 降级。
- Navidrome token/salt 在实际请求时生成，不持久化在新缓存和下载元数据中。
- 连接测试使用独立临时 ID，finally 清理，绝不能用保存来源的 ID 测试其他密码。
- 旧 host:port 注册入口只为旧无来源调用的兼容测试保留；应用中的已保存来源必须走 source-scoped 上下文。
- CDN 签名参数仅用于当前请求，不调用通用 stripAuthQuery 去破坏实际网盘请求，也不把重定向结果写入磁盘。

## WebDAV 协议合同

- 只读 `PROPFIND Depth: 1` 与媒体 GET；不递归扫描、不上传、不修改远程文件。
- 支持 Basic 与匿名认证；仅 Digest 的服务明确提示不支持，不误报为密码错误。
- 目录解析识别 DAV namespace 和每个 propstat 的状态，排除根目录自身、跨根 href、外域 href、非直接子项和失败属性块。
- 规范路径保留编码和大小写，展示层解码一次；中文、空格、百分号、编码斜杠均须回归。禁止通过 `..` 越过根目录。
- XML 同时限制响应体大小并禁止 DTD/外部实体；取消必须保持到响应体读完，不能只取消等待响应头。
- 浏览缓存限制目录数及总条目数；大目录仍可浏览，但不写入无界 JSON 缓存。
- WebDAV 原画播放，不提供 Emby 转码、季集元数据或片头标记。同目录选集与可选前台自动连播按文件名自然顺序，详见[视频自动连播合同](./video-auto-play-next.md)。音轨来自实际 Media3 Tracks；同目录同名 SRT/ASS/VTT 作为外挂字幕。
- 字幕启用状态与具体选中的轨道分开，自动字幕必须挂载 SubtitleView，不能依赖用户手动选轨才显示。
- Range > 0 必须得到匹配 Content-Range 的 206。200/416 或错误范围抛协议错误，禁止靠丢弃整段视频字节模拟快进，并提供从头播放。
- 401/403/410 临时地址错误仅自动重试一次原 WebDAV 地址；其余错误可手动重试。WebDAV 进度只保存在本机，完成后不出现在继续观看。

## 设置与 UI

- 设置首页八类入口，子页返回保留位置；搜索只检索设置名/说明，不索引凭证。
- 通用设置即时持久化；默认字幕/音轨和清晰度注明下次播放生效。视频长按临时倍速不能写入默认倍速。
- 来源编辑保留显式保存、独立测试与未保存退出保护。草稿密码只在内存，不放进 rememberSaveable / SavedState Bundle。
- 分组、输入、按钮、选择行、48dp 触达与主题字体遵循 ui-consistency；播放设置与播放器控制共享同一持久化来源。
- 不能展示没有实现的投屏、Plex、云同步、视频下载或备份按钮。

## 错误与验收

| 场景 | 必须结果 |
|---|---|
| 同主机不同账号、相同媒体 ID | 认证、缓存、历史与下载均不串源 |
| 迁移/保存 commit=false | 原数据保留、标记可重试、无幽灵来源 |
| 取消目录请求或切换来源 | 关闭请求并忽略旧结果 |
| 目录 401/403/404、HTML 200、非法 XML | 类型化、中文、可操作的错误 |
| 网盘跨域重定向 | 无原服务器凭证，保留签名参数和 Range |
| 清目录缓存 | 收藏文件夹与观看进度保留 |
| 手动归属旧下载遇到冲突 | 不覆盖目标文件，原冲突文件保留 |
| 自动选择字幕 | SubtitleView 与实际选中轨道同步 |

必需测试包括 `MediaSourceTest`、`MultiSourceSafetyTest`、`WebDavRepositoryTest`、`ScopedMediaSafetyTest`、`WebDavTracksTest`、`SettingsCenterTest` 及既有媒体回归。编译/单测/Lint/打包不能替代真实 NAS、AList/OpenList 和 Android 设备播放/视觉验收，未验证的环境必须明确记录。