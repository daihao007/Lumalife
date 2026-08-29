# 三服务拆分与渐进迁移

该目录承载 `identity-service`、`merchant-service`、`order-service` 三个独立 Spring Boot 入口。当前已完成第一阶段业务切片：身份服务提供登录、注册、用户资料和地址能力；商家服务提供商家/商品目录能力；订单服务提供创建、查询和取消能力。服务内部只维护自己的数据，跨服务只传递用户、商家和商品 ID 引用。

## 构建与启动

聚合验证三个服务：

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
| identity-service | 8081 | `IDENTITY_SERVICE_PORT` |
| merchant-service | 8082 | `MERCHANT_SERVICE_PORT` |
| order-service | 8083 | `ORDER_SERVICE_PORT` |

每个服务均提供：

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`

所有 `/internal/v1/**` 请求都必须携带 `X-Luma-Service-Token`，该值由 `LUMALIFE_INTERNAL_SERVICE_TOKEN` 配置。涉及用户或商家写入的请求还必须携带与路径/请求体一致的 `X-User-Id` 或 `X-Merchant-Id`，避免仅凭路径参数越权。

业务契约入口：

- identity-service：`/internal/v1/auth/*`、`/internal/v1/users/*`
- merchant-service：`/internal/v1/merchants/*`
- order-service：`/internal/v1/orders/*`

单体网关默认保持 `monolith` 路由。Compose 默认启动三个服务，并打开身份服务以及 merchant 的目录读取/商品写入、order 的订单查询/取消远程路由；设置对应 `LUMALIFE_*_REMOTE_ENABLED` 和 `*_BACKFILL_COMPLETED` 为 `false` 即可逐服务回滚。通过 `GET /internal/migration/status` 查看实时路由。merchant/order 仍有购物车、支付、团购、评价、券码等未迁移能力，这些能力会显式回落到单体，不能据此宣称全量流量已切换。

业务迁移必须遵守 `docs/19_D04C微服务边界接口与数据归属初稿.md` 冻结的所有权，不允许跨域 Repository、共享可写数据库表或复制单体业务代码形成双写。
