# LumaLife HPA 实验报告

## 1. 最终验收结论

**HPA：PASS。** 2026-09-02 在远端 ECS 的单节点 K3s 验收集群完成了真实实验：`metrics.k8s.io` 可用，`merchant-service` 的 Ready 副本和 HPA current/desired 均观察到 `1 → 2 → 3 → 1`。本结论只依据本次 raw CSV、kubectl transcript、事件、资源指标和请求日志，不依据清单存在或历史观测推断。

现场环境：K3s `v1.36.3+k3s1`、Kubernetes context `default`、namespace `lumalife`；验收提交为 `8c335eb7d79400c1f56630bd5c6530ac86e25cf2`，目标 Deployment 使用 `ghcr.io/daihao007/lumalife-merchant-service:sha-8c335eb`。实验负载为 20 workers、120 秒，冷却 180 秒，10 秒目标采样间隔。

## 2. metrics.k8s.io 与目标配置

远端 preflight 成功：

- `kubectl get apiservice v1beta1.metrics.k8s.io`：`Available=True`，reason `Passed`，backend 为 `kube-system/metrics-server`；
- `kubectl get --raw /apis/metrics.k8s.io/v1beta1`：返回 `nodes` 和 `pods` 两类资源；
- `kubectl top nodes` 和 `kubectl top pod -l app=merchant-service`：均返回 CPU/Memory；
- 因 metrics API 已可用，本次没有安装或替换 metrics-server，避免在可用集群上盲目修改组件。

目标 HPA 为 `merchant-service`，`minReplicas=1`、`maxReplicas=3`、CPU target `60%`，scale-up stabilization `0s`，scale-down stabilization `120s`。清单离线校验仍可用 `kubectl kustomize k8s` 完成。

## 3. 实验观测

原始 CSV 每行同时记录 timestamp、replicas、Ready Pod、HPA current/desired、CPU/Memory、累计请求、累计错误、error rate、throughput、Avg 和 P95。关键观测如下：

| 阶段 | timestamp (UTC) | replicas / Ready | HPA current / desired | HPA CPU / target | CPU / Memory | 累计请求 / 错误 | 吞吐 req/s | Avg / P95 ms |
| --- | --- | --- | --- | --- | --- | ---: | ---: | --- |
| baseline | 09:24:41 | 1 / 1 | 1 / 1 | 8% / 60% | 6m / 196Mi | 0 / 0 | 0.00 | N/A / N/A |
| load | 09:25:25 | 2 / 2 | 2 / 2 | 1408% / 60% | 1297m / 394Mi | 4,376 / 0 | 139.50 | 110.82 / 300 |
| load | 09:26:21 | 3 / 3 | 3 / 3 | 570% / 60% | 1338m / 550Mi | 11,596 / 0 | 163.90 | 73.19 / 230 |
| load-complete | 09:26:53 | 3 / 3 | 3 / 3 | 308% / 60% | 617m / 591Mi | 14,467 / 0 | 0.00 | 66.06 / 210 |
| cooldown | 09:29:14 | 1 / 1 | 1 / 1 | 6% / 60% | 3m / 208Mi | 14,467 / 0 | 0.00 | 66.06 / 210 |

负载期间累计 **14,467 请求、0 错误、error rate 0.00%**。Avg/P95 是脚本按请求日志累计计算的值；逐次采样和所有原始请求记录见 CSV/日志，不用表格摘要替代原始数据。

## 4. 扩缩容事件与结果判定

事件 transcript 中记录了：

- HPA `SuccessfulRescale`：`New size: 2`，原因是 CPU utilization above target；
- HPA `SuccessfulRescale`：`New size: 3`，原因是 CPU utilization above target；
- 冷却后 HPA `SuccessfulRescale`：`New size: 1`，原因是 All metrics below target；
- 同期 Deployment 事件记录 ReplicaSet `1 to 2`、`2 to 3` 和 `3 to 1`。

