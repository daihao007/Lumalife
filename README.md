# LumaLife 综合生活助手平台

LumaLife 是一个面向课程验收场景的本地生活服务平台演示项目，覆盖用户消费闭环、商家履约闭环和平台观测闭环。

> 最终答辩事实口径（2026-09-03，接力基线 `main@71d74a6`）：关键数字、验证来源与未完成项统一见 [`docs/project-facts.md`](docs/project-facts.md) 和 [`docs/final-audit/final-project-audit.md`](docs/final-audit/final-project-audit.md)。带日期的阶段报告与旧 PDF 是历史证据，不代表当前 HEAD。

## 技术栈

- 后端：Java 17+、Spring Boot 3、Spring Security、Maven、JUnit 5
- 前端：React 18、TypeScript、Vite、CSS Modules 风格的全局设计系统
- 部署：Docker Compose、Kubernetes/Kustomize、Nginx、MySQL 8.4、版本化数据库迁移

## 当前实现范围

- CR-01：普通用户注册、演示账号登录、角色鉴权、当前用户信息、昵称与头像 URL 维护
- CR-01：用户地址管理，最多 5 个地址，支持默认地址
- CR-02：分类、商家搜索、推荐排序、商家详情
- CR-03：购物车加购、数量修改、删除/清空、外卖下单、模拟支付、待支付取消、订单状态时间线
- CR-04：团购下单、券码生成、商家核销
- CR-05：完成订单评价、重复评价限制、商家评分更新
- CR-06：商家工作台、订单处理、团购核销、平台看板
- CR-06：商家商品和团购套餐新增、上架、下架
- 加分项：规则型 AI 客服、系统健康状态、操作日志、推荐理由

## 快速启动

### Docker Compose 全栈启动（推荐）

干净环境只需 Docker Engine 24+ 和 Docker Compose v2。首次启动会构建前后端镜像，并在空 MySQL 数据卷中自动创建 Schema；无需手工执行迁移命令。

```bash
cp .env.example .env
# 按需替换 .env 中的示例密码；不要提交 .env
docker compose up --detach --build --wait
docker compose ps
```

`docker compose ps` 应显示 8 个默认服务：`mysql`、`rabbitmq`、`backend`、`frontend`、`identity-service`、`merchant-service`、`order-service`、`assistant-service`。

- 前端：http://localhost:5173
- 后端：http://localhost:8080
- 后端健康检查：http://localhost:8080/actuator/health
- 前端代理健康检查：http://localhost:5173/actuator/health

校验数据库已自动初始化且迁移 checksum 一致：

```bash
docker compose --profile db-tools run --rm db-migrate
```

如需把演示测试数据写入 MySQL，可显式执行（生产环境禁止执行）：

```bash
docker compose --profile db-tools run --rm db-seed
```

停止服务但保留数据库数据：

```bash
docker compose down
```

如需模拟干净环境并从空库重新验收：

```bash
docker compose down --volumes --remove-orphans
docker compose up --detach --build --wait
```

### 源码开发启动

