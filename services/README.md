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

业务契约入口：

- identity-service：`/internal/v1/auth/*`、`/internal/v1/users/*`
- merchant-service：`/internal/v1/merchants/*`
- order-service：`/internal/v1/orders/*`

单体网关默认保持 `monolith` 路由；设置 `LUMALIFE_IDENTITY_REMOTE_ENABLED=true` 并配置 `IDENTITY_SERVICE_URL` 后，身份登录、令牌校验、注册、资料和地址请求切换到 identity-service。通过 `GET /internal/migration/status` 可查看当前路由，出现问题时把对应开关改回 `false` 即可回滚。

业务迁移必须遵守 `docs/19_D04C微服务边界接口与数据归属初稿.md` 冻结的所有权，不允许跨域 Repository、共享可写数据库表或复制单体业务代码形成双写。
