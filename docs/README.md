# Documentation Map

## 当前权威文档

以下文件描述 `main@dc96528` 的当前事实：

- [`project-facts.md`](project-facts.md)：唯一关键数字与结论
- [`final-audit/requirements-checklist.md`](final-audit/requirements-checklist.md)：课程任务书逐项验收
- [`final-audit/traceability-matrix.md`](final-audit/traceability-matrix.md)：REQ/UC/设计/代码/测试追溯
- [`final-audit/final-project-audit.md`](final-audit/final-project-audit.md)：最终审计结论与风险
- [`final-audit/documentation-conflicts.md`](final-audit/documentation-conflicts.md)：第二轮冲突扫描与处理状态
- [`testing/test-inventory.md`](testing/test-inventory.md)：测试唯一口径
- [`testing/public-api-coverage-matrix.md`](testing/public-api-coverage-matrix.md)：52 个公开 API 的逐接口直接测试证据
- [`architecture/microservices-inventory.md`](architecture/microservices-inventory.md)、[`architecture/data-ownership.md`](architecture/data-ownership.md)、[`architecture/service-communication.md`](architecture/service-communication.md)：当前架构三表
- [`diagrams/final/current-architecture.mmd`](diagrams/final/current-architecture.mmd)：当前微服务总图

## 历史文档的使用规则

仓库中带日期、Issue、D02~D08、NIGHTLY、REMEDIATION、ROUND、AUDIT 等名称的文件是阶段性快照或实验记录。它们可以证明当时做过什么，但其中的 SHA、测试数量、服务数量和“当前状态”不得覆盖 `project-facts.md`。

`00~14`、`10组-*.pdf` 以及 `diagrams/d03|d04` 主要描述单体/兼容层设计。相关类仍在代码中用于 `monolith-start` 基线与回滚，但这些材料不是 `prod,remote` 微服务运行时的服务调用图。

## PDF 状态

现有 7 个 PDF 均为历史导出，未与本轮 Markdown 校准同步。课程要求可编辑文件、PDF 和模型源文件同时提交；因此当前 PDF 交付状态为 **❌ 未完成**。在答辩提交前，应从最终校准后的需求、概要、详细、测试、部署和用户文档重新导出 PDF，并做逐页视觉检查。不得把旧 PDF 当作当前版本。

## 事实来源顺序

课程任务书 > 当前代码 > 当前配置 > 本轮本地结果 > 只读服务器结果 > 已有原始证据 > 历史 docs。
