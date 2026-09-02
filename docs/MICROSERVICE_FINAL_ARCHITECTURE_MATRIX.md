# LumaLife 微服务最终架构口径矩阵

> 更新日期：2026-09-02。本文以当前代码、`services/data-ownership.yml`、Microservice E2E 实测和 Kubernetes 清单为准；`DemoStore`、`monolith` profile 与 legacy `life_assistant` 仍保留为兼容、迁移和回滚能力，不是 `prod,remote` 运行时的业务事实源。

## 1. 运行模式

| 模式 | 配置入口 | 业务实现 | legacy DB 运行依赖 |
| --- | --- | --- | --- |
| compatibility / monolith | `spring.profiles.active=monolith` 或显式 compatibility 组合 | backend legacy implementation / `DemoStore` | 允许，用于兼容、本地演示、迁移和回滚 |
| prod,remote | `spring.profiles.active=prod,remote`；Compose 和 E2E 显式设置 | backend BFF + remote service adapters | 不访问 legacy 业务表，不参与 backend readiness/liveness |

普通配置的 default profile 已指向 `prod,remote`；生产容器仍通过显式环境变量固定为 `prod,remote`，避免静默回到完整单体模式。

## 2. Controller 与接口边界

以下数量只统计业务 controller 中的 `@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`、`@PatchMapping`，不把三个健康探针重复计入业务接口：

| 服务 | Controller | 根路径 | 业务 mapping 数 | 主要职责 |
| --- | --- | --- | ---: | --- |
| identity-service | `IdentityApi` | `/internal/v1` | 13 | 登录、注册、Token、账号、资料、地址 |
| merchant-service | `MerchantApi` | `/internal/v1` | 30 | 分类、商家、商品、团购、收藏、会话、库存 |
| order-service | `OrderApi` | `/internal/v1/orders` | 19 | 购物车、订单、支付、履约、券码、评价 |
| order-service | `MerchantReviewProjectionApi` | `/internal/v1/merchants` | 1 | 给 merchant-service 的评价只读投影 |
| assistant-service | `AssistantApi` | `/internal/v1/assistant` | 1 | AI 答复和确定性降级 |
| 每个业务服务 | `ProbeController` | `/actuator/health*` | 3 | health、liveness、readiness |

外部客户端继续使用 backend 的 `/api/v1/**` 兼容入口；backend 通过内部 token 调用上述内部接口，服务之间不共享 controller 或数据库实体。

## 3. 数据归属

| 服务 | 数据库 | 负责表/业务事实 | 跨服务引用 |
| --- | --- | --- | --- |
| identity-service | `life_assistant_identity` | `user_account`、`user_address`、`auth_session` | `merchantId` 只是 opaque reference |
| merchant-service | `life_assistant_merchant` | `category`、`merchant`、`merchant_catalog`、`group_deal`、`merchant_favorite`、`chat_message`、库存预留及 merchant inbox/outbox | `userId`、`orderId` 只是 opaque reference |
| order-service | `life_assistant_order` | `order_record`、订单行/事件、购物车、支付、券码、评价、order inbox/outbox、`order_inventory_saga` | `userId`、`merchantId`、`itemId` 只是 opaque reference |
| assistant-service | 无业务数据库 | 请求级上下文处理和 AI provider/fallback | 上下文由 backend 注入 |
| legacy source | `life_assistant` | 拆分前遗留数据、迁移/backfill/rollback 源 | `prod,remote` 不作为业务读写源 |

三项 service DB 的目标名称由 `application-prod.yml` 固定为 identity、merchant、order 对应数据库；环境变量缺失时不 fallback 到 `life_assistant`。

## 4. 跨服务调用

| 调用方 | 被调用方 | 协议/可靠性 | 适用口径 |
| --- | --- | --- | --- |
| backend | identity-service | HTTP internal contract + service token | `prod,remote` 的身份、资料、地址 |
| backend | merchant-service | HTTP internal contract + service token | `prod,remote` 的商家、商品、收藏、会话 |
| backend | order-service | HTTP internal contract + service token | `prod,remote` 的购物车、订单、支付、履约 |
| backend | assistant-service | HTTP internal AI contract + service token | `prod,remote` 的 AI 问答和降级 |
| order-service | merchant-service | RabbitMQ inventory command/result；transactional Outbox + Inbox；at-least-once + 幂等 | 正式 broker Saga |
| order-service → merchant-service | merchant-service | HTTP inventory fallback | 仅 broker 关闭的兼容/本地路径，不是正式 remote broker 路径 |
| merchant-service | order-service | RabbitMQ inventory result | 预占、确认、释放结果推进订单 Saga |

## 5. UC01～UC09 微服务 E2E 追溯

Microservice E2E 使用真实 HTTP 黑盒 runner，在 `prod,remote`、独立 Compose 项目、三项 service-owned DB、RabbitMQ 环境执行；当前 run 为 9/9 PASS。

| 用例 | 远程业务证据 | 结果 | 原始证据 |
| --- | --- | --- | --- |
| UC01 | identity-service 注册/登录/资料与地址 | PASS | `microservice-e2e-summary.json` / `raw.log` |
| UC02 | merchant-service 分类、搜索、详情 | PASS | 同上 |
| UC03 | order-service 跨商家订单、支付、库存 Saga | PASS；Saga `CONFIRMED`、payment `SUCCESS` | 同上 |
| UC04 | order-service 履约、收货、评价 | PASS | 同上 |
| UC05 | 团购订单、支付、券码 | PASS | 同上 |
| UC06 | 券码核销与异常路径 | PASS | 同上 |
| UC07 | merchant-service 经营内容维护 | PASS | 同上 |
| UC08 | USER / MERCHANT_AI / MERCHANT 会话角色链路 | PASS | 同上 |
| UC09 | 运营看板、用户/商家/订单/金额投影 | PASS | 同上 |

详细执行环境和 run ID 见 `docs/MICROSERVICE_E2E_REPORT.md`；单体 E2E 仍独立保留，不能与该远程链路证据混写。
