# Project Facts

> 最终答辩唯一事实源。更新日期：2026-09-03；当前审计基线：`3696741`。若本文件与带日期的阶段报告冲突，以本文件及其列出的原始证据为准。历史报告不得改写为“本次执行”。

## Project Version

- 当前分支：`main`
- 当前审计基线：`3696741`（同步最新 `main` 后的 R26 工具审计输入基线）
- 单体基线标签：`monolith-start`（`3eb8f44`）
- Java 项目声明版本：17；本轮本地测试实际 JVM：21.0.7
- 前端：React 18 / TypeScript / Vite；本轮本地 Node：24.15

## Business Scenarios

正式范围为 **9 个业务场景 / 9 个用例**，统一编号 `REQ01~REQ09` 与 `UC01~UC09`：

1. UC01 用户注册/登录并维护资料与地址
2. UC02 搜索、筛选、查看详情与收藏并形成购买决策
3. UC03 跨商家购物车拆单、下单、支付或取消
4. UC04 商家履约、用户收货并评价
5. UC05 购买团购套餐、支付并获得券码
6. UC06 商家核销券码并处理无效、重复和越权
7. UC07 商家维护商品/团购并发布到用户端
8. UC08 用户、商家与 AI 客服沟通闭环
9. UC09 管理员查看运营指标与系统状态

完整用例字段见 [`16_业务场景用例清单.md`](16_业务场景用例清单.md)。仓库中未找到教师/助教对最终清单的确认原件，因此“清单已确认”仍为 `UNVERIFIED`。

## Architecture

当前默认运行模式是：

```text
frontend -> backend BFF/API facade -> identity / merchant / order / assistant
                                      order <-> merchant via RabbitMQ Saga
```

- 架构类型：微服务运行模式，同时保留 `monolith` profile 作为基线、回滚和性能对照。
- `backend` 是 BFF/API facade，不计入业务微服务。
- 不存在独立 API Gateway、注册中心或配置中心。
- 默认 Compose 使用一个 MySQL 实例中的三个服务数据库；Kubernetes 清单同时部署 legacy MySQL 与三个服务数据库实例。

## Business Microservices

业务微服务共 **4 个**：`identity-service`、`merchant-service`、`order-service`、`assistant-service`。

## Services

- 后端应用服务：5（backend BFF + 4 个业务微服务）
- 应用部署单元：6（上述 5 个 + frontend）
- Compose 默认运行服务：8（6 个应用 + MySQL + RabbitMQ）
- Kubernetes 工作负载控制器：11（7 Deployment + 4 StatefulSet）

## Ports

| 组件 | 端口 | 说明 |
| --- | ---: | --- |
| frontend | 5173（Compose host）/ 80（K8s Service） | 用户界面 |
| backend | 8080 | 52 个公共业务 API 的统一入口 |
| identity-service | 8081 | 内部身份/资料 API |
| merchant-service | 8082 | 内部商家/目录/库存/会话 API |
| order-service | 8083 | 内部购物车/订单/支付/评价 API |
| assistant-service | 8084 | 内部 AI 答复 API |
| MySQL | 3306 | 数据存储 |
| RabbitMQ | 5672 / 15672 | AMQP / 管理端口 |

## Databases

- 服务业务数据库：3 个：`life_assistant_identity`、`life_assistant_merchant`、`life_assistant_order`
- `assistant-service` 无业务数据库。
- `life_assistant` 是 legacy/迁移/回滚源，不是 `prod,remote` 的业务事实源。

## Data Ownership

| 服务 | 表数 | 主要表 |
| --- | ---: | --- |
| identity-service | 3 | `user_account`、`user_address`、`auth_session` |
| merchant-service | 10 | `category`、`merchant`、`merchant_catalog`、`group_deal`、`merchant_favorite`、`chat_message`、库存与 inbox/outbox 表 |
| order-service | 10 | `order_record`、订单行/事件、购物车、支付、券码、评价、inbox/outbox、Saga 表 |
| assistant-service | 0 | 无 |

静态边界脚本未发现三个有状态服务跨服务直接 SQL 访问；详细清单见 [`architecture/data-ownership.md`](architecture/data-ownership.md)。

## APIs

- 公共业务 API：**52**（仅 backend `/api/v1` 显式 method mappings；不计 Actuator/Swagger/internal）
- 内部业务 API：**65**（identity 13、merchant 30、order 21、assistant 1）
- 52 个公共接口均有直接 `MockMvc` API 测试，逐项映射见 [`testing/public-api-coverage-matrix.md`](testing/public-api-coverage-matrix.md)。矩阵证明每个公开映射至少有直接 HTTP 断言；不把它夸大为所有参数组合和异常分支的穷举覆盖。

## Tests

