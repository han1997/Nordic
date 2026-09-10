# Bug Analysis: Windows 换行导致接口替换遗漏

## 1. Root Cause Category
- C（变更传播失败）与 E（隐含假设）：脚本假设旧 Kotlin 文件使用 LF，但 checkout 使用 CRLF。声明替换未命中，调用方和函数体已更新。

## 2. Why Fixes Failed
1. apply_patch 的批处理多行参数失败后改用字符串替换，未对每个文件统一规范化换行。
2. 只修复具体编译报错，后续共享顶栏和重试接口又重复了同类遗漏。

## 3. Prevention Mechanisms
| 优先级 | 措施 | 状态 |
|---|---|---|
| P0 | 读取后规范化换行、断言锚点、写回后搜索声明与调用 | 已执行 |
| P0 | 每个跨层接线批次先编译再继续 | 已执行 |
| P1 | HeaderAction 回调使用命名参数 | 已执行 |

## 4. Systematic Expansion
- 核对视频重试、字幕开关、临时倍速、播放器顶栏的参数声明及调用。
- 编译只能验证接口存在，不能替代字幕显示、来源隔离和持久化行为测试。

## 5. Knowledge Capture
- 已更新 backend/quality-guidelines.md；本项目无需要同步的对应生成模板。