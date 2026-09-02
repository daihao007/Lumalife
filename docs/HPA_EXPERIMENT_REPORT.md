# LumaLife HPA 实验报告

## 1. 实验结论

本轮已完成 merchant-service HPA 的清单收口和真实负载观测，但当前 Docker Desktop 集群没有 Resource Metrics API（`metrics.k8s.io`），因此不能宣称完成了基于 CPU 的自动扩容/缩容验收。结论是：配置和实验脚本 PASS，目标集群扩缩容观测 BLOCKED，证据完整保留。

## 2. 配置

- HPA：`merchant-service`，目标 Deployment：`merchant-service`
- `minReplicas=1`、`maxReplicas=3`
- CPU 目标：60%
- scale-up 稳定窗口：0 秒；scale-down 稳定窗口：120 秒
- backend HPA 仍保留，未删除既有实验入口
- 负载入口：merchant-service 内部 HTTP API，并携带既有内部服务 token

## 3. 实测观测

本次使用同一脚本执行了 2 个 worker、5 秒负载、每 5 秒采样的短验证；真实请求全部返回 200：

| 阶段 | merchant 副本 | HPA desired | CPU 指标 | 请求数 | 错误数 | 吞吐 |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| baseline | 1 | 0（控制器未能计算） | N/A | 0 | 0 | 0.00 req/s |
| load | 1 | 0（控制器未能计算） | N/A | 2701 | 0 | 540.20 req/s |
| load-complete | 1 | 0（控制器未能计算） | N/A | 2701 | 0 | 0.00 req/s |
| cooldown | 1 | 0（控制器未能计算） | N/A | 2701 | 0 | 0.00 req/s |

HPA 控制器实际错误为：

```text
the HPA was unable to compute the replica count: failed to get cpu utilization:
unable to fetch metrics from resource metrics API: the server could not find the
requested resource (get pods.metrics.k8s.io)
```

## 4. 原始证据

- `04_tests/cloud-native/hpa-observation.csv`
- `04_tests/cloud-native/hpa-observation-load.log`
- `04_tests/cloud-native/hpa-observation-top.log`
- `04_tests/cloud-native/hpa-observation-hpa.yaml`
- `04_tests/cloud-native/hpa-observation-deployment.yaml`
- `04_tests/cloud-native/hpa-observation-summary.md`

## 5. 后续执行条件

在课程验收集群安装并确认 metrics-server 或等价 Resource Metrics API 后，重新运行：

```bash
NAMESPACE=lumalife LOAD_SECONDS=180 COOLDOWN_SECONDS=180 \
  bash 04_tests/cloud-native/run-hpa-experiment.sh
```

只有原始 CSV 同时展示负载阶段 `desiredReplicas > 1`、Pod 增加，以及冷却阶段回到 1，才能将 HPA 实验标记为完整通过。
