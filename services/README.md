# 三服务拆分与渐进迁移

该目录承载 `identity-service`、`merchant-service`、`order-service` 三个独立 Spring Boot 入口。当前已完成第一阶段业务切片：身份服务提供登录、注册、用户资料和地址能力；商家服务提供商家/商品目录能力；订单服务提供创建、查询和取消能力。服务内部只维护自己的数据，跨服务通常传递 ID 引用；外卖下单额外传递经身份边界校验的不可变地址快照，订单服务不会回写身份数据。

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

身份服务的当前独立边界如下：

- `identity-service` 独占账号、密码哈希、角色、会话和收货地址；状态文件只写入服务自有数据，`sessions` 的 key 为 access token 的 SHA-256 哈希，另存过期时间和撤销时间，不落盘原始 bearer token。
- `LUMALIFE_IDENTITY_BACKFILL_SOURCE_FILE` 仅在身份服务状态文件不存在时执行一次性历史快照回填；回填完成后由 `LUMALIFE_IDENTITY_STATE_FILE` 继续持有身份数据，写入采用临时文件加原子替换。
- 除服务令牌外，内部请求必须带 `X-Request-Id`、合法 W3C `traceparent` 和 `X-Caller-Service`。身份服务提供 `POST /internal/v1/tokens/introspect` 供令牌校验，并提供 `GET /internal/v1/users/{userId}/addresses/{addressId}` 供订单服务读取归属地址快照。
- 地址由身份服务校验用户归属、默认地址唯一性和每用户最多 5 条；BFF 只通过 `RemoteIdentityServicePort` 调用这些接口，不直接读写身份状态。

业务契约入口：

- identity-service：`/internal/v1/auth/*`、`/internal/v1/users/*`
- merchant-service：`/internal/v1/merchants/*`
- order-service：`/internal/v1/orders/*`

单体网关默认保持 `monolith` 路由。Compose/Kubernetes 会启动 identity-service，但身份远程路由和 backfill 完成标记默认均为 `false`；只有准备并挂载历史身份快照、验证账号与地址数量后，才能显式同时开启这两个开关。merchant/order 的迁移开关维持各自既有配置。通过 `GET /internal/migration/status` 查看实时路由，任一远程服务均可将对应 `LUMALIFE_*_REMOTE_ENABLED` 设为 `false` 回滚。

业务迁移必须遵守 `docs/19_D04C微服务边界接口与数据归属初稿.md` 冻结的所有权，不允许跨域 Repository、共享可写数据库表或复制单体业务代码形成双写。
