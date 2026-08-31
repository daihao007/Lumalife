# 三服务拆分代码骨架

该目录承载 `identity-service`、`merchant-service`、`order-service` 三个独立 Spring Boot 入口。目前只提供构建、配置和健康检查骨架，不迁移业务逻辑，也不替换 `backend/` 单体基线。

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

业务迁移必须遵守 `docs/19_D04C微服务边界接口与数据归属初稿.md` 冻结的所有权，不允许跨域 Repository、共享可写数据库表或复制单体业务代码形成双写。
