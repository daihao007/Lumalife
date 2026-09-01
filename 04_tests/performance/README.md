# 性能对比实验

`load-test.mjs` 使用 Node.js 原生 `fetch`，对同一公开查询接口执行固定并发、固定请求数、3 次重复测量，输出 JSON 原始结果和 CSV 明细。脚本不会生成虚构的“提升”结论；应在相同机器、相同数据、相同请求参数下分别运行单体和微服务配置。

## 本地运行

```powershell
$env:PERF_LABEL = "microservices"
$env:PERF_BASE_URL = "http://127.0.0.1:8080"
$env:PERF_REQUESTS = "90"
$env:PERF_CONCURRENCY = "12"
node 04_tests/performance/load-test.mjs
```

单体配置只需将三个 `LUMALIFE_*_REMOTE_ENABLED` 设为 `false` 后重新启动 backend，再用相同命令改 `PERF_LABEL=monolith` 运行。比较两个 JSON/CSV 的 `throughputRps`、`averageMs`、`p95Ms`、`errorRate`，并同时记录压测期间的 CPU 和内存。

## 约束

- 两个版本使用同一台机器、同一批数据库种子和同一个 endpoint。
- 默认每个版本 3 次正式重复，另有 12 次预热请求；正式请求失败率超过 0 会使命令失败。
- `PERF_REQUESTS`、`PERF_CONCURRENCY`、`PERF_TIMEOUT_MS` 可由实验记录覆盖，但两边必须保持一致。
- 结果文件应作为流水线 artifact 保存，不能只提交截图。
