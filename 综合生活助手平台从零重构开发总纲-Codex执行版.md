# 综合生活助手平台从零重构开发总纲（Codex 执行版）

> 适用项目：软件工程基础 2026 春大作业选题 2：生活助手平台  
> 目标读者：Codex / Claude Code / 组内开发者 / 测试与文档负责人  
> 文档目标：在不照搬开源项目的前提下，基于课程要求与既有 SRS、SDD、SDP，指导团队从零完成一个范围受控、链路完整、UI 统一、后端逻辑可靠、文档可追踪的综合生活助手平台。  
> 重要原则：本文件是开发总纲，不是让 AI 一次性生成整个项目。所有 AI 生成代码都必须经过人工审查、运行、测试和解释。

---

## 0. 开发总原则

### 0.1 本项目的最终定位

本项目不定位为“完整复刻美团/饿了么/大众点评”，而定位为：

> 一个面向课程验收场景的本地生活服务平台，重点完成用户端消费闭环、商家端履约闭环、平台端观测闭环。

系统应完成以下三条演示闭环：

1. **用户消费闭环**  
   用户注册/登录 → 浏览/搜索商家 → 查看商家详情 → 加购菜品或购买团购 → 创建订单 → 模拟支付 → 查看订单状态 → 完成后评价。

2. **商家履约闭环**  
   商家管理员登录 → 管理店铺与商品/套餐 → 查看订单 → 接单/配送/完成 → 核销团购券 → 查看评价。

3. **平台观测闭环**  
   平台管理员登录 → 查看用户数、商家数、订单量、交易额、热门分类、系统健康状态 → 辅助答辩展示项目完整度。

### 0.2 重构边界

本次重构应遵守以下边界：

- 保留“生活助手平台”的课程题目方向。
- 保留用户、商家、商品/套餐、购物车、订单、支付、评价、后台管理等核心模块。
- 不接入真实支付。
- 不做真实地图定位、真实配送、真实短信、真实支付回调。
- 不做复杂推荐算法，只做可解释的评分 + 距离排序。
- 不做完整商业系统，只做稳定可演示的课程系统。
- 不直接复制开源项目代码，只借鉴架构、视觉风格、目录组织和工程实践。
- 所有最终文档必须与最终代码一致。

### 0.3 Codex 工作原则

Codex 每次只完成一个明确任务，不允许一次性生成整个系统。每次任务必须满足：

1. 明确输入：当前文件、目标模块、接口约定、数据库字段。
2. 明确输出：新增/修改哪些文件。
3. 明确约束：不得修改哪些文件、不得破坏哪些接口。
4. 明确验收：如何运行、如何测试、预期返回什么。
5. 每次生成后必须人工 review。
6. 所有涉及状态流转、权限、支付、评价的代码必须加测试。

---

## 1. 课程要求映射

### 1.1 课程基本项

| 课程要求 | 项目对应措施 |
|---|---|
| 核心需求覆盖度 | 定义 6 个核心需求 CR-01 至 CR-06，并在答辩、README、测试报告中逐一列出 |
| 交付系统可用性 | 准备固定演示数据、固定测试账号、Docker Compose 部署、端到端测试脚本 |
| 答辩展示清晰度 | 准备一条用户端主链路、一条商家端主链路、一条管理员看板链路 |
| 团队分工合理性 | 5 人分别负责核心业务模块，每人至少覆盖前端页面、后端接口、测试、文档 |
| 过程文档连续性 | docs 目录维护需求、概要设计、详细设计、测试报告、部署文档、用户手册、AI 使用说明 |

### 1.2 课程加分项

| 加分项 | 本项目实现方案 |
|---|---|
| 大模型 Skills | 提供 `.ai/skills/` 目录，定义需求拆解、接口生成、测试生成、代码审查等 Skill |
| Agent 模块 | 实现规则检索式 AI 客服，预留 LLM API 接口 |
| UI/UX 美观设计 | 建立统一主题色、组件库、布局系统、空状态、加载态、状态标签 |
| 可观测性 | 平台管理员仪表盘 + Spring Boot Actuator 健康状态 + 业务指标 |
| 实用拓展功能 | 推荐理由、操作日志、订单状态时间线、团购券核销 |

---

## 2. 核心需求冻结

后续所有开发以本节为准。除非团队全体确认，否则不得继续扩大范围。

### CR-01 用户认证与个人中心

#### 目标

支持普通用户、商家管理员、平台管理员三类角色登录系统，并维护基础个人信息与地址。

#### 必做功能

- 手机号 + 密码注册普通用户。
- 手机号 + 密码登录。
- 登录成功返回 JWT Token。
- 密码 BCrypt 加密存储。
- 获取当前登录用户信息。
- 修改昵称、头像。
- 新增、编辑、删除、设置默认收货地址。
- 每个用户最多 5 个地址。
- 登出时前端清除 Token，可选实现 Token 黑名单。

#### 验收标准

- 明文密码不落库。
- 未登录访问需要登录的接口返回 401。
- 普通用户不能访问商家后台。
- 商家管理员不能访问其他商家数据。
- 平台管理员可以访问管理员看板。

---

### CR-02 商家搜索、分类、推荐与详情

#### 目标

用户可以查找本地商家，按分类筛选，查看商家详情、菜品、团购套餐和评价。

#### 必做功能

- 首页展示分类入口。
- 商家列表分页。
- 按分类筛选。
- 按关键词搜索商家名称、商品名称。
- 根据评分和距离进行推荐排序。
- 商家详情页展示基础信息。
- 商家详情页展示菜品列表。
- 商家详情页展示团购套餐。
- 商家详情页展示评价列表。
- 搜索无结果时展示友好空状态。

#### 推荐排序算法

```text
ratingScore = merchant.avgScore / 5.0
distanceScore = max(0, 1 - distanceKm / 5.0)
score = 0.6 * ratingScore + 0.4 * distanceScore
```

#### 验收标准

- 商家列表接口支持 `page`、`size`、`keyword`、`categoryId`、`sort`。
- 距离超过 5km 的商家距离得分为 0。
- 返回数据包含推荐理由，如“评分高”“距离近”“近期销量较好”。
- 前端显示商家卡片：图片、名称、评分、人均、距离、营业状态、推荐理由。

---

### CR-03 外卖购物车、下单、模拟支付、订单状态跟踪

#### 目标

用户可以将同一商家的菜品加入购物车，创建外卖订单，完成模拟支付，并跟踪订单状态。

#### 必做功能

- 加入购物车。
- 修改购物车商品数量。
- 删除购物车商品。
- 清空购物车。
- 限制购物车只能包含同一商家的商品。
- 选择收货地址。
- 创建外卖订单。
- 以服务端价格重新计算订单总价。
- 模拟支付。
- 支付成功后写入支付记录并更新订单状态。
- 用户查看订单列表和详情。
- 商家接单、配送、完成。
- 待支付订单可取消。
- 已支付订单不可由用户直接取消。

#### 外卖订单状态机

```text
PENDING_PAYMENT 待支付
PAID 已支付
ACCEPTED 商家已接单
DELIVERING 配送中
COMPLETED 已完成
CANCELLED 已取消
```

合法流转：

