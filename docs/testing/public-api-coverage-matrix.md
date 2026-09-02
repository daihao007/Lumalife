# Public API Coverage Matrix

> 更新日期：2026-09-03。范围仅包含 `ApiControllers` 在 `/api/v1` 下显式声明的 52 个公开业务映射，不包含 Actuator、Swagger 或 `/internal/**`。主要直接证据为 `backend/src/test/java/com/lumalife/controller/ApiSecurityIntegrationTest.java`，身份服务不可用证据补充来自同目录的 `IdentityAvailabilityIntegrationTest.java`；两者均使用 `MockMvc` 经过真实 Spring MVC、Security、Controller 与 Service。

## 结论

- 公开 API：52。
- 有直接 API 测试：52。
- 无直接 API 测试：0。
- 核心主路径、权限、参数校验、幂等、重复操作、跨商家越权等异常由 28 个 `ApiSecurityIntegrationTest` case 组合覆盖。
- “52/52”表示每个公开映射至少有一次直接 HTTP API 断言，不表示每个接口的所有参数组合和所有异常分支均已穷举。

## 逐接口矩阵

| # | Method | Public API | 直接 API 测试方法 | 主要验证 | 状态 |
| ---: | --- | --- | --- | --- | --- |
| 1 | POST | `/api/v1/auth/login` | `userCanRegisterReadAndUpdateProfileThroughApi`；`identityOutageUsesServiceUnavailableHttpStatus` | 登录成功；身份服务不可用 503 | ✅ |
| 2 | POST | `/api/v1/auth/register` | `userCanRegisterReadAndUpdateProfileThroughApi`；`publicRegistrationCannotChoosePrivilegedRole` | 用户注册；禁止角色提权 | ✅ |
| 3 | POST | `/api/v1/auth/register/merchant` | `merchantRegisterCreatesMerchantAdminAndCanAccessWorkbench` | 商家注册与工作台权限 | ✅ |
| 4 | GET | `/api/v1/auth/me` | `userCanRegisterReadAndUpdateProfileThroughApi`；`anonymousUserCannotAccessCurrentUserEndpoint` | 当前用户；匿名拒绝 | ✅ |
| 5 | POST | `/api/v1/user/profile` | `userCanRegisterReadAndUpdateProfileThroughApi` | 昵称和头像持久化 | ✅ |
| 6 | GET | `/api/v1/categories` | `publicCatalogApiReturnsCategoriesSearchAndMerchantDetail` | 公开分类列表 | ✅ |
| 7 | GET | `/api/v1/merchants` | `publicCatalogApiReturnsCategoriesSearchAndMerchantDetail`；`merchantListAppliesSortingAndFilters` | 搜索、排序、价格和评分过滤 | ✅ |
| 8 | GET | `/api/v1/merchants/{id}` | `publicCatalogApiReturnsCategoriesSearchAndMerchantDetail` | 商家、商品、团购和评价投影 | ✅ |
| 9 | GET | `/api/v1/cart` | `userCanSetDefaultAddressManageFavoritesAndCleanCartThroughApi`；`anonymousUserCannotAccessCart` | 清空后购物车；匿名拒绝 | ✅ |
| 10 | GET | `/api/v1/cart/detail` | `userDeliveryOrderApiCoversCartPaymentMerchantWorkflowAndReview`；`userOnlyApisRejectMerchantAndPlatformAdminTokens` | 购物车金额投影；角色拒绝 | ✅ |
| 11 | GET | `/api/v1/user/addresses` | `userCanManageAddressesThroughApi`；`nonUserRolesCannotAccessUserScopedApis` | 地址列表与默认状态；角色拒绝 | ✅ |
| 12 | POST | `/api/v1/user/addresses` | `userCanManageAddressesThroughApi` | 新增地址与默认地址切换 | ✅ |
| 13 | POST | `/api/v1/user/addresses/{id}/default` | `userCanSetDefaultAddressManageFavoritesAndCleanCartThroughApi` | 指定默认地址 | ✅ |
| 14 | POST | `/api/v1/user/addresses/{id}/delete` | `userCanManageAddressesThroughApi` | 删除地址并复查列表 | ✅ |
| 15 | GET | `/api/v1/user/favorites` | `userCanSetDefaultAddressManageFavoritesAndCleanCartThroughApi` | 收藏列表增删前后状态 | ✅ |
| 16 | POST | `/api/v1/user/favorites` | `userCanSetDefaultAddressManageFavoritesAndCleanCartThroughApi` | 新增收藏 | ✅ |
| 17 | POST | `/api/v1/user/favorites/{merchantId}/delete` | `userCanSetDefaultAddressManageFavoritesAndCleanCartThroughApi` | 取消收藏 | ✅ |
| 18 | POST | `/api/v1/cart/items` | `userCanCompleteCartOrderAndIdempotentPaymentThroughApi`；`reviewApiRejectsInvalidScoreAndDuplicateSubmission` | 加购与后续订单闭环 | ✅ |
| 19 | POST | `/api/v1/cart/items/{productId}` | `userCanCompleteCartOrderAndIdempotentPaymentThroughApi` | 修改数量 | ✅ |
| 20 | POST | `/api/v1/cart/items/{productId}/delete` | `userCanSetDefaultAddressManageFavoritesAndCleanCartThroughApi` | 删除单项并校验剩余商品 | ✅ |
| 21 | POST | `/api/v1/cart/clear` | `userCanSetDefaultAddressManageFavoritesAndCleanCartThroughApi` | 清空并复查购物车 | ✅ |
| 22 | POST | `/api/v1/orders/delivery` | `userCanCompleteCartOrderAndIdempotentPaymentThroughApi`；`userCanCancelPendingOrderAndCannotPayItAfterwards` | 创建外卖订单；取消后不可支付 | ✅ |
| 23 | POST | `/api/v1/orders/group-buy` | `groupBuyApiGeneratesCouponCanBeReviewedAfterUseAndMerchantVerifiesOnlyOnceForOwnStore`；`groupBuyApiRejectsInvalidQuantityAndUnknownCoupon` | 团购主路径；非法数量 | ✅ |
| 24 | POST | `/api/v1/payments` | `userCanCompleteCartOrderAndIdempotentPaymentThroughApi` | 支付成功与 clientRequestId 幂等 | ✅ |
| 25 | POST | `/api/v1/orders/{id}/cancel` | `userCanCancelPendingOrderAndCannotPayItAfterwards`；`userCanCompleteCartOrderAndIdempotentPaymentThroughApi` | 待支付取消；已支付冲突 | ✅ |
| 26 | POST | `/api/v1/orders/{id}/receive` | `userDeliveryOrderApiCoversCartPaymentMerchantWorkflowAndReview` | 完成履约后确认收货 | ✅ |
| 27 | GET | `/api/v1/orders` | `userCanCompleteCartOrderAndIdempotentPaymentThroughApi`；`nonUserRolesCannotAccessUserScopedApis` | 用户订单列表；角色拒绝 | ✅ |
| 28 | POST | `/api/v1/reviews` | `userDeliveryOrderApiCoversCartPaymentMerchantWorkflowAndReview`；`reviewApiRejectsInvalidScoreAndDuplicateSubmission` | 评价成功；非法评分和重复评价 | ✅ |
| 29 | GET | `/api/v1/conversations` | `userAndMerchantCanCompleteConversationRoundTripAndUseAssistantApi` | 用户会话列表 | ✅ |
| 30 | GET | `/api/v1/conversations/{merchantId}` | `userAndMerchantCanCompleteConversationRoundTripAndUseAssistantApi` | 用户读取会话与商家回复 | ✅ |
| 31 | POST | `/api/v1/conversations/{merchantId}/messages` | `userAndMerchantCanCompleteConversationRoundTripAndUseAssistantApi` | 用户消息与 AI 回复持久化 | ✅ |
| 32 | GET | `/api/v1/merchant-admin/orders` | `merchantRegisterCreatesMerchantAdminAndCanAccessWorkbench`；`normalUserCannotAccessMerchantAdminApis` | 商家订单列表；普通用户拒绝 | ✅ |
| 33 | GET | `/api/v1/merchant-admin/profile` | `merchantCanReadAndDeleteOwnedCatalogResourcesThroughApi` | 商家和账号投影 | ✅ |
| 34 | POST | `/api/v1/merchant-admin/profile` | `merchantCanUpdateNicknameAndPublicStoreName` | 更新商家昵称和公开店名 | ✅ |
| 35 | PUT | `/api/v1/merchant-admin/profile` | `merchantProfilePutPersistsNicknameAcrossLoginAndDiscovery` | PUT 替换并跨登录持久化 | ✅ |
| 36 | GET | `/api/v1/merchant-admin/reviews` | `userDeliveryOrderApiCoversCartPaymentMerchantWorkflowAndReview` | 商家读取所属评价 | ✅ |
| 37 | GET | `/api/v1/merchant-admin/products` | `merchantCanReadAndDeleteOwnedCatalogResourcesThroughApi` | 商品列表与删除后复查 | ✅ |
| 38 | POST | `/api/v1/merchant-admin/products` | `merchantAdminCanMaintainProductAndGroupDealThroughApi` | 新增商品；非法价格 | ✅ |
| 39 | POST | `/api/v1/merchant-admin/products/{id}/toggle` | `merchantAdminCanMaintainProductAndGroupDealThroughApi` | 上下架切换 | ✅ |
| 40 | POST | `/api/v1/merchant-admin/products/{id}/delete` | `merchantCanReadAndDeleteOwnedCatalogResourcesThroughApi` | 删除自有商品 | ✅ |
| 41 | GET | `/api/v1/merchant-admin/group-deals` | `merchantCanReadAndDeleteOwnedCatalogResourcesThroughApi` | 团购列表与删除后复查 | ✅ |
| 42 | POST | `/api/v1/merchant-admin/group-deals` | `merchantAdminCanMaintainProductAndGroupDealThroughApi`；`merchantApiRejectsCrossStoreOrderAndGroupDealMutation` | 新增团购；跨店修改拒绝 | ✅ |
| 43 | POST | `/api/v1/merchant-admin/group-deals/{id}/toggle` | `merchantAdminCanMaintainProductAndGroupDealThroughApi` | 启停团购 | ✅ |
| 44 | POST | `/api/v1/merchant-admin/group-deals/{id}/delete` | `merchantCanReadAndDeleteOwnedCatalogResourcesThroughApi` | 删除自有团购 | ✅ |
| 45 | POST | `/api/v1/merchant-admin/orders/{id}/transition` | `userDeliveryOrderApiCoversCartPaymentMerchantWorkflowAndReview`；`merchantApiRejectsCrossStoreOrderAndGroupDealMutation` | 履约状态机；跨店拒绝 | ✅ |
| 46 | POST | `/api/v1/merchant-admin/coupons/verify` | `groupBuyApiGeneratesCouponCanBeReviewedAfterUseAndMerchantVerifiesOnlyOnceForOwnStore`；`groupBuyApiRejectsInvalidQuantityAndUnknownCoupon` | 核销成功、重复、越权和未知券码 | ✅ |
| 47 | GET | `/api/v1/merchant-admin/conversations` | `userAndMerchantCanCompleteConversationRoundTripAndUseAssistantApi` | 商家会话列表 | ✅ |
| 48 | GET | `/api/v1/merchant-admin/conversations/{userId}` | `userAndMerchantCanCompleteConversationRoundTripAndUseAssistantApi` | 商家读取指定用户会话 | ✅ |
| 49 | POST | `/api/v1/merchant-admin/conversations/{userId}/messages` | `userAndMerchantCanCompleteConversationRoundTripAndUseAssistantApi` | 商家人工回复及用户可见性 | ✅ |
| 50 | POST | `/api/v1/merchant-admin/assistant/ask` | `merchantAssistantEndpointReturnsReplyForMerchantAdmin` | 商家 AI 回复与角色授权 | ✅ |
| 51 | GET | `/api/v1/admin/metrics` | `platformAdminCanAccessMetrics`；`merchantAdminCannotAccessPlatformAdminApis` | 平台指标；商家角色拒绝 | ✅ |
| 52 | POST | `/api/v1/assistant/ask` | `userAndMerchantCanCompleteConversationRoundTripAndUseAssistantApi` | 公共助手返回非空答案 | ✅ |

## 维护规则

1. 新增、删除或修改 `ApiControllers` 映射时，必须在同一提交更新本矩阵。
2. 每个公开映射至少关联一个直接 `MockMvc` API 测试；仅 Service 单测、前端 mock 或文档声明不计直接覆盖。
3. 高风险写操作应至少覆盖主路径和一个权限、参数、状态或幂等异常；未覆盖的分支必须明确记录，而不能把 52/52 解释为穷举覆盖。
4. 复验命令：`cd backend && mvn -B -ntp -Dtest=ApiSecurityIntegrationTest test`；全量后端复验：`cd backend && mvn test`。
