# Final Audit Handoff

> 更新时间：2026-09-03；本轮接力同步基线：`main@3696741`；R26 工具审计绑定该提交。接力时以 `git log -1` 和远端同步结果为准；这是最终答辩审计的续做入口。

## 同步与提交状态

- 当前分支：`main`
- 远端：`origin = https://github.com/daihao007/Lumalife.git`
- 远端并行提交：`d1792e0`、`36917b2`、`e75b3ed`，内容为 CI/CD 自动交付验证、Maven cache 串行化和 GitHub cache export 容错。
- 合并提交：`1a99a78`，使用普通 merge 合入 `origin/main`，无冲突、未覆盖远端历史。
- 最新已推送的文档交付提交：`01de84a`（当前软件开发计划与 PDF）、`2cf1f83`（当前用户手册与 PDF）。R26 提交以本文所在的当前 Git 历史为准。
- 最新交接提交：本文件所在提交；推送完成后以 `git rev-list --left-right --count origin/main...HEAD` 返回 `0 0` 为同步完成标准。
- 工作区要求：现有未跟踪 `tmp/` 不删除、不覆盖、不提交；已跟踪文件应保持干净。

## 已完成

1. 完整阅读外部课程任务书：
   `/Users/daihao/Library/Containers/com.tencent.xinWeChat/Data/Library/Application Support/com.tencent.xinWeChat/2.0b4.0.9/1844852a624662cb07ed5ad495c76bf0/Message/MessageTemp/a7f17234a8f296dc003905bd353dd6b0/File/软件工程基础实践-2026夏.md`
2. 全仓只读扫描：代码、测试、Docker/Compose、CI、K8s、HPA、故障、性能、docs/PDF/图。
3. 前序本地真实执行：`dc96528` 上 services 71/71、Vitest 35/35；`9f0a755` UI E2E 3/3；本次 API 覆盖工作树 backend 96/96。
4. 本地验证：frontend build PASS、Compose config PASS、Kustomize 27 对象 PASS、性能矩阵 validator PASS、5 个安全 Shell 套件 PASS。
5. 创建当前唯一事实与审计文档：
   - `docs/project-facts.md`
   - `docs/testing/test-inventory.md`
   - `docs/architecture/{microservices-inventory,data-ownership,service-communication}.md`
   - `docs/final-audit/{requirements-checklist,traceability-matrix,final-project-audit}.md`
   - `docs/README.md`
   - `docs/diagrams/README.md`
   - `docs/diagrams/final/current-architecture.mmd`