```text
PENDING_PAYMENT -> PAID
PENDING_PAYMENT -> CANCELLED
PAID -> ACCEPTED
ACCEPTED -> DELIVERING
DELIVERING -> COMPLETED
```

#### 验收标准

- 非法状态流转返回 409。
- 支付接口具备幂等控制。
- 支付与订单状态更新在同一事务中完成。
- 前端订单详情页用时间线展示状态。

---

### CR-04 到店团购购买、券码生成、商家核销

#### 目标

用户可以购买团购套餐，获得券码，商家可以核销券码。

#### 必做功能

- 商家详情页展示团购套餐。
- 用户选择套餐和数量。
- 创建团购订单。
- 模拟支付。
- 支付成功后生成 12 位券码。
- 用户在订单详情页查看券码。
- 商家后台输入券码查询。
- 商家确认核销。
- 核销后订单状态变为已使用。

#### 团购状态

```text
PENDING_PAYMENT 待支付
PAID 待使用
USED 已使用
EXPIRED 已过期
CANCELLED 已取消
```

#### 验收标准

- 已核销券码不可重复使用。
- 过期套餐不可购买。
- 库存不足不可购买。
- 商家只能核销自己店铺的券码。

---

### CR-05 订单完成后的用户评价与商家评分统计

#### 目标

保证评价真实性：只有真实完成订单的用户才能评价，且一单一评。

#### 必做功能

- 已完成外卖订单可评价。
- 已使用团购订单可评价。
- 每个订单只能评价一次。
- 评价包含总体评分、服务评分、口味评分、文字内容。
- 评价文字最多 200 字。
- 可选图片 URL，占位实现即可。
- 提交评价后更新商家平均评分。
- 商家详情页展示评价列表。
- 用户个人中心展示我的评价。

#### 验收标准

- 未完成订单评价返回 409。
- 重复评价返回 409。
- 评分必须在 1 到 5。
- 商家平均评分与评价表数据一致。
- 用户昵称展示时脱敏。

---

### CR-06 商家后台管理商品、套餐、订单

#### 目标

商家管理员能够维护店铺运营数据，并处理订单履约流程。

#### 必做功能

- 商家工作台。
- 店铺信息查看与编辑。
- 菜品新增、编辑、上下架。
- 团购套餐新增、编辑、上下架。
- 外卖订单列表。
- 接单。
- 标记配送中。
- 标记完成。
- 团购券核销。
- 查看评价。
- 查看销售统计。

#### 验收标准

- 商家管理员只能管理自己的商家。
- 下架商品用户端不可下单。
- 商品价格、库存必须校验。
- 订单状态操作必须符合状态机。

---

## 3. 推荐技术栈

### 3.1 后端

```text
Java 17
Spring Boot 3.x
Spring Security
JWT
MyBatis-Plus
MySQL 8.x
Redis 7.x
Springdoc OpenAPI / Swagger UI
Spring Boot Actuator
JUnit 5
Maven
Docker
```

### 3.2 前端

优先方案：

```text
React 18 / React 19
TypeScript
Vite
Tailwind CSS
shadcn/ui
React Router
TanStack Query
Zustand
React Hook Form
Zod
Recharts
Axios
```

备选方案：

```text
Vue 3
TypeScript
Vite
Element Plus / Naive UI
Pinia
Vue Router
ECharts
Axios
```

### 3.3 选择建议

如果当前项目 Vue 代码还能复用，则继续 Vue。  
如果当前前端混乱且准备从零重做，则优先 React + shadcn/ui，因为它更适合快速建立统一设计系统。

---

## 4. 目标仓库结构

建议使用 monorepo：

```text
life-assistant-platform/
  README.md
  docker-compose.yml
  .env.example
  .gitignore

  docs/
    00_项目总览.md
    01_核心需求说明.md
    02_需求跟踪矩阵.md
    03_概要设计说明.md
    04_详细设计说明.md
    05_接口文档.md
    06_数据库设计.md
    07_测试报告.md
    08_部署文档.md
    09_用户手册.md
    10_AI使用说明.md
    diagrams/
      architecture.puml
      er.puml
      order-state.puml
      sequence-order-pay.puml

  backend/
    pom.xml
    src/main/java/com/lifeassistant/
      LifeAssistantApplication.java
      common/
      config/
      security/
      module/
        auth/
        user/
        address/
        merchant/
        product/
        cart/
        order/
        payment/
        coupon/
        review/
        admin/
        assistant/
        observability/
    src/main/resources/
      application.yml
      application-dev.yml
      application-prod.yml
      db/
        migration/
        seed/
    src/test/java/com/lifeassistant/

  frontend/
    package.json
    index.html
    src/
      main.tsx
      app/
      routes/
      layouts/
      pages/
        auth/
        user/
        merchant/
        order/
        merchant-admin/
        admin/
      components/
        common/
        business/
        ui/
      lib/
      services/
      stores/
      types/
      styles/
      mocks/

  .ai/
    prompts/
      codex-system.md
      code-review.md
      module-task-template.md
    skills/
      backend-module.skill.md
      frontend-page.skill.md
      api-contract.skill.md
      test-generation.skill.md
      doc-sync.skill.md

  scripts/
    init-db.sql
    seed-demo-data.sql
    build.sh
    deploy.sh
```

---

## 5. 后端架构设计

### 5.1 后端分层规则

所有模块必须遵循：

```text
Controller -> Service -> Mapper -> Database
```

禁止：

- Controller 直接调用 Mapper。
- Controller 写复杂业务逻辑。
- 不同模块直接操作彼此的表。
- 前端传来的价格直接作为最终价格。
- 用字符串散落表示订单状态。
- 在业务代码中硬编码密钥和数据库密码。

### 5.2 后端公共包

```text
common/
  response/
    ApiResponse.java
    PageResponse.java
  exception/
    BusinessException.java
    ErrorCode.java
    GlobalExceptionHandler.java
  enums/
    UserRole.java
    UserStatus.java
    MerchantStatus.java
    ProductStatus.java
    ProductType.java
    OrderType.java
    OrderStatus.java
    PaymentStatus.java
    CouponStatus.java
  util/
    IdGenerator.java
    DistanceUtils.java
    MaskUtils.java
    MoneyUtils.java
    TimeUtils.java
```

