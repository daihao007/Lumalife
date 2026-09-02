# Microservices Inventory

> 当前事实基线：`main@dc96528`。业务微服务数量为 4；backend BFF、frontend、数据库和 RabbitMQ 不计入业务微服务。

| 服务 | 类型 | 职责 | 端口 | 数据库 | 管理表 | 对外 API | 依赖服务 |
| --- | --- | --- | ---: | --- | --- | ---: | --- |
| identity-service | 有状态业务微服务 | 登录注册、Token、用户资料、地址 | 8081 | `life_assistant_identity` | 3 | 13 个 internal | 无同步下游 |
| merchant-service | 有状态业务微服务 | 分类、商家、商品/团购、收藏、客服会话、库存 | 8082 | `life_assistant_merchant` | 10 | 30 个 internal | order（支付状态查询）；RabbitMQ |
| order-service | 有状态业务微服务 | 购物车、订单、支付、券码、履约、评价、库存 Saga | 8083 | `life_assistant_order` | 10 | 21 个 internal | merchant（HTTP 兼容路径）；RabbitMQ |
| assistant-service | 无状态业务微服务 | AI provider 边界与确定性降级 | 8084 | 无 | 0 | 1 个 internal | Agnes provider（可降级） |
| backend | BFF/API facade，非业务微服务 | 鉴权、52 个公共 API、跨服务编排和响应兼容 | 8080 | `prod,remote` 无业务表 | 0 | 52 个 public | 四个业务微服务 |

## 划分理由

- identity：身份、Token 和地址具有独立安全边界与数据生命周期。
- merchant：经营内容、用户发现、客服会话和库存围绕商家聚合，写入频率与扩缩容需求独立。
- order：交易状态机、支付幂等、券码和评价共享订单不变量，需要独立事务与 Saga。
- assistant：外部 AI 延迟/失败模式与核心交易不同，无状态且可独立降级、扩展或替换 provider。

这不是按 Controller 或用例机械拆分。UC03~UC06 会跨 merchant/order 边界，证明服务边界围绕数据所有权与业务不变量，而非“一用例一服务”。

## 运行口径

- Compose 默认：一个 MySQL 实例、三个逻辑服务数据库。
- `docker-compose.physical-db.yml` 与 Kubernetes：三个服务数据库物理实例；Kubernetes 另保留 legacy MySQL 用于迁移/回滚。
- `monolith` profile 仍保留，不能宣称遗留单体代码已物理移除。
