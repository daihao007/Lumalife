# 四服务拆分与渐进迁移

该目录承载 `identity-service`、`merchant-service`、`order-service`、`assistant-service` 四个独立 Spring Boot 入口。identity 负责账户/资料/地址，merchant 负责商家/商品/团购/库存/会话，order 负责购物车/订单详情/支付/履约/券码/评价，assistant 负责模型调用和 AI 降级。生产配置下前三个有状态服务分别连接 `life_assistant_identity`、`life_assistant_merchant`、`life_assistant_order`，assistant 无业务数据库；backend 仍提供 `/api/v1/**` 兼容入口。

旧的 `life_assistant` 库保留为 BFF 兼容和回滚源。Compose 默认文件仍提供兼容模式；生产物理隔离使用 `docker-compose.physical-db.yml`，先运行 `db-isolate-services` 按所有权导出，再启动服务。Kubernetes 默认清单则让三个有状态服务分别连接 `mysql-identity`、`mysql-merchant`、`mysql-order`，legacy `mysql` 只承载兼容源和 BFF。回填/隔离完成后，服务只写自己的数据库，不能跨域写旧库。

## 构建与启动

聚合验证四个服务：

```bash
mvn -B -ntp -f services/pom.xml verify
```

也可独立构建和启动任一服务：

```bash
mvn -B -ntp -f services/identity-service/pom.xml verify
mvn -f services/identity-service/pom.xml spring-boot:run
```

把路径中的 `identity-service` 替换为 `merchant-service` 或 `order-service` 即可。默认端口和覆盖变量如下：

| 服务 | 默认端口 | 环境变量 |
|---|---:|---|
| identity-service | 8081 | `LUMALIFE_IDENTITY_HTTP_PORT` |
| merchant-service | 8082 | `LUMALIFE_MERCHANT_HTTP_PORT` |
| order-service | 8083 | `LUMALIFE_ORDER_HTTP_PORT` |
| assistant-service | 8084 | `LUMALIFE_ASSISTANT_HTTP_PORT` |

每个服务均提供：

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`

所有 `/internal/v1/**` 请求都必须携带 `X-Luma-Service-Token`，该值由 `LUMALIFE_INTERNAL_SERVICE_TOKEN` 配置；当前兼容适配器也接受 `X-Internal-Service-Token`。涉及用户或商家写入的请求还必须携带与路径/请求体一致的 `X-User-Id` 或 `X-Merchant-Id`，避免仅凭路径参数越权。

业务契约入口：

- identity-service：`/internal/v1/auth/*`、`/internal/v1/users/*`（9 个业务接口）
- merchant-service：`/internal/v1/merchants/*`、`/internal/v1/products/*`、`/internal/v1/deals/*`（12 个业务接口）
- order-service：`/internal/v1/orders/*`（18 个业务接口）
- assistant-service：`POST /internal/v1/assistant/answer`（内部 AI 答案契约）

单体网关只有在显式 `monolith` profile 下才保持单体路由。Compose/Kubernetes 打开 identity、merchant、order、assistant 四类远程路由；生产配置不加载 `monolith`，因此 `DemoStore` 不会作为隐式回退创建，漏配远程实现会直接暴露为启动错误。通过 `GET /internal/migration/status` 查看实时路由。筛选排序已由远程 adapter 和 `MerchantStore` 实现，团购在 JDBC 可用时持久化到 `group_deal`。order-service 会保存商品名称、商家名称和配送地址快照，订单详情不依赖商家服务在线才能显示历史信息。

远程切流后，管理员看板由 backend 的 `RemoteMetricsServicePort` 聚合三个有状态服务的只读投影。order-service 的状态变更和库存命令在本地事务中写入 `service_outbox_event`；启用 `LUMALIFE_EVENTS_BROKER_ENABLED=true` 后由 RabbitMQ Topic Exchange 投递。生产支付链路按 `RESERVE_PENDING → RESERVED → CONFIRM_PENDING → CONFIRMED` 运行：merchant-service 以 `merchant_inbox_event` 幂等消费预占/确认/释放命令，再以 `merchant_outbox_event` 发布结果；order-service 以 `order_inbox_event` 幂等消费结果并推进 `order_inventory_saga`。同步 HTTP 预占仅在显式关闭 broker 的兼容模式使用。AI provider 和 deterministic fallback 已移入 assistant-service，backend 只负责上下文编排。

物理数据库隔离示例：

```bash
docker compose -f docker-compose.yml -f docker-compose.physical-db.yml up -d mysql mysql-identity mysql-merchant mysql-order rabbitmq
docker compose -f docker-compose.yml -f docker-compose.physical-db.yml --profile db-tools run --rm db-migrate
docker compose -f docker-compose.yml -f docker-compose.physical-db.yml --profile db-tools run --rm db-seed
docker compose -f docker-compose.yml -f docker-compose.physical-db.yml --profile physical-db run --rm db-isolate-services
docker compose -f docker-compose.yml -f docker-compose.physical-db.yml up -d identity-service merchant-service order-service assistant-service backend frontend
```

业务迁移必须遵守 [`docs/28_D07服务接口数据归属与需求追溯.md`](../docs/28_D07服务接口数据归属与需求追溯.md) 的当前事实和 `docs/19_D04C微服务边界接口与数据归属初稿.md` 的目标所有权，不允许跨域 Repository、共享可写数据库表或复制单体业务代码形成双写。
