# 微服务整改第一轮报告

## 结论

本轮以《软件工程基础实践》第二阶段验收为目标，完成了微服务运行模式、legacy DB 运行隔离、服务数据库 fail-closed、Kubernetes 清单统一，以及库存 Saga `confirm failure` 最小补偿闭环。

结论是：核心微服务划分和数据归属已满足本轮课程验收口径；项目仍然不是严格意义上的“全量微服务化”。`DemoStore`、`monolith` profile 和旧 `life_assistant` 数据库仍作为兼容、迁移和回滚能力保留，不能表述为单体代码和旧库已经全部移除。

## 1. 修改了什么

### 1.1 remote 与 compatibility 模式

- backend 默认 profile 从 `monolith` 调整为 `prod,remote`。
- 增加 `compatibility` profile group，显式映射到 `monolith`。
- 普通启动不再无提示地进入完整单体实现；需要显式使用 `--spring.profiles.active=monolith` 或 `compatibility` 才启用兼容实现。
- 增加独立的 `migration` profile，保留 legacy 数据迁移、回填和回滚所需的 JDBC 配置。
- Compose、Kubernetes 和 `.env.example` 的正式运行入口统一指向 `prod,remote`。
- 现有 E2E runner 明确声明使用 `monolith`，表示它是兼容链路测试；远程微服务 E2E 作为下一阶段单独验收。
- 保留 `DemoStore` 和原单体实现，没有删除历史兼容能力。

### 1.2 remote backend 去除 legacy DB 运行依赖

- `JdbcBusinessStateRepository` 和 `MysqlBusinessStateHealthIndicator` 增加 `monolith`、`migration` profile 限制。
- 在 `prod,remote` 下不会实例化 legacy Repository，也不会注册 legacy MySQL health indicator。
- Kubernetes/Compose backend 不再注入 legacy `MYSQL_*` 和 `LUMALIFE_PERSISTENCE=mysql` 配置。
- 新增远程 profile 集成测试，验证 remote 模式没有 legacy Repository/health indicator bean。

### 1.3 服务数据库 fail-closed

- identity、merchant、order 三个服务的 `application-prod.yml` 均改为使用必填的 `${MYSQL_DATABASE}`。
- 删除服务数据库缺失时回退到 `life_assistant` 的行为。
- 正式目标数据库分别为 `life_assistant_identity`、`life_assistant_merchant`、`life_assistant_order`；数据库名由 Compose/Kubernetes 的服务级配置提供。
- 对已经存在的 Kubernetes 独立数据库补充 V017 迁移标记和 order Saga 约束升级，避免只升级 central/legacy DB。

### 1.4 Kubernetes 清单统一

- 将 `k8s/services.yaml` 作为业务服务唯一 canonical source。
- 删除 `k8s/services/*.yaml` 和其独立 `kustomization.yaml`，消除正式部署与 smoke fixture 的内容漂移。
- identity、merchant、order、assistant 四个 Deployment 均具备：
  - `startupProbe`
  - `readinessProbe`
  - `livenessProbe`
  - `resources.requests.cpu/memory`
  - `resources.limits.cpu/memory`
- `scripts/smoke-services-k8s.sh` 改为复制 canonical 清单生成临时 overlay，只在 overlay 中缩容未测试服务、替换测试镜像和关闭外部依赖。
- 变更服务检测和 services CD path filter 改为识别 canonical `k8s/services.yaml`。

### 1.5 库存 Saga confirm failure 补偿

- 新增 `V017__inventory_saga_failure_compensation.sql`，将状态约束扩展为：

  `RESERVE_PENDING`、`RESERVED`、`RESERVE_FAILED`、`CONFIRM_PENDING`、`CONFIRMED`、`CONFIRM_FAILED`、`RELEASE_PENDING`、`RELEASED`、`FAILED`。

