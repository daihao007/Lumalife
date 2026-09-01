# 三服务拆分与渐进迁移

该目录承载 `identity-service`、`merchant-service`、`order-service` 三个独立 Spring Boot 入口。当前可运行的内部业务接口分别为 9、13、18 个：identity 负责账户/资料/地址，merchant 负责商家资料/商品/团购，order 负责购物车/订单详情/支付/履约/券码/评价。backend 仍提供 `/api/v1/**` 兼容入口；当前是共享 MySQL 上的逻辑服务表隔离，不是三个独立数据库。跨服务通常传递 ID 引用；外卖下单额外传递经身份边界校验的不可变地址快照，订单服务不会回写身份数据。

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
| identity-service | 8081 | `LUMALIFE_IDENTITY_HTTP_PORT` |
| merchant-service | 8082 | `LUMALIFE_MERCHANT_HTTP_PORT` |
| order-service | 8083 | `LUMALIFE_ORDER_HTTP_PORT` |

每个服务均提供：

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`

所有 `/internal/v1/**` 请求都必须携带 `X-Luma-Service-Token`，该值由 `LUMALIFE_INTERNAL_SERVICE_TOKEN` 配置；当前兼容适配器也接受 `X-Internal-Service-Token`。涉及用户或商家写入的请求还必须携带与路径/请求体一致的 `X-User-Id` 或 `X-Merchant-Id`，避免仅凭路径参数越权。

身份服务的当前独立边界如下：

- `identity-service` 独占账号、密码哈希、角色、会话和收货地址；状态文件只写入服务自有数据，`sessions` 的 key 为 access token 的 SHA-256 哈希，另存过期时间和撤销时间，不落盘原始 bearer token。
- `LUMALIFE_IDENTITY_BACKFILL_SOURCE_FILE` 仅在身份服务状态文件不存在时执行一次性历史快照回填；回填完成后由 `LUMALIFE_IDENTITY_STATE_FILE` 继续持有身份数据，写入采用临时文件加原子替换。
- 除服务令牌外，内部请求必须带 `X-Request-Id`、合法 W3C `traceparent` 和 `X-Caller-Service`。身份服务提供 `POST /internal/v1/tokens/introspect` 供令牌校验，并提供 `GET /internal/v1/users/{userId}/addresses/{addressId}` 供订单服务读取归属地址快照。
- 地址由身份服务校验用户归属、默认地址唯一性和每用户最多 5 条；BFF 只通过 `RemoteIdentityServicePort` 调用这些接口，不直接读写身份状态。

业务契约入口：

- identity-service：`/internal/v1/auth/*`、`/internal/v1/users/*`（9 个业务接口）
- merchant-service：`/internal/v1/merchants/*`、`/internal/v1/products/*`、`/internal/v1/deals/*`（13 个业务接口，含商家昵称更新）
- order-service：`/internal/v1/orders/*`（18 个业务接口）

单体网关默认保持 `monolith` 路由。Compose/Kubernetes 可打开三类远程路由，但身份远程路由和 backfill 完成标记默认均为 `false`；只有准备并挂载历史身份快照、验证账号与地址数量后，才能显式同时开启这两个开关。设置对应 `LUMALIFE_*_REMOTE_ENABLED` 和 `*_BACKFILL_COMPLETED` 为 `false` 即可逐服务回滚，并可通过 `GET /internal/migration/status` 查看实时路由。merchant 的分类、收藏、客服/AI 仍可能回落单体；筛选排序已由远程 adapter 和 `MerchantStore` 实现，团购在 JDBC 可用时持久化到 `group_deal`。库存预占/释放、跨服务事件总线尚未完成，不能据此宣称全量流量已切换。

业务迁移必须遵守 [`docs/28_D07服务接口数据归属与需求追溯.md`](../docs/28_D07服务接口数据归属与需求追溯.md) 的当前事实和 `docs/19_D04C微服务边界接口与数据归属初稿.md` 的目标所有权，不允许跨域 Repository、共享可写数据库表或复制单体业务代码形成双写。
