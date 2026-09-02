# Final Project Audit

## 1. Overall Result

**基本满足，但尚未完全满足课程最终验收要求。** 核心微服务、数据归属、52/52 公开 API 直接测试、CI 质量门、Kubernetes、HPA、故障和性能原始证据已形成，UI E2E 已修复并在 `9f0a755` 3/3 通过；需求、概要、详细设计及其三份正式 PDF 已校准。GitHub Project 的 50 项计划与 40 项既有 Done 已核验，但十日站会/截图原件不完整。当前阻断项是同一当前提交必要测试尚未全量重跑、管理原件缺口，以及答辩交付材料缺失。

课程项统计：✅ 16；⚠️ 10；❌ 1；❓ 0。

## 2. Requirement Audit

详见 [`requirements-checklist.md`](requirements-checklist.md)。本轮结论以课程任务书、当前代码/配置和真实执行结果为准，不使用旧文档自证。

## 3. Business Scenarios

正式业务场景 9 个，对应 UC01~UC09。用例字段完整，但教师/助教确认原件未入仓库。

## 4. Architecture

当前默认是 frontend + backend BFF + 4 个业务微服务。保留 monolith profile 作为基线、回滚和性能对照；不能描述为“完全移除单体”。

## 5. Microservices

业务微服务 4 个：identity、merchant、order、assistant。backend、frontend、MySQL、RabbitMQ 不计入业务微服务。

## 6. Data Ownership

identity 3 表、merchant 10 表、order 10 表、assistant 0 表。静态边界验证未发现跨服务直接 SQL；默认 Compose 为逻辑分库，Kubernetes/physical overlay 为物理实例分离。

## 7. Tests

- Unit/Component：108/108 LOCAL-VERIFIED
- Integration/API：94/94 LOCAL-VERIFIED；52 public API 均有直接 MockMvc 证据
- E2E：19 defined；12 pass、0 fail、7 NOT-RUN
- Total：221 defined；214 pass、0 fail、0 skipped、7 NOT-RUN

最新逐套本地结果 205：205 pass / 0 fail。backend 96 来自本次 API 覆盖工作树，services 71 与 Vitest 35 来自 `dc96528` 前序审计，UI 3/3 来自 `9f0a755`；9 个 Microservice E2E pass 来自旧提交既有证据。不得宣传“当前 HEAD 214/221 全通过”。

## 8. CI/CD

静态配置满足失败阻断：11 类验证 -> quality gate -> 6 镜像 -> Kind smoke -> acceptance deploy；ECS 仅消费成功 main push 的 bundle。UI E2E 修复后本地 3/3，但当前提交尚未以 GitHub Actions 重新验证。

## 9. Kubernetes

Kustomize PASS；Deployment 7、StatefulSet 4、Service 11、HPA 2、PVC 2、Namespace 1，无 Ingress。所有应用有 readiness/liveness/startup probes 和资源 requests/limits；HPA 仅 backend 与 merchant。

## 10. HPA Experiment

`EXISTING-EVIDENCE-VERIFIED`：merchant-service 在旧提交远端 K3s 观测 1->2->3->1、14,467 请求、0 错误。报告已校准为指向实际存在的 `04_tests/cloud-native/*-8c335eb*` 文件；本轮未重压服务器。

## 11. Fault Handling

`EXISTING-EVIDENCE-VERIFIED`：merchant 1->0->1、业务请求 200->503->200、backend readiness 保持 200。真实术语是 timeout、显式 503、故障隔离、AI fallback、消息幂等与 Saga；没有通用 circuit breaker、rate limit 或同步 retry。

## 12. Performance Benchmark

`⚠️ EXISTING-EVIDENCE-VERIFIED`：3 API × 2 模式 × 3 次 = 18 runs，180 请求、0 失败。小样本中单体三接口均更快。原始 commit 未记录、无当前成功 workflow artifact，CPU/内存只采 backend，未公平覆盖微服务全栈。

## 13. Documentation Consistency