- order-service 根据失败事件的 `sourceEventType` 区分 `RESERVE_FAILED` 和 `CONFIRM_FAILED`。
- `CONFIRM_FAILED` 后执行：
  1. 记录 Saga confirm failure 和错误原因；
  2. 将订单从 `PAID` 改为 `CANCELLED`；
  3. 将对应支付从 `SUCCESS` 改为 `FAILED`；
  4. 释放未使用优惠券；
  5. 使用独立事务写入 `RELEASE_PENDING` 和 `inventory.release.requested` Outbox 事件；
  6. 收到库存释放成功结果后进入 `RELEASED`。

- 远程 JDBC 模式不再从可能过期的内存支付缓存返回 `SUCCESS`；失败支付重放会返回明确的 `FAILED`，订单状态为 `CANCELLED`。
- 新增 confirm failure 单元测试，验证订单取消、支付失败和 release command 均被触发。

## 2. 每个问题修改前后对比

| 问题 | 修改前 | 修改后 |
|---|---|---|
| 默认运行模式 | backend 默认 `monolith`，普通启动可能进入完整单体链路 | 默认 `prod,remote`；`monolith/compatibility` 仅显式启用 |
| legacy Repository | `lumalife.persistence=mysql` 时可在普通运行模式加载 | 仅 `monolith`/`migration` profile 可加载 |
| legacy health indicator | legacy DB 可能影响 backend readiness | remote 模式不注册该 indicator，legacy DB 不参与 remote readiness/liveness |
| 服务 DB fallback | 服务 DB 缺失时可回退 `${MYSQL_DATABASE:life_assistant}` | `${MYSQL_DATABASE}` 必填；正式 DB 为 identity/merchant/order 独立库 |
| Kubernetes Deployment 来源 | `k8s/services.yaml` 与 `k8s/services/*.yaml` 两套逻辑漂移 | 只保留 `k8s/services.yaml`；smoke 使用临时 overlay |
| 业务服务运行保护 | canonical 清单缺少统一 startup/liveness/resources | 四个业务服务统一具备三类 probe 和 requests/limits |
| inventory confirm failure | 可能留下 `order=PAID`、`payment=SUCCESS`、`Saga=FAILED` | `CONFIRM_FAILED → RELEASE_PENDING → RELEASED`；订单 `CANCELLED`，支付 `FAILED` |
| 旧实现与旧库 | 仍存在但边界不够显式 | 继续保留，用于兼容/迁移/回滚；不作为 remote 默认业务运行依赖 |

## 3. 修改文件列表

### 配置与运行入口