### 5.3 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "page": 1,
    "size": 20,
    "total": 100,
    "pages": 5
  }
}
```

### 5.4 统一错误码

| HTTP 状态 | 业务 code | 场景 |
|---|---:|---|
| 400 | 40000 | 参数错误 |
| 401 | 40100 | 未登录 |
| 403 | 40300 | 无权限 |
| 404 | 40400 | 资源不存在 |
| 409 | 40900 | 业务冲突，如非法状态流转 |
| 500 | 50000 | 系统内部错误 |

### 5.5 安全设计

#### 角色

```text
USER
MERCHANT_ADMIN
PLATFORM_ADMIN
```

#### 鉴权规则

- `/api/v1/auth/**` 公开。
- `GET /api/v1/merchants/**` 公开。
- `GET /api/v1/reviews/merchant/**` 公开。
- `/api/v1/cart/**` 需要 USER。
- `/api/v1/orders/**` 需要 USER。
- `/api/v1/reviews` 提交评价需要 USER。
- `/api/v1/merchant-admin/**` 需要 MERCHANT_ADMIN。
- `/api/v1/admin/**` 需要 PLATFORM_ADMIN。

#### 权限细节

- 商家管理员必须绑定 `merchant_id`。
- 查询、修改商品时必须校验商品属于当前商家。
- 处理订单时必须校验订单属于当前商家。
- 核销券码时必须校验券码属于当前商家。
- 普通用户查询订单时只能查询自己的订单。
- 平台管理员才能访问全局数据。

### 5.6 数据一致性规则

以下操作必须加事务：

- 创建订单 + 写入订单明细。
- 支付成功 + 写入支付记录 + 更新订单状态。
- 团购支付成功 + 生成券码。
- 评价提交 + 更新商家平均评分。
- 商家接单/配送/完成订单状态。
- 核销券码 + 更新订单状态。

### 5.7 幂等规则

支付接口必须支持幂等：

```text
clientRequestId = 前端生成的 UUID
同一 userId + orderId + clientRequestId 只能成功处理一次
重复请求返回第一次的支付结果
```

建议 `payment` 表中添加唯一索引：

```sql
UNIQUE KEY uk_payment_request (user_id, order_id, client_request_id)
```

---

## 6. 数据库设计

### 6.1 命名规范

- 表名：小写下划线。
- 字段名：小写下划线。
- 主键：`xxx_id`。
- 金额：统一使用 `BIGINT`，单位为分。
- 时间：`created_at`、`updated_at`。
- 软删除：`is_deleted TINYINT DEFAULT 0`。
- 状态：使用 `VARCHAR(32)`，Java 中用枚举约束。

### 6.2 核心表清单

#### user

| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | BIGINT PK | 用户 ID |
| phone | VARCHAR(11) UNIQUE | 手机号 |
| password_hash | VARCHAR(100) | BCrypt 哈希 |
| nickname | VARCHAR(32) | 昵称 |
| avatar_url | VARCHAR(255) | 头像 |
| role | VARCHAR(32) | USER / MERCHANT_ADMIN / PLATFORM_ADMIN |
| merchant_id | BIGINT NULL | 商家管理员绑定商家 |
| status | VARCHAR(32) | NORMAL / LOCKED |
| login_fail_count | INT | 登录失败次数 |
| locked_until | DATETIME NULL | 锁定截止时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| is_deleted | TINYINT | 软删除 |

#### user_address

| 字段 | 类型 | 说明 |
|---|---|---|
| address_id | BIGINT PK | 地址 ID |
| user_id | BIGINT | 用户 ID |
| province | VARCHAR(32) | 省 |
| city | VARCHAR(32) | 市 |
| district | VARCHAR(32) | 区 |
| detail | VARCHAR(128) | 详细地址 |
| contact_name | VARCHAR(32) | 联系人 |
| contact_phone | VARCHAR(11) | 联系电话 |
| is_default | TINYINT | 是否默认 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| is_deleted | TINYINT | 软删除 |

#### category

| 字段 | 类型 | 说明 |
|---|---|---|
| category_id | BIGINT PK | 分类 ID |
| name | VARCHAR(32) | 分类名称 |
| icon | VARCHAR(64) | 图标名 |
| sort_order | INT | 排序 |
| status | VARCHAR(32) | ENABLED / DISABLED |

#### merchant

| 字段 | 类型 | 说明 |
|---|---|---|
| merchant_id | BIGINT PK | 商家 ID |
| category_id | BIGINT | 分类 ID |
| name | VARCHAR(64) | 商家名称 |
| description | VARCHAR(512) | 简介 |
| cover_url | VARCHAR(255) | 主图 |
| address | VARCHAR(255) | 地址 |
| latitude | DECIMAL(10,6) | 纬度 |
| longitude | DECIMAL(10,6) | 经度 |
| phone | VARCHAR(20) | 联系电话 |
| business_hours | VARCHAR(64) | 营业时间 |
| avg_score | DECIMAL(3,2) | 平均评分 |
| monthly_sales | INT | 月销量 |
| per_capita | BIGINT | 人均消费 |
| delivery_fee | BIGINT | 配送费 |
| delivery_time_min | INT | 预计配送分钟 |
| status | VARCHAR(32) | OPEN / CLOSED / OFFLINE |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| is_deleted | TINYINT | 软删除 |

#### product

| 字段 | 类型 | 说明 |
|---|---|---|
| product_id | BIGINT PK | 商品 ID |
| merchant_id | BIGINT | 商家 ID |
| category_id | BIGINT NULL | 商品分类 |
| product_type | VARCHAR(32) | FOOD / GROUP_DEAL |
| name | VARCHAR(64) | 商品名 |
| description | VARCHAR(512) | 描述 |
| image_url | VARCHAR(255) | 图片 |
| price | BIGINT | 当前价格，单位分 |
| original_price | BIGINT NULL | 原价，团购使用 |
| stock | INT | 库存 |
| sales | INT | 销量 |
| valid_from | DATETIME NULL | 团购有效期开始 |
| valid_until | DATETIME NULL | 团购有效期结束 |
| status | VARCHAR(32) | ON_SALE / OFF_SALE |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| is_deleted | TINYINT | 软删除 |

#### cart_item

可选。如果使用 Redis 存购物车，可不建表。课程项目为了稳定，建议数据库 + Redis 缓存并用。

| 字段 | 类型 | 说明 |
|---|---|---|
| cart_item_id | BIGINT PK | 购物车项 ID |
| user_id | BIGINT | 用户 ID |
| merchant_id | BIGINT | 商家 ID |
| product_id | BIGINT | 商品 ID |
| quantity | INT | 数量 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

#### user_order

避免使用 SQL 关键字 `order` 作为表名。

| 字段 | 类型 | 说明 |
|---|---|---|
| order_id | BIGINT PK | 订单 ID |
| order_no | VARCHAR(32) UNIQUE | 订单号 |
| user_id | BIGINT | 用户 ID |
| merchant_id | BIGINT | 商家 ID |
| order_type | VARCHAR(32) | DELIVERY / GROUP_DEAL |
| status | VARCHAR(32) | 订单状态 |
| product_amount | BIGINT | 商品金额 |
| delivery_fee | BIGINT | 配送费 |
| discount_amount | BIGINT | 优惠金额 |
| total_amount | BIGINT | 实付金额 |
| address_snapshot | VARCHAR(512) NULL | 地址快照 |
| remark | VARCHAR(255) NULL | 备注 |
| paid_at | DATETIME NULL | 支付时间 |
| completed_at | DATETIME NULL | 完成时间 |
| cancelled_at | DATETIME NULL | 取消时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| is_deleted | TINYINT | 软删除 |

#### order_item

| 字段 | 类型 | 说明 |
|---|---|---|
| order_item_id | BIGINT PK | 明细 ID |
| order_id | BIGINT | 订单 ID |
| product_id | BIGINT | 商品 ID |
| product_name | VARCHAR(64) | 商品名快照 |
| product_image | VARCHAR(255) | 商品图快照 |
| price | BIGINT | 下单价格 |
| quantity | INT | 数量 |
| subtotal | BIGINT | 小计 |

#### payment

| 字段 | 类型 | 说明 |
|---|---|---|
| payment_id | BIGINT PK | 支付 ID |
| order_id | BIGINT | 订单 ID |
| user_id | BIGINT | 用户 ID |
| payment_no | VARCHAR(32) UNIQUE | 支付流水号 |
| client_request_id | VARCHAR(64) | 幂等请求 ID |
| amount | BIGINT | 支付金额 |
| status | VARCHAR(32) | SUCCESS / FAILED |
| paid_at | DATETIME | 支付时间 |
| created_at | DATETIME | 创建时间 |

#### coupon

| 字段 | 类型 | 说明 |
|---|---|---|
| coupon_id | BIGINT PK | 券 ID |
| order_id | BIGINT | 订单 ID |
| merchant_id | BIGINT | 商家 ID |
| user_id | BIGINT | 用户 ID |
| coupon_code | VARCHAR(16) UNIQUE | 12 位券码 |
| status | VARCHAR(32) | UNUSED / USED / EXPIRED |
| used_at | DATETIME NULL | 使用时间 |
| valid_until | DATETIME | 有效期 |
| created_at | DATETIME | 创建时间 |

#### review

| 字段 | 类型 | 说明 |
|---|---|---|
| review_id | BIGINT PK | 评价 ID |
| order_id | BIGINT UNIQUE | 订单 ID，一单一评 |
| user_id | BIGINT | 用户 ID |
| merchant_id | BIGINT | 商家 ID |
| overall_score | INT | 总体评分 |
| taste_score | INT NULL | 口味评分 |
| service_score | INT NULL | 服务评分 |
| content | VARCHAR(200) | 评价内容 |
| image_urls | VARCHAR(1024) | JSON 字符串 |
| created_at | DATETIME | 创建时间 |
| is_deleted | TINYINT | 软删除 |

#### operation_log

| 字段 | 类型 | 说明 |
|---|---|---|
| log_id | BIGINT PK | 日志 ID |
| operator_id | BIGINT | 操作者 |
| role | VARCHAR(32) | 角色 |
| module | VARCHAR(32) | 模块 |
| action | VARCHAR(64) | 动作 |
| target_id | BIGINT | 目标 ID |
| detail | VARCHAR(512) | 详情 |
| created_at | DATETIME | 创建时间 |

### 6.3 必要索引

```sql
-- 用户
CREATE UNIQUE INDEX uk_user_phone ON user(phone);

-- 商家查询
CREATE INDEX idx_merchant_category_status ON merchant(category_id, status);
CREATE INDEX idx_merchant_score_sales ON merchant(avg_score, monthly_sales);

-- 商品
CREATE INDEX idx_product_merchant_type_status ON product(merchant_id, product_type, status);

-- 订单
CREATE UNIQUE INDEX uk_order_no ON user_order(order_no);
CREATE INDEX idx_order_user_status ON user_order(user_id, status);
CREATE INDEX idx_order_merchant_status ON user_order(merchant_id, status);

-- 支付幂等
CREATE UNIQUE INDEX uk_payment_request ON payment(user_id, order_id, client_request_id);

-- 券码
CREATE UNIQUE INDEX uk_coupon_code ON coupon(coupon_code);

-- 评价
CREATE UNIQUE INDEX uk_review_order ON review(order_id);
CREATE INDEX idx_review_merchant_created ON review(merchant_id, created_at);
```

---

## 7. API 设计

### 7.1 API 基础规则

- 统一前缀：`/api/v1`
- 统一 JSON。
- 分页参数：`page` 从 1 开始，`size` 默认 20，最大 100。
- 鉴权头：`Authorization: Bearer <token>`
- 所有金额单位为分。
- 时间格式：ISO 8601，例如 `2026-05-23T10:00:00+08:00`。
- 所有 POST/PUT/PATCH 请求都要参数校验。

### 7.2 认证接口

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/logout
GET  /api/v1/auth/me
```

#### 注册请求

```json
{
  "phone": "13800000001",
  "password": "abc123456",
  "nickname": "小明"
}
```

#### 登录响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "jwt-token",
    "user": {
      "userId": 1,
      "nickname": "小明",
      "role": "USER"
    }
  }
}
```

### 7.3 用户地址接口

```text
GET    /api/v1/addresses
POST   /api/v1/addresses
PUT    /api/v1/addresses/{addressId}
DELETE /api/v1/addresses/{addressId}
POST   /api/v1/addresses/{addressId}/default
```

### 7.4 商家接口

```text
GET /api/v1/categories
GET /api/v1/merchants
GET /api/v1/merchants/{merchantId}
GET /api/v1/merchants/{merchantId}/products
GET /api/v1/merchants/{merchantId}/reviews
```

#### 商家列表参数

```text
keyword
categoryId
latitude
longitude
sort = recommend | distance | rating | sales
page
size
```

### 7.5 购物车接口

```text
GET    /api/v1/cart
POST   /api/v1/cart/items
PUT    /api/v1/cart/items/{cartItemId}
DELETE /api/v1/cart/items/{cartItemId}
DELETE /api/v1/cart
```

#### 加购请求

```json
{
  "merchantId": 10001,
  "productId": 30001,
  "quantity": 2
}
```

### 7.6 订单接口

```text
POST /api/v1/orders/delivery
POST /api/v1/orders/group-deal
GET  /api/v1/orders
GET  /api/v1/orders/{orderId}
POST /api/v1/orders/{orderId}/cancel
POST /api/v1/orders/{orderId}/pay
```

#### 外卖下单请求

```json
{
  "addressId": 20001,
  "remark": "不要辣"
}
```

#### 团购下单请求

```json
{
  "productId": 30088,
  "quantity": 2
}
```

#### 支付请求

```json
{
  "clientRequestId": "uuid-from-frontend"
}
```

### 7.7 商家后台接口

```text
GET   /api/v1/merchant-admin/dashboard
GET   /api/v1/merchant-admin/profile
PUT   /api/v1/merchant-admin/profile

GET   /api/v1/merchant-admin/products
POST  /api/v1/merchant-admin/products
PUT   /api/v1/merchant-admin/products/{productId}
PATCH /api/v1/merchant-admin/products/{productId}/status

GET   /api/v1/merchant-admin/orders
POST  /api/v1/merchant-admin/orders/{orderId}/accept
POST  /api/v1/merchant-admin/orders/{orderId}/deliver
POST  /api/v1/merchant-admin/orders/{orderId}/complete

POST  /api/v1/merchant-admin/coupons/verify
POST  /api/v1/merchant-admin/coupons/{couponCode}/use

GET   /api/v1/merchant-admin/reviews
```

### 7.8 平台管理员接口

```text
GET /api/v1/admin/dashboard/overview
GET /api/v1/admin/users
GET /api/v1/admin/merchants
GET /api/v1/admin/orders
GET /api/v1/admin/operation-logs
GET /api/v1/admin/system/health
```

### 7.9 AI 客服接口

```text
POST /api/v1/assistant/chat
GET  /api/v1/assistant/faqs
```

请求：

```json
{
  "message": "为什么我不能评价订单？"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "只有已完成的外卖订单或已使用的团购订单可以评价，且每个订单只能评价一次。",
    "source": "RULE_BASED"
  }
}
```

---

## 8. 前端设计系统

### 8.1 视觉定位

关键词：

```text
温暖、干净、生活化、卡片化、移动端优先、后台专业化
```

### 8.2 主题规范

| 项目 | 建议 |
|---|---|
| 主色 | 橙色 / 暖黄色 |
| 成功色 | 绿色 |
| 警告色 | 黄色 |
| 错误色 | 红色 |
| 信息色 | 蓝色 |
| 页面背景 | `#F7F8FA` |
| 卡片背景 | 白色 |
| 圆角 | 12px / 16px |
| 页面间距 | 16px 移动端，24px 桌面端 |

### 8.3 必须封装的通用组件

```text
AppLayout
MobileShell
AdminLayout
PageHeader
SectionTitle
MerchantCard
ProductCard
GroupDealCard
CartItemRow
OrderCard
OrderStatusTimeline
RatingStars
StatusBadge
PriceText
EmptyState
LoadingSkeleton
ConfirmDialog
DataStatCard
DashboardChartCard
```

### 8.4 状态标签设计

| 状态 | 显示文案 | 颜色 |
|---|---|---|
| PENDING_PAYMENT | 待支付 | 黄色 |
| PAID | 已支付 | 蓝色 |
| ACCEPTED | 商家已接单 | 蓝色 |
| DELIVERING | 配送中 | 紫色 |
| COMPLETED | 已完成 | 绿色 |
| CANCELLED | 已取消 | 灰色 |
| USED | 已使用 | 绿色 |
| EXPIRED | 已过期 | 灰色 |

---

## 9. 前端页面规划

### 9.1 用户端页面

#### 9.1.1 登录/注册页

路径：

```text
/login
/register
```

功能：

- 手机号输入。
- 密码输入。
- 注册时昵称输入。
- 表单校验。
- 登录成功跳转首页。
- 根据角色跳转不同端：
  - USER → `/`
  - MERCHANT_ADMIN → `/merchant-admin`
  - PLATFORM_ADMIN → `/admin`

#### 9.1.2 首页

路径：

```text
/
```

模块：

- 顶部定位与搜索框。
- 分类入口。
- 今日推荐。
- 热门商家。
- 热门团购。
- 底部导航。

#### 9.1.3 商家列表页

路径：

```text
/merchants
```

功能：

- 分类筛选。
- 关键词搜索。
- 排序：推荐、距离、评分、销量。
- 商家卡片列表。
- 空状态。

#### 9.1.4 商家详情页

路径：

```text
/merchants/:merchantId
```

模块：

- 商家头图。
- 商家名称、评分、地址、营业时间。
- Tab：点餐 / 团购 / 评价 / 商家信息。
- 菜品列表。
- 团购套餐列表。
- 底部购物车浮层。

#### 9.1.5 购物车页

路径：

```text
/cart
```

功能：

- 展示购物车商品。
- 修改数量。
- 删除商品。
- 清空购物车。
- 显示商品金额、配送费、总价。
- 去结算。

#### 9.1.6 确认订单页

路径：

```text
/checkout
```

功能：

- 选择地址。
- 展示商品明细。
- 展示价格。
- 填写备注。
- 提交订单。

#### 9.1.7 模拟支付页

路径：

```text
/pay/:orderId
```

功能：

- 展示支付金额。
- 显示模拟支付卡片。
- 点击“确认支付”。
- 防重复点击。
- 支付成功跳转订单详情。

#### 9.1.8 我的订单页

路径：

```text
/orders
/orders/:orderId
```

功能：

- 按状态筛选。
- 展示订单卡片。
- 订单详情显示状态时间线。
- 待支付订单可取消/支付。
- 已完成订单可评价。
- 团购订单显示券码。

#### 9.1.9 评价页

路径：

```text
/reviews/create?orderId=xxx
```

功能：

- 星级评分。
- 文字评价。
- 图片 URL 占位。
- 提交评价。

#### 9.1.10 个人中心

路径：

```text
/profile
/addresses
/my-reviews
```

功能：

- 用户信息。
- 地址管理。
- 我的评价。
- 退出登录。

---

### 9.2 商家后台页面

路径前缀：

```text
/merchant-admin
```

页面：

```text
/merchant-admin/dashboard
/merchant-admin/profile
/merchant-admin/products
/merchant-admin/products/new
/merchant-admin/products/:id/edit
/merchant-admin/orders
/merchant-admin/coupons
/merchant-admin/reviews
```

#### 工作台指标

- 今日订单数。
- 今日销售额。
- 待接单数。
- 待核销团购券。
- 店铺平均评分。
- 热销商品 TOP5。

#### 商品管理

- 表格列：图片、名称、类型、价格、库存、销量、状态、操作。
- 操作：新增、编辑、上架、下架。
- 商品类型：外卖菜品 / 团购套餐。

#### 订单管理

- 按状态筛选。
- 外卖订单操作：
  - 已支付 → 接单。
  - 商家已接单 → 配送中。
  - 配送中 → 完成。
- 团购订单操作：
  - 输入券码核销。

---

### 9.3 平台管理员页面

路径前缀：

```text
/admin
```

页面：

```text
/admin/dashboard
/admin/users
/admin/merchants
/admin/orders
/admin/logs
/admin/system
```

#### 仪表盘

指标：

- 总用户数。
- 总商家数。
- 今日订单数。
- 今日交易额。
- 订单状态分布。
- 热门分类。
- 热门商家。
- 系统健康状态。
- 最近操作日志。

---

## 10. 后端模块实现顺序

### 10.1 第一阶段：项目骨架

Codex 任务：

```text
创建 Spring Boot 项目骨架，包含 common、config、security、module 包结构。
实现统一 ApiResponse、PageResponse、BusinessException、ErrorCode、GlobalExceptionHandler。
集成 MyBatis-Plus、MySQL、Redis、Spring Security、JWT、Springdoc OpenAPI。
提供 application-dev.yml 和 .env.example。
```

验收：

- 项目能启动。
- `/actuator/health` 返回 UP。
- Swagger UI 可打开。
- 全局异常处理生效。

### 10.2 第二阶段：认证与用户

Codex 任务：

```text
实现 auth、user、address 模块。
包括注册、登录、当前用户、地址 CRUD、默认地址。
密码使用 BCrypt。
JWT 包含 userId、role、merchantId。
```

验收：

- 注册成功后数据库密码不是明文。
- 登录成功返回 token。
- 未登录访问地址接口返回 401。
- 用户最多 5 个地址。
- 设置默认地址时同用户其他地址自动取消默认。

### 10.3 第三阶段：商家与商品

Codex 任务：

```text
实现 category、merchant、product 模块。
包括分类列表、商家列表、商家详情、商品列表、推荐排序。
```

验收：

- 商家列表分页。
- 分类筛选有效。
- 关键词搜索有效。
- 推荐排序返回推荐理由。
- 下架商品不展示。

### 10.4 第四阶段：购物车

Codex 任务：

```text
实现 cart 模块。
购物车支持加入、修改数量、删除、清空。
限制同一用户购物车只能包含同一商家商品。
```

验收：

- 同商家商品可加入。
- 跨商家加购返回业务提示。
- 数量限制 1-99。
- 商品下架后购物车结算时提示。

### 10.5 第五阶段：订单与支付

Codex 任务：

```text
实现 order、payment 模块。
支持外卖订单创建、订单列表、订单详情、取消、模拟支付。
实现订单状态机服务和支付幂等。
```

验收：

- 创建订单使用服务端价格。
- 支付成功写 payment 并更新订单。
- 重复支付不产生重复记录。
- 已支付订单不能取消。
- 非法状态流转返回 409。

### 10.6 第六阶段：商家后台履约

Codex 任务：

```text
实现 merchant-admin 接口。
商家管理员可管理自己店铺的商品、套餐和订单。
支持接单、配送中、完成、团购券核销。
```

验收：

- 商家管理员不能访问其他商家商品。
- 接单状态流转正确。
- 核销券码后状态变更。
- 重复核销返回 409。

### 10.7 第七阶段：评价

Codex 任务：

```text
实现 review 模块。
支持提交评价、商家评价列表、我的评价。
评价提交后重新计算商家平均分。
```

验收：

- 未完成订单不可评价。
- 重复评价返回 409。
- 评分范围校验。
- 商家平均分更新正确。

### 10.8 第八阶段：管理员看板与可观测性

Codex 任务：

```text
实现 admin、observability 模块。
提供平台指标、订单统计、热门分类、热门商家、系统健康状态。
集成 Actuator health 信息。
```

验收：

- 管理员看板有真实数据。
- `/api/v1/admin/system/health` 返回后端、数据库、Redis 状态。
- 普通用户不能访问管理员接口。

### 10.9 第九阶段：AI 客服

Codex 任务：

```text
实现 assistant 模块。
采用规则检索式 FAQ。
支持根据用户问题返回订单、支付、评价、团购、商家相关回答。
预留 LLMProvider 接口，但默认不接真实大模型。
```

验收：

- 输入“怎么取消订单”返回取消规则。
- 输入“为什么不能评价”返回评价条件。
- 输入未知问题返回兜底回答。

---

## 11. 前端实现顺序

### 11.1 第一阶段：前端骨架

Codex 任务：

```text
创建 Vite + React + TypeScript 项目。
集成 Tailwind CSS、shadcn/ui、React Router、TanStack Query、Axios、Zustand。
实现 AppLayout、MobileShell、AdminLayout。
实现路由守卫和角色跳转。
```

验收：

- `/login` 可访问。
- `/` 用户端布局正常。
- `/merchant-admin` 商家后台布局正常。
- `/admin` 管理员布局正常。
- 未登录访问受保护页面跳转登录。

### 11.2 第二阶段：设计系统组件

Codex 任务：

```text
实现 MerchantCard、ProductCard、OrderCard、StatusBadge、PriceText、RatingStars、EmptyState、LoadingSkeleton、OrderStatusTimeline、DataStatCard。
```

验收：

- 所有页面复用这些组件。
- 不允许每个页面重复写状态样式。
- 状态颜色统一。

### 11.3 第三阶段：用户端页面

按以下顺序：

```text
登录/注册
首页
商家列表
商家详情
购物车
确认订单
支付页
订单列表/详情
评价页
个人中心/地址管理
```

要求：

- 先用 mock 数据搭完整页面。
- 再接真实接口。
- 每个页面必须有 loading、empty、error 状态。
- 移动端宽度 390px 不崩。

### 11.4 第四阶段：商家后台页面

按以下顺序：

```text
商家工作台
店铺信息
商品/套餐管理
订单管理
券码核销
评价管理
```

要求：

- 表格搜索条件统一。
- 所有写操作成功后刷新列表。
- 状态操作前二次确认。
- 错误提示清晰。

### 11.5 第五阶段：平台管理员页面

按以下顺序：

```text
管理员仪表盘
用户列表
商家列表
订单列表
操作日志
系统状态
```

要求：

- 图表使用真实后端数据。
- 健康状态展示后端、MySQL、Redis。
- 管理员端视觉与商家后台保持一致。

---

## 12. 前后端联调策略

### 12.1 API 契约优先

在实现接口前，先在 `docs/05_接口文档.md` 中写清：

- 路径。
- 方法。
- 鉴权角色。
- 请求参数。
- 响应字段。
- 错误码。
- 示例请求。
- 示例响应。

### 12.2 Mock 优先

前端页面先使用 mock 数据，后端接口稳定后再切换。

推荐结构：

```text
frontend/src/services/
  authApi.ts
  merchantApi.ts
  cartApi.ts
  orderApi.ts
  paymentApi.ts
  reviewApi.ts
  merchantAdminApi.ts
  adminApi.ts
```

### 12.3 联调顺序

```text
登录注册
商家列表/详情
购物车
创建订单
支付
订单状态
商家后台接单
评价
管理员看板
```

不要先联调看板，也不要先联调 AI 客服。

---

## 13. 测试计划

### 13.1 后端单元测试重点

| 模块 | 测试点 |
|---|---|
| AuthService | 注册、重复手机号、登录成功、密码错误、锁定 |
| AddressService | 地址数量限制、默认地址切换 |
| MerchantService | 分类筛选、关键词搜索、推荐排序 |
| CartService | 同商家限制、数量限制、下架商品 |
| OrderService | 创建订单、价格校验、状态机 |
| PaymentService | 支付成功、重复支付、事务回滚 |
| CouponService | 券码生成、核销、重复核销、跨商家核销 |
| ReviewService | 订单完成校验、重复评价、评分更新 |
| Permission | 用户越权、商家越权、管理员权限 |

### 13.2 后端集成测试

至少覆盖：

```text
用户注册登录
商家浏览
加入购物车
创建订单
支付订单
商家接单
商家完成
用户评价
团购购买
团购核销
管理员看板
```

### 13.3 前端测试

至少手动测试：

- 页面加载。
- 表单校验。
- 登录态过期。
- 空状态。
- 接口错误提示。
- 移动端适配。
- 订单状态按钮是否按状态显示。

### 13.4 演示测试账号

建议初始化：

| 角色 | 手机号 | 密码 |
|---|---|---|
| 普通用户 | 13800000001 | abc123456 |
| 商家管理员 A | 13800000002 | abc123456 |
| 商家管理员 B | 13800000003 | abc123456 |
| 平台管理员 | 13800000000 | admin123456 |

### 13.5 演示数据

必须初始化：

- 5 个分类。
- 10 个商家。
- 每个商家 8 到 15 个菜品。
- 每个商家 2 到 4 个团购套餐。
- 20 条历史订单。
- 20 条评价。
- 若干操作日志。

---

## 14. 部署方案

### 14.1 开发环境

```text
前端：http://localhost:5173
后端：http://localhost:8080
MySQL：localhost:3306
Redis：localhost:6379
Swagger：http://localhost:8080/swagger-ui/index.html
Actuator：http://localhost:8080/actuator/health
```

### 14.2 Docker Compose

应包含：

```text
mysql
redis
backend
frontend / nginx
```

### 14.3 环境变量

`.env.example`：

```text
MYSQL_ROOT_PASSWORD=lifeassist
MYSQL_DATABASE=life_assistant
MYSQL_USER=lifeassist
MYSQL_PASSWORD=lifeassist123

REDIS_HOST=redis
REDIS_PORT=6379

JWT_SECRET=replace-with-long-random-secret
JWT_EXPIRE_HOURS=24

SPRING_PROFILES_ACTIVE=prod
```

### 14.4 部署验收

部署后必须验证：

```text
前端首页可访问
后端 health 为 UP
数据库连接正常
Redis 连接正常
登录成功
主链路可完成
Swagger 可访问或有离线接口文档
```

---

## 15. 文档交付清单

### 15.1 README.md

必须包含：

- 项目简介。
- 技术栈。
- 核心功能。
- 快速启动。
- 测试账号。
- 项目结构。
- 演示流程。
- 团队分工。
- AI 使用说明入口。
- 注意事项。

### 15.2 docs/01_核心需求说明.md

包含：

- CR-01 至 CR-06。
- 每个核心需求的用户故事。
- 验收标准。
- 对应接口。
- 对应页面。
- 对应测试用例。

### 15.3 docs/02_需求跟踪矩阵.md

格式：

| 需求 ID | 需求名称 | 后端模块 | 前端页面 | 测试用例 | 状态 |
|---|---|---|---|---|---|

### 15.4 docs/04_详细设计说明.md

包含：

- 后端模块类设计。
- Service 方法签名。
- DTO/VO。
- 状态机。
- 数据库字段。
- 事务边界。
- 权限规则。
- 异常处理。

### 15.5 docs/07_测试报告.md

包含：

- 测试环境。
- 测试账号。
- 功能测试表。
- 接口测试表。
- 异常测试表。
- 性能简测。
- 缺陷与修复记录。
- 测试结论。

### 15.6 docs/08_部署文档.md

包含：

- 环境依赖。
- 本地启动。
- Docker 启动。
- 数据初始化。
- 常见问题。
- 端口说明。
- 服务器部署步骤。

### 15.7 docs/09_用户手册.md

按角色写：

- 普通用户手册。
- 商家管理员手册。
- 平台管理员手册。

### 15.8 docs/10_AI使用说明.md

必须写清：

- 使用了哪些 AI 工具。
- AI 用于哪些任务。
- 哪些内容由人审查。
- 是否使用 Skills。
- 提供 Session 或提示词摘要。
- 说明未直接照搬开源项目。

---

## 16. AI Skills 设计

### 16.1 `.ai/skills/backend-module.skill.md`

用途：生成后端模块。

要求输出：

- Entity。
- DTO。
- VO。
- Mapper。
- Service 接口。
- Service 实现。
- Controller。
- 测试。
- Swagger 注解。
- 权限校验。
- 异常处理。

### 16.2 `.ai/skills/frontend-page.skill.md`

用途：生成前端页面。

要求输出：

- 页面组件。
- API 调用。
- loading 状态。
- empty 状态。
- error 状态。
- 表单校验。
- 响应式布局。
- 组件复用说明。

### 16.3 `.ai/skills/api-contract.skill.md`

用途：生成接口契约。

要求输出：

- endpoint。
- method。
- auth。
- request。
- response。
- error codes。
- example。
- frontend usage。

### 16.4 `.ai/skills/test-generation.skill.md`

用途：生成测试用例。

要求输出：

- 正常流程。
- 异常流程。
- 边界值。
- 权限测试。
- 幂等测试。
- 状态机测试。

### 16.5 `.ai/skills/doc-sync.skill.md`

用途：根据代码变更更新文档。

要求输出：

- 变更的需求。
- 变更的接口。
- 变更的数据库表。
- 变更的测试用例。
- 需要同步的文档位置。

---

## 17. Codex 任务模板

每次给 Codex 的任务建议使用以下模板：

```text
你现在在 life-assistant-platform 项目中工作。

任务名称：
[填写任务]

背景：
[说明当前模块、已有文件、相关需求 ID]

目标：
[明确要实现什么]

范围：
允许修改：
- [文件/目录]

禁止修改：
- [文件/目录]

技术约束：
- 后端必须遵循 Controller -> Service -> Mapper。
- 所有返回必须使用 ApiResponse。
- 所有业务错误必须抛 BusinessException。
- 所有写操作必须校验权限。
- 金额单位为分。
- 状态必须使用枚举。

接口契约：
[写明 endpoint、请求、响应]

验收标准：
1. [标准 1]
2. [标准 2]
3. [标准 3]

测试要求：
- 添加/更新单元测试。
- 给出手动测试步骤。
- 说明如何运行测试。

输出要求：
- 先说明修改计划。
- 再修改代码。
- 最后总结修改文件和验证方式。
```

---

## 18. 推荐 Codex 任务拆分清单

### 后端任务

```text
BE-00 初始化 Spring Boot 项目骨架
BE-01 实现 common 统一响应与异常
BE-02 集成数据库、Redis、Swagger、Actuator
BE-03 实现 JWT 和 Spring Security
BE-04 实现注册登录
BE-05 实现用户地址
BE-06 实现分类、商家列表、详情
BE-07 实现商品/套餐查询
BE-08 实现商家推荐排序
BE-09 实现购物车
BE-10 实现外卖订单创建
BE-11 实现订单状态机
BE-12 实现模拟支付与幂等
BE-13 实现商家后台商品管理
BE-14 实现商家后台订单处理
BE-15 实现团购订单和券码
BE-16 实现团购核销
BE-17 实现评价与评分统计
BE-18 实现管理员看板
BE-19 实现操作日志
BE-20 实现 AI 客服
BE-21 补全后端测试
BE-22 编写 seed 数据
```

### 前端任务

```text
FE-00 初始化 Vite + React + TS 项目
FE-01 集成 Tailwind/shadcn/ui/Router/Query/Axios
FE-02 实现布局和路由守卫
FE-03 实现通用组件
FE-04 实现登录注册页
FE-05 实现首页
FE-06 实现商家列表页
FE-07 实现商家详情页
FE-08 实现购物车页
FE-09 实现确认订单页
FE-10 实现支付页
FE-11 实现订单列表和详情
FE-12 实现评价页
FE-13 实现个人中心和地址管理
FE-14 实现商家后台工作台
FE-15 实现商家商品管理
FE-16 实现商家订单管理
FE-17 实现团购券核销
FE-18 实现平台管理员仪表盘
FE-19 实现系统状态页
FE-20 实现 AI 客服浮窗
FE-21 前端错误处理和空状态统一
FE-22 移动端适配和视觉优化
```

### 文档任务

```text
DOC-01 更新 README
DOC-02 编写核心需求说明
DOC-03 编写需求跟踪矩阵
DOC-04 编写详细设计说明
DOC-05 编写接口文档
DOC-06 编写数据库设计
DOC-07 编写测试报告
DOC-08 编写部署文档
DOC-09 编写用户手册
DOC-10 编写 AI 使用说明
```

---

## 19. 开发里程碑

### M1：项目骨架可运行

完成：

- 后端启动。
- 前端启动。
- 数据库、Redis 启动。
- 登录接口可用。
- Swagger 可访问。
- README 有启动方式。

### M2：用户端主链路可跑通

完成：

- 登录。
- 商家列表。
- 商家详情。
- 加购物车。
- 创建订单。
- 支付。
- 查看订单。

### M3：商家端履约可跑通

完成：

- 商家后台登录。
- 查看订单。
- 接单。
- 配送。
- 完成。
- 商品管理。

### M4：团购与评价可跑通

完成：

- 购买团购套餐。
- 生成券码。
- 商家核销。
- 用户评价。
- 商家评分更新。

### M5：管理员看板与加分项完成

完成：

- 管理员仪表盘。
- 系统健康状态。
- 操作日志。
- AI 客服。
- 推荐理由。

### M6：交付准备完成

完成：

- 测试报告。
- 部署文档。
- 用户手册。
- AI 使用说明。
- 演示视频脚本。
- 答辩 PPT 素材。

---

## 20. 演示脚本

### 20.1 用户端演示

1. 普通用户登录。
2. 首页查看分类和推荐商家。
3. 搜索“川菜”或“奶茶”。
4. 进入商家详情。
5. 添加两个菜品到购物车。
6. 进入购物车修改数量。
7. 提交订单。
8. 模拟支付。
9. 查看订单状态。

### 20.2 商家端演示

1. 商家管理员登录。
2. 查看今日工作台。
3. 查看待处理订单。
4. 对刚才用户订单接单。
5. 标记配送中。
6. 标记完成。
7. 查看评价列表。

### 20.3 评价演示

1. 普通用户刷新订单详情。
2. 点击评价。
3. 提交星级和文字评价。
4. 回到商家详情查看评价出现。
5. 展示商家评分变化。

### 20.4 团购演示

1. 用户进入商家详情团购 Tab。
2. 购买套餐。
3. 模拟支付。
4. 查看券码。
5. 商家后台输入券码。
6. 核销成功。

### 20.5 管理员演示

1. 平台管理员登录。
2. 查看总用户、总商家、今日订单、交易额。
3. 查看订单状态分布图。
4. 查看热门分类。
5. 查看系统健康状态。
6. 打开 AI 客服问“为什么不能评价订单”。

---

## 21. 最终验收清单

### 21.1 功能验收

- [ ] 用户注册登录正常。
- [ ] JWT 鉴权正常。
- [ ] 地址管理正常。
- [ ] 商家列表正常。
- [ ] 商家搜索正常。
- [ ] 分类筛选正常。
- [ ] 商家详情正常。
- [ ] 菜品加购正常。
- [ ] 跨商家购物车限制正常。
- [ ] 外卖订单创建正常。
- [ ] 模拟支付正常。
- [ ] 重复支付幂等正常。
- [ ] 商家接单正常。
- [ ] 商家完成订单正常。
- [ ] 团购下单正常。
- [ ] 券码生成正常。
- [ ] 券码核销正常。
- [ ] 评价提交正常。
- [ ] 重复评价限制正常。
- [ ] 商家评分更新正常。
- [ ] 管理员看板正常。
- [ ] AI 客服正常。

### 21.2 工程验收

- [ ] 前后端可一键启动。
- [ ] Docker Compose 可启动。
- [ ] 有初始化数据。
- [ ] 有 Swagger 或接口文档。
- [ ] 有统一异常处理。
- [ ] 有统一响应格式。
- [ ] 有权限控制。
- [ ] 有测试用例。
- [ ] 无明显控制台报错。
- [ ] 无接口 500。
- [ ] 移动端页面不崩。
- [ ] README 完整。
- [ ] 文档与代码一致。

### 21.3 答辩验收

- [ ] PPT 有项目定位。
- [ ] PPT 有核心需求。
- [ ] PPT 有系统架构图。
- [ ] PPT 有 ER 图。
- [ ] PPT 有订单状态机。
- [ ] PPT 有团队分工。
- [ ] PPT 有测试结果。
- [ ] PPT 有 AI 使用说明。
- [ ] 演示视频流畅。
- [ ] 每个成员能解释自己负责的模块。

---

## 22. 风险与规避

| 风险 | 表现 | 规避 |
|---|---|---|
| 范围失控 | 功能越加越多 | 冻结 CR-01 至 CR-06，新增功能必须证明不影响主链路 |
| 前端不好看 | 页面风格不统一 | 先做设计系统组件，再写页面 |
| 后端逻辑混乱 | Controller 堆业务 | 强制 Controller-Service-Mapper 分层 |
| 状态流转错误 | 已支付订单被取消 | 状态机集中管理 |
| 支付重复 | 多次点击产生多条支付 | clientRequestId 幂等 |
| 权限越权 | 商家操作其他商家订单 | 所有商家端接口校验 merchantId |
| 演示翻车 | 数据不稳定 | 固定 seed 数据和演示账号 |
| 文档不一致 | 代码改了文档没改 | 每个模块完成后运行 doc-sync |
| AI 生成不可解释 | 成员无法答辩 | 每个成员必须 review 并能解释自己模块 |
| 抄袭风险 | 直接复制开源项目 | 只借鉴结构和风格，保留 AI 使用说明和人工修改记录 |

---

## 23. 给 Codex 的总提示词

可作为仓库根目录 `.ai/prompts/codex-system.md`：

```text
你是本项目的工程实现助手，负责协助开发“综合生活助手平台”。

项目定位：
这是软件工程课程大作业，目标是实现一个本地生活服务平台，不是完整商业系统。你必须优先保证核心链路稳定、代码结构清晰、文档可追踪、演示不翻车。

核心需求：
CR-01 用户认证与个人中心
CR-02 商家搜索、分类、推荐与详情
CR-03 外卖购物车、下单、模拟支付、订单状态跟踪
CR-04 到店团购购买、券码生成、商家核销
CR-05 订单完成后的用户评价与商家评分统计
CR-06 商家后台管理商品、套餐、订单

通用开发规则：
1. 不允许一次性生成整个项目。
2. 每次只实现一个明确模块。
3. 后端必须遵守 Controller -> Service -> Mapper 分层。
4. Controller 不写复杂业务逻辑。
5. 所有返回使用 ApiResponse。
6. 所有业务错误使用 BusinessException。
7. 状态字段必须使用枚举，不允许散落字符串。
8. 金额统一使用分，类型 BIGINT/Long。
9. 支付、订单、评价、核销等写操作必须考虑事务。
10. 支付必须考虑幂等。
11. 商家后台必须校验 merchantId，防止越权。
12. 前端必须使用统一组件，不允许每页重复造样式。
13. 每个页面必须考虑 loading、empty、error。
14. 每个模块完成后必须说明测试方式。
15. 不得照搬开源项目代码。

输出格式：
- 先说明实现计划。
- 再给出修改文件。
- 再给出关键代码。
- 最后给出运行和测试步骤。
```

---

## 24. 推荐开发顺序总结

最终请严格按以下顺序推进：

```text
1. 冻结核心需求
2. 建立 monorepo
3. 后端 common/security/db 基础设施
4. 前端设计系统和布局
5. 用户认证
6. 商家和商品
7. 购物车
8. 外卖订单
9. 支付幂等
10. 商家履约
11. 团购券码
12. 评价与评分
13. 管理员看板
14. AI 客服
15. 测试与部署
16. 文档同步
17. 演示视频和答辩材料
```

只要这份计划被严格执行，你们最终得到的项目应该是：

- 范围受控；
- 核心链路完整；
- 前端统一美观；
- 后端逻辑可靠；
- 数据库关系清楚；
- 接口文档明确；
- 可部署、可测试、可演示；
- 能解释 AI 使用过程；
- 能对应课程文档与验收要求。
