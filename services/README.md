# 三服务拆分与渐进迁移

该目录承载 `identity-service`、`merchant-service`、`order-service` 三个独立 Spring Boot 入口。三者均可独立构建、健康检查和部署：身份服务负责账户/会话/地址，商家服务负责分类、商家、商品、团购、收藏和客服会话，订单服务负责购物车、订单、支付、履约、券码和评价。生产模式使用 MySQL 持久化；服务通过 HTTP 内部契约交换 ID 和快照，网关对外仍保持 `/api/v1`。

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
| identity-service | 8081 | `LUMALIFE_IDENTITY_PORT` |
| merchant-service | 8082 | `LUMALIFE_MERCHANT_PORT` |
| order-service | 8083 | `LUMALIFE_ORDER_PORT` |

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

Compose/Kubernetes 默认启动网关和三个服务，并打开三组远程路由。网关对远程服务只保留兼容性回退开关；正常部署下身份、目录、收藏/客服、购物车、订单、支付、团购、履约、券码和评价均由对应服务处理。通过 `GET /internal/migration/status` 查看实时路由；发生故障时可将对应 `LUMALIFE_*_REMOTE_ENABLED` 或 `*_BACKFILL_COMPLETED` 设为 `false` 回滚单个边界。

首次使用已有数据库时，先执行 `docker compose --profile db-tools run --rm db-seed`，再执行 `docker compose --profile db-tools run --rm db-backfill-services`。新版本会自动应用 V007：头像字段升级为 `MEDIUMTEXT`，并将旧 `order_record` 迁移为带商品明细、商家快照和状态时间线的订单模型。

业务迁移必须遵守 `docs/19_D04C微服务边界接口与数据归属初稿.md` 冻结的所有权，不允许跨域 Repository 或复制单体业务代码形成双写。当前兼容窗口仍在同一 MySQL 实例内按服务表集合划分所有权；后续可按同一 HTTP 契约无损迁移到独立 schema/数据库。