- `.env.example`
- `docker-compose.yml`
- `e2e/runner.mjs`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-monolith.yml`
- `backend/src/main/resources/application-migration.yml`
- `k8s/backend.yaml`
- `services/identity-service/src/main/resources/application-prod.yml`
- `services/merchant-service/src/main/resources/application-prod.yml`
- `services/order-service/src/main/resources/application-prod.yml`

### backend 与 order-service

- `backend/src/main/java/com/lumalife/service/JdbcBusinessStateRepository.java`
- `backend/src/main/java/com/lumalife/service/MysqlBusinessStateHealthIndicator.java`
- `backend/src/test/java/com/lumalife/controller/RemoteModeIntegrationTest.java`
- `backend/src/test/java/com/lumalife/DatabaseAssetsTest.java`
- `services/order-service/src/main/java/com/lumalife/order/OrderInventoryResultConsumer.java`
- `services/order-service/src/main/java/com/lumalife/order/OrderSagaEventStore.java`
- `services/order-service/src/main/java/com/lumalife/order/OrderStore.java`
- `services/order-service/src/test/java/com/lumalife/order/OrderInventoryResultConsumerTest.java`

### 数据库与部署脚本

- `database/migrations/V017__inventory_saga_failure_compensation.sql`
- `database/bin/provision-service-databases.sh`
- `database/bin/isolate-service-databases.sh`
- `database/bin/verify.sh`
- `scripts/deploy-k8s.sh`
- `scripts/smoke-services-k8s.sh`
- `scripts/detect-changed-services.sh`
- `scripts/test-deploy-k8s.sh`
- `scripts/test-detect-changed-services.sh`
- `.github/workflows/ci.yml`
- `.github/workflows/services-cd.yml`

### Kubernetes 清单

- `k8s/services.yaml`：统一后的唯一业务服务清单。
- 删除 `k8s/services/identity-service.yaml`、`merchant-service.yaml`、`order-service.yaml`、`assistant-service.yaml` 和 `k8s/services/kustomization.yaml` 重复来源。

## 4. 测试结果

| 检查项 | 结果 |
|---|---|
| `mvn -B -ntp -f backend/pom.xml verify` | PASS，92/92 |
| `mvn -B -ntp -f services/pom.xml verify` | PASS，36/36；identity 11、merchant 8、order 15、assistant 2 |
| order-service 定向 `mvn verify` | PASS，15/15 |
| `docker compose config` | PASS |
| `kubectl kustomize k8s` | PASS；输出 7 个 Deployment（4 个业务服务 + backend/frontend/rabbitmq） |
| `bash scripts/test-deploy-k8s.sh` | PASS |
| `bash scripts/test-detect-changed-services.sh` | PASS |
| `bash scripts/test-service-data-ownership.sh` | PASS |
| 相关 shell `bash -n` 检查 | PASS |
| `docker compose up --detach --build --wait` | PASS；RabbitMQ、MySQL、backend、frontend 及 4 个业务服务均 healthy |
| `bash scripts/test-order-service-mysql-contract.sh` | PASS；支付幂等、重复支付冲突及 RabbitMQ 库存预占确认链路通过 |

Compose smoke 在 Docker Registry 证书恢复后通过。由于本机复用了一个已有的 MySQL volume，首次启动后还需要显式执行 `docker compose --profile db-tools run --rm db-seed` 和 `docker compose --profile db-tools run --rm db-backfill-services`，将演示商品和服务自有库数据补齐；未删除 volume，也未清理用户数据。

## 5. 是否满足课程验收的两项核心要求

### 微服务划分：满足本轮课程验收

- identity-service、merchant-service、order-service、assistant-service 具有独立启动入口。
- backend 的正式运行模式通过 HTTP 调用远程服务。
- Kubernetes canonical manifest 和服务级健康探针/资源约束已统一。
- 保留单体兼容层不影响 remote 默认业务路由，因此不把“仍有回滚实现”误判为核心拆分未完成。

### 数据归属：满足本轮课程验收

- identity、merchant、order 使用目标独立数据库。
- 三个服务数据库配置缺失时 fail-closed，不再回退到 `life_assistant`。
- 数据归属静态检查通过；order/merchant/identity 的服务自有表边界保持明确。
- legacy `life_assistant` 仍保留为迁移源、兼容源和回滚保障，remote backend 不在运行时访问其业务表。

因此，本轮可以对外表述为：

> 已完成课程要求的核心微服务拆分，并已完成正式运行模式、数据归属和 Kubernetes 资源收口；同时保留单体兼容层及旧库迁移/回滚能力。当前不是“单体代码和旧库全部移除”的全量微服务化状态。

## 6. 尚未处理的问题

以下内容明确留到下一阶段，不作为本轮未完成项倒推本轮结果：

- Microservice UC01~UC09 E2E，尤其是 `prod,remote` 的完整黑盒链路。
- 故障处理实验，包括服务不可用、消息重投、补偿失败和恢复演练。
- HPA。
- 性能对比。
- 文档最终同步，包括旧文档中的 manifest 路径、运行模式和服务数量口径。

同时，生产级 observability、复杂分布式事务、全量 P1/P2 和拆除 legacy DB 也不在本轮范围内；本轮继续沿用 RabbitMQ、Outbox、Inbox 和 Saga，没有引入 Kafka、Seata 或新的微服务框架。
