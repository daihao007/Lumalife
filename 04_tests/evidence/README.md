# LumaLife 第二阶段统一 Evidence

本目录是第二阶段最终交付的证据索引。目录中的原始文件只记录实际执行结果；历史快照不覆盖当前状态，HPA 只有在原始 CSV 证实真实扩缩容和冷却缩容时才标记 `PASS`。

## 当前基线与线上链接

- 当前验收基线：`8c335eb7d79400c1f56630bd5c6530ac86e25cf2`。Microservice E2E 与 HPA 均直接在该提交部署的远端 K3s 上重新生成；历史 CI 链接保留作流水线记录，不冒充本次现场验收。
- 最新 Monolith CI：[`33584079761`](https://github.com/daihao007/Lumalife/actions/runs/33584079761)，包含 Backend、Frontend、Microservice E2E、Kubernetes smoke、Kubernetes acceptance 和 quality gate。
- Microservice E2E：[`job 100104417404`](https://github.com/daihao007/Lumalife/actions/runs/33584079761/job/100104417404)，UC01～UC09 为 9/9 PASS。
- Kubernetes rollout smoke：[`job 100105159011`](https://github.com/daihao007/Lumalife/actions/runs/33584079761/job/100105159011)。
- Deploy Kubernetes：[`job 100105958216`](https://github.com/daihao007/Lumalife/actions/runs/33584079761/job/100105958216)。
- ECS/K3s：[`job 100110913513`](https://github.com/daihao007/Lumalife/actions/runs/33584774218/job/100110913513)。
- Performance workflow：[`Performance comparison`](https://github.com/daihao007/Lumalife/actions/workflows/performance.yml)。最近一次远端 [`33488817667`](https://github.com/daihao007/Lumalife/actions/runs/33488817667) 是旧版失败运行；当前本地修复尚未提交，不能把该旧运行写成修复成功。

## 证据目录

| 范围 | 原始证据 | 当前结论 |
| --- | --- | --- |
| Microservice E2E | [`../e2e/microservices/latest/`](../e2e/microservices/latest/) | UC01～UC09，9/9 PASS |
| Fault tolerance | [`../cloud-native/fault/`](../cloud-native/fault/) | merchant-service 停止/恢复，PASS |
| HPA final acceptance | [`../cloud-native/hpa-observation-20260902-8c335eb.csv`](../cloud-native/hpa-observation-20260902-8c335eb.csv)、[summary](../cloud-native/hpa-observation-20260902-8c335eb-summary.md) | PASS；当前镜像 `sha-8c335eb`，14,467 请求、0 错误，merchant-service 真实 1→2→3→1 |
| HPA historical blocked preflight | [`../cloud-native/hpa-observation-20260902-blocked.csv`](../cloud-native/hpa-observation-20260902-blocked.csv)、[summary](../cloud-native/hpa-observation-20260902-blocked-summary.md)、[Docker diagnostic](../cloud-native/hpa-observation-20260902-blocked-docker-diagnostic.txt) | 历史 BLOCKED；本机无 context/API server，不覆盖远端 PASS |
| HPA report | [`../../docs/HPA_EXPERIMENT_REPORT.md`](../../docs/HPA_EXPERIMENT_REPORT.md) | PASS；只依据真实 metrics、CSV、事件和日志 |
| Performance raw result | [`../performance/results/nightly-20260902/`](../performance/results/nightly-20260902/) | 2 modes × 3 APIs × 3 repeats；每组 JSON/CSV、CPU/Memory CSV、summary 已保存 |
| Performance validator | [`../performance/validate-results.sh`](../performance/validate-results.sh) | CI 成功前硬校验矩阵、零错误和资源采样；artifact 缺失时失败 |
| Deployment manifest | `kubernetes-manifests` artifact in CI run | 清单渲染/部署真实结果见上方 CI job |

## Performance artifact 下载与校验

成功的 Performance comparison workflow 会上传名为 `performance-comparison-results` 的 artifact，内容必须包括：

- `merchant-search`、`categories`、`merchant-detail` 三个接口；
- `microservices` 与 `monolith` 两种模式；
- 每个组合 3 次 repeat 的 JSON/CSV；
- 每个组合的 CPU/Memory `*-resources.csv` 和运行日志；
- `comparison-summary.csv` 与运行元数据。

在 GitHub Actions 页面打开具体成功 run，选择 Artifacts 下载；本地复核使用：

```bash
bash 04_tests/performance/validate-results.sh \
  04_tests/performance/results/<run-directory> \
  <requests-per-repeat> <concurrency> 3
```

当前仓库内已保存的 nightly 结果实际使用 `10` requests/repeat、`4` concurrency、`3` repeats，命令为：

```bash
bash 04_tests/performance/validate-results.sh \
  04_tests/performance/results/nightly-20260902 10 4 3
```

如果运行环境没有 Bash/`jq`，不能把“验证器未运行”写成 PASS；应在 Ubuntu/GitHub Actions runner 上执行上述命令。

## 最终演示步骤

以下命令在隔离验收集群执行。生产 Secret 不从仓库文件读取；部署前由 CI/Secret Manager 注入环境变量：

```bash
export LUMALIFE_INTERNAL_SERVICE_TOKEN="$(openssl rand -hex 24)"
export RABBITMQ_USER="lumalife"
export RABBITMQ_PASSWORD="$(openssl rand -hex 24)"
bash scripts/deploy-k8s.sh sha-<validated-main-sha>
kubectl -n lumalife get deployments,pods,svc -o wide
```

演示顺序：

1. 部署并确认 backend、frontend、RabbitMQ、四个业务服务和三套 service DB ready；内部 token/RabbitMQ 凭据来自 `lumalife-runtime` Secret。
2. 按 [`MICROSERVICE_E2E_REPORT.md`](../../docs/MICROSERVICE_E2E_REPORT.md) 的真实 remote runner 复现 UC09，展示管理员指标聚合和普通用户拒绝；UC01～UC08 可从同一 runner 顺序复现。
3. 先执行 `kubectl get apiservice v1beta1.metrics.k8s.io`。确认 API 可用后，以 `merchant-service` 为目标执行 [`run-hpa-experiment.sh`](../cloud-native/run-hpa-experiment.sh)，保存 1→2/3 负载和冷却→1 的 CSV、`kubectl top`、事件及日志；本次远端证据已证明该路径 PASS。没有真实扩缩容时现场结论仍为 BLOCKED。
4. 在隔离窗口执行 `kubectl -n lumalife scale deployment/merchant-service --replicas=0`，访问商家搜索接口并记录用户可见 503；确认 identity/order/assistant、数据库和 RabbitMQ 未被级联停止。
5. 执行 `kubectl -n lumalife scale deployment/merchant-service --replicas=1`，等待 readiness，再复查商家请求恢复。
6. 打开 Performance comparison workflow，下载 `performance-comparison-results`，确认三接口、两模式、三轮以及 CPU/Memory 文件齐全后，引用 `comparison-summary.csv`，不得用截图或理论值替代原始结果。

## 仍未覆盖的故障场景

- 本次目标 K3s 已有可用 `kube-system/metrics-server`，未执行替换安装；其他目标集群若 `metrics.k8s.io` 不可用，仍需按发行版/版本选择兼容组件后再实验。
- merchant release `CHECK_REQUIRED`、`RELEASE_FAILED`、消息重复投递和人工恢复的 RabbitMQ + MySQL 集成故障实验尚未形成远端 E2E 证据；代码和单元测试已覆盖主要状态边界。
- 性能 workflow 的当前修复需要在包含本地未提交改动的 CI run 中实际执行后，才能拥有对应的成功远端 artifact 链接；现有 nightly 原始结果可复核但不是该 workflow 的成功 run。
