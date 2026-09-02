# 第一轮微服务整改定向回归审查

审查时间：2026-09-02  
审查范围：上一轮整改的 remote/compatibility、legacy DB 运行隔离、服务数据库隔离、Kubernetes 清单统一、库存 Saga confirm failure 补偿。  
初始复审阶段未继续修改业务代码，仅执行代码/配置审查、回归测试和运行态验证；随后已按本报告发现的四项 P1 完成第一轮修复，修复跟进记录见本文末尾。

## 结论

**PASS WITH MINOR ISSUES**

当前整改已真实生效，可以进入完整 Microservice E2E 阶段。核心理由是：

- backend 的正式配置和 Kubernetes 部署均指向 `prod,remote`，remote 路由运行态返回 `remote-service`；
- remote backend 不创建 legacy `BusinessStateRepository`，也不注册 legacy MySQL health indicator；
- identity、merchant、order 的生产数据库名称不再 fallback 到 `life_assistant`，Compose/Kubernetes wiring 指向三个独立数据库；
- 正式 Kubernetes 清单和 smoke 使用同一个 `k8s/services.yaml`；
- confirm failure 的正常补偿路径已将订单改为 `CANCELLED`、支付改为 `FAILED`，并持久化 `RELEASE_PENDING` 释放命令；
- backend、四个服务、Compose、RabbitMQ、Kustomize 和 ownership 检查均通过。

本结论不是“全量微服务化”结论。`DemoStore`、`monolith` profile 和旧 `life_assistant` 迁移/回滚能力仍按设计保留。

## 1. remote / compatibility 模式

### 审查结果：PASS，存在本地 `.env` 覆盖的小问题

- [backend/src/main/resources/application.yml](../backend/src/main/resources/application.yml) 将默认 profile 设置为 `prod,remote`，并将 `compatibility` profile 显式映射到 `monolith`。
- [backend/src/main/resources/application-monolith.yml](../backend/src/main/resources/application-monolith.yml) 关闭四个 remote migration gate，保留兼容持久化和 legacy MySQL 配置。
- [DemoStore.java](../backend/src/main/java/com/lumalife/service/DemoStore.java) 仅使用 `@Profile("monolith")`，不会在 remote profile 中创建。
- `RemoteModeIntegrationTest` 强制使用 `prod,remote`，并验证 legacy repository/health indicator 均不存在。
- [RemoteMerchantServicePort.java](../backend/src/main/java/com/lumalife/service/RemoteMerchantServicePort.java)、[RemoteOrderServicePort.java](../backend/src/main/java/com/lumalife/service/RemoteOrderServicePort.java) 和 identity/assistant remote adapter 在 migration gate 开启时创建 HTTP client。

运行态验证：

- 使用显式 `SPRING_PROFILES_ACTIVE=prod,remote` 重建 Compose backend 成功；
- `/internal/migration/status` 返回 `identity=remote-service`、`merchant=remote-service`、`order=remote-service`；
- 通过 backend 请求 `/api/v1/categories` 和商家搜索接口均返回 200，证明实际调用链已进入远程服务。

非阻塞问题：当前本机未提交 `.env` 中仍是 `SPRING_PROFILES_ACTIVE=prod`，正式 E2E 前应改为 `prod,remote` 或在命令行显式覆盖。该配置没有导致单体回退：当前容器仍报告 remote-service，且 `DemoStore` 没有被创建。

## 2. legacy DB runtime dependency

### 审查结果：PASS

- [JdbcBusinessStateRepository.java](../backend/src/main/java/com/lumalife/service/JdbcBusinessStateRepository.java) 使用 `@Profile({"monolith", "migration"})`。
- [MysqlBusinessStateHealthIndicator.java](../backend/src/main/java/com/lumalife/service/MysqlBusinessStateHealthIndicator.java) 使用相同 profile 限制。
- remote 集成测试即使显式设置 `lumalife.persistence=mysql`，仍验证 `BusinessStateRepository` bean 为空、`businessStateMysql` bean 不存在。
- Compose backend 没有注入 `MYSQL_*` legacy 数据库环境变量；`prod,remote` 运行时 readiness/liveness 均返回 `{"status":"UP"}`。
- legacy `life_assistant` 仍由 migration/backfill/rollback 脚本使用，未删除兼容能力。

