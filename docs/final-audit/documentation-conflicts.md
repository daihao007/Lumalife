# Documentation Conflict Registry

> 第二轮全局搜索日期：2026-09-02。旧数字保留在带日期的历史记录中，但不再具有“当前事实”权威性。

| 冲突主题 | 发现的历史口径 | 当前口径 | 处理状态 |
| --- | --- | --- | --- |
| 测试 | 40、46、59、68、74、78、92、218 等 | 221 defined；最新逐套本地结果 205/205，但跨明确记录的提交/工作树 | 已建立唯一 Inventory；历史报告加提示，旧执行数字保留 |
| 内部 API | 39、64 | 65（13+30+20+1+1） | 当前索引/矩阵/契约 README 已校准；阶段审计快照仍可保留 64 |
| Compose 服务 | 6 | 8 | 根 README 已修正 |
| 业务微服务 | 3 | 4 | 当前架构三表已修正；“三服务”文件名保留为历史 |
| 数据架构 | DemoStore/内存/单库为当前 | prod,remote 三服务 DB + RabbitMQ；monolith 仅基线/回滚 | 12/13/14 已按当前微服务重写；历史阶段文档保留提示 |
| HPA 时长 | 180 秒负载 | 120 秒负载 + 180 秒冷却 | HPA/NIGHTLY/计划已校准 |
| HPA 证据路径 | evidence 目录声称含 hash 文件 | hash 批次实际在 `04_tests/cloud-native/` | 报告已校准 |
| E2E 状态 | UI 3/3 历史记录、前序审计 UI 2/3 | 本次 UI 修复工作树完整运行 3/3；Microservice 9/9 仍为旧提交证据 | Inventory/README/审计已校准，当前提交全量 E2E 仍待复验 |
| Public API 测试 | 52 个公开接口缺一对一证明 | 52/52 均有直接 MockMvc API 测试 | 已建立 `testing/public-api-coverage-matrix.md` 并补 3 个缺口 case |
| 性能结论 | PASS/闭环 | 原始矩阵有效但 commit、workflow、全栈资源不足，状态 ⚠️ | Project Facts/审计已校准 |
| 图编号 | 历史 CR/UC/SEQ 混用 | `REQ/UC/SYS-SEQ/COMP-SEQ/OBJ-SEQ` | 已于 2026-09-03 迁移 27 mmd + 27 SVG 文件名与引用；图内容仍明确为单体基线 |
| PDF | 旧导出可用于当前 | 测试/部署/需求/概要/详细五份 PDF 已于 2026-09-03 重生成；其余两份仍为历史导出 | 状态 ⚠️；五份已完成 5/6/8/7/8 页视觉 QA，其余待校准 |

## 仍允许出现旧数字的位置

- 以日期、Issue、PR、NIGHTLY、evidence 命名的原始执行记录。
- `monolith-start` 基线文档与历史看板统计。
- 历史审计报告对当时缺口的陈述。

这些位置必须结合文件日期/顶部历史提示阅读。任何答辩汇总、README、PPT 或新报告只能引用 `docs/project-facts.md`。