脚本只有在 metrics API active、HPA current/desired 至少到 2、Ready Pod 至少到 2，并在 cooldown 同时回到 1 时才输出 PASS；本次 summary 的 `scale-up observed in raw CSV: true` 和 `scale-down observed in raw CSV: true` 均为真实脚本结果。

## 5. 原始证据

`8c335eb` 最终验收批次的权威证据目录为 [`04_tests/cloud-native/`](../04_tests/cloud-native/)；`04_tests/evidence/second-stage-20260902/hpa/` 只保存未带 commit hash 的另一批镜像，不包含下列 `-8c335eb` 文件，不能作为这些文件的链接目标：

- `hpa-observation-20260902-8c335eb.csv`、`hpa-observation-20260902-8c335eb-summary.md`；
- `hpa-observation-20260902-8c335eb-load.log`、`hpa-observation-20260902-8c335eb-load-create.log`；
- `hpa-observation-20260902-8c335eb-kubectl.log`、`hpa-observation-20260902-8c335eb-top.log`；
- `hpa-observation-20260902-8c335eb-events.log`、`hpa-observation-20260902-8c335eb-service.log`；
- `hpa-observation-20260902-8c335eb-metrics-server.yaml`、`-metrics-server-pods.txt`、`-metrics-api.yaml`；
- `hpa-observation-20260902-8c335eb-hpa.yaml`、`-deployment.yaml` 以及实验结束后的 `-final-*` 快照。

此前本地无 context 的 `hpa-observation-20260902-blocked*` 文件继续保留为真实 BLOCKED preflight 历史，不覆盖本次远端 PASS，也不作为扩缩容数据。

## 6. 验证命令

| 命令/检查 | 结果 |
| --- | --- |
| `kubectl get apiservice v1beta1.metrics.k8s.io` | PASS，Available=True |
| `kubectl get --raw /apis/metrics.k8s.io/v1beta1` | PASS，返回 NodeMetrics/PodMetrics 资源 |
| `kubectl top nodes` / `kubectl top pod` | PASS |
| `bash 04_tests/cloud-native/run-hpa-experiment.sh` | PASS，退出码 0，原始 CSV 证实 1→2→3→1 |
| `bash -n 04_tests/cloud-native/run-hpa-experiment.sh` | PASS |
| `kubectl kustomize k8s` | PASS，HPA 清单可渲染 |

本机此前的无 context preflight 仍记录为 BLOCKED；远端实验通过 SSH 在目标 K3s 上执行，未将本机 Java 或本机 Docker 状态作为 HPA 结论。

## 7. 复现入口

在具备目标集群 kubeconfig 的 Linux/CI 主机上执行：

```bash
kubectl config current-context
kubectl get --raw /apis/metrics.k8s.io/v1beta1
kubectl top nodes

NAMESPACE=lumalife \
TARGET_DEPLOYMENT=merchant-service \
HPA_NAME=merchant-service \
LOAD_SECONDS=120 \
COOLDOWN_SECONDS=180 \
SAMPLE_SECONDS=10 \
LOAD_CONCURRENCY=20 \
bash 04_tests/cloud-native/run-hpa-experiment.sh
```

如果 metrics API 不可用，必须先按集群发行版和 Kubernetes 版本选择兼容的 metrics-server，等待 APIService、`kubectl top nodes` 和 `kubectl top pods` 均成功，再开始负载；在此之前脚本只能输出 BLOCKED，不能填入推测数值。

## 8. 尚未覆盖的故障场景

- 本次是单节点 K3s，未覆盖多节点调度、节点资源不足、Pod Pending 或跨节点网络故障；
- 未覆盖 metrics-server 暂时不可用、证书失效、API 延迟或采集数据陈旧期间的 HPA 保守行为；
- 本次事件中扩出的 Pod 曾出现短暂 startup/readiness probe warning，随后达到 3/3 Ready；尚未做滚动发布与扩缩容同时发生的长时间稳定性实验；
- 未覆盖 HPA 反复抖动、RabbitMQ/MySQL 故障与业务请求故障同时发生时的联合实验。