6. 校准根 README 的 Compose 8 服务、52 public API、65 internal API、221 tests 和真实验证来源。
7. 给正式测试/部署/需求/概要/详细/追溯文档加了权威性和历史范围提示；修正架构矩阵 order API 为 20 + 评价投影 1。
8. 修复 UI 客服 E2E：注册辅助函数改为等待确定的 `#/profile` 路由，客服场景注册唯一用户并按唯一昵称定位会话，避免固定种子用户状态污染与注册/路由竞态；连续两次完整 Playwright 均 3/3 通过（12.4s、11.1s）。
9. 建立 `docs/testing/public-api-coverage-matrix.md`：52/52 公开业务映射均关联直接 MockMvc API 测试；新增 3 个 Spring API case 补齐地址/收藏/购物车清理、商家目录删除、客服往返和公共助手缺口。`ApiSecurityIntegrationTest` 28/28、backend 全量 96/96 通过。
10. 将 `docs/12_软件需求规格说明书.md`、`13_概要设计说明书.md`、`14_详细设计说明书.md` 重写为当前 BFF + 4 业务微服务版本；新增 `scripts/export-final-docs.py`，重新生成三份正式 PDF。PDF 分别为 8/7/8 页，A4、无文本空页，已使用 Poppler 渲染全部 23 页并完成视觉 QA，未见裁切、越界或乱码。
11. 只读核验公开 GitHub Project #2 和 Issue #16～#65，建立 `docs/05_management/`：确认 50 个十日任务、五人各 10 项/70 计划工时、D01～D08 40 项 Done；同时明确站会勾选仅 9/50、只覆盖 4 个实践日，仓库每日截图仅 D01/D02 共 4 张。没有倒填站会、实际工时或历史截图。
12. 建立 `docs/06_defense/` 最终答辩交付包：生成 13 页可编辑 PPTX、当前技术总结和个人权重/全员确认待签模板。PPTX 包结构检查通过（13 slides / 13 notes / 13 `[Sources]`），13/13 页已渲染并逐页视觉检查，导出布局检查为 0 个越界元素。权重与确认仍保持 `UNVERIFIED`，没有代填或代签。
13. 将 `docs/07_测试报告.md` 从 2026 年 6 月单体阶段记录重写为当前测试资产、52/52 API 覆盖和证据分层口径；更新导出脚本支持单文档生成，重新生成 5 页 A4 正式 PDF。PDF 文本提取 5/5 非空，Poppler 全页渲染并完成视觉 QA，未见裁切、溢出、乱码或空白页。本项遵循用户“停止测试”指示，没有运行任何测试。
14. 将 `docs/08_部署文档.md` 从单体/Redis 历史说明重写为当前 8 服务 Compose、3 个服务数据库、27 个静态 Kubernetes 对象、不可变镜像和 CI/ECS 证据边界；重新生成 6 页 A4 正式 PDF。PDF 文本提取 6/6 非空，Poppler 全页渲染并完成视觉 QA，首轮孤页已修正，未见裁切、溢出、乱码或空白页。本项没有启动容器、集群、服务器或测试。
15. 将 `docs/09_用户手册.md` 校准为当前角色路由、业务操作、6 个演示账号及默认 `prod,remote` 持久化口径；扩展 `scripts/export-final-docs.py` 支持用户手册、Markdown 本地图片和跨平台中文字体，重新生成 12 页 A4 正式 PDF。PDF 12/12 页文本非空、嵌入 8/8 张截图，Poppler 全页渲染并完成视觉 QA，未见裁切、重叠、越界、乱码或图注分离。本项没有启动容器、服务或测试。
16. 从 17 页历史 PDF 重建 `docs/10组-软件开发计划书.md`，校准为 9 用例、BFF + 4 微服务、三服务数据库、52/65 API、221 测试资产、27 个 K8s 对象和 350 计划工时口径；明确 D09/D10、管理原件、当前 CI/E2E 和签字的未验证边界。重新生成 10 页 A4 正式 PDF，10/10 页文本非空并完成 Poppler 全页视觉 QA，未见裁切、重叠、表格越界、乱码或异常孤页。
17. 校准 `.env.example` 为当前 MySQL 三服务库、RabbitMQ、内部 Token、微服务开关与 Agnes 变量，移除已退出当前架构的 Redis/JWT 样例；重写 AI/开源说明并新增前序正则 Secret 静态审计报告。当时受跟踪文本与 Git 文本历史高置信特征 0 命中，专用工具缺口已由第 22 项继续处理。
18. 推送前发现远端 `main` 比本地多 3 个提交，本地比远端多 7 个交付提交；先只读检查差异，再以普通 merge 合入远端，生成 `1a99a78`。远端改动涉及 `.github/workflows/ci.yml`、`.github/workflows/services-cd.yml`、frontend 与四个服务 Dockerfile；合并无冲突，没有 force push 或覆盖同学提交。
19. 将 27 个历史 Mermaid 图源、27 个历史 SVG 和 3 份 D03 分层说明从 CR/UC 混合文件名迁移为 `UCxx-{SYS|COMP|OBJ}-SEQxx`，同步修正 D03/D04/需求矩阵/统一追溯表的链接与标识；历史 CR 映射文字仍保留。迁移没有把 `DemoStore` 单体图冒充为当前微服务图；当前微服务逐用例图另见第 21 项。同时修正最终追溯矩阵中过期的“UC08 UI 当前失败”为 `9f0a755` 3/3，但当前 remote E2E 仍未重跑。
20. 修正 `docs/HPA_EXPERIMENT_REPORT.md` 的原始证据断链：现在逐项直接链接实际存在的 `04_tests/cloud-native/hpa-observation-20260902-8c335eb*` 文件，并将 `NIGHTLY_SECOND_STAGE_EXECUTION_REPORT.md` 的统一索引改为 `04_tests/evidence/README.md`。未修改原始实验产物、未连接集群、未重跑压力实验。
21. 按当前 BFF + 4 业务微服务边界补齐 UC01~UC09 的 27 个当前三层 Mermaid 源和 27 个 SVG，入口为 `docs/diagrams/final/use-cases/README.md`；同步更新逐项追溯链接，R03/R04 已由“待补当前图”调整为已完成。
22. 在 `main@3696741` 使用校验过发布包 SHA-256 的 Gitleaks 8.30.1 与 Syft 1.51.0 完成 R26 工具审计：当前 616 个受跟踪文件快照与全部 233 个 Git 提交均为 0 finding；生成 SPDX 2.3 SBOM，识别 287 个包条目。249 项有许可证声明，38 项为 `NOASSERTION`；仓库无根 LICENSE，因此只闭环 Secret scanner 与基础 SBOM，不虚构全许可证合规。
23. 使用经官方 checksums 校验的 Grype 0.118.0 和 2026-09-02 构建的有效数据库扫描同一 `3696741` SPDX SBOM：发现 11 个唯一 npm match，0 Critical、6 High、4 Medium、1 Low，11 项均标记有修复版本。原始 JSON、数据库和工具只留在未跟踪 `tmp/`；没有启动服务或测试。该扫描不覆盖 Maven 完整传递依赖、实际容器镜像或漏洞可达性，R26 保持 PARTIAL。

