# Documentation Map

## 当前权威文档

以下文件描述 `main` 当前验收基线的事实：

- [`project-facts.md`](project-facts.md)：唯一关键数字与结论
- [`final-audit/requirements-checklist.md`](final-audit/requirements-checklist.md)：课程任务书逐项验收
- [`final-audit/traceability-matrix.md`](final-audit/traceability-matrix.md)：REQ/UC/设计/代码/测试追溯
- [`final-audit/final-project-audit.md`](final-audit/final-project-audit.md)：最终审计结论与风险
- [`final-audit/documentation-conflicts.md`](final-audit/documentation-conflicts.md)：第二轮冲突扫描与处理状态
- [`testing/test-inventory.md`](testing/test-inventory.md)：测试唯一口径
- [`testing/public-api-coverage-matrix.md`](testing/public-api-coverage-matrix.md)：52 个公开 API 的逐接口直接测试证据
- [`05_management/README.md`](05_management/README.md)：项目管理材料入口、五人分工与真实证据边界
- [`05_management/ten-day-evidence-matrix.md`](05_management/ten-day-evidence-matrix.md)：D01～D10 任务、站会和截图逐日缺口矩阵
- [`06_defense/README.md`](06_defense/README.md)：最终答辩交付包入口、演示顺序与提交前清单
- [`06_defense/technical-summary.md`](06_defense/technical-summary.md)：当前架构、测试、云原生与证据边界技术总结
- [`06_defense/contribution-signoff.md`](06_defense/contribution-signoff.md)：个人权重与全员确认模板（待成员本人填写和签署）
- [`architecture/microservices-inventory.md`](architecture/microservices-inventory.md)、[`architecture/data-ownership.md`](architecture/data-ownership.md)、[`architecture/service-communication.md`](architecture/service-communication.md)：当前架构三表
- [`diagrams/final/current-architecture.mmd`](diagrams/final/current-architecture.mmd)：当前微服务总图
- [`diagrams/final/use-cases/README.md`](diagrams/final/use-cases/README.md)：UC01~UC09 当前微服务逐用例三层图（27 个 Mermaid 源 + 27 个 SVG）
- [`10_AI使用说明.md`](10_AI使用说明.md)：AI 辅助范围、运行时 AI、开源与敏感信息边界
- [`security/secret-scan-2026-09-03.md`](security/secret-scan-2026-09-03.md)：当前树与 Git 历史的静态 Secret 审计结果及限制
- [`security/gitleaks-sbom-license-audit-2026-09-03.md`](security/gitleaks-sbom-license-audit-2026-09-03.md)：专用 Gitleaks 扫描、SPDX SBOM 与许可证缺口
- [`security/lumalife-3696741.spdx.json`](security/lumalife-3696741.spdx.json)：绑定 `3696741` 的 Syft SPDX 2.3 SBOM
- [`security/vulnerability-scan-2026-09-03.md`](security/vulnerability-scan-2026-09-03.md)：绑定同一 SBOM 的 Grype 扫描结果、11 个开放发现与覆盖边界

## 历史文档的使用规则

仓库中带日期、Issue、D02~D08、NIGHTLY、REMEDIATION、ROUND、AUDIT 等名称的文件是阶段性快照或实验记录。它们可以证明当时做过什么，但其中的 SHA、测试数量、服务数量和“当前状态”不得覆盖 `project-facts.md`。

`07_测试报告.md`、`08_部署文档.md`、`09_用户手册.md`、`10组-软件开发计划书.md`、`12_软件需求规格说明书.md`、`13_概要设计说明书.md`、`14_详细设计说明书.md` 及其七份对应 PDF 已更新为当前微服务验收版。其余阶段文档及 `diagrams/d03|d04` 仍主要描述单体/兼容层或历史阶段；相关类仍可用于 `monolith-start` 基线与回滚，但不能据此说明 `prod,remote` 的当前服务调用关系。

## PDF 状态

七份正式 PDF（测试报告、部署文档、用户手册、软件开发计划书、需求、概要、详细设计）已于 2026-09-03 从当前 Markdown 重新生成，并完成 5/6/12/10/8/7/8 页逐页视觉检查。用户手册 PDF 为 A4、12/12 页文本非空并嵌入 8/8 张操作截图；开发计划书 PDF 为 A4、10/10 页文本非空。正式 PDF 交付状态为 **✅ 当前版本已生成并完成视觉 QA**。

## 事实来源顺序

课程任务书 > 当前代码 > 当前配置 > 本轮本地结果 > 只读服务器结果 > 已有原始证据 > 历史 docs。
