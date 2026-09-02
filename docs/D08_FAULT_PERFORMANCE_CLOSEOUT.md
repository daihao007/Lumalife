# D08 故障处理与性能对比收口记录

> 历史分支收口记录（`a698c8e`），不是当前 `main@dc96528` 事实源。当前性能状态和缺口以 [`project-facts.md`](project-facts.md) 与 [`final-audit/final-project-audit.md`](final-audit/final-project-audit.md) 为准。

## 当前执行基线

- 工作分支：`codex/d08-fault-performance-closeout`
- 基于：`origin/main`，SHA `a698c8e`
- 原始 `monolith-start` 标签：`3eb8f44`
- 当前仓库的远端 ECS/K3s 访问和 GitHub Actions 触发凭据未配置，本记录不虚构远端运行结果。

## 已完成的本地收口

- 性能脚本仍保持 2 模式 × 3 接口 × 3 重复和原有汇总 CSV 格式。
- 新增 backend 之外的 identity、merchant、order、assistant 容器逐秒资源采样。
- 新增按采样时间聚合的总 CPU/内存 CSV 和文本摘要。
- GitHub Actions 性能校验在新运行中要求完整应用容器资源文件。
- assistant-service fallback 测试显式清空 `agnes.api-key`，避免受开发机外部环境变量影响。
- backend 测试通过：93 tests, 0 failures。
- services 测试通过：71 tests, 0 failures。

## 课程故障处理映射

课程基础故障实验已有 merchant-service `1→0→1`、商家接口 `200→503→200`、backend readiness 保持 `200` 的真实证据，基础 2 分要求已覆盖。

代码级测试还覆盖：

- `CHECK_REQUIRED`；
- `RELEASE_FAILED`；
- 重复 release/result 消息幂等；
- 过期预占的安全处理。

尚未声称完成的部分是 RabbitMQ + MySQL 真实远端 release failure/retry E2E，仍需在隔离环境执行并保存数据库、消息和日志证据。

## 仍需人工执行

1. 推送本分支后手动触发 Performance comparison workflow。
2. 下载 `performance-comparison-results`，确认六个组合、三轮结果和 stack resource 文件全部存在。
3. 在目标 Kubernetes 环境复现 release failure/retry，并把成功 run/artifact 链接回 evidence README。
