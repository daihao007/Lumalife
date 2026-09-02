# Final Traceability Matrix

> 正式编号只使用 `REQ01~09 / UC01~09`。历史 `CR-01~06` 仅保留在 `monolith-start` 模型文件名中。当前服务总图见 `diagrams/final/current-architecture.mmd`。

| REQ | UC | System Design | Component Design | Object Design | Code | Unit | Integration/API | E2E | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REQ01 | UC01 账号、资料、地址 | d03 `CR-01-SYS`（单体基线） | d03 `CR-01-COMP` + 当前架构总图 | d03 `CR-01-OBJ` | `AuthService`、`RemoteIdentityServicePort`、`IdentityApi/Store`、Login/Profile | DemoStore、routing | IdentityAvailability、IdentityHealth/Token | microservice UC01 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ02 | UC02 发现与收藏 | d03 `CR-02-SYS` | d03 `CR-02-COMP` + 当前架构总图 | d03 `CR-02-OBJ` | `CatalogService`、`FavoriteService`、`RemoteMerchantServicePort`、`MerchantApi/Store` | DemoStore、api/routing | MerchantBusiness | microservice UC02 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ03 | UC03 外卖下单/支付/取消 | d03 `CR-03-SYS` | d03 `CR-03-COMP` + merchant/order/RabbitMQ | d03 `CR-03-OBJ` | `CartService`、`OrderWorkflowService`、`RemoteOrderServicePort`、Order Store/Saga | DemoStore、OrderStore/consumer、OrderInteractions | ApiSecurity、OrderBusiness | microservice UC03 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ04 | UC04 履约/收货/评价 | d04 `UC04-SYS` | d04 `UC04-COMP` + 当前架构总图 | d04 `UC04-OBJ` | `MerchantAdminService`、Order API/Store、review projection | DemoStore、OrderStore/consumer | OrderBusiness、MerchantBusiness | microservice UC04 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ05 | UC05 团购/支付/券码 | d04 `UC05-SYS` | d04 `UC05-COMP` + merchant/order | d04 `UC05-OBJ` | `OrderWorkflowService`、Merchant/Order Store | DemoStore、Order consumer | OrderBusiness、MerchantBusiness | microservice UC05 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ06 | UC06 券码核销 | d04 `UC06-SYS` | d04 `UC06-COMP` + order | d04 `UC06-OBJ` | `MerchantAdminService`、Order API/Store | DemoStore | ApiSecurity、OrderBusiness | microservice UC06 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ07 | UC07 经营内容发布 | d04 `UC07-SYS` | d04 `UC07-COMP` + merchant | d04 `UC07-OBJ` | `MerchantAdminService`、Merchant API/Store、MerchantProducts | DemoStore、Vitest UC07 | MerchantBusiness | microservice UC07 旧提交 PASS | ⚠️ 当前 remote 未重跑 |
| REQ08 | UC08 用户/商家/AI 客服 | d04 `UC08-SYS` | d04 `UC08-COMP` 是单体基线；当前为 merchant+assistant | d04 `UC08-OBJ` 是单体基线 | `AssistantService`、Remote ports、MerchantStore、AssistantAnswerService、两个客服页 | assistant、frontend components | AssistantAnswer、MerchantBusiness | microservice UC08 旧提交 PASS；UI 当前 FAIL | ❌ 当前 UI 失败 |
| REQ09 | UC09 管理指标/健康 | d04 `UC09-SYS` | d04 `UC09-COMP` + BFF fan-out | d04 `UC09-OBJ` | `AdminDashboardService`、`RemoteMetricsServicePort`、三个服务 metrics | RemoteMetrics | ApiSecurity、三个服务 health | microservice UC09 旧提交 PASS | ⚠️ 当前 remote 未重跑 |

## 追溯限制

- 27 张旧三层图是 `monolith-start`/兼容层模型；类仍存在，但不是默认 `prod,remote` 服务调用路径。
- 当前微服务组件边界由架构总图和三份架构表补足。答辩时必须明确“基线模型”和“当前微服务模型”的版本差异。
- 当前 UI E2E 失败使 UC08 不能标记为全部通过。
