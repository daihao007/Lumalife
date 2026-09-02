# Requirements Checklist

> 课程基准：《软件工程基础实践》课程任务书（2026 夏季学期），本机外部来源 `软件工程基础实践-2026夏.md`。仓库内没有任务书副本；若授权允许，答辩交付前应纳入 `02_docs`。接力基线：`main@71d74a6`；本次 UI 修复提交见当前 Git 历史。

| ID | 课程要求 | 对应评分项 | 当前状态 | 代码/配置证据 | 测试/实验证据 | 文档证据 | 是否需要修改 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| R01 | 保留改造前版本与 Git 标签 | 交付 | ✅ 已完成并验证 | `monolith-start@3eb8f44` | 标签可解析 | README/project facts | 否 |
| R02 | 最终 UC 清单经教师/助教确认 | 文档/中期 | ⚠️ 基本完成但证据/文档存在问题 | 9 个 UC 对应当前代码 | 9 个远程 E2E 旧提交证据 | `16_业务场景用例清单.md` | 是：补确认原件 |
| R03 | 9 个 UC 的需求、系统/组件/对象三层模型 | 文档 10 | ⚠️ 基本完成但证据/文档存在问题 | 相关类仍存在 | 27 个 mmd/27 个 SVG | d03/d04 为单体基线且编号混用 | 是 |
| R04 | 同表追溯 REQ/UC/三层设计/代码/测试 | 文档 10 | ⚠️ 基本完成但证据/文档存在问题 | 当前服务代码已索引 | 当前结果已更新 | `traceability-matrix.md`；历史 22 过期 | 是：迁移旧编号 |
| R05 | 有断言的单元测试并实际运行 | 测试 10 | ✅ 已完成并验证 | 108 Unit/Component case | LOCAL-VERIFIED 108/108 | `testing/test-inventory.md` | 否 |
| R06 | 集成/API 覆盖主、备选和异常 | 测试 10 | ✅ 已完成并验证 | 94 Spring API/integration case | backend 96/96；核心权限、参数、状态、幂等异常 | `testing/public-api-coverage-matrix.md` | 否 |
| R07 | E2E 覆盖全部 UC 且当前版本通过 | 测试/用例回归 | ⚠️ 基本完成但证据/文档存在问题 | 19 个 E2E 场景 | UI 3/3 已在本次工作树通过；remote 9/9 为旧提交；legacy 7 NOT-RUN | Inventory | 是：当前提交重跑隔离 Microservice E2E |
| R08 | 测试报告写清总数、结果、环境和失败原因 | 测试 10 | ✅ 已完成并验证 | 当前 runner 可复现 | 最新逐套 205 次执行结果已记录并区分提交 | `testing/test-inventory.md` | 否 |
| R09 | 前端、后端、数据库容器化 | 容器化 8 | ✅ 已完成并验证 | 7 Dockerfile；Compose 8 默认服务 | `docker compose config` PASS | README | 否 |
| R10 | 数据库自动初始化、迁移与测试数据 | 容器化 8 | ✅ 已完成并验证 | V001~V019、seed/clean/verify | CI 配置和脚本静态验证 | database/README 相关文档 | 否 |
| R11 | push 后八步 CI/CD 且失败阻断 | 原系统 CI/CD 10 | ⚠️ 基本完成但证据/文档存在问题 | quality-gate -> images -> smoke -> deploy | 历史成功/失败记录存在；UI 本地 3/3，但当前 HEAD CI 未重跑 | `.github/workflows/ci.yml` | 是：当前提交 CI |
| R12 | 镜像有版本号，不只 latest | CI/CD | ✅ 已完成并验证 | CI 发布 `sha-*` + `main`，部署使用 `sha-*` | 脚本拒绝残余 `:main` | project facts | 否 |
| R13 | 至少 3 个真正业务微服务 | 微服务划分 7 | ✅ 已完成并验证 | identity/merchant/order/assistant | 71 服务测试通过 | microservices inventory | 否 |
| R14 | 说明服务业务/数据边界和拆分理由 | 微服务划分 7 | ✅ 已完成并验证 | 4 服务独立 Maven 模块 | 独立 verify 配置 | microservices inventory | 否 |
| R15 | 表唯一归属，不跨服务直接访问 | 数据归属 5 | ✅ 已完成并验证 | 三服务数据库与 23 张归属表 | ownership shell suite PASS | data ownership | 否 |
| R16 | 跨服务接口/事件及失败处理说明 | 数据归属 5 | ✅ 已完成并验证 | HTTP + RabbitMQ Outbox/Inbox/Saga | 故障/服务测试证据 | service communication | 否 |
| R17 | 所有后端公开接口均有 API 测试 | 用例回归 4 | ✅ 已完成并验证 | public API 52 | 52/52 直接 MockMvc API 证据；ApiSecurity 28/28 | `testing/public-api-coverage-matrix.md` | 否 |
| R18 | 微服务自动构建、测试、镜像、K8s 部署/探针/版本 | 微服务流水线 5 | ✅ 已完成并验证 | CI、7 Deployment、探针、sha tag | Kustomize PASS；历史 CI 证据 | project facts | 否 |
| R19 | HPA 压力扩容、冷却缩容及完整指标 | 自动扩缩容 3 | ✅ 已完成并验证 | 2 HPA；merchant 为实验目标 | 旧提交 raw CSV 1->2->3->1、14,467/0 | HPA report/raw | 是：修正断链 |
| R20 | 停止依赖后设计结果且其他服务不崩 | 故障处理 2 | ✅ 已完成并验证 | timeout/503/readiness isolation | raw 200->503->200，backend ready 200 | fault summary | 是：强化脚本 gate |
| R21 | 2~3 API，同机同数据同脚本，两模式各 3 次 | 性能对比 4 | ⚠️ 基本完成但证据/文档存在问题 | 3 API/2 模式/18 runs | validator PASS；180/0 | commit/workflow/full-stack resources 缺失 | 是 |
| R22 | README 环境、端口、启动、健康、账号、初始数据准确 | 交付 6 | ⚠️ 基本完成但证据/文档存在问题 | 根 README 已校准核心入口 | Compose/Kustomize 本轮验证 | 旧部署文档仍过期 | 是 |
| R23 | 10 天站会、看板、任务、每日截图证据 | 项目管理 4 | ⚠️ 基本完成但证据/文档存在问题 | GitHub Project #2：50 任务、五人各 10 项/70 计划工时；D01～D08 共 40 Done | 站会已勾选 9/50，仅覆盖 4 个实践日；仓库每日截图仅 D01/D02 共 4 张 | `05_management/README.md`、十日证据矩阵 | 是：补 D03～D10 截图和站会原件，完成 D09/D10 |
| R24 | 可编辑文档、当前 PDF、模型源文件齐全 | 交付 6 | ⚠️ 基本完成但证据/文档存在问题 | mmd 源存在；12/13/14 已校准 | 三份核心 PDF 8/7/8 页已生成并逐页视觉 QA | 其余四份 PDF 仍为历史导出 | 是：补测试/部署/用户等当前 PDF |
| R25 | PPT、技术总结、个人权重、全员确认 | 演示/答辩 10 | ⚠️ 基本完成但证据/文档存在问题 | 可编辑 13 页 PPTX、当前技术总结 | PPTX 13/13 页渲染并逐页视觉检查；布局 0 越界 | `06_defense` 已建立；权重/确认模板待签 | 是：五名成员填写权重并签署确认 |
| R26 | AI 使用说明、开源来源、无 Secret 泄露 | 学术诚信 | ⚠️ 基本完成但证据/文档存在问题 | env/Secret 方式存在 | 未做完整 secret scanner | `10_AI使用说明.md` | 是 |
| R27 | 最终审计、唯一事实源和风险清单 | 本轮交付 | ✅ 已完成并验证 | 当前代码/配置证据 | 本轮真实结果 | `project-facts.md`、final audit | 否 |

## 统计

- ✅ 已完成并验证：16
- ⚠️ 基本完成但证据/文档存在问题：11
- ❌ 未完成：0
- ❓ 当前无法验证：0
