# Final Traceability Matrix

> 正式编号使用 `REQ01~09 / UC01~09 / {SYS|COMP|OBJ}-SEQ01~09`。历史 `CR-01~06` 只作为旧范围映射文字保留，不再出现于三层图文件名。当前服务总图见 `diagrams/final/current-architecture.mmd`。

| REQ | UC | System Design | Component Design | Object Design | Code | Unit | Integration/API | E2E | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ01 | UC01 账号、资料、地址 | `SYS-SEQ01`（单体基线） | `COMP-SEQ01` + 当前架构总图 | `OBJ-SEQ01` | `AuthService`、`RemoteIdentityServicePort`、`IdentityApi/Store`、Login/Profile | DemoStore、routing | IdentityAvailability、IdentityHealth/Token | microservice UC01 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ02 | UC02 发现与收藏 | `SYS-SEQ02` | `COMP-SEQ02` + 当前架构总图 | `OBJ-SEQ02` | `CatalogService`、`FavoriteService`、`RemoteMerchantServicePort`、`MerchantApi/Store` | DemoStore、api/routing | MerchantBusiness | microservice UC02 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ03 | UC03 外卖下单/支付/取消 | `SYS-SEQ03` | `COMP-SEQ03` + merchant/order/RabbitMQ | `OBJ-SEQ03` | `CartService`、`OrderWorkflowService`、`RemoteOrderServicePort`、Order Store/Saga | DemoStore、OrderStore/consumer、OrderInteractions | ApiSecurity、OrderBusiness | microservice UC03 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ04 | UC04 履约/收货/评价 | `SYS-SEQ04` | `COMP-SEQ04` + 当前架构总图 | `OBJ-SEQ04` | `MerchantAdminService`、Order API/Store、review projection | DemoStore、OrderStore/consumer | OrderBusiness、MerchantBusiness | microservice UC04 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ05 | UC05 团购/支付/券码 | `SYS-SEQ05` | `COMP-SEQ05` + merchant/order | `OBJ-SEQ05` | `OrderWorkflowService`、Merchant/Order Store | DemoStore、Order consumer | OrderBusiness、MerchantBusiness | microservice UC05 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ06 | UC06 券码核销 | `SYS-SEQ06` | `COMP-SEQ06` + order | `OBJ-SEQ06` | `MerchantAdminService`、Order API/Store | DemoStore | ApiSecurity、OrderBusiness | microservice UC06 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ07 | UC07 经营内容发布 | `SYS-SEQ07` | `COMP-SEQ07` + merchant | `OBJ-SEQ07` | `MerchantAdminService`、Merchant API/Store、MerchantProducts | DemoStore、Vitest UC07 | MerchantBusiness | microservice UC07 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ08 | UC08 用户/商家/AI 客服 | `SYS-SEQ08` | `COMP-SEQ08` 是单体基线；当前为 merchant+assistant | `OBJ-SEQ08` 是单体基线 | `AssistantService`、Remote ports、MerchantStore、AssistantAnswerService、两个客服页 | assistant、frontend components | AssistantAnswer、MerchantBusiness | microservice UC08 旧提交 PASS；UI `9f0a755` 3/3 | ⚠️ 当前 remote 未重跑 |
| REQ09 | UC09 管理指标/健康 | `SYS-SEQ09` | `COMP-SEQ09` + BFF fan-out | `OBJ-SEQ09` | `AdminDashboardService`、`RemoteMetricsServicePort`、三个服务 metrics | RemoteMetrics | ApiSecurity、三个服务 health | microservice UC09 旧提交 PASS | ⚠️ 当前 remote 未重跑 |

## 追溯限制

- 27 张旧三层图已统一文件名和图标识，但内容仍是 `monolith-start`/兼容层模型；类仍存在，但不是默认 `prod,remote` 服务调用路径。
- 当前微服务组件边界由架构总图和三份架构表补足。答辩时必须明确“基线模型”和“当前微服务模型”的版本差异。
- UI E2E 在 `9f0a755` 已通过 3/3；由于当前 remote 尚未重跑，UC08 仍不能标记为当前提交的完整通过。
