# Service Communication

| 调用方 | 被调用方 | 接口/协议 | 用途 | 超时 | 失败处理 |
| --- | --- | --- | --- | --- | --- |
| frontend | backend | HTTP `/api/v1/**` | 所有公共业务 | 浏览器客户端 | UI 显示 API 错误 |
| backend | identity | internal HTTP + service token | 登录、资料、地址、Token、用户指标 | connect/read 2s/2s | 显式 503；无自动 fallback/retry |
| backend | merchant | internal HTTP + service token | 目录、商品、收藏、会话、库存上下文 | 2s/3s | 显式 503；无 circuit breaker |
| backend | order | internal HTTP + service token | 购物车、订单、支付、履约、评价 | 2s/3s | 显式 503；局部展示字段可降级 |
| backend | assistant | internal HTTP + service token | AI 答复 | 2s/35s | assistant 内部确定性 fallback |
| backend | identity + merchant + order | HTTP fan-out | 管理员运营指标 | 各 2s/3s | 依赖失败映射为服务不可用 |
| order | merchant | RabbitMQ topic + Outbox/Inbox | 库存预占、确认、释放与结果 | 异步 | 至少一次投递、Inbox 幂等、Saga 状态/补偿 |
| order | merchant | internal HTTP（broker 关闭时） | 库存兼容路径 | 2s/3s | 超时/错误推进失败结果 |
| merchant | order | internal HTTP | 过期预占前查询支付状态 | 1s/2s | 不可用则 `CHECK_REQUIRED`，不盲目释放 |
| assistant | Agnes provider | HTTPS | 外部 AI 答复 | connect 3s / request 30s | 规则型安全 fallback |

## 术语校准

- 已实现：timeout、AI fallback、HTTP 503 边界、Outbox/Inbox、消息幂等、Saga、人工核验状态。
- 未实现：通用同步 retry、circuit breaker、rate limit、bulkhead、DLQ。
- Outbox publisher 当前在 `convertAndSend` 后即标记 `PUBLISHED`，没有 publisher confirm；RabbitMQ 队列也没有 DLQ。这两项是答辩前应诚实说明的可靠性缺口。
