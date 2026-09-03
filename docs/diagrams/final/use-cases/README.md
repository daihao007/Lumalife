# Current Microservice Use-Case Diagrams

> 范围：`prod,remote` 当前微服务运行时；生成日期：2026-09-03。

本目录是 9 个正式用例的当前三层模型。每个 UC 均有一个系统级图（SYS）、组件顺序图（COMP）和对象级图（OBJ），共 27 个可编辑 Mermaid 源文件；对应 SVG 导出文件位于 [`docs/assets/final/use-cases/`](../../../assets/final/use-cases/)。文件名遵循 `UCxx-{SYS|COMP|OBJ}-SEQxx-CURRENT`，与统一追溯矩阵的 `REQ/UC/SYS-SEQ/COMP-SEQ/OBJ-SEQ` 标识一致。

| UC | 当前服务边界 | 系统级 | 组件级 | 对象级 |
| --- | --- | --- | --- | --- |
| UC01 账号、资料与地址 | identity-service | [MMD](UC01-SYS-SEQ01-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC01-SYS-SEQ01-CURRENT.svg) | [MMD](UC01-COMP-SEQ01-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC01-COMP-SEQ01-CURRENT.svg) | [MMD](UC01-OBJ-SEQ01-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC01-OBJ-SEQ01-CURRENT.svg) |
| UC02 发现、详情与收藏 | merchant-service | [MMD](UC02-SYS-SEQ02-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC02-SYS-SEQ02-CURRENT.svg) | [MMD](UC02-COMP-SEQ02-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC02-COMP-SEQ02-CURRENT.svg) | [MMD](UC02-OBJ-SEQ02-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC02-OBJ-SEQ02-CURRENT.svg) |
| UC03 外卖下单、支付与取消 | order + merchant + RabbitMQ | [MMD](UC03-SYS-SEQ03-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC03-SYS-SEQ03-CURRENT.svg) | [MMD](UC03-COMP-SEQ03-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC03-COMP-SEQ03-CURRENT.svg) | [MMD](UC03-OBJ-SEQ03-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC03-OBJ-SEQ03-CURRENT.svg) |
| UC04 履约、收货与评价 | order + merchant | [MMD](UC04-SYS-SEQ04-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC04-SYS-SEQ04-CURRENT.svg) | [MMD](UC04-COMP-SEQ04-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC04-COMP-SEQ04-CURRENT.svg) | [MMD](UC04-OBJ-SEQ04-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC04-OBJ-SEQ04-CURRENT.svg) |
| UC05 团购购买与券码 | order + merchant + RabbitMQ | [MMD](UC05-SYS-SEQ05-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC05-SYS-SEQ05-CURRENT.svg) | [MMD](UC05-COMP-SEQ05-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC05-COMP-SEQ05-CURRENT.svg) | [MMD](UC05-OBJ-SEQ05-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC05-OBJ-SEQ05-CURRENT.svg) |
| UC06 券码核销 | order-service | [MMD](UC06-SYS-SEQ06-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC06-SYS-SEQ06-CURRENT.svg) | [MMD](UC06-COMP-SEQ06-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC06-COMP-SEQ06-CURRENT.svg) | [MMD](UC06-OBJ-SEQ06-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC06-OBJ-SEQ06-CURRENT.svg) |
| UC07 商家经营内容发布 | merchant-service | [MMD](UC07-SYS-SEQ07-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC07-SYS-SEQ07-CURRENT.svg) | [MMD](UC07-COMP-SEQ07-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC07-COMP-SEQ07-CURRENT.svg) | [MMD](UC07-OBJ-SEQ07-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC07-OBJ-SEQ07-CURRENT.svg) |
| UC08 用户、商家与 AI 客服 | merchant + assistant | [MMD](UC08-SYS-SEQ08-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC08-SYS-SEQ08-CURRENT.svg) | [MMD](UC08-COMP-SEQ08-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC08-COMP-SEQ08-CURRENT.svg) | [MMD](UC08-OBJ-SEQ08-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC08-OBJ-SEQ08-CURRENT.svg) |
| UC09 运营指标与健康聚合 | identity + merchant + order | [MMD](UC09-SYS-SEQ09-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC09-SYS-SEQ09-CURRENT.svg) | [MMD](UC09-COMP-SEQ09-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC09-COMP-SEQ09-CURRENT.svg) | [MMD](UC09-OBJ-SEQ09-CURRENT.mmd) · [SVG](../../../assets/final/use-cases/UC09-OBJ-SEQ09-CURRENT.svg) |

## 证据边界

- 图只表达当前服务边界与关键协作，不替代接口契约、数据归属或测试结果。
- UC03/UC05 的库存流程明确画出 RabbitMQ、Outbox/Inbox 与 Saga；同步调用仍以 HTTP + timeout/503 为准。
- UC08 的 Agnes provider 是外部依赖；assistant-service 无业务数据库，provider 不可用时使用确定性 fallback。
- `d03/`、`d04/` 下的同编号图仍是 `monolith-start`/兼容基线，不能与本目录的当前微服务图混称。

## 可复现导出

```bash
python3 scripts/generate-current-use-case-diagrams.py
for f in docs/diagrams/final/use-cases/*.mmd; do
  base=${f##*/}
  npx --yes @mermaid-js/mermaid-cli@11.12.0 \
    -i "$f" -o "docs/assets/final/use-cases/${base%.mmd}.svg" \
    --backgroundColor white
done
```
