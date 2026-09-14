# 最终质量复核记录

## 状态

2026-09-14：Phase 2.2 / 3.1 自动门禁与本轮源码复核通过；3.2 复盘及 3.3 规范更新已落盘。2026-09-14 用户已确认首轮样板方向与全部 55 个文件的单次提交范围；执行 Phase 3.4 后归档本轮，不能标全应用完成。

## 代码与边界

- 生产宿主仍负责业务：`MusicPlayerScreen` 持有下载订阅、恢复和取消；`MusicPlayerContent` 仅接状态/回调。`ServerEditorScreen` 保留测试请求版本/取消、显式保存、返回/草稿保护；`ServerEditorContent` 无网络/存储依赖。
- 所有数据协议、来源隔离、播放服务和持久化实现未改；设置开关继续走原更新链路，未增加手动重启或第二份生产状态。
- 歌曲行保留时长与歌手兜底；专辑详情不重复专辑名。共享 SettingsRow、Dock 和播放器表面仍用现有 tokens，无运行时字体/图标/动画依赖新增。
- 队列新几何函数有 JVM 回归；拖动使用最新回调与实测行中心，菜单保留原动作，不复制生产队列。仍未实现跨视口自动拖动，不能宣传该能力。
- 没有新增生产调试日志、异常吞并、类型绕过或为通过测试降低阈值；测试中的 getRunningServices 弃用告警保留并用于当前应用服务集合比较，不以 suppress 掩盖。
- Debug 目录、资源与测试依赖的归属通过实际 Release manifest/resources/mapping 检查，不只看 source set。

## 验证结果

- `delivery-verification.log`：BUILD SUCCESSFUL in 2m 34s，JVM 实际重新执行；后续只修改 instrumentation 同步/几何断言，`final-stable-verification.log` 全任务通过且命中已有 JVM/产物缓存。
- 712 JVM / 54 suites，0 失败/错误/跳过；Lint 0 Error/Fatal，27 Warning、18 Information。Debug/Release 0.1.9/9 签名与身份正确，36 个 Retrofit suspend 泛型合同保留。
- `final-interaction` / `final-interaction-short` 分别 17/17；`delivery-live` 1/1；9 个最终截图批次共 198 张，通过哈希/尺寸/系统字号/像素守卫校验。交互另保存 4 张控制区图。
- Python 报告脚本通过 py_compile；任务 14+14 条上下文路径通过 task.py validate；git diff --check 通过。以上最终复核完成后若修改源码，需按指纹重新验证，不能复用旧结论。

## 本轮发现与回归

1. 短屏 lazy item 必须滚动后定位；屏外播放按钮必须先滚到完整可见再真实点击。
2. Compose idle 不保证窗口截图稳定；所有截图使用同一稳定窗口入口，像素守卫同时判断背景和图标并拒绝空白/相反主题帧。
3. assertIsDisplayed 允许部分相交；控制区追加完整尺寸边界断言，避免半个按钮仍判通过。
4. 空样例秒/毫秒位置一起归零，回归断言 0:00 与禁用播放。
5. IME 弹出/收起都等待真实 inset 和表单视口稳定；父子几何在同一 UI 帧比较，避免测量跨动画帧。
6. 选中副标题完整 onPrimaryContainer 保留 4.5:1 合成对比底线，未恢复原 0.78 alpha。

## 规范和文档

已同步 `ui-consistency.md`、`music-ui.md`、`quality-guidelines.md`，新增七节式 `ui-catalog-verification.md` 并登记索引；校准 DESIGN.md，更新 CHANGELOG/README。复盘中的旧待办已转换为实际合同，本仓库无 Trellis 生成器模板目录，不创建无关模板。

## 限制

完整证据与限制以 evidence-index.md 为准：历史基线缺系统字号/源码指纹；首轮视觉方向已获用户确认，但全应用逐页迁移、真实媒体服务和个人设备验收未完成。初始视口截图不能等同整页/全部交互状态已验收。