## 唯一关键数字

- Business Scenarios / Use Cases：9 / 9
- Business Microservices：4
- 后端应用服务 / 应用部署单元 / Compose 默认服务：5 / 6 / 8
- Public APIs / Internal APIs：52 / 65
- Unit/Component / Integration/API / E2E / Total：108 / 94 / 19 / 221
- 最新逐套本地结果：205；205 pass / 0 fail / 0 skipped（backend 96 为本次工作树，services/Vitest 为 `dc96528`，UI 3/3 为 `9f0a755`，非同一提交一次性全量重跑）
- 混合既有证据口径：214 pass / 0 fail / 0 skipped / 7 NOT-RUN
- K8s Deployment / Service / StatefulSet / HPA：7 / 11 / 4 / 2
- Performance interfaces / runs：3 / 18

## 当前失败与未验证

当前没有已执行测试失败。backend 本次工作树 96/96，UI E2E 在 `9f0a755` 3/3。Microservice E2E 因 Docker 引擎未就绪且用户要求停止，尚未启动任何容器或场景，无新 artifact；services/Vitest 仍沿用 `dc96528` 前序证据。合并远端 CI 修复后的当前 HEAD 没有重跑测试或取得完整 GitHub Actions 成功证据，不得写成当前 HEAD 全部通过。

非测试审计存在 11 个开放 npm 漏洞 match：6 High、4 Medium、1 Low。它们尚未升级或完成可达性/VEX 判断，不得标记为已修复、误报或风险接受。

## 后续必须做（优先级顺序）

### P0

1. 当前提交必要测试仍未同批完成；Microservice E2E 已按用户要求停止，恢复前必须重新确认本地 Docker 隔离环境并保存 commit-bound artifact。
2. `05_management` 索引和缺口矩阵已建立；仍需项目成员提供 D03～D10 站会/截图原件并真实完成 D09/D10。`06_defense` 的 PPT 与技术总结已完成；仍需五名成员本人填写个人权重并签署全员确认。

### P1

1. 单独升级前端依赖以消除 11 个 Grype npm match，至少优先关闭 6 个 High；更新 lockfile 后必须恢复并执行前端单测、build、UI E2E，再重建 SBOM 和复扫。
2. 当前 HEAD 触发完整 GitHub Actions；确认修复后的 UI 3/3 并取得成功 quality gate run。
3. 重跑性能 workflow，记录 commit/workflow，采全部容器而非只采 backend 资源。
4. HPA 报告断链已修正；NIGHTLY 中 120 秒负载/180 秒冷却现已标明为同一最终验收批次的两个参数。仍需保留联合场景为未覆盖。
5. 27 张历史三层图编号已迁移；当前微服务版逐用例三层图已生成并完成 Mermaid CLI 导出、SVG XML 校验和抽样视觉检查，后续只需答辩前复核链接和内容。

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

