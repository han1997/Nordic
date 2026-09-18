# WebDAV 备份与恢复

## Goal

为应用新增基于 WebDAV 的手动备份与恢复能力：用户配置独立备份 WebDAV（地址/账号/密码/目录）与备份密码后，可将应用的逻辑数据导出为密码加密归档上传到自己的 WebDAV，并可跨设备列出、下载、校验、确认后完整覆盖恢复。

## Requirements

### 备份配置（独立于视频媒体来源）
- 设置中新增“备份与恢复”入口。
- 独立配置：WebDAV 地址、用户名、密码、备份目录（默认建议 `nordic-backup`）。
- 提供“测试连接”，成功/失败以现有错误分级风格给出可操作提示（HTTP 401/403/404、不支持的 Digest-only、非 WebDAV 地址、HTTP 明文需明确确认等）。
- 凭据沿用现有 `EncryptedConfigStore` 的加密偏好模式保存。

### 备份内容（仅逻辑数据）
导出并加密以下数据（以仓库实际实现为准，缺省项跳过）：
- 媒体服务器配置与认证信息（Navidrome / WebDAV 来源）。
- 应用偏好设置（设置页各项偏好）。
- 播放历史与播放进度（视频 WebDAV 进度、Navidrome 播放记录如存在本地持久化）。
- 书签/收藏（WebDAV 收藏等）。

不包含：
- 已下载音乐等大文件。
- 媒体目录与图片缓存（可重新生成）。
- Android Keystore 或应用私有文件本身（恢复必须走逻辑写入路径）。

### 备份动作
- 仅手动触发。
- 每次生成带时间戳的新归档（文件名含 UTC 时间戳），上传到配置目录。
- 归档为密码加密（PBKDF2 派生密钥 + AES-GCM 认证加密），内含格式版本、创建时间、应用版本、数据负载与完整性校验（GCM tag 即完整性）。
- 上传成功后自动清理远端旧归档，仅保留最近 5 份（按文件名时间戳排序）。
- 备份过程有忙碌状态、成功提示与错误提示（复用设置页现有模式）。

### 恢复动作
- 列出远端可用归档：时间、应用版本、大小，供用户选择。
- 下载所选归档，要求输入备份密码；先做格式版本与完整性校验。
- 校验通过后展示“完整覆盖、不可逆”确认对话框；确认后才写入。
- 恢复 = 用归档数据完整替换上述“备份内容”范围内的本地数据（不合并）。
- 成功后停止播放并提示重启应用（避免内存旧状态与新数据混用）。
- 解密失败、格式不兼容、网络/认证/权限失败、校验失败时：本地数据零改动，并给出可操作错误提示。

## Acceptance Criteria

- [ ] 设置页可进入“备份与恢复”，完成 WebDAV 配置并测试连接成功。
- [ ] 手动备份：输入备份密码 → 上传成功；远端目录出现带时间戳的加密归档。
- [ ] 上传成功后远端仅保留最近 5 份归档，旧文件被删除。
- [ ] 恢复页能列出归档（时间/版本/大小）并选择其一。
- [ ] 输错密码时解密失败，本地数据不变，错误提示可操作。
- [ ] 确认覆盖恢复后，本地逻辑数据与归档一致；恢复后停止播放并提示重启。
- [ ] 未确认/校验失败/网络失败路径下，本地数据零改动。
- [ ] 单元测试覆盖：归档编解码、密码加解密与错误路径、WebDAV 上传/列举/删除（MockWebServer）、恢复覆盖行为。
- [ ] UI 测试覆盖：设置入口、备份操作、恢复选择与确认对话框。
- [ ] lint / 单元测试通过。

## Definition of Done

- 单元测试与 UI 测试按上述验收标准补充并通过。
- `gradlew :app:lintDebug`、`gradlew :app:testDebugUnitTest` 绿。
- 行为变化按需更新文档/注释。
- 恢复路径保证失败安全（先校验后写入）。

## Technical Approach

- 新增 `BackupRepository`：负责导出逻辑数据（从 `EncryptedConfigStore` 及相关 DataStore/Repository 读取）、归档编解码（JSON + GZIP 可选，版本字段在前）、PBKDF2+AES-GCM 加密。
- 新增备份用 WebDAV 客户端能力：复用 `WebDavRepository` 的 OkHttp/错误分级模式，但独立实现 `PUT` / `GET` / `PROPFIND` / `DELETE`（备份协议需求与媒体浏览不同，避免污染现有类）。
- 新增 `BackupSettingsStore`：独立备份配置（地址/账号/密码/目录）保存进加密偏好。
- 设置 UI：新增页面/分区，含表单、测试连接、备份、恢复列表、确认对话框；复用 `SettingsDataPages.kt` 的忙碌/错误/确认模式。
- 恢复流程：列出 → 下载到内存 → 输密码解密校验 → 确认 → 原子写入各数据源 → 停止播放 → 提示重启。
- 失败安全：任何写入前完成全部校验；写入阶段各 store 尽量原子（先写内存/临时再提交），失败即中止并保持原状。

## Decision (ADR-lite)

**Context**：完整备份含服务器凭据等敏感配置，需跨设备恢复；应用已有 Keystore 加密偏好与 WebDAV 媒体栈。
**Decision**：
1. 备份内容收敛为“逻辑数据”，不含下载与缓存。
2. 使用独立备份 WebDAV 配置，不复用视频来源。
3. 归档使用用户备份密码派生密钥（PBKDF2）+ AES-GCM 加密，支持跨设备。
4. 恢复为确认后完整覆盖，不做合并。
5. 仅手动触发备份；远端保留最近 5 份。
**Consequences**：实现边界清晰、跨设备可用；用户需自行保管备份密码（遗失无法恢复归档）；大文件不进备份，恢复后下载/缓存需重新拉取。

## Out of Scope

- 定时自动备份（用户已明确取消）。
- 合并式恢复。
- 备份下载音乐、媒体目录缓存、图片缓存。
- 多套备份配置或备份到多个目标。

## Technical Notes

- 接入点已核实：
  - `app/src/main/java/com/nordic/mediahub/data/EncryptedConfigStore.kt` — 加密服务器配置与偏好集中存储。
  - `app/src/main/java/com/nordic/mediahub/data/WebDavRepository.kt` — OkHttp、超时、Basic 认证、错误分级（`WebDavException`）模式可复用。
  - `app/src/main/java/com/nordic/mediahub/ui/SettingsDataPages.kt` — 设置页异步操作、忙碌、错误与危险操作确认模式。
- minSdk 26，compileSdk 34，Kotlin + Compose，已有 okhttp/mockwebserver 依赖，无需新增依赖（加密用平台 JCE：PBKDF2WithHmacSHA256 + AES/GCM/NoPadding）。