因此，`prod,remote` 下 backend 不访问 legacy `life_assistant` 业务表，legacy DB 也不参与 backend 的 readiness/liveness。

## 3. service DB isolation

### 审查结果：PASS

- 三个生产配置的 JDBC URL 均使用必填 `${MYSQL_DATABASE}`，没有 `${MYSQL_DATABASE:life_assistant}` fallback：
  - identity-service → `life_assistant_identity`
  - merchant-service → `life_assistant_merchant`
  - order-service → `life_assistant_order`
- Compose 分别通过 `MYSQL_IDENTITY_DATABASE`、`MYSQL_MERCHANT_DATABASE`、`MYSQL_ORDER_DATABASE` 注入数据库名。
- Kubernetes 分别通过 `identity-database`、`merchant-database`、`order-database` Secret key 注入数据库名，且连接主机分别为 `mysql-identity`、`mysql-merchant`、`mysql-order`。
- 未注入 `MYSQL_DATABASE` 时，Spring 无法解析 datasource URL，不会静默改连 `life_assistant`。
- `scripts/test-service-data-ownership.sh` 通过，未发现跨服务 SQL 表访问或数据库 wiring 退回 legacy 的证据。

## 4. Kubernetes manifests

### 审查结果：PASS

- 根 [k8s/kustomization.yaml](../k8s/kustomization.yaml) 只引用 `k8s/services.yaml`。
- `k8s/services/` 下不存在第二套业务 Deployment 文件。
- [scripts/smoke-services-k8s.sh](../scripts/smoke-services-k8s.sh) 从 canonical `k8s/services.yaml` 复制生成临时 overlay，仅在 overlay 中做镜像、replica 和 broker patch。
- `kubectl kustomize k8s` 成功，渲染出 8 个 Deployment（4 个业务服务、backend、frontend、rabbitmq 和 MySQL 部署）。
- identity、merchant、order、assistant 四个业务 Deployment 均具备：
  - `startupProbe`
  - `readinessProbe`
  - `livenessProbe`
  - `resources.requests.cpu/memory`
  - `resources.limits.cpu/memory`

非阻塞问题：旧的 `docs/24_D06多服务Kubernetes清单与增量流水线.md` 仍引用已删除的 `k8s/services/*.yaml` 路径；部署实际来源已经统一，文档同步留到下一阶段。

## 5. Saga confirm compensation

### 审查结果：PASS WITH MINOR ISSUES

正常 confirm failure 路径已经形成：

```text
CONFIRM_PENDING
    ↓ inventory.result.failed(sourceEventType=inventory.confirm.requested)
CONFIRM_FAILED
    ↓ failPaidOrder
order=CANCELLED, payment=FAILED
    ↓ OrderSagaEventStore.scheduleRelease（加入消费者现有事务）
RELEASE_PENDING
    ↓ inventory.release.requested
RELEASED
```

代码证据：

- [OrderInventoryResultConsumer.java](../services/order-service/src/main/java/com/lumalife/order/OrderInventoryResultConsumer.java) 根据 `sourceEventType` 区分 `RESERVE_FAILED` 和 `CONFIRM_FAILED`。
- `CONFIRM_FAILED` 会调用 `failPaidOrderAndScheduleRelease`，将 PAID 订单改成 `CANCELLED`，将 SUCCESS payment 改成 `FAILED`，并写入 release command。
- [OrderSagaEventStore.java](../services/order-service/src/main/java/com/lumalife/order/OrderSagaEventStore.java) 已改为加入消费者现有事务，避免外层事务持有 Saga 行锁时再次开启 `REQUIRES_NEW` 导致锁等待；同一事务持久化 `RELEASE_PENDING` 和 `inventory.release.requested` Outbox 事件。
- Outbox publisher 会继续扫描 `PENDING`/`FAILED`，Rabbit listener 外层异常会重新抛出并允许消息重投，因此已有基础设施级 retry/failure path。
- 新增的 `OrderInventoryResultConsumerTest` 通过，验证了 confirm failure、订单取消、支付失败和 release command。
- Compose 的真实 RabbitMQ 库存预占/确认链路通过，未出现 `order=PAID + payment=SUCCESS + Saga=FAILED` 的无解释状态。

