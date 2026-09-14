# Bug Analysis：样板验收中的字体、语义与合成色边界

## 1. 根因分类

- **E：隐式假设**。`hasScrollToIndexAction()` 同时匹配表单 LazyColumn 和嵌套类型 LazyRow；禁用 TextField 不再暴露 SetText；不能假定一个动作选择器在所有状态下都唯一或存在。
- **B/D：跨窗口合同与覆盖缺口**。只改 LocalDensity 不能证明 Dialog 的系统字体环境一致。实际改系统字号又会重建 Activity；仅等待 application resources 更新，不代表新的 Activity 已消费样板请求。
- **E：错误尺寸模型**。Android 14 字体非线性缩放；2× 迷你条实测约 95.33dp，硬写至少 100dp 不等于检测文字裁切。
- **B：颜色合成边界**。浅/深选中容器均有 alpha，必须先合成到实际不透明 sheet。修正测试的底色后，深色选中副标题 0.78 alpha 的实测对比仍只有 3.781329:1，是实际可读性缺陷。

## 2. 先前尝试为何失败

1. 把 `performScrollTo` 换成 `performScrollToNode` 仍未限定方向，嵌套横向选择器仍被同时选中。
2. 直接放宽 100dp 高度断言只能掩盖错误模型；现改为小/大字体实际增高、文字布局无纵向溢出、文字边界处于条内。
3. 初次对比断言忽略容器合成，得到无意义的浅色 2.0759:1；补齐底色后才定位到真实深色 3.78:1。最终修正正文角色，不降低 4.5 阈值。
4. 字号切换后 application resources 已更新，但 Activity 生命周期仍异步重建（日志存在 DESTROYED/CREATED），导致后一次显示请求偶发落在旧宿主。Debug 样板 Activity 显式处理 fontScale，生产 Activity 不改。

## 3. 预防措施

| 优先级 | 机制 | 本轮动作 | 状态 |
|---|---|---|---|
| P0 | 语义选择器 | 使用 VerticalScrollAxisRange + ScrollToIndex；禁用字段按 label 定位并断言无 SetText | 已实现；最终复核批次见 evidence-index.md |
| P0 | 布局断言 | 实测增高、GetTextLayoutResult.didOverflowHeight、文本边界；48dp 实际目标 | 已实现；最终复核批次见 evidence-index.md |
| P0 | 窗口与字号 | 截图实际切系统字体；封存每张 systemFontScale；Debug 宿主处理 fontScale | 已实现；最终复核批次见 evidence-index.md |
| P0 | 合成对比 | 选中副标题用完整 onPrimaryContainer；测试覆盖实际合成背景 | 已实现；最终复核批次见 evidence-index.md |
| P1 | 可追溯证据 | 记录 APK/测试包/源码/运行脚本 SHA-256、尺寸、矩阵完整性；恢复设备显示设置 | 已实现 |
| P1 | 规范 | 在质量验证后同步共享 UI 与样板验收合同 | 已同步，最终结果见 evidence-index.md |

## 4. 系统性扩展

- 共享 MediaPlayerChoiceRow 同时影响音乐、有声书及视频调用方；本轮只确认首轮样板，其余页面仍需逐页验收。
- 队列拖动按真实行中心及被拖行 extent 位移，不从固定 64dp 推断大字体几何；菜单提供无需拖动的上下移入口。
- 真实主页面的上轮超时伴随模拟器 Keystore generateKey 等待；本轮相同生产存储代码已通过真实设置 1/1。记录为环境现象，不据此改写或降级加密存储。
- 历史大字体截图若没有 systemFontScale 证据，不用于 Dialog 同条件前后对比；保留历史文件而不覆盖。

## 5. 知识沉淀

- [x] `.trellis/spec/backend/ui-consistency.md`：选中副标题、真实目标和样板隔离边界。
- [x] `.trellis/spec/backend/ui-catalog-verification.md` 样板专用验收规范：系统字体、Activity 生命周期、语义选择器、像素证据和未验证范围。
- [x] 更新队列可变行高规则，保留基于实测几何的测试。
- 本仓库不维护 Trellis 生成器的 `src/templates/markdown/spec/`，不创建无关模板；提交随本任务视觉确认后的提交计划执行，不单独穿插 bookkeeping 提交。

## 6. 2026-09-14 续跑发现与补强

1. **E：把 lazy item 不在视口误认成数据不存在。** 320×480dp 下清除歌曲筛选后，“深空尽头”尚未合成。测试先结束 IME，再通过竖向 LazyColumn 滚动到目标；不删断言，也不改生产筛选实现。
2. **B/D：Compose 语义 idle 与实际窗口帧不同步。** 交互截图曾抓到淡出的目录页，虽然回调断言已通过。样板/交互截图现在共享 `captureStableWindow()`；检查全窗口原图，不能只认 OK。
3. **E：像素守卫把背景当成图标。** 原守卫只累计亮/暗像素，相反主题背景也能通过。现在先检查背景多数像素明暗，再统计相反色图标；新增空白帧/相反主题回归。
4. **D：assertIsDisplayed 仅要求部分相交。** 一次向上滑动后播放按钮曾露出大半却仍通过。保留真实滑动，定位完整目标后比较 `boundsInWindow` 与原节点 size，真实点击并封存完整控制区图片。
5. **E：样例状态不是零散字段。** 空播放器不该保留普通样例的 76 秒。秒/毫秒位置按同一 `initialPosition` 初始化，新增 `emptyPlayerStartsAtZeroWithDisabledTransport` 断言零进度和禁用播放。
6. **B：IME visible 不等于布局稳定。** 新增回归改变测试时序后，原 IME 保存检查出现 1/17 失败。现同时观察 IME bottom 与实际表单视口高度稳定 500ms，再滚动到保存按钮并断言完整可见；保留真实键盘、坐标点击和回调检查，不靠盲目重跑。

失败批次 `continue-interaction-short`、`delivery-interaction` 及中间批次全部保留在构建报告中，不覆盖、不计作最终通过。原图复核也排除了“通过但抓错帧/半截按钮”的中间图片。最终验收只引用同一源码、APK 和测试包指纹的批次。

预防合同已写入共享 UI、队列几何和样板验收规范；本仓库没有 Trellis 生成器模板目录，不创建无关副本。

## 7. 最终复核

IME 弹出同步修正后，短屏曾在下一用例的迷你条检查中失败：父/子矩形跨窗口动画帧读取，而 IME hide 尚未完成。最终同时补齐收起稳定等待和同 UI 帧几何比较，保留原严格边界断言。`final-interaction` 与 `final-interaction-short` 均 17/17，真实设置 1/1；198 张最终截图按白名单封存。所有失败/中间批次排除在交付计数之外。