分类规则：非 `@SpringBootTest` Java + 全部 Vitest 计 Unit/Component；`@SpringBootTest` Java 计 Integration/API；三个 E2E runner 的场景计 E2E。

| 类型 | 源码 Case | 本轮/证据 Passed | Failed | Skipped | NOT-RUN |
| --- | ---: | ---: | ---: | ---: | ---: |
| Unit/Component | 108 | 108 | 0 | 0 | 0 |
| Integration/API | 94 | 94 | 0 | 0 | 0 |
| E2E | 19 | 12 | 0 | 0 | 7 |
| Total | **221** | **214** | **0** | **0** | **7** |

证据分层：最新逐套本地结果为 205 次执行（205 pass / 0 fail），但 backend 96 来自本次 52 API 覆盖工作树，services 71 与 Vitest 35 来自 `dc96528` 前序审计，UI E2E 3/3 来自 `9f0a755`，尚不是同一当前提交的全量重跑。旧提交 `8c335eb` 的 Microservice E2E 9/9 为 `EXISTING-EVIDENCE-VERIFIED`；legacy API E2E 7 项因有状态写入未重跑，记 `NOT-RUN`。6 个 Shell 验证套件另列，不并入 221：前序安全执行 5 个均通过，MySQL contract 因会写业务数据而未执行。

UI E2E 当前结果：客服测试改为注册唯一用户、等待 `#/profile` 确认注册完成后进入首页，并按唯一昵称定位商家会话；2026-09-03 完整 Playwright 3/3 通过。当前没有已执行测试失败，但当前提交的其余必要测试仍待重跑。

## Kubernetes

`kubectl kustomize k8s` 本轮渲染通过，静态清单共 27 个对象：

| 资源 | 数量 |
| --- | ---: |
| Namespace | 1 |
| Deployment | 7 |
| StatefulSet | 4 |
| Service | 11 |
| HPA | 2 |
| PVC | 2 |
| Ingress | 0 |
| 静态 ConfigMap / Secret | 0 / 0 |

部署脚本运行时另创建 2 Secret 和 2 ConfigMap；它们不计入静态 27。声明式最低 Pod 基线为 **12**（frontend 2，其余 Deployment/StatefulSet 各 1）。HPA 仅覆盖 backend 和 merchant-service，均为 min 1 / max 3 / CPU 60%。

## HPA Experiment

状态：`EXISTING-EVIDENCE-VERIFIED`。提交 `8c335eb`、镜像 `sha-8c335eb` 的远端 K3s 原始 CSV 证明 merchant-service `1 -> 2 -> 3 -> 1`；20 workers、120 秒负载、14,467 请求、0 错误。该实验不是当前接力基线的本轮重跑。

## Fault Handling

- 同步 HTTP：显式 connect/read timeout；核心服务不可用映射为明确 503，不存在通用 retry/circuit breaker/rate limit/bulkhead。
- AI provider：确定性 fallback。
- 库存链路：RabbitMQ Outbox/Inbox、幂等消费、Saga 状态与补偿/人工核验。
- 已有故障证据：merchant-service `1 -> 0 -> 1`，商家请求 `200 -> 503 -> 200`，backend readiness 全程 200。

不得把 timeout 或 503 映射写成 circuit breaker；不得把配置切回 monolith 写成自动 fallback。

## Performance Benchmark

- 接口：3（merchant search、categories、merchant detail）
- 模式：2（monolith、microservices）
- 重复：每个模式/接口 3 次
- Performance Runs：**18**；每次 10 请求，并发 4；总请求 180，失败 0
- 本批小样本中单体三个接口均更快；不得表述为“微服务性能提升”。
- 状态：`⚠️ EXISTING-EVIDENCE-VERIFIED`。原始 commit 未记录、没有当前成功远端 workflow artifact，且资源 CSV 只覆盖 backend，不是微服务全栈 CPU/内存公平口径。

## Evidence Status

- LOCAL-VERIFIED：本次 API 覆盖工作树的 backend 96/96；前序 `dc96528` 的 services 71/71、Vitest 35/35、frontend build、Compose config、Kustomize render、性能矩阵只读校验、5 个静态 Shell 套件；`9f0a755` 的 UI E2E 3/3。
- SERVER-VERIFIED：0（本轮未连接服务器）。
- EXISTING-EVIDENCE-VERIFIED：Microservice E2E 9/9、HPA、故障实验、性能原始数据。
- UNVERIFIED / NOT-RUN：当前 HEAD 的远程 Microservice E2E、legacy API E2E 7 项、教师确认、管理原件、个人权重与全员确认。

## AI, Open Source and Secret Review