保留的非阻塞缺口：merchant 的 JDBC release 在部分数据不一致场景会返回 `CHECK_REQUIRED`；当前 Inbox 对任何返回对象都发送 `inventory.result.released`，order 侧会将 Saga 标成 `RELEASED`。这属于释放失败/人工处理实验的边界缺口，不影响本轮已验证的正常 confirm failure 补偿和进入 Microservice E2E；不得将其表述为生产级补偿闭环，后续故障处理实验需覆盖。

## 6. 回归验证结果

| 检查项 | 结果 |
|---|---|
| `mvn -B -ntp -f backend/pom.xml verify` | PASS，92/92 |
| `mvn -B -ntp -f services/pom.xml verify` | PASS，36/36（identity 11、merchant 8、order 15、assistant 2） |
| `mvn -B -ntp -f services/order-service/pom.xml verify` | PASS，15/15，包含 `OrderInventoryResultConsumerTest` |
| `docker compose config` | PASS |
| `kubectl kustomize k8s` | PASS，8 个 Deployment |
| `bash scripts/test-service-data-ownership.sh` | PASS |
| Docker Compose `up --detach --wait` | PASS |
| RabbitMQ `rabbitmq-diagnostics -q ping` | PASS |
| 5 个 HTTP 服务 readiness | PASS：backend、identity、merchant、order、assistant 均成功返回 |
| `bash scripts/test-order-service-mysql-contract.sh` | PASS：支付幂等、重复支付冲突、RabbitMQ 库存确认链路 |

## 7. 尚未处理的问题

以下内容不阻塞进入完整 Microservice E2E，按计划留到后续阶段：

1. Microservice UC01~UC09 E2E；
2. 故障处理实验，包括 confirm/release failure、消息重投、补偿失败和恢复演练；
3. HPA；
4. 性能对比；
5. 文档最终同步，包括旧 Kubernetes 路径、Compose profile 和当前接口/测试口径。

## 8. 最终判定

```text
第一轮整改复审：

remote / compatibility：PASS（正式配置为 prod,remote；本机 .env 有 prod 覆盖，但运行态仍为 remote-service）
legacy DB dependency：PASS
service DB isolation：PASS
K8s manifests：PASS
Saga compensation：PASS WITH MINOR ISSUES（CHECK_REQUIRED 释放失败映射留待故障实验）

backend tests：PASS（92/92）
services tests：PASS（36/36）
ownership check：PASS
kustomize：PASS

最终结论：PASS WITH MINOR ISSUES
是否可以进入 Microservice E2E：可以

若不可进入，阻塞问题：
无

## 11. P1 修复跟进记录（2026-09-02）

针对后续 PR 审查提出的四项 P1，已按“Saga → OrderStore → 数据库隔离 → RabbitMQ”顺序完成最小范围修复：

1. `OrderSagaEventStore.scheduleRelease` 不再使用 `REQUIRES_NEW`，改为加入 `OrderInventoryResultConsumer` 的现有事务，并增加事务传播属性回归测试。
2. remote `OrderStore` 以数据库为权威来源，不再优先返回 JVM 缓存；条件更新检查影响行数，失败时不追加状态事件。
3. `isolate-service-databases.sh` 在导入前移除跨服务外键，并在同一 MySQL 会话中关闭/恢复 `FOREIGN_KEY_CHECKS`；已用新数据库名执行物理 overlay 验证。
4. RabbitMQ 增加 Compose named volume、Kubernetes PVC 和持久消息投递；新增结构性 durability 检查，真实 Microservice E2E UC01–UC09 9/9 通过。

当前仍保留以下非本轮范围：Kubernetes Secret 化、release `CHECK_REQUIRED` 故障实验、HPA/metrics-server、性能对比和文档最终同步。

## 夜间第二阶段执行更新（2026-09-02）

上一节结论已按当时复审范围保留。随后已完成 Microservice E2E 9/9、merchant-service 故障注入 before/during/after 恢复验证和同机三 API 性能对比；HPA 已切换为 merchant-service 优先并完成真实负载记录，但 Docker Desktop 缺少 `metrics.k8s.io`，scale-up/scale-down 仍待目标集群复测。最终汇总见 `docs/NIGHTLY_SECOND_STAGE_EXECUTION_REPORT.md`。
```
