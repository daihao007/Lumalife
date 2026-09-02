# Data Ownership

> 事实来源：`services/data-ownership.yml`、三个 Store 的 JDBC SQL、`application-prod.yml` 与本轮 `scripts/test-service-data-ownership.sh` 通过结果。

| 数据表 | 所属服务 | 实际访问服务 | 跨服务直接访问 | 正确跨服务方式 |
| --- | --- | --- | --- | --- |
| `user_account` | identity | identity | 未发现 | identity internal HTTP |
| `user_address` | identity | identity | 未发现 | identity internal HTTP；订单保存地址快照 |
| `auth_session` | identity | identity | 未发现 | Token introspection HTTP |
| `category`、`merchant`、`merchant_catalog`、`group_deal` | merchant | merchant | 未发现 | merchant internal HTTP |
| `merchant_favorite`、`chat_message` | merchant | merchant | 未发现 | merchant internal HTTP |
| `inventory_reservation`、`inventory_reservation_item` | merchant | merchant | 未发现 | RabbitMQ inventory commands/results |
| `merchant_inbox_event`、`merchant_outbox_event` | merchant | merchant | 未发现 | Outbox/Inbox |
| `order_record`、`service_order_line`、`service_order_event` | order | order | 未发现 | order internal HTTP |
| `service_cart_item`、`service_payment`、`service_coupon`、`service_review` | order | order | 未发现 | order internal HTTP；评价只读投影 |
| `service_outbox_event`、`order_inbox_event`、`order_inventory_saga` | order | order | 未发现 | RabbitMQ Outbox/Inbox/Saga |

## 结论与限制

- 三个服务各自使用原生 `JdbcTemplate`/Store；代码中没有 JPA `Entity`、Repository 或 MyBatis Mapper。设计文档不得虚构 ORM 分层。
- ID 跨服务传递仅为 opaque reference；没有发现跨库 join 或由其他服务直接读写归属表。
- 静态脚本是一项边界守卫，不等于运行时数据库权限隔离证明。默认 Compose 是逻辑分库；只有 physical overlay/Kubernetes 才是物理实例分离。
- legacy `life_assistant` 只用于迁移、单体基线和回滚；`prod,remote` 不应读写其业务表。