本轮创建唯一事实源、测试 Inventory、课程 Checklist、当前架构/数据归属/通信三表、追溯矩阵、文档地图和当前微服务 Mermaid；修正根 README 的 Compose 服务口径、API/测试/Kubernetes关键数字。12/13/14 三份正式 Markdown 已重写为当前 BFF + 4 服务架构，对应 PDF 已重生成并完成 8/7/8 页视觉 QA。阶段文档和其余四份旧 PDF 被明确标为历史证据，不再作为当前事实。

仍未完成：其余四份测试/部署/用户等 PDF 的最终校准、迁移 27 张图的编号、逐份清除 71 个历史 Markdown 内的旧数字。为避免篡改历史实验记录，本轮通过文档地图进行权威性分层，而非删除旧结果。

## 14. Remaining Problems

### P0

1. 在当前 UI 修复提交上重跑必要测试，优先完成隔离 Microservice E2E 9 项并保留 commit-bound artifact。
2. 补齐 `05_management` 的 D03～D10 站会/看板原件并完成 D09/D10；补 `06_defense` 的 PPT、技术总结、个人权重、全员确认。

### P1

1. 将历史 CR/UC/SEQ 三套编号迁移到唯一 `REQ/UC/SYS-SEQ/COMP-SEQ/OBJ-SEQ`。
2. 当前 HEAD 的隔离 Microservice E2E 已提升为 P0，完成后再触发完整 GitHub Actions。
3. 重新跑性能 workflow，采集全部容器 CPU/内存，记录 commit 和 workflow run。
4. HPA 证据目录断链和 NIGHTLY 120/180 秒混写已校准；答辩前再做链接检查。
5. 为 RabbitMQ publisher 增加 confirm/return，为毒消息增加 DLQ；这属于实现增强，未在本轮审计擅自改代码。

### P2

1. 前端主 chunk 782.15 kB，可做路由级拆包。
2. 本机安装 actionlint 后复验工作流语法。
3. backend readiness 可纳入 merchant/order/assistant 或文档解释当前策略。

## Defense Risks

1. 为什么 4 个服务是业务边界而不是按 Controller 拆分？
2. 默认 Compose 是逻辑分库还是物理数据库？Kubernetes 为什么还有 legacy MySQL？
3. 52 个公共 API 如何证明全部有 API 测试？应展示 `testing/public-api-coverage-matrix.md` 与 28/28 ApiSecurity 测试结果，并说明 52/52 不等于穷举所有分支。
4. 为什么“总测试 221”却最新逐套只执行 205，且不是同一提交？必须解释既有证据、提交边界和 7 项 NOT-RUN。
5. UI E2E 原失败为何是注册路由竞态，修复后 CI 是否取得 3/3 和成功质量门证据？
6. HTTP 失败处理为什么不能称为熔断？因为未使用 circuit breaker，只实现 timeout/503/fallback/隔离。
7. HPA 为什么扩容？requests、CPU 60% target、min/max 和 1->2->3->1 原始 CSV 如何对应？
8. 性能为什么微服务更慢？应解释网络、BFF、序列化和 fan-out 开销，不得声称提升。
9. Outbox 没有 publisher confirm、队列没有 DLQ，会怎样处理丢消息/毒消息？
10. 为什么旧三层图里仍有 DemoStore？它们是 monolith-start 基线，不是默认生产链路。

## 答辩前重点准备的证据

- `docs/project-facts.md` 与本 Checklist。
- `ui-e2e/tests/core-flows.spec.ts` 的唯一用户/确定路由修复，以及 2026-09-03 本地 3/3 输出；成功运行默认不会保留失败 trace。
- `.github/workflows/ci.yml` 的 quality-gate、images、smoke、deploy needs 链。
- `k8s/hpa.yaml`、Kustomize 渲染结果、HPA raw CSV。
- `services/data-ownership.yml` 和 ownership shell gate。
- Microservice E2E summary JSON（说明 commit）。
- fault 目录的 before/during/after status 与 body。
- 性能 6 份 JSON/CSV、metadata、validator 输出，以及“单体更快”的诚实解释。
