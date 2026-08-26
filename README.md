# LumaLife 综合生活助手平台

LumaLife 是一个面向课程验收场景的本地生活服务平台演示项目，覆盖用户消费闭环、商家履约闭环和平台观测闭环。

## 技术栈

- 后端：Java 17+、Spring Boot 3、Spring Security、Maven、JUnit 5
- 前端：React 18、TypeScript、Vite、CSS Modules 风格的全局设计系统
- 部署：Docker Compose 预留 MySQL、Redis、后端、前端服务

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

### 环境要求

- Java 17+
- Maven 3.9+
- Node.js 18+
- npm 9+

依赖安装使用仓库中的 lockfile；首次安装前端依赖请使用 `npm ci`。不要提交 `.env` 或本地运行状态文件；需要配置 AI 客服时，可复制 `.env.example` 为本地 `.env` 并填入自己的配置。

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
  frontend/     React/Vite 前端，已按 App、api、types、pages、components 拆分
  docs/         需求、设计、接口、部署、测试与用户手册
  .ai/          AI 使用说明、prompts 与项目 skills
  scripts/      初始化与演示辅助脚本
```

## 持续集成

[![Monolith CI](https://github.com/daihao007/Lumalife/actions/workflows/ci.yml/badge.svg)](https://github.com/daihao007/Lumalife/actions/workflows/ci.yml)

向 `main` 提交 PR 时会自动执行后端测试、前端构建和 Docker 镜像构建验证；代码进入 `main` 且全部检查通过后，流水线会把带提交版本标签的前后端镜像发布到 GHCR。详细说明见 [原系统 CI 构建、测试和镜像流水线](docs/15_%E5%8E%9F%E7%B3%BB%E7%BB%9FCI%E6%B5%81%E6%B0%B4%E7%BA%BF%E8%AF%B4%E6%98%8E.md)。

## 当前工程状态

- 前端入口已从单文件拆分为 `App.tsx`、`api.ts`、`types.ts`、`utils.ts`、`pages/` 和 `components/`，业务行为保持不变。
- 后端 Controller 已按认证、商家目录、购物车、订单、商家后台、管理员看板、AI 客服拆出 Service 门面，当前仍委托内存版 `DemoStore`。
- 自动化测试包含后端业务规则测试和 Web 层权限集成测试，当前基线为 40 个用例；详见 [测试报告](docs/07_测试报告.md)、[测试基线与缺口矩阵](docs/15_测试基线与缺口矩阵_2026-08-25.md) 与 [单体基线与范围冻结记录](docs/12_单体基线与范围冻结记录.md)。
- 数据库持久化已形成迁移计划，见 `docs/11_数据库持久化迁移计划.md`，建议后续从 `AuthService` 开始逐步替换内存实现。
- 单体后端代码审计与用户认证、商家商品、订单三服务拆分草案见 `docs/15_单体后端审计与三服务拆分草案.md`；该草案明确排除骑手领域。

## 说明

当前版本是从零搭建的课程演示版，业务数据保存在内存中，便于快速运行和答辩演示。数据库表设计、Docker MySQL/Redis、MyBatis-Plus 分层已在文档和配置中预留，后续可按模块迁移到持久化实现。
