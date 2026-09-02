# Test Inventory

> 本轮测试日期：2026-09-02；当前代码：`dc96528`。旧文档中的 40、59、78、92 等数字均不是当前事实。以下只统计具有明确 runner case 边界的测试；6 个 Shell 验证脚本作为套件单列。

## 统计口径

- Unit/Component：73 个非 `@SpringBootTest` Java case + 35 个 Vitest case = 108。
- Integration/API：91 个 `@SpringBootTest` Java case。
- E2E：legacy API runner 7 + microservice runner 9 + Playwright 3 = 19。

| 类型 | 测试文件 | Case 数量 | Passed | Failed | Skipped | NOT-RUN | 验证环境 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Unit/Component | 11 | 108 | 108 | 0 | 0 | 0 | LOCAL-VERIFIED；JVM 21.0.7 / Node 24.15 |
| Integration/API | 15 | 91 | 91 | 0 | 0 | 0 | LOCAL-VERIFIED；Spring Boot test context |
| E2E | 3 | 19 | 11 | 1 | 0 | 7 | UI 3 本地；microservice 9 为旧提交证据；legacy 7 未重跑 |
| Total | **29** | **218** | **210** | **1** | **0** | **7** | 混合来源，见下 |

## 本轮实际执行

| 命令 | 执行数 | Passed | Failed | 状态 |
| --- | ---: | ---: | ---: | --- |
| `cd backend && mvn test` | 93 | 93 | 0 | LOCAL-VERIFIED |
| `cd services && mvn test` | 71 | 71 | 0 | LOCAL-VERIFIED |
| `cd frontend && npm test -- --reporter=verbose` | 35 | 35 | 0 | LOCAL-VERIFIED |
| `cd ui-e2e && npm test` | 3 | 2 | 1 | LOCAL-VERIFIED |

本轮合计实际执行 **202**，结果 **201 passed / 1 failed / 0 skipped**。

失败详情：`ui-e2e/tests/core-flows.spec.ts:71` 的客服往返场景期望出现名称匹配 `/林夏/` 的会话按钮，但 10 秒内未找到。未修改代码迁就旧文档。

## Existing Evidence

- `04_tests/e2e/microservices/latest/microservice-e2e-summary.json`：提交 `8c335eb` 的 UC01~UC09 9/9，标记 `EXISTING-EVIDENCE-VERIFIED`，不冒充本轮重跑。
- `e2e/runner.mjs`：7 个有状态 legacy API E2E 本轮未运行，避免在非隔离环境写业务数据，标记 `NOT-RUN`。

## Shell Verification Suites

仓库有 6 个 `scripts/test-*.sh` 套件，不并入 218 个 case。本轮安全执行并通过 5 个：changed-services、deploy-k8s、legacy-migrations、RabbitMQ durability、service data ownership。`test-order-service-mysql-contract.sh` 会创建订单并写数据库，本轮未启动隔离 Compose 环境，标记 `NOT-RUN`。

## Build / Config Verification

- `npm run build`：PASS；产生 782.15 kB JS chunk 警告，不影响构建结果。
- `docker compose config`：PASS；默认服务 8 个。
- `kubectl kustomize k8s`：PASS；27 个对象。
- 性能结果 validator：PASS（10 requests/repeat、concurrency 4、repeat 3）。
- `actionlint`：本机未安装，GitHub Actions 语法未由本轮 actionlint 复验。

## 解释限制

- 218 是源码中形式化 case 数，不代表 218 项都在同一环境本轮执行。
- 210 passed 混合了 201 个本轮本地通过和 9 个旧提交既有证据；严禁写成“当前 HEAD 210/218 全部通过”。
- Shell 脚本内部多个断言没有统一 case ID，因此只按 6 个套件统计。
