# 第二阶段收口证据包（2026-09-02）

这是统一证据目录的日期索引，避免把 CI、E2E、性能和 HPA 文件散落引用。

| 类别 | 入口 |
| --- | --- |
| 当前实现口径 | [`docs/MICROSERVICE_FINAL_ARCHITECTURE_MATRIX.md`](../../../docs/MICROSERVICE_FINAL_ARCHITECTURE_MATRIX.md) |
| 执行报告 | [`docs/NIGHTLY_SECOND_STAGE_EXECUTION_REPORT.md`](../../../docs/NIGHTLY_SECOND_STAGE_EXECUTION_REPORT.md) |
| Microservice E2E 报告 | [`docs/MICROSERVICE_E2E_REPORT.md`](../../../docs/MICROSERVICE_E2E_REPORT.md) |
| HPA 报告 | [`docs/HPA_EXPERIMENT_REPORT.md`](../../../docs/HPA_EXPERIMENT_REPORT.md) |
| 性能结果 | [`04_tests/performance/results/nightly-20260902/`](../../performance/results/nightly-20260902/) |
| HPA 原始证据 | [`04_tests/cloud-native/hpa-observation-20260902.csv`](../../cloud-native/hpa-observation-20260902.csv)、[`hpa/`](hpa/)；历史 BLOCKED preflight 仍见 [`hpa-observation-20260902-blocked.csv`](../../cloud-native/hpa-observation-20260902-blocked.csv) |
| 故障注入 | [`04_tests/cloud-native/fault/`](../../cloud-native/fault/) |

当前可交付结论：Microservice E2E、故障实验、远端 HPA 和本地性能原始结果可引用；HPA 已在 K3s 上真实完成 1→2→3→1。性能 workflow 的当前修复仍需目标 CI run 成功后再追加实际 artifact run 链接。
