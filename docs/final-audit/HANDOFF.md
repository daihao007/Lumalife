# Final Audit Handoff

> 更新时间：2026-09-02；当前工作基线：`main@dc96528`。这是最终答辩审计的续做入口。

## 已完成

1. 完整阅读外部课程任务书：
   `/Users/daihao/Library/Containers/com.tencent.xinWeChat/Data/Library/Application Support/com.tencent.xinWeChat/2.0b4.0.9/1844852a624662cb07ed5ad495c76bf0/Message/MessageTemp/a7f17234a8f296dc003905bd353dd6b0/File/软件工程基础实践-2026夏.md`
2. 全仓只读扫描：代码、测试、Docker/Compose、CI、K8s、HPA、故障、性能、docs/PDF/图。
3. 本地真实执行：backend 93/93、services 71/71、Vitest 35/35、UI E2E 2/3；UI 客服往返失败。
4. 本地验证：frontend build PASS、Compose config PASS、Kustomize 27 对象 PASS、性能矩阵 validator PASS、5 个安全 Shell 套件 PASS。
5. 创建当前唯一事实与审计文档：
   - `docs/project-facts.md`
   - `docs/testing/test-inventory.md`
   - `docs/architecture/{microservices-inventory,data-ownership,service-communication}.md`
   - `docs/final-audit/{requirements-checklist,traceability-matrix,final-project-audit}.md`
   - `docs/README.md`
   - `docs/diagrams/README.md`
   - `docs/diagrams/final/current-architecture.mmd`
6. 校准根 README 的 Compose 8 服务、52 public API、65 internal API、218 tests 和真实失败。
7. 给正式测试/部署/需求/概要/详细/追溯文档加了权威性和历史范围提示；修正架构矩阵 order API 为 20 + 评价投影 1。

## 唯一关键数字

- Business Scenarios / Use Cases：9 / 9
- Business Microservices：4
- 后端应用服务 / 应用部署单元 / Compose 默认服务：5 / 6 / 8
- Public APIs / Internal APIs：52 / 65
- Unit/Component / Integration/API / E2E / Total：108 / 91 / 19 / 218
- 本轮本地执行：202；201 pass / 1 fail / 0 skipped
- 混合既有证据口径：210 pass / 1 fail / 0 skipped / 7 NOT-RUN
- K8s Deployment / Service / StatefulSet / HPA：7 / 11 / 4 / 2
- Performance interfaces / runs：3 / 18

## 当前唯一失败

`ui-e2e/tests/core-flows.spec.ts:71`：客服往返场景等待名称匹配 `/林夏/` 的会话按钮超时。失败 trace 位于 `ui-e2e/reports/test-results/`。未修改业务代码或测试来掩盖失败。

## 后续必须做（优先级顺序）

### P0

1. 诊断并修复 UI E2E 客服往返，然后重跑 `cd ui-e2e && npm test`，更新 Inventory。
2. 重写 `docs/12_软件需求规格说明书.md`、`13_概要设计说明书.md`、`14_详细设计说明书.md` 为当前微服务版本；重新导出 PDF 并视觉 QA。
3. 补 `05_management`：10 天站会、看板、任务证据；补 `06_defense`：PPT、技术总结、权重、全员确认。
4. 建立 52 public API -> API test 一对一矩阵并补缺口。

### P1

1. 在隔离环境重跑当前 HEAD 的 Microservice E2E 9 项，保存绑定 commit 的 artifact。
2. 当前 HEAD 触发完整 GitHub Actions；确认 UI 失败会阻断 quality gate，修复后取得成功 run。
3. 重跑性能 workflow，记录 commit/workflow，采全部容器而非只采 backend 资源。
4. 修正 `docs/HPA_EXPERIMENT_REPORT.md` 统一证据目录断链，以及 NIGHTLY 中 120 秒负载/180 秒冷却混写。
5. 将 27 张历史三层图从 CR/UC 混合编号迁到统一 `SYS-SEQ/COMP-SEQ/OBJ-SEQ`，并生成当前微服务版可编辑图和导出图。

## 不得做

- 不把旧提交的 9/9 写成本轮结果。
- 不把 timeout/503 写成 circuit breaker。
- 不改服务器状态、不重新压力测试共享服务器、不做服务器故障注入。
- 不把旧 PDF、旧 SHA、40/59/78/92 等数字当当前事实。

## 复验命令

```bash
cd backend && mvn test
cd ../services && mvn test
cd ../frontend && npm test -- --reporter=verbose && npm run build
cd ../ui-e2e && npm test
cd ..
docker compose config --services
kubectl kustomize k8s >/tmp/lumalife-k8s-audit.yaml
bash 04_tests/performance/validate-results.sh 04_tests/performance/results/nightly-20260902 10 4 3
bash scripts/test-detect-changed-services.sh
bash scripts/test-deploy-k8s.sh
bash scripts/test-legacy-migrations.sh
bash scripts/test-rabbitmq-durability.sh
bash scripts/test-service-data-ownership.sh
```

`scripts/test-order-service-mysql-contract.sh` 会写订单数据；只在明确隔离、可销毁的 Compose test stack 中执行。

## 当前工作树注意事项

- 用户原有未跟踪目录：`tmp/`；不要删除或覆盖。
- 本轮只改文档/README，没有修改业务代码、服务器或原始实验数据。
