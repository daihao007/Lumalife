# 三服务机器可读契约

本目录承接 Issue [#23](https://github.com/daihao007/Lumalife/issues/23) 的 `draft-2026-08-26` 契约候选，并作为 Issue [#33](https://github.com/daihao007/Lumalife/issues/33) 的接口机器可读附件：

| 文件 | 所有者 | 内容 |
|---|---|---|
| `identity-service.openapi.yaml` | identity-service | 9 个外部 API、token introspection、地址快照内部 API |
| `merchant-service.openapi.yaml` | merchant-service | 25 个外部 API、批量商品快照、库存预占/释放内部 API |
| `order-service.openapi.yaml` | order-service | 18 个外部 API、评价查询和支付状态内部 API |
| `domain-events.asyncapi.yaml` | 三服务 | 8 个版本化领域事件、公共信封和载荷约束 |

规范文件是迁移目标，不表示当前单体已经实现内部 API、JWT、数据库、Outbox/Inbox 或事件总线。现有行为与目标差异记录在 [`../16_三服务接口数据归属与契约草案.md`](../16_三服务接口数据归属与契约草案.md)。

## 校验

建议在 CI 使用固定版本执行：

```bash
npx --yes @redocly/cli@1.34.5 lint --config docs/contracts/redocly.yaml docs/contracts/identity-service.openapi.yaml docs/contracts/merchant-service.openapi.yaml docs/contracts/order-service.openapi.yaml
npx --yes @asyncapi/cli@3.1.1 validate docs/contracts/domain-events.asyncapi.yaml
```

Redocly 的 operation 级 4xx 响应完整度属于后续 lint 加严项；本草案首先冻结所有权、路径、请求/响应主模型、内部调用和事件载荷。任何破坏性变更必须更新版本并经过 consumer 兼容评审。
