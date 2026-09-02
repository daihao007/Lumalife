# LumaLife 项目技术总结

## 1 项目概述

LumaLife 是面向本地生活场景的综合服务平台，覆盖用户注册登录、商家发现、收藏、购物车、外卖与团购订单、支付、履约、评价、客服和平台运营指标。正式范围采用 REQ01～REQ09 与 UC01～UC09。

当前默认运行架构为 React 前端、Spring Boot backend BFF、Identity、Merchant、Order、Assistant 四个业务微服务、MySQL 和 RabbitMQ。`monolith` profile 仍作为基线、回滚和性能对照，不得描述为已经完全移除。

## 2 架构与服务边界

浏览器只访问 backend BFF 暴露的 52 个公开业务 API。BFF 负责统一鉴权、公开契约和跨服务编排，并通过 65 个内部 API 与四个业务微服务通信。

| 服务 | 主要职责 | 数据所有权 |
|---|---|---|
| Identity | 注册、登录、身份、资料和地址 | 3 张表 |
| Merchant | 商家、商品、库存、团购、优惠券和商家会话 | 10 张表 |
| Order | 购物车、订单、支付、配送、券码、评价和 Saga | 10 张表 |
| Assistant | 客服建议和辅助能力 | 无业务数据库 |

三个有状态服务各自拥有数据库，共 23 张业务表。服务不得跨库直接读写；同步查询和命令使用 HTTP，库存与订单跨服务状态通过 RabbitMQ Outbox/Inbox/Saga 协调。

## 3 关键工程设计

### 3.1 稳定公开契约

前端只依赖 BFF 的 `/api/v1/**` 契约，服务拆分不要求浏览器感知内部服务地址。Remote Port 将 BFF 应用服务与内部 HTTP 客户端隔离，兼容层只用于明确的单体基线或回退场景。

### 3.2 数据一致性

单个服务内使用本地事务；跨服务不使用分布式数据库事务。Order 创建业务状态与 Outbox 事件，消费者通过 Inbox 去重，Saga 记录步骤与补偿状态。当前尚未补齐 RabbitMQ publisher confirm/return 和 DLQ，因此不能把消息可靠性描述为完全闭环。

### 3.3 安全与故障处理

Spring Security 和服务层资源归属共同限制用户、商家管理员和平台管理员的访问范围。同步服务调用配置连接/读取超时，关键依赖不可用时返回显式 503；Assistant 可提供确定性 fallback。当前没有通用 circuit breaker、rate limit 或同步自动重试。

## 4 测试与质量证据

正式测试资产共 221：Unit/Component 108、Integration/API 94、E2E 19。52 个公开 API 均有至少一个直接 MockMvc 测试引用，但该矩阵不代表穷举所有参数组合和异常分支。

当前可陈述结果：

- backend 全量测试 96/96 PASS；
- UI Playwright 3/3 PASS，客服流程修复后连续两轮全通过；
- services 71/71 和 Vitest 35/35 来自前序明确记录的提交；
- Microservice E2E 当前未运行，状态为 `NOT-RUN`；旧提交 9/9 是既有证据；
- legacy API E2E 7 项因有状态写入未重跑，保持 `NOT-RUN`。

因此不能宣传“当前 HEAD 221/221 全部通过”。

## 5 容器、流水线与 Kubernetes

Compose 默认运行 8 个服务：frontend、backend、4 个业务微服务、MySQL、RabbitMQ。CI 质量门按测试、镜像、smoke 和部署依赖关系阻断失败路径，并使用 `sha-*` 版本镜像。

Kubernetes 静态清单共 27 个对象：7 Deployment、4 StatefulSet、11 Service、2 HPA、2 PVC、1 Namespace。所有应用工作负载具备 readiness/liveness/startup probes 和资源 requests/limits；HPA 仅覆盖 backend 与 merchant-service。

## 6 实验结论

### 6.1 HPA

既有提交 `8c335eb` 的远端 K3s 原始 CSV 显示 merchant-service 副本 `1 -> 2 -> 3 -> 1`，20 workers、120 秒负载、14,467 请求、0 错误。该结果不是当前提交重跑。

### 6.2 故障处理

既有证据显示 merchant-service `1 -> 0 -> 1`，商家请求 `200 -> 503 -> 200`，backend readiness 保持 200。该结果说明超时、503 映射和故障隔离，不证明存在通用熔断器。

### 6.3 性能

性能矩阵为 3 个 API、2 种模式、每个组合 3 次，共 18 runs、180 请求、0 失败。该小样本中单体三个接口均更快，原因可能包括 BFF、网络、序列化和跨服务 fan-out 开销。现有证据未记录原始 commit，且资源采样只覆盖 backend，因此状态为 `⚠️ EXISTING-EVIDENCE-VERIFIED`。

## 7 项目管理

公开 GitHub Project #2 包含 D01～D10 共 50 个任务，五名成员各 10 项、每项 7 计划工时。2026-09-03 只读核验时 D01～D08 共 40 项 Done，D09/D10 共 10 项 Todo。

管理证据仍不完整：仓库每日截图只覆盖 D01/D02；已勾选站会记录仅 9/50，覆盖 D01、D02、D04、D05。计划工时不等于实际工时，缺失的历史站会和截图不得倒填。

## 8 项目成果与局限

项目已形成当前微服务代码、数据所有权、公开 API 测试矩阵、容器/CI/Kubernetes 配置、HPA/故障/性能证据及需求/概要/详细设计文档。

答辩前仍需：

1. 在用户允许后对当前提交运行隔离 Microservice E2E，并保存 commit-bound artifact；
2. 补齐 D03～D10 管理原件并真实完成 D09/D10；
3. 由成员填写贡献权重并完成全员确认；
4. 补 RabbitMQ publisher confirm/return 与 DLQ，或在答辩中明确其为风险；
5. 在相同提交和公平资源口径下重跑完整性能实验。

## 9 结论

LumaLife 已完成从单体基线到 BFF + 4 业务微服务的主要工程改造，并形成较完整的测试、部署和实验链路。项目的答辩重点不是宣称所有指标完美，而是展示业务边界、数据一致性、质量门禁和可核验证据，同时诚实说明当前 `NOT-RUN`、`UNVERIFIED` 和设计增强项。