- 现有未跟踪目录：`tmp/`；不要删除、覆盖或提交。
- 同步前本地 11 个已跟踪改动和 1 个未跟踪文件已安全保存在 `stash@{0}`，名称 `pre-final-audit-sync-2026-09-03`，未删除。
- 服务器 `/opt/Lumalife` 为 `main@a698c8e` 且有 5 项未跟踪实验材料；本轮仅只读查看 status/branch/log，未修改服务器。
- 本项新增 3 个 backend API 测试、52 项矩阵并更新事实/审计文档；没有修改业务代码或原始实验数据。
- 本项校准需求、概要、详细三份核心正式 Markdown，新增可复现 PDF 导出脚本；后续完成测试报告、部署文档、用户手册和软件开发计划书。当前七份正式 PDF 均有可编辑源并完成逐页 QA，`tmp/` 未提交。
- 本项新增 `docs/05_management/` 两份管理材料，只读查询 GitHub Project/Issue；没有修改远端 Project、Issue、PR 或补造历史证据。
- 本项新增 `docs/06_defense/` 的 13 页可编辑 PPTX、技术总结、交付说明和待签确认模板；PPTX 已完成 13/13 页视觉 QA 与 0 越界布局检查。`slides_test.py` 所调用的 Windows 渲染进程在写完全部 13 张图片后异常退出，因此未将该命令记为 PASS；改以完整渲染产物、PPTX ZIP 结构、布局 JSON 和逐页人工检查留存真实结论。
- 本项校准 `docs/07_测试报告.md` 并重生成 `docs/07_测试报告.pdf`；正式 PDF 5 页、A4、无文本空页，已全页视觉检查。`tmp/` 仅存 QA 中间产物，未提交。
- 本项校准 `docs/08_部署文档.md` 并重生成 `docs/08_部署文档.pdf`；正式 PDF 6 页、A4、无文本空页，已全页视觉检查。`tmp/` 仅存 QA 中间产物，未提交。
- 本项校准 `docs/09_用户手册.md` 并重生成 `docs/09_用户手册.pdf`；正式 PDF 12 页、A4、12/12 页文本非空、8/8 张截图已嵌入并完成全页视觉检查。导出脚本已支持 macOS/Windows/Linux 中文字体。未启动容器或测试。
- 本项新增 `docs/10组-软件开发计划书.md` 并覆盖历史同名 PDF；正式 PDF 10 页、A4、10/10 页文本非空并完成全页视觉检查。350 小时仅标为计划工时，D09/D10 与实际投入未被提前闭环。
- 本项新增 `docs/security/gitleaks-sbom-license-audit-2026-09-03.md` 与绑定 `3696741` 的 SPDX SBOM；Gitleaks 原始空报告和工具二进制仅存 `tmp/`，不提交。服务器只读复核仍为 `main@a698c8e` 且有 5 项未跟踪 HPA/视频材料，本轮未修改服务器。
- 本项新增 `docs/security/vulnerability-scan-2026-09-03.md`；Grype 扫描原始 JSON、数据库和工具仅存 `tmp/`，不提交。结果为 11 个开放 npm match（6 High、4 Medium、1 Low），未运行测试、未修改依赖。
- 推送前已获取并合并 `origin/main@e75b3ed`，保留远端 3 个并行提交；未使用 rebase、reset、checkout 覆盖或 force push。合并后未重新执行测试，当前 HEAD 验证状态保持 NOT-RUN/UNVERIFIED 边界。

## 下一位接力者的第一步

按用户“停止测试”的指示，不继续启动 Docker 或 E2E。Gitleaks、基础 SPDX SBOM 和源码 SBOM 漏洞扫描已完成；下一项可独立推进的是单独升级并复核 11 个 npm match，或解析 38 个 `NOASSERTION` 并补 Maven/容器许可证与漏洞扫描。需要成员参与的 P0 仍是填写 `docs/06_defense/contribution-signoff.md`、提供 D03～D10 站会/截图原件并真实完成 D09/D10。若恢复 Microservice E2E，必须使用本地隔离、可销毁环境并保存绑定提交的原始 artifact，不得使用服务器旧提交 `a698c8e` 冒充当前结果。