- 开发过程使用 Codex 辅助代码、测试、文档和审计；人工仍负责复核、验收和本人签署。该用途与 `assistant-service` 的 Agnes 运行时 AI 能力分开说明。
- 2026-09-03 对当前受跟踪文本与 Git 文本历史进行高置信静态特征扫描，真实凭据命中为 0；宽泛候选经复核为变量名、占位符或测试/演示值。
- Gitleaks 8.30.1 对 `3696741` 的 616 个受跟踪文件快照和全部 233 个 Git 提交均为 0 finding；Syft 1.51.0 已生成 SPDX 2.3 SBOM，共 287 个包条目。
- 许可证声明为 MIT 224、ISC 18、BSD-3-Clause 3、Apache-2.0 2、MIT AND ISC 1、CC-BY-4.0 1、NOASSERTION 38。已声明项未出现 GPL/AGPL/LGPL/EPL/MPL，但仓库无根 LICENSE，Maven 传递依赖、容器镜像和漏洞扫描未闭环。
- R26 状态为 `⚠️ SECRET-SCAN-PASS / SBOM-GENERATED / LICENSE-PARTIAL / VULNERABILITY-NOT-RUN`。详见 `docs/security/gitleaks-sbom-license-audit-2026-09-03.md`。

## Formal Documents and PDFs

- `12_软件需求规格说明书.md`、`13_概要设计说明书.md`、`14_详细设计说明书.md` 已校准为当前 BFF + 4 业务微服务版本。
- 三份对应正式 PDF 已于 2026-09-03 重新生成，页数分别为 8、7、8；文本提取无空页，Poppler 全页渲染后完成视觉检查。
- `07_测试报告.md` 已重写为当前测试资产和证据分层口径；对应正式 PDF 已于 2026-09-03 重新生成，共 5 页，文本提取无空页，Poppler 全页渲染后完成视觉检查。
- `08_部署文档.md` 已重写为当前 8 服务 Compose、27 对象 Kubernetes、CI/CD 与证据边界口径；对应正式 PDF 已于 2026-09-03 重新生成，共 6 页，文本提取无空页，Poppler 全页渲染后完成视觉检查。
- `09_用户手册.md` 已校准为当前角色路由、业务操作、演示账号和默认微服务持久化口径；对应正式 PDF 已于 2026-09-03 重新生成，共 12 页、A4、12/12 页文本非空并嵌入 8/8 张截图，Poppler 全页渲染后完成视觉检查。
- `10组-软件开发计划书.md` 已从历史 PDF 重建为当前可编辑计划，区分 350 计划工时、快照状态和未验证实际投入；对应正式 PDF 已于 2026-09-03 重新生成，共 10 页、A4、10/10 页文本非空，Poppler 全页渲染后完成视觉检查。
- 27 个历史 Mermaid 三层图源与 27 个 SVG 已统一为 `UCxx-{SYS|COMP|OBJ}-SEQxx`，引用无旧文件名残留；其内容仍是 `monolith-start`/兼容基线。当前微服务逐用例三层图另有 27 个 Mermaid 源和 27 个 SVG，入口为 [`diagrams/final/use-cases/`](diagrams/final/use-cases/)，不与历史基线混称。
- 七份正式 PDF 均已有当前可编辑源并完成逐页视觉 QA；PDF 交付状态为 `✅ CURRENT`。

## Project Management Evidence

- 2026-09-03 通过只读 `gh` 命令核验公开 GitHub Project #2：D01～D10 共 50 个任务，五名负责人各 10 项，每项 7 计划工时，即每人 70 计划工时、合计 350 人时。
- Project 状态为 D01～D08 共 40 `Done`，D09～D10 共 10 `Todo`。计划工时不得表述为已完成实际工时。
- 仓库只找到 D01/D02 的 4 张每日看板/统计截图；Issue 中已勾选站会记录为 9/50，覆盖 D01、D02、D04、D05。十日全员站会和每日截图状态为 `PARTIAL/UNVERIFIED`。
- 详细证据和缺口见 `docs/05_management/ten-day-evidence-matrix.md`。

## Defense Materials

- `docs/06_defense/LumaLife-最终答辩.pptx` 为当前可编辑答辩稿，共 13 页；PPTX 包结构完整，13 页均含 `[Sources]` 演讲者备注。2026-09-03 已渲染 13/13 页并逐页视觉检查，导出布局检查为 13 页、0 个越界元素。
- `docs/06_defense/technical-summary.md` 已按本文件口径生成；答辩数字、测试提交边界和性能结论均保留限定条件。
- `docs/06_defense/contribution-signoff.md` 仅为待填模板。个人权重、签字/电子确认和最终演示分工仍为 `UNVERIFIED`，不得由审计者代填或代签。
- 答辩交付状态为 `⚠️ PARTIAL`：可编辑 PPT 与技术总结已完成，成员确认和管理原件仍未闭环。