本地源码开发需要 Java 17+、Maven 3.9+、Node.js 18+ 和 npm 9+。依赖安装使用仓库中的 lockfile；首次安装前端依赖请使用 `npm ci`。

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm ci
npm run dev
```

- 前端：http://localhost:5173
- 后端：http://localhost:8080
- 健康检查：http://localhost:8080/actuator/health

### 数据库生命周期工具

默认容器运行模式为 `prod,remote`：identity、merchant、order 分别写入 `life_assistant_identity`、`life_assistant_merchant`、`life_assistant_order`，assistant 无业务数据库；库存操作通过 RabbitMQ Outbox/Inbox/Saga 协作。`JdbcBusinessStateRepository`、`DemoStore` 与 legacy `life_assistant` 仅用于 `monolith` 基线、迁移和回滚，不是默认生产事实源。手工迁移、seed、校验工具仍可独立执行：

```bash
docker compose --profile db-tools run --rm db-migrate
docker compose --profile db-tools run --rm db-seed
docker compose --profile db-tools run --rm db-verify
```

迁移可重复执行，已经应用的文件会校验 SHA-256 后跳过。`db-seed` 只在显式调用时装载演示弱密码账号，不会随生产迁移自动执行。清空业务数据但保留 Schema 和迁移历史：

```bash
docker compose --profile db-tools run --rm db-clean
```

## 演示账号

| 角色 | 手机号 | 密码 |
|---|---|---|
| 普通用户 | 13800000001 | abc123456 |
| 巷口川味研究所 | 13800000002 | abc123456 |
| 晨雾咖啡局 | 13800000003 | abc123456 |
| 绿盒轻食 | 13800000004 | abc123456 |
| 栗香烘焙室 | 13800000005 | abc123456 |
| 平台管理员 | 13800000000 | admin123456 |

## 演示流程

1. 用户登录后搜索商家，进入详情，添加菜品到购物车。
2. 在购物车选择收货地址、调整数量并创建外卖订单，随后模拟支付或取消待支付订单。
3. 商家管理员登录，在工作台接单、配送、完成订单，用户订单页展示状态时间线。
4. 用户提交评价，商家评分自动更新。
5. 用户购买团购套餐，支付后获得 12 位券码。
6. 商家后台输入券码核销。
7. 平台管理员查看用户、商家、订单、交易额、系统健康和操作日志。

## 项目结构

```text
LumaLife/
  backend/      Spring Boot API 服务
  services/     identity、merchant、order、assistant 四服务的独立构建、内部 API 与健康检查
  database/     MySQL 版本迁移、显式演示 seed、清理和验证脚本
  frontend/     React/Vite 前端，已按 App、api、types、pages、components 拆分
  e2e/          独立真实 HTTP 黑盒 E2E 运行器与报告输出
  04_tests/     HPA 故障/扩缩容实验和 monolith/microservices 性能对比脚本
  docs/         需求、设计、接口、部署、测试与用户手册
  .ai/          AI 使用说明、prompts 与项目 skills
  scripts/      初始化与演示辅助脚本
