# D06 全组 CI/CD 流水线记录

> 任务日期：2026-08-31（Asia/Shanghai）
> 编制日期：2026-09-01（Asia/Shanghai）
> 关联任务：[#3](https://github.com/daihao007/Lumalife/issues/3)、[#41](https://github.com/daihao007/Lumalife/issues/41)、[#42](https://github.com/daihao007/Lumalife/issues/42)、[#43](https://github.com/daihao007/Lumalife/issues/43)、[#44](https://github.com/daihao007/Lumalife/issues/44)、[#45](https://github.com/daihao007/Lumalife/issues/45)

## 1. 统计口径

本记录按 GitHub Actions 的 UTC 日窗口统计：`2026-08-31T00:00:00Z` 至 `2026-09-01T00:00:00Z`，对应北京时间 2026-08-31 08:00 至 2026-09-01 08:00。D06 任务在北京时间 9 月 1 日凌晨完成的 #183/#35 收口运行一并纳入。

当天共记录 87 次 Actions 运行。该数字包含同一 PR 的重复运行、取消运行、条件跳过和历史遗留运行，不代表 87 个独立交付任务。

| 工作流 | 运行次数 | 成功 | 失败 | 取消 | 跳过 | 排队/等待 |
|---|---:|---:|---:|---:|---:|---:|
| Monolith CI | 33 | 26 | 3 | 4 | 0 | 0 |
| Deploy ECS K3s | 33 | 6 | 11 | 0 | 16 | 0 |
| Deploy Local Kubernetes | 16 | 0 | 0 | 13 | 1 | 2 |
| Microservices incremental delivery | 3 | 1 | 2 | 0 | 0 | 0 |
| `.github/workflows/deploy-ecs-k3s.yml` | 2 | 0 | 2 | 0 | 0 | 0 |
| **合计** | **87** | **33** | **18** | **17** | **17** | **2** |

## 2. D06 各成员任务流水线

| 成员/任务 | 代码或 PR | CI/CD 证据 | 最终判定 |
|---|---|---|---|
| daihao007 / #41 | [PR #119](https://github.com/daihao007/Lumalife/pull/119)，合并提交 [`1fab4ec`](https://github.com/daihao007/Lumalife/commit/1fab4ec811c2206bd5075bb963d23da50b4d4987) | [PR CI #178](https://github.com/daihao007/Lumalife/actions/runs/33407551708)；[main CI #179](https://github.com/daihao007/Lumalife/actions/runs/33407627625)；[ECS #31](https://github.com/daihao007/Lumalife/actions/runs/33409352978) | #41 已完成；主线 CI、Kubernetes 验收和 ECS 部署成功。主线 CI 完成时间：2026-08-31 23:35:49（北京时间） |
| Chrmysle / #42 | [网关契约适配提交 `b5d5d5e`](https://github.com/daihao007/Lumalife/commit/b5d5d5e9876634639df8a887a0f3fb8f8e8801e2) | [main CI #172](https://github.com/daihao007/Lumalife/actions/runs/33404490873)；[ECS #23](https://github.com/daihao007/Lumalife/actions/runs/33405361250) | #42 已完成；前端网关适配、核心回归和部署成功。Issue 已关闭 |
| yuwu-code / #43 | 暂无独立完成 PR | 共享主线 CI 中的 identity-service 骨架检查通过，但没有独立拆分 PR、回填验证和服务切流证据 | #43 仍开放，不能认定认证业务服务拆分完成 |
| ZQHtech / #44 | [PR #118](https://github.com/daihao007/Lumalife/pull/118)，合并后主线提交 [`efe71b7`](https://github.com/daihao007/Lumalife/commit/efe71b792f1b1eca4d4ed16bea7a3a555cf2741c) | [最终 PR CI #182](https://github.com/daihao007/Lumalife/actions/runs/33411603273)；[main CI #183](https://github.com/daihao007/Lumalife/actions/runs/33412649654)；[ECS #35](https://github.com/daihao007/Lumalife/actions/runs/33413574378) | #44 已完成；PR、主线 CI、Kubernetes 验收和 ECS 部署成功。最终部署完成时间：2026-09-01 00:21:53（北京时间） |
| Sun0720336 / #45 | [PR #116](https://github.com/daihao007/Lumalife/pull/116) | [增量流水线 #3](https://github.com/daihao007/Lumalife/actions/runs/33374906231)；[main CI #166](https://github.com/daihao007/Lumalife/actions/runs/33374906225) | 流水线验证曾成功，但 PR #116 已关闭且未合并，#45 仍开放，不能计为已交付 |

## 3. 最终主线验证结果

### 3.1 PR #119 合并后的主线 CI #179

验证提交为 `1fab4ec811c2206bd5075bb963d23da50b4d4987`，共 19 个 Job，全部成功：

- 后端 Maven 测试：79/79 通过；
- 前端 Vitest：6 个测试文件、31/31 通过；
- API E2E：7/7 通过；
- Real UI E2E：3/3 通过；
- identity、merchant、order 三个服务骨架检查：全部通过；
- 数据库 Schema 生命周期、Compose 三容器冒烟及持久化恢复：通过；
- Kubernetes 清单渲染、Quality gate：通过；
- backend、frontend、identity-service、merchant-service、order-service 五个镜像构建：全部通过；
- Kubernetes rollout smoke test 和 `Deploy Kubernetes`：通过；
- ECS 部署 [#31](https://github.com/daihao007/Lumalife/actions/runs/33409352978)：成功。

### 3.2 PR #118 合并后的主线 CI #183

验证提交为 `efe71b792f1b1eca4d4ed16bea7a3a555cf2741c`，共 19 个 Job，全部成功；测试规模与 #179 一致：后端 79/79、前端 31/31、API E2E 7/7、UI E2E 3/3，五个镜像构建、Kubernetes rollout 和 `Deploy Kubernetes` 均通过。

对应的 ECS 部署为 [#35](https://github.com/daihao007/Lumalife/actions/runs/33413574378)，结果成功。

### 3.3 最终运行产物

主线运行 #179 和 #183 均保留以下产物，可从对应 Actions 页面下载：

- `backend-test-reports`
- `frontend-test-reports`
- `frontend-dist`
- `api-e2e-reports`
- `ui-e2e-reports`
- `e2e-failure-injection-reports`
- `kubernetes-manifests`
- `ecs-deploy-bundle`

其中 `E2E failure injection evidence` 虽然会让被测服务按设计返回失败，但 Job 对预期失败进行了正确识别，因此最终状态为成功。

## 4. 失败、取消和未闭环记录

### 4.1 PR #117 回归修复未通过

[CI #171](https://github.com/daihao007/Lumalife/actions/runs/33402493336) 的普通测试、API/UI E2E 和镜像构建均通过，但 `Kubernetes rollout smoke test` 失败。原始日志显示 backend Pod 进入 `CrashLoopBackOff`，应用因无法从 MySQL 关系表加载业务状态而启动失败；后续 `Deploy Kubernetes` 被跳过。PR #117 仍开放，不能作为已完成证据。

### 4.2 PR #116 增量 Kubernetes 流水线

PR #116 的最终增量流水线 [#3](https://github.com/daihao007/Lumalife/actions/runs/33374906231) 成功，但此前 [#1](https://github.com/daihao007/Lumalife/actions/runs/33371963313) 和 [#2](https://github.com/daihao007/Lumalife/actions/runs/33373650946) 的 Kind smoke test 失败；对应的普通 CI 初次运行 [#161](https://github.com/daihao007/Lumalife/actions/runs/33371963573) 也失败。由于 PR 已关闭且未合并，#45 仍需重新形成可评审 PR。

### 4.3 历史 Local Kubernetes 等待运行

仓库中仍有两条旧的 `Deploy Local Kubernetes` 运行没有最终结论：

- [运行 #7](https://github.com/daihao007/Lumalife/actions/runs/33353978868)：`queued`；
- [运行 #21](https://github.com/daihao007/Lumalife/actions/runs/33375399608)：`pending`。

这两条运行不是本次最终主线验收路径；本次 D06 主线使用 GitHub-hosted Kind 的 `Kubernetes rollout smoke test` 和 `Deploy Kubernetes`，两者在 #179/#183 中均已成功。

## 5. 结论

截至本记录编制时：

1. #41 的第二阶段实施与追溯计划已通过 PR #119 合并，且合并后的主线 CI/CD 全部成功；
2. #42 和 #44 的最终主线 CI/CD 已成功并完成部署；
3. #43 尚无独立服务拆分完成证据；
4. #45 的流水线验证虽有成功运行，但没有合并交付；
5. PR #117 的 Kubernetes rollout 失败仍需修复；
6. 本记录证明的是当前提交的构建、测试、镜像和部署结果，不代表三个业务服务已经完成全量微服务切流。

本文件只记录流水线证据，不替代各 Issue 的实际完成状态、评审记录和业务功能验收。
