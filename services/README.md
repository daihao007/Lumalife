# 三服务拆分与渐进迁移

该目录承载 `identity-service`、`merchant-service`、`order-service` 三个独立 Spring Boot 入口。当前可运行的内部业务接口分别为 9、12、17 个：identity 负责账户/资料/地址，merchant 负责商家/商品/团购，order 负责购物车/订单/支付/履约/券码/评价。backend 仍提供 `/api/v1/**` 兼容入口；当前是共享 MySQL 上的逻辑服务表隔离，不是三个独立数据库。

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

所有 `/internal/v1/**` 请求都必须携带 `X-Luma-Service-Token`，该值由 `LUMALIFE_INTERNAL_SERVICE_TOKEN` 配置；当前兼容适配器也接受 `X-Internal-Service-Token`。涉及用户或商家写入的请求还必须携带与路径/请求体一致的 `X-User-Id` 或 `X-Merchant-Id`，避免仅凭路径参数越权。

业务契约入口：

- identity-service：`/internal/v1/auth/*`、`/internal/v1/users/*`（9 个业务接口）
- merchant-service：`/internal/v1/merchants/*`、`/internal/v1/products/*`、`/internal/v1/deals/*`（12 个业务接口）
- order-service：`/internal/v1/orders/*`（17 个业务接口）

单体网关默认保持 `monolith` 路由。Compose/Kubernetes 可打开三类远程路由；设置对应 `LUMALIFE_*_REMOTE_ENABLED` 和 `*_BACKFILL_COMPLETED` 为 `false` 即可逐服务回滚。通过 `GET /internal/migration/status` 查看实时路由。merchant 的分类、收藏、客服/AI 和部分筛选排序仍可能回落单体；团购持久化、库存预占/释放、完整多商品订单和事件总线也尚未完成，不能据此宣称全量流量已切换。

业务迁移必须遵守 [`docs/28_D07服务接口数据归属与需求追溯.md`](../docs/28_D07服务接口数据归属与需求追溯.md) 的当前事实和 `docs/19_D04C微服务边界接口与数据归属初稿.md` 的目标所有权，不允许跨域 Repository、共享可写数据库表或复制单体业务代码形成双写。