```

## 持续集成

[![Monolith CI](https://github.com/daihao007/Lumalife/actions/workflows/ci.yml/badge.svg)](https://github.com/daihao007/Lumalife/actions/workflows/ci.yml)
[![Deploy ECS K3s](https://github.com/daihao007/Lumalife/actions/workflows/deploy-ecs-k3s.yml/badge.svg)](https://github.com/daihao007/Lumalife/actions/workflows/deploy-ecs-k3s.yml)
[![Performance comparison](https://github.com/daihao007/Lumalife/actions/workflows/performance.yml/badge.svg)](https://github.com/daihao007/Lumalife/actions/workflows/performance.yml)

向 `main` 提交 PR 时会自动执行后端测试、前端构建、MySQL 数据生命周期验证、Compose 冒烟测试、API E2E、Kubernetes 清单渲染、镜像构建和临时 Kind 集群部署。代码进入 `main` 且全部检查通过后，流水线会发布带 `sha-<短提交号>` 标签的六个应用镜像（backend、frontend、identity、merchant、order、assistant），并在 GitHub 托管 Runner 的一次性 Kind 集群完成最终验收部署与健康检查；该验收部署不依赖校园电脑或 `KUBE_CONFIG_BASE64`，作业结束后集群会销毁。CI 全部成功后，ECS 自托管 Runner 会把同一版本自动部署到阿里云 ECS 的 k3s 集群，并执行集群内健康检查。Kubernetes 的内部服务令牌和 RabbitMQ 凭据由部署脚本创建的 `lumalife-runtime` Secret 注入，不写入清单。详细说明见 [原系统 CI 构建、测试和镜像流水线](docs/15_%E5%8E%9F%E7%B3%BB%E7%BB%9FCI%E6%B5%81%E6%B0%B4%E7%BA%BF%E8%AF%B4%E6%98%8E.md)、[Kubernetes 自动部署与健康检查](docs/19_D05_Kubernetes%E8%87%AA%E5%8A%A8%E9%83%A8%E7%BD%B2%E4%B8%8E%E5%81%A5%E5%BA%B7%E6%A3%80%E6%9F%A5.md) 和 [ECS 自动部署配置](docs/26_ECS自动部署配置.md)。

## 当前工程状态

- 前端入口已从单文件拆分为 `App.tsx`、`api.ts`、`types.ts`、`utils.ts`、`pages/` 和 `components/`，业务行为保持不变。
- 后端 Controller 已按认证、商家目录、购物车、订单、商家商品后台、订单履约后台、管理员看板和 AI 客服拆出 Service 门面。当前公共业务 API 为 52，均有直接 MockMvc API 测试，逐项证据见 [`docs/testing/public-api-coverage-matrix.md`](docs/testing/public-api-coverage-matrix.md)；内部业务 API 为 identity 13、merchant 30、order 21（含评价投影）、assistant 1，共 65。服务库按 identity/merchant/order 归属隔离；backend 是 BFF 入口，兼容能力保留在 `DemoStore`。当前边界见 [`docs/architecture/`](docs/architecture/) 三表。
- 形式化源码测试共 221：Unit/Component 108、Integration/API 94、E2E 19。最新逐套本地结果为 205 项全通过，但跨多个明确记录的提交/工作树；旧提交另有 Microservice E2E 9/9 既有证据，legacy E2E 7 项未运行。唯一口径见 [`docs/testing/test-inventory.md`](docs/testing/test-inventory.md)，不得沿用历史 40/59/78/92 等数字，也不得写成“当前 HEAD 221 项全部通过”。
- MySQL Schema、V001～V019 版本迁移、演示 seed、清理机制和关系表业务读写已落地，见 `docs/06_数据库设计.md`；V001～V018 为已存在的基线/渐进迁移，V019 补充库存释放结果去重、过期预占重试字段和 `CHECK_REQUIRED`/`RELEASE_FAILED` 状态约束。CI 会直接检查购物车关系行，并在重启后端后通过 API 验证恢复结果。
- 单体后端代码审计与用户认证、商家商品、订单三服务拆分草案见 `docs/15_单体后端审计与三服务拆分草案.md`；该草案是历史设计快照，明确排除骑手领域，当前四服务事实以 D07/架构矩阵为准。
- 四服务的完整外部/内部 API、Schema 数据归属、错误码、事件和契约测试冻结候选见 `docs/16_三服务接口数据归属与契约草案.md`；OpenAPI/AsyncAPI 文件位于 `docs/contracts/`。库存预占/确认/释放已由 merchant-service 通过 RabbitMQ transactional Outbox/Inbox 实现至少一次投递和幂等消费，释放结果明确区分 `RELEASED`、`CHECK_REQUIRED`、`RELEASE_FAILED`，order Saga 不会把人工核对写成 `RELEASED`。
- D03 服务边界落地、错误响应兼容和可运行契约样例见 `docs/17_D03服务边界落地记录.md`。
- D05 中期全量测试报告与失败注入记录见 `docs/19_D05中期全量测试报告_2026-08-27.md`。
- Issue #33 的中期检查入口（架构图、边界/接口/数据归属、故障策略、构建证据、风险与决策记录）见 `docs/19_D04C微服务边界接口与数据归属初稿.md`。
- Issue #38 的微服务方案评审、历史三服务独立构建/配置/健康检查骨架与回滚边界见 `docs/20_D05C微服务方案评审与拆分骨架.md`；当前正式运行口径为四服务，单体路径仍保留用于兼容、迁移和回滚。
- 本次微服务拆分的实施批次、数据回填、发布顺序、验收口径和回滚开关见 [微服务拆分实施与验收计划](docs/27_微服务拆分实施与验收计划.md)。
- 课程评分项对应的 HPA、故障处理和性能实验入口见 [D08 云原生实验与性能对比计划](docs/29_D08云原生实验与性能对比计划.md)；性能结果必须使用脚本实际生成的 JSON/CSV，不能以截图或理论数据替代。

## 说明

当前版本是从零搭建的课程演示版。本地直接运行默认使用文件状态，Compose/Kubernetes 默认使用 MySQL 关系表持久化。四项独立服务和 backend/frontend 共六个应用镜像可独立构建、测试、部署和健康检查；merchant-service 入口已配置 CPU HPA（1～3 副本），远端 K3s 已通过 `metrics.k8s.io` 完成真实 1→2→3→1 验收，复现入口为 `04_tests/cloud-native/run-hpa-experiment.sh`。其他目标集群若未真实观察到 1→2/3→1，状态必须保持 BLOCKED。
