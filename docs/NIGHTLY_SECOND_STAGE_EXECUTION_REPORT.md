# LumaLife 第二阶段夜间自主执行报告

执行日期：2026-09-02（Asia/Shanghai）  
仓库：`D:\Projects\26summer\Lumalife`
执行范围：Microservice E2E、故障处理、HPA 最终验收、性能对比、文档同步和最终回归。

当前 main 对应 CI/CD 运行链接： [Monolith CI 33584079761](https://github.com/daihao007/Lumalife/actions/runs/33584079761)（含 Microservice E2E、Kubernetes smoke、Deploy Kubernetes）、[Microservice E2E job](https://github.com/daihao007/Lumalife/actions/runs/33584079761/job/100104417404)、[Kubernetes rollout smoke job](https://github.com/daihao007/Lumalife/actions/runs/33584079761/job/100105159011)、[Deploy Kubernetes job](https://github.com/daihao007/Lumalife/actions/runs/33584079761/job/100105958216)、[ECS/K3s deployment 33584774218](https://github.com/daihao007/Lumalife/actions/runs/33584774218/job/100110913513)。性能 workflow 入口为 [Performance comparison](https://github.com/daihao007/Lumalife/actions/workflows/performance.yml)；远端最近一次旧版 run 为 [33488817667（failure）](https://github.com/daihao007/Lumalife/actions/runs/33488817667)，不能作为当前修复成功证据。统一证据索引见 [`04_tests/evidence/README.md`](../04_tests/evidence/README.md)。

## 1. 总体结论

**Microservice E2E 阶段：PASS。** 当前仓库已经具备进入并完成完整 Microservice E2E 的条件，UC01～UC09 在真实 `prod,remote` 链路中 9/9 通过。

**第二阶段云原生实验状态：PASS（HPA 已完成）。** 故障实验、性能对比和 merchant-service HPA 均已有真实证据；HPA 在远端 ECS/K3s 上确认 `metrics.k8s.io` 可用，并实际观察到 Ready 副本与 HPA current/desired 从 1 到 2/3 再冷却回 1。本机此前无 context 的 BLOCKED preflight 仍保留为历史证据，不覆盖远端结果。

## 2. Microservice E2E

| 项目 | 结果 |
| --- | --- |
| Compose run | `20260902T102500Z-p1-retry` |
| backend profile | `prod,remote` |
| remote flags | identity、merchant、order、assistant 全部开启 |
| compatibility store | 关闭 |
| service databases | `life_assistant_identity`、`life_assistant_merchant`、`life_assistant_order` |
| 基础设施 | MySQL、RabbitMQ、四个服务、backend、frontend 全部 healthy |
| UC01～UC09 | 9/9 PASS |
| 隔离环境清理 | PASS；未留下该 E2E project 的容器、网络或 volume |

UC03 实测库存 Saga 为 `CONFIRMED`、payment 为 `SUCCESS`；UC08 实测 sender role 包含 `USER`、`MERCHANT_AI`、`MERCHANT`；UC09 实测看板统计可返回。

原始证据：`04_tests/e2e/microservices/latest/`，详细说明见 `docs/MICROSERVICE_E2E_REPORT.md`。

## 3. CI Gate

| 检查 | 结果 |
| --- | --- |
| Microservice E2E job | 已接入 `.github/workflows/ci.yml` |
| quality-gate 依赖 | 已显式等待 monolith E2E 和 Microservice E2E |
| 性能 workflow | 已改为调用同一 `run-comparison.sh`，保留三次重复和 artifact |
| 本地 GitHub Actions 执行 | 未执行；本地报告不冒充 GitHub 运行结果 |

## 4. Fault Experiment

结果：**PASS**。

`merchant-service` 从 1 个副本缩容到 0 后：

- backend readiness：200 → 200 → 200；
- `/api/v1/merchants`：200 → 503 → 200；
- 故障期间业务码为 `50300`，reason 为 `MERCHANT_SERVICE_UNAVAILABLE`；
- identity、order、assistant、数据库和 RabbitMQ 未被级联停止；
- merchant-service 恢复为 1/1，HPA 清单已恢复。

原始证据：`04_tests/cloud-native/fault/`；报告：`docs/FAULT_TOLERANCE_EXPERIMENT_REPORT.md`。

## 5. HPA / Scale Up / Scale Down

| 项目 | 结果 |
| --- | --- |
| HPA target | merchant-service，min=1，max=3，CPU target=60% |
| Context/API server | PASS；远端 K3s context `default`，Kubernetes v1.36.3+k3s1 |
| `metrics.k8s.io` | PASS；APIService `Available=True`，raw API 与 `kubectl top` 均成功 |
| metrics-server | PASS；集群已有 `kube-system/metrics-server`，本次无需安装替换 |
| Load | PASS；20 workers、180 秒负载，10 秒采样 |
| Scale Up | PASS；merchant-service Ready `1 → 2 → 3`，HPA current/desired 同步达到 `2/2`、`3/3` |
| Scale Down | PASS；180 秒冷却后 Ready 与 HPA current/desired 均回到 `1/1` |
| CPU / memory | PASS；每轮有 `kubectl top` CPU/Memory 原始采样 |
| 原始数据 | `04_tests/cloud-native/hpa-observation-20260902*`；统一索引见 `04_tests/evidence/second-stage-20260902/hpa/` |

本次远端实验的原始 kubectl、metrics-server、事件、merchant-service 日志、请求日志和 CSV 均已保存；共 24,857 请求、0 错误。此前本地无 context 的 BLOCKED preflight 以及 2026-09-01 的短观测仍保留，但不覆盖本次 PASS。报告：`docs/HPA_EXPERIMENT_REPORT.md`。

## 6. Performance

使用同一 Compose project、同一数据、同一 `04_tests/performance/load-test.mjs`、同一主机和相同并发配置，分别运行 `prod,remote` 与显式 `monolith` profile；每个模式的每个 API 均为 3 次重复、每次 10 个请求，错误数均为 0。

| 模式 | API | 总请求 | 吞吐 req/s | 平均 ms | P95 ms | backend CPU avg/max | backend memory avg/max MiB |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| microservices | merchant search | 30 | 130.93 | 27.19 | 50.26 | 25.05% / 49.94% | 278.70 / 278.70 |
| microservices | categories | 30 | 561.25 | 6.55 | 11.40 | 1.82% / 3.48% | 285.50 / 285.60 |
| microservices | merchant detail | 30 | 127.98 | 27.82 | 47.47 | 0.29% / 0.41% | 294.25 / 294.30 |
| monolith | merchant search | 30 | 1009.78 | 3.58 | 7.39 | 0.18% / 0.24% | 286.80 / 286.80 |
| monolith | categories | 30 | 1083.39 | 3.23 | 7.19 | 1.68% / 3.21% | 290.30 / 290.40 |
| monolith | merchant detail | 30 | 872.20 | 4.10 | 8.34 | 2.66% / 5.07% | 294.55 / 294.60 |

这是一次小规模课程实验的原始测量，不是生产容量结论。结果显示当前机器上远程 HTTP、BFF 编排和服务边界调用带来了明显延迟成本；它没有证明微服务一定更快或更慢。

原始证据：`04_tests/performance/results/nightly-20260902/`；入口脚本：`04_tests/performance/run-comparison.sh`。

## 7. Saga confirm compensation

代码与单元测试复核结果：**PASS（正常补偿路径）**。

当前可审计状态链为：

```text
RESERVE_PENDING → CONFIRM_PENDING → CONFIRMED
                         └→ CONFIRM_FAILED → RELEASE_PENDING → RELEASED
```

confirm failure 后：

1. order-side Saga 先记录 `CONFIRM_FAILED`；
2. 订单从 `PAID` 改为 `CANCELLED`；
3. payment 从 `SUCCESS` 改为 `FAILED`；
4. `OrderSagaEventStore` 加入订单结果消费者现有事务，持久化 `RELEASE_PENDING` 和 release Outbox，避免同一 Saga 行的嵌套新事务锁等待；
5. 后续 release result 可推进到 `RELEASED`。

`OrderInventoryResultConsumerTest` 已随 services verify 通过。尚未在本轮伪造或强行注入数据库级 confirm failure E2E；当前证据是代码路径、事务边界和针对性单元测试，不能扩大表述为生产级分布式事务。

本次 PR 复审后已补充四项 P1 修复：Saga release 事务边界、remote OrderStore 数据库权威读取与条件更新、物理数据库 overlay 跨服务外键清理、RabbitMQ 存储卷与持久消息。修复后的 Microservice E2E 已重新运行并以当前修复提交生成 9/9 通过证据；HPA 随后在远端 K3s 完成真实 1→2→3→1 验收，剩余故障实验范围保持不变。

## 8. Documentation

已同步或新增：

- `docs/MICROSERVICE_FINAL_ARCHITECTURE_MATRIX.md`：controller mapping、数据归属、跨服务调用、UC 追溯；
- `docs/FAULT_TOLERANCE_EXPERIMENT_REPORT.md`；
- `docs/HPA_EXPERIMENT_REPORT.md`；
- `04_tests/cloud-native/hpa-observation-20260902*`：远端最终验收 PASS 原始证据及此前本机 BLOCKED preflight；
- `docs/29_D08云原生实验与性能对比计划.md`；
- `docs/16_业务场景用例清单.md`、`docs/22_统一需求用例三层模型代码测试追溯表.md`；
- `docs/24_D06多服务Kubernetes清单与增量流水线.md`：改为唯一 `k8s/services.yaml` 来源；
- `docs/MICROSERVICE_E2E_REPORT.md`：补充后续实验实际状态。

## 9. Final Checks

下表保留既有 nightly 回归结果；本次 HPA final acceptance 新增并核验了
远端 K3s 的 metrics、扩缩容、冷却、事件和日志证据；此前本机 preflight 的
BLOCKED 文件仍保留为历史失败，不与本次远端成功混用。

| 检查项 | 结果 |
| --- | --- |
| `mvn -B -ntp -f backend/pom.xml verify` | PASS，92/92 |
| `mvn -B -ntp -f services/pom.xml verify` | PASS，40/40 |
| `npm --prefix e2e test` | PASS，Monolith E2E 7/7 |
| Microservice E2E | PASS，UC01～UC09 9/9 |
| `docker compose config` | PASS |
| `kubectl kustomize k8s` | PASS |
| `bash scripts/test-service-data-ownership.sh` | PASS |
| `bash scripts/test-deploy-k8s.sh` | PASS |
| `git diff --check` | PASS |
| Kubernetes 5 个 HTTP readiness | 历史 nightly PASS：backend、identity、merchant、order、assistant；本次 HPA 目标 merchant-service 的远端扩缩容实验结束时 1/1 |
| RabbitMQ `rabbitmq-diagnostics -q ping` | 历史 nightly PASS；本次 HPA 负载期间请求无错误，未单独执行诊断命令 |
| K8s 当前 Pod 基线 | HPA final acceptance 远端 PASS；结束时 merchant-service 为 1/1，完整 Pod 快照已保存 |

## 10. 本轮改动文件与证据

### 代码、配置和脚本

- `04_tests/cloud-native/run-fault-experiment.sh`
- `04_tests/cloud-native/run-hpa-experiment.sh`
- `04_tests/performance/run-comparison.sh`
- `.github/workflows/performance.yml`
- `k8s/hpa.yaml`
- `k8s/services.yaml`

### 文档

- `docs/NIGHTLY_SECOND_STAGE_EXECUTION_REPORT.md`
- `docs/FAULT_TOLERANCE_EXPERIMENT_REPORT.md`
- `docs/HPA_EXPERIMENT_REPORT.md`
- `docs/MICROSERVICE_FINAL_ARCHITECTURE_MATRIX.md`
- `docs/MICROSERVICE_E2E_REPORT.md`
- `docs/16_业务场景用例清单.md`
- `docs/22_统一需求用例三层模型代码测试追溯表.md`
- `docs/24_D06多服务Kubernetes清单与增量流水线.md`
- `docs/29_D08云原生实验与性能对比计划.md`

### 原始证据目录

- `04_tests/e2e/microservices/latest/`
- `04_tests/cloud-native/fault/`
- `04_tests/cloud-native/hpa-observation*`
- `04_tests/performance/results/nightly-20260902/`

第一轮整改和 Microservice E2E 的既有代码/报告仍保留，未删除 monolith、DemoStore 或 legacy migration/backfill/rollback 能力。

## 11. 尚未完成与明日首要动作

### 尚未完成

1. merchant release `CHECK_REQUIRED`、`RELEASE_FAILED`、消息重复投递和人工恢复的更深故障实验；
2. GitHub Actions 实际运行记录和更大样本性能实验仍需在目标环境补充；
3. 多节点、metrics-server 故障和滚动发布同时扩缩容等 HPA 联合场景。

### Tomorrow first actions

1. 运行 release failure/retry 场景，确认 `RELEASE_PENDING` 不会被错误标记为 `RELEASED`；
2. 触发当前修复后的 Performance workflow，并把成功 artifact run 回填文档；
3. 在多节点或故障注入环境补充 HPA 联合场景。

## 12. Terminal Summary

```text
===== SECOND STAGE NIGHTLY RUN =====
Microservice E2E: PASS
UC01: PASS
UC02: PASS
UC03: PASS
UC04: PASS
UC05: PASS
UC06: PASS
UC07: PASS
UC08: PASS
UC09: PASS
CI Gate: CONFIGURED; GitHub run not executed locally
Fault Experiment: PASS
HPA: PASS - remote K3s metrics.k8s.io available; merchant-service 1->2->3->1 observed
Scale Up: PASS - Ready replicas and HPA current/desired reached 2 and 3
Scale Down: PASS - Ready replicas and HPA current/desired returned to 1 after cooldown
Performance: PASS
API Count: 3 APIs x 2 modes x 3 repeats
Monolith Runs: 3 API groups, 0 errors
Microservice Runs: 3 API groups, 0 errors
CPU/Memory: PASS - Docker stats raw samples saved
Documentation: PASS
Backend Tests: PASS 92/92
Services Tests: PASS 40/40
Ownership: PASS
Kustomize: PASS
Overall status: PARTIAL - remaining workflow/fault-depth evidence
Remaining blockers:
1. Release failure and retry path needs a separate fault experiment
2. GitHub Actions target-environment performance artifact for the current workflow fix is not produced by this local run
3. Multi-node and metrics-server fault HPA scenarios remain untested
Tomorrow first actions:
1. Run release failure/retry compensation experiment
2. Trigger the current Performance workflow and sync its artifact
3. Add multi-node/metrics-server fault HPA coverage when such a cluster is available
```
