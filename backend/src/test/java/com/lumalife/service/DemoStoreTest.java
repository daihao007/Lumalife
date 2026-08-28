package com.lumalife.service;

import com.lumalife.common.BusinessException;
import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.domain.Models.ChatMessage;
import com.lumalife.domain.Models.Merchant;
import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DemoStoreTest {
  @TempDir
  Path tempDir;

  @Test
  void paymentIsIdempotentForSameClientRequestId() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    store.addCart(user.id(), 1001, 1);
    Order order = store.createDeliveryOrder(user, null);
    Order first = store.pay(user, order.id, "req-1");
    Order second = store.pay(user, order.id, "req-1");
    Assertions.assertEquals(first.id, second.id);
    Assertions.assertEquals(OrderStatus.PAID, second.status);
  }

  @Test
  void normalUserLoginCanReturnNullMerchantId() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    Assertions.assertEquals("13800000001", ((java.util.Map<?, ?>) store.login("13800000001", "abc123456").get("user")).get("phone"));
  }

  @Test
  void registeredUserCanUpdateProfileWithoutChangingRole() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    store.register("13900000001", "abc123456", "资料测试用户");
    User registered = store.userByPhone("13900000001");

    java.util.Map<String, Object> updated = store.updateProfile(registered, "资料更新用户", "avatar.png");

    Assertions.assertEquals("资料更新用户", updated.get("nickname"));
    Assertions.assertEquals("avatar.png", updated.get("avatarUrl"));
    Assertions.assertEquals(com.lumalife.domain.Enums.UserRole.USER, updated.get("role"));
    Assertions.assertNull(updated.get("merchantId"));
  }

  @Test
  void merchantDetailContainsVisibleProductsAndGroupDeals() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());

    Map<String, Object> detail = store.merchantDetail(1);

    Assertions.assertEquals(1L, ((Merchant) detail.get("merchant")).id());
    Assertions.assertTrue(((List<?>) detail.get("products")).stream().anyMatch(product -> ((com.lumalife.domain.Models.Product) product).id() == 1001));
    Assertions.assertTrue(((List<?>) detail.get("groupDeals")).stream().anyMatch(deal -> ((com.lumalife.domain.Models.GroupDeal) deal).id() == 1));
    Assertions.assertNotNull(detail.get("reviews"));
  }

  @Test
  void illegalMerchantStateTransitionIsRejected() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");
    store.addCart(user.id(), 1001, 1);
    Order order = store.createDeliveryOrder(user, null);
    Assertions.assertThrows(BusinessException.class, () -> store.transition(admin, order.id, OrderStatus.DELIVERING));
  }

  @Test
  void userCanOnlyKeepFiveAddresses() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    store.saveAddress(user, null, "A", "13800000001", "地址 3", false);
    store.saveAddress(user, null, "A", "13800000001", "地址 4", false);
    store.saveAddress(user, null, "A", "13800000001", "地址 5", false);
    Assertions.assertThrows(BusinessException.class,
      () -> store.saveAddress(user, null, "A", "13800000001", "地址 6", false));
  }

  @Test
  void merchantCannotToggleOtherMerchantProduct() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User admin = store.userByPhone("13800000003");
    Assertions.assertThrows(BusinessException.class, () -> store.toggleProduct(admin, 1001));
  }

  @Test
  void merchantCanDeleteOwnProductAndRemoveItFromCarts() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");

    store.addCart(user.id(), 1001, 1);
    store.deleteProduct(admin, 1001);

    Assertions.assertTrue(store.merchantProducts(admin).stream().noneMatch(p -> p.id() == 1001));
    Assertions.assertTrue(store.cart(user.id()).isEmpty());
  }

  @Test
  void userCanCancelOnlyPendingPaymentOrder() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    store.addCart(user.id(), 1001, 1);
    Order order = store.createDeliveryOrder(user, null);
    Assertions.assertEquals(OrderStatus.CANCELLED, store.cancel(user, order.id).status);

    store.addCart(user.id(), 1001, 1);
    Order paid = store.createDeliveryOrder(user, null);
    store.pay(user, paid.id, "req-cancel");
    Assertions.assertThrows(BusinessException.class, () -> store.cancel(user, paid.id));
  }

  @Test
  void cancelledOrderCannotBePaid() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    store.addCart(user.id(), 1002, 1);
    Order order = store.createDeliveryOrder(user, null);
    store.cancel(user, order.id);

    Assertions.assertThrows(BusinessException.class, () -> store.pay(user, order.id, "req-cancelled"));
  }

  @Test
  void cartItemQuantityCanBeChangedAndRemoved() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    store.addCart(user.id(), 1001, 1);
    Assertions.assertEquals(3, store.updateCartItem(user.id(), 1001, 3).get(0).quantity());
    Assertions.assertTrue(store.removeCartItem(user.id(), 1001).isEmpty());
  }

  @Test
  void registerCreatesNormalUserWithEncodedPassword() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    store.register("13900000001", "abc123456", "新同学");
    User user = store.userByPhone("13900000001");
    Assertions.assertEquals("新同学", user.nickname());
    Assertions.assertEquals(com.lumalife.domain.Enums.UserRole.USER, user.role());
    Assertions.assertNotEquals("abc123456", user.password());
  }

  @Test
  void merchantRegisterCreatesMerchantAdminAccount() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    store.register("new-shop-owner", "abc123456", "新店主", com.lumalife.domain.Enums.UserRole.MERCHANT_ADMIN);
    User user = store.userByPhone("new-shop-owner");

    Assertions.assertEquals(com.lumalife.domain.Enums.UserRole.MERCHANT_ADMIN, user.role());
    Assertions.assertNotNull(user.merchantId());
    Assertions.assertTrue(store.merchantOrders(user).isEmpty());
    Assertions.assertEquals("新店主", store.merchants("新店主", null, "recommend", null, null, null).get(0).name());
  }

  @Test
  void registerRejectsDuplicateUsernameAcrossRoles() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());

    Assertions.assertThrows(BusinessException.class,
      () -> store.register("13800000002", "abc123456", "重复普通用户"));
    Assertions.assertThrows(BusinessException.class,
      () -> store.register("13800000001", "abc123456", "重复商家", com.lumalife.domain.Enums.UserRole.MERCHANT_ADMIN));
  }

  @Test
  void seededLightFoodAndBakeryMerchantsHaveAdminAccounts() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());

    User lightFoodAdmin = store.userByPhone("13800000004");
    User bakeryAdmin = store.userByPhone("13800000005");

    Assertions.assertEquals(3L, lightFoodAdmin.merchantId());
    Assertions.assertEquals(4L, bakeryAdmin.merchantId());
    Assertions.assertEquals("绿盒轻食", lightFoodAdmin.nickname());
    Assertions.assertEquals("栗香烘焙室", bakeryAdmin.nickname());
    Assertions.assertTrue(store.login("13800000004", "abc123456").containsKey("token"));
    Assertions.assertTrue(store.login("13800000005", "abc123456").containsKey("token"));
  }

  @Test
  void merchantNicknameUpdateKeepsStoreNameAndAccountNameInSync() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User admin = store.userByPhone("13800000004");

    store.updateMerchantNickname(admin, "绿盒轻食 Plus");

    Assertions.assertEquals("绿盒轻食 Plus", store.userByPhone("13800000004").nickname());
    Assertions.assertEquals("绿盒轻食 Plus", store.merchants("绿盒轻食 Plus", null, "recommend", null, null, null).get(0).name());
    Assertions.assertEquals("绿盒轻食 Plus", ((Merchant) store.merchantProfile(store.userByPhone("13800000004")).get("merchant")).name());
  }

  @Test
  void merchantNicknameUpdatePersistsAcrossStoreRestart() {
    Path stateFile = tempDir.resolve("lumalife-state.json");
    DemoStore firstStore = new DemoStore(new BCryptPasswordEncoder(), stateFile);
    firstStore.updateMerchantNickname(firstStore.userByPhone("13800000004"), "Light Food Forever");

    DemoStore restartedStore = new DemoStore(new BCryptPasswordEncoder(), stateFile);
    User admin = restartedStore.userByPhone("13800000004");

    Assertions.assertEquals("Light Food Forever", admin.nickname());
    Assertions.assertEquals("Light Food Forever", ((java.util.Map<?, ?>) restartedStore.login("13800000004", "abc123456").get("user")).get("nickname"));
    Assertions.assertEquals("Light Food Forever", restartedStore.merchants("Light Food Forever", null, "recommend", null, null, null).get(0).name());
    Assertions.assertEquals("Light Food Forever", ((Merchant) restartedStore.merchantProfile(admin).get("merchant")).name());
  }

  @Test
  void registeredMerchantAccountPersistsAcrossStoreRestart() {
    Path stateFile = tempDir.resolve("lumalife-state.json");
    DemoStore firstStore = new DemoStore(new BCryptPasswordEncoder(), stateFile);
    firstStore.register("15670665527", "abc123456", "新开的粥铺", com.lumalife.domain.Enums.UserRole.MERCHANT_ADMIN);

    DemoStore restartedStore = new DemoStore(new BCryptPasswordEncoder(), stateFile);
    User admin = restartedStore.userByPhone("15670665527");

    Assertions.assertEquals("新开的粥铺", admin.nickname());
    Assertions.assertNotNull(admin.merchantId());
    Assertions.assertEquals("新开的粥铺", ((java.util.Map<?, ?>) restartedStore.login("15670665527", "abc123456").get("user")).get("nickname"));
    Assertions.assertEquals("新开的粥铺", restartedStore.merchants("新开的粥铺", null, "recommend", null, null, null).get(0).name());
    java.util.List<?> merchantAccounts = (java.util.List<?>) restartedStore.adminMetrics().get("merchantAccounts");
    Assertions.assertTrue(merchantAccounts.stream().anyMatch(account -> "15670665527".equals(((java.util.Map<?, ?>) account).get("username"))));
  }

  @Test
  void merchantStateFileOverridesSeededStoreNameForExistingMerchant() throws Exception {
    Path stateFile = tempDir.resolve("lumalife-state.json");
    Files.writeString(stateFile, """
      {
        "merchantProfiles": [
          { "phone": "13800000002", "merchantId": 1, "nickname": "巷口川菜馆" }
        ]
      }
      """);

    DemoStore store = new DemoStore(new BCryptPasswordEncoder(), stateFile);
    User admin = store.userByPhone("13800000002");

    Assertions.assertEquals("巷口川菜馆", admin.nickname());
    Assertions.assertEquals("巷口川菜馆", ((java.util.Map<?, ?>) store.login("13800000002", "abc123456").get("user")).get("nickname"));
    Assertions.assertEquals("巷口川菜馆", store.merchants("巷口川菜馆", null, "recommend", null, null, null).get(0).name());
    Assertions.assertEquals("巷口川菜馆", ((Merchant) store.merchantProfile(admin).get("merchant")).name());
  }

  @Test
  void oldMerchantProfileStateFileIsMigratedToAccountState() throws Exception {
    Path stateFile = tempDir.resolve("lumalife-state.json");
    Files.writeString(stateFile, """
      {
        "merchantProfiles": [
          { "phone": "13800000002", "merchantId": 1, "nickname": "巷口川菜馆" }
        ]
      }
      """);

    new DemoStore(new BCryptPasswordEncoder(), stateFile);

    String content = Files.readString(stateFile);
    Assertions.assertTrue(content.contains("\"accounts\""));
    Assertions.assertTrue(content.contains("\"13800000002\""));
    Assertions.assertTrue(content.contains("\"merchantProfiles\""));
  }

  @Test
  void merchantListSupportsSortAndFilters() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());

    Assertions.assertEquals("栗香烘焙室", store.merchants(null, null, "priceAsc", null, null, null).get(0).name());
    Assertions.assertEquals("巷口川味研究所", store.merchants(null, null, "salesDesc", null, null, null).get(0).name());
    Assertions.assertTrue(store.merchants(null, null, "recommend", 10, 20, null).isEmpty());
    Assertions.assertEquals(3, store.merchants(null, null, "recommend", null, null, 4.6).size());
  }

  @Test
  void merchantStatsAreAffectedByPaidOrdersAndReviews() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");

    Merchant before = store.merchants("巷口川味研究所", null, "recommend", null, null, null).get(0);
    store.addCart(user.id(), 1003, 1);
    Order order = store.createDeliveryOrder(user, null);
    store.pay(user, order.id, "req-live-stats");
    store.transition(admin, order.id, OrderStatus.ACCEPTED);
    store.transition(admin, order.id, OrderStatus.DELIVERING);
    store.transition(admin, order.id, OrderStatus.COMPLETED);
    store.review(user, order.id, 2, 2, 2, "这次有点一般");

    Merchant after = store.merchants("巷口川味研究所", null, "recommend", null, null, null).get(0);
    Assertions.assertEquals(before.monthlySales() + 1, after.monthlySales());
    Assertions.assertTrue(after.avgPrice() < before.avgPrice());
    Assertions.assertTrue(after.avgScore() < before.avgScore());
  }

  @Test
  void ordersReviewsMetricsAndMerchantStatsPersistAcrossStoreRestart() {
    Path stateFile = tempDir.resolve("lumalife-state.json");
    DemoStore firstStore = new DemoStore(new BCryptPasswordEncoder(), stateFile);
    User user = firstStore.userByPhone("13800000001");
    User admin = firstStore.userByPhone("13800000002");
    Merchant before = firstStore.merchants("巷口川味研究所", null, "recommend", null, null, null).get(0);

    firstStore.addCart(user.id(), 1003, 1);
    Order order = firstStore.createDeliveryOrder(user, null);
    firstStore.pay(user, order.id, "req-persist-stats");
    firstStore.transition(admin, order.id, OrderStatus.ACCEPTED);
    firstStore.transition(admin, order.id, OrderStatus.DELIVERING);
    firstStore.transition(admin, order.id, OrderStatus.COMPLETED);
    firstStore.review(user, order.id, 2, 2, 2, "持久化后的评价");

    DemoStore restartedStore = new DemoStore(new BCryptPasswordEncoder(), stateFile);
    Map<String, Object> metrics = restartedStore.adminMetrics();
    Merchant after = restartedStore.merchants("巷口川味研究所", null, "recommend", null, null, null).get(0);

    Assertions.assertEquals(1, metrics.get("orders"));
    Assertions.assertTrue((Long) metrics.get("amountCent") > 0);
    Assertions.assertEquals(before.monthlySales() + 1, after.monthlySales());
    Assertions.assertTrue(after.avgPrice() < before.avgPrice());
    Assertions.assertTrue(after.avgScore() < before.avgScore());
    Assertions.assertTrue(restartedStore.userOrders(restartedStore.userByPhone("13800000001")).get(0).reviewed);
  }

  @Test
  void customerServiceConversationPersistsAcrossStoreRestart() {
    Path stateFile = tempDir.resolve("lumalife-state.json");
    DemoStore firstStore = new DemoStore(new BCryptPasswordEncoder(), stateFile);
    User user = firstStore.userByPhone("13800000001");
    User admin = firstStore.userByPhone("13800000002");

    List<ChatMessage> userMessages = firstStore.sendUserMessage(user, 1, "今天可以少放辣吗", ignored -> "可以的，下单备注少辣即可。");
    Assertions.assertEquals(2, userMessages.size());
    firstStore.sendMerchantMessage(admin, user.id(), "我们已经帮您备注。");

    DemoStore restartedStore = new DemoStore(new BCryptPasswordEncoder(), stateFile);
    List<ChatMessage> restored = restartedStore.userConversation(restartedStore.userByPhone("13800000001"), 1);

    Assertions.assertEquals(3, restored.size());
    Assertions.assertEquals("今天可以少放辣吗", restored.get(0).content());
    Assertions.assertEquals("我们已经帮您备注。", restored.get(2).content());
    Assertions.assertEquals(1, restartedStore.merchantConversationSummaries(restartedStore.userByPhone("13800000002")).size());
  }

  @Test
  void merchantCannotReadOrReplyToAnotherMerchantsConversation() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User customer = store.userByPhone("13800000001");
    User merchantA = store.userByPhone("13800000002");
    User merchantB = store.userByPhone("13800000003");

    store.sendUserMessage(customer, merchantA.merchantId(), "只属于商家 A 的会话", ignored -> "收到");

    Assertions.assertThrows(BusinessException.class, () -> store.merchantConversation(merchantB, customer.id()));
    Assertions.assertThrows(BusinessException.class, () -> store.sendMerchantMessage(merchantB, customer.id(), "越权回复"));
  }

  @Test
  void deliveryOrderUsesSelectedAddressSnapshot() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    store.addCart(user.id(), 1001, 1);
    Order order = store.createDeliveryOrder(user, 102L);
    Assertions.assertEquals(102L, order.addressId);
    Assertions.assertTrue(order.addressSnapshot.contains("学院路"));
  }

  @Test
  void deliveryOrderRequiresAnAddress() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    store.register("13900000002", "abc123456", "无地址用户");
    User user = store.userByPhone("13900000002");
    store.addCart(user.id(), 1001, 1);
    Assertions.assertThrows(BusinessException.class, () -> store.createDeliveryOrder(user, null));
  }

  @Test
  void cartCanCreateSeparateDeliveryOrdersByMerchant() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    store.addCart(user.id(), 1001, 1);
    store.addCart(user.id(), 1004, 1);
    Assertions.assertEquals(2, store.createDeliveryOrders(user, null).size());
  }

  @Test
  void paymentDeductsDeliveryProductStock() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");
    int before = store.merchantProducts(admin).stream().filter(p -> p.id() == 1001).findFirst().orElseThrow().stock();

    store.addCart(user.id(), 1001, 2);
    Order order = store.createDeliveryOrder(user, null);
    store.pay(user, order.id, "req-stock-delivery");

    int after = store.merchantProducts(admin).stream().filter(p -> p.id() == 1001).findFirst().orElseThrow().stock();
    Assertions.assertEquals(before - 2, after);
    Assertions.assertEquals("巷口川味研究所", order.merchantName);
  }

  @Test
  void paymentDoesNotPartiallyDeductDeliveryStockWhenLaterLineFails() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");
    int firstBefore = store.merchantProducts(admin).stream().filter(p -> p.id() == 1001).findFirst().orElseThrow().stock();

    store.addCart(user.id(), 1001, 1);
    store.addCart(user.id(), 1002, 1);
    Order order = store.createDeliveryOrder(user, null);
    store.saveProduct(admin, 1002L, "毛血旺小锅", "课程演示热门搜索菜", 4280, 0, true);

    Assertions.assertThrows(BusinessException.class, () -> store.pay(user, order.id, "req-stock-rollback"));
    Assertions.assertEquals(firstBefore, store.merchantProducts(admin).stream()
      .filter(p -> p.id() == 1001).findFirst().orElseThrow().stock());
    Assertions.assertEquals(0, store.merchantProducts(admin).stream()
      .filter(p -> p.id() == 1002).findFirst().orElseThrow().stock());
    Assertions.assertEquals(OrderStatus.PENDING_PAYMENT, order.status);
    Assertions.assertFalse(order.stockDeducted);
  }

  @Test
  void orderListRepairsUnknownMerchantName() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");

    store.addCart(user.id(), 1001, 1);
    Order order = store.createDeliveryOrder(user, null);
    order.merchantName = "未知商家";

    Assertions.assertEquals("巷口川味研究所", store.userOrders(user).get(0).merchantName);
  }

  @Test
  void merchantCompletionKeepsDeductedStockVisible() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");
    int before = store.merchantProducts(admin).stream().filter(p -> p.id() == 1001).findFirst().orElseThrow().stock();

    store.addCart(user.id(), 1001, 1);
    Order order = store.createDeliveryOrder(user, null);
    store.pay(user, order.id, "req-stock-workflow");
    store.transition(admin, order.id, OrderStatus.ACCEPTED);
    store.transition(admin, order.id, OrderStatus.DELIVERING);
    store.transition(admin, order.id, OrderStatus.COMPLETED);

    int after = store.merchantProducts(admin).stream().filter(p -> p.id() == 1001).findFirst().orElseThrow().stock();
    Assertions.assertEquals(before - 1, after);
  }

  @Test
  void completedOrderRequiresReviewContent() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");

    store.addCart(user.id(), 1001, 1);
    Order order = store.createDeliveryOrder(user, null);
    store.pay(user, order.id, "req-review-content");
    store.transition(admin, order.id, OrderStatus.ACCEPTED);
    store.transition(admin, order.id, OrderStatus.DELIVERING);
    store.transition(admin, order.id, OrderStatus.COMPLETED);

    Assertions.assertThrows(BusinessException.class, () -> store.review(user, order.id, 5, 5, 5, " "));
    Assertions.assertEquals("体验很好", store.review(user, order.id, 5, 5, 5, " 体验很好 ").content());
  }

  @Test
  void merchantCanSeeReviewsForOwnOrders() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");
    User otherAdmin = store.userByPhone("13800000003");

    store.addCart(user.id(), 1001, 1);
    Order order = store.createDeliveryOrder(user, null);
    store.pay(user, order.id, "req-merchant-review");
    store.transition(admin, order.id, OrderStatus.ACCEPTED);
    store.transition(admin, order.id, OrderStatus.DELIVERING);
    store.transition(admin, order.id, OrderStatus.COMPLETED);
    store.review(user, order.id, 4, 5, 4, "味道不错");

    Assertions.assertEquals(order.id, store.merchantReviews(admin).get(0).orderId());
    Assertions.assertTrue(store.merchantReviews(otherAdmin).isEmpty());
  }

  @Test
  void paymentDeductsGroupDealStockOnceForSameRequest() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");
    int before = store.merchantDeals(admin).stream().filter(d -> d.id() == 1).findFirst().orElseThrow().stock();

    Order order = store.createGroupOrder(user, 1, 3);
    store.pay(user, order.id, "req-stock-deal");
    store.pay(user, order.id, "req-stock-deal");

    int after = store.merchantDeals(admin).stream().filter(d -> d.id() == 1).findFirst().orElseThrow().stock();
    Assertions.assertEquals(before - 3, after);
  }

  @Test
  void paymentRequestKeyCannotBeReusedForAnotherOrderByTheSameUser() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    Order first = store.createGroupOrder(user, 1, 1);
    Order second = store.createGroupOrder(user, 1, 1);

    store.pay(user, first.id, "req-user-scoped-key");
    BusinessException error = Assertions.assertThrows(BusinessException.class,
      () -> store.pay(user, second.id, "req-user-scoped-key"));

    Assertions.assertEquals(40900, error.code());
    Assertions.assertEquals("IDEMPOTENCY_CONFLICT", error.reason());
    Assertions.assertEquals(OrderStatus.PENDING_PAYMENT, second.status);
  }

  @Test
  void paidGroupOrderGeneratesTwelveDigitCouponCodeAndCanBeVerifiedOnce() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");

    Order order = store.createGroupOrder(user, 1, 1);
    Order paid = store.pay(user, order.id, "req-coupon-code");

    Assertions.assertNotNull(paid.couponCode);
    Assertions.assertTrue(paid.couponCode.matches("\\d{12}"));
    Assertions.assertEquals(OrderStatus.USED, store.verifyCoupon(admin, paid.couponCode).status);
    Assertions.assertThrows(BusinessException.class, () -> store.verifyCoupon(admin, paid.couponCode));
  }

  @Test
  void groupOrderRejectsNonPositiveQuantity() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");

    BusinessException error = Assertions.assertThrows(BusinessException.class,
      () -> store.createGroupOrder(user, 1, 0));
    Assertions.assertEquals(40900, error.code());
    Assertions.assertEquals("套餐不可购买", error.getMessage());
    Assertions.assertTrue(store.userOrders(user).isEmpty());
  }

  @Test
  void merchantCannotModifyAnotherMerchantGroupDeal() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User admin = store.userByPhone("13800000002");

    BusinessException error = Assertions.assertThrows(BusinessException.class,
      () -> store.saveDeal(admin, 2L, "越权套餐", "不应修改", 4990, 1, true));
    Assertions.assertEquals(40300, error.code());
    Assertions.assertEquals("无权维护该套餐", error.getMessage());
  }

  @Test
  void groupOrderCannotEnterDeliveryFulfillmentStates() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");

    Order order = store.createGroupOrder(user, 1, 1);
    Order paid = store.pay(user, order.id, "req-group-transition");

    BusinessException error = Assertions.assertThrows(BusinessException.class,
      () -> store.transition(admin, paid.id, OrderStatus.ACCEPTED));
    Assertions.assertEquals(40900, error.code());
    Assertions.assertEquals("团购订单只能通过券码核销", error.getMessage());
    Assertions.assertEquals(OrderStatus.PAID, paid.status);
    Assertions.assertEquals(OrderStatus.USED, store.verifyCoupon(admin, paid.couponCode).status);
  }

  @Test
  void merchantCannotVerifyCouponFromAnotherStore() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User otherAdmin = store.userByPhone("13800000003");

    Order order = store.createGroupOrder(user, 1, 1);
    Order paid = store.pay(user, order.id, "req-coupon-cross-store");

    Assertions.assertThrows(BusinessException.class, () -> store.verifyCoupon(otherAdmin, paid.couponCode));
  }

  @Test
  void merchantCannotTransitionAnotherMerchantOrder() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User wrongAdmin = store.userByPhone("13800000002");

    store.addCart(user.id(), 1004, 1);
    Order order = store.createDeliveryOrder(user, null);
    store.pay(user, order.id, "req-cross-store-transition");

    BusinessException error = Assertions.assertThrows(BusinessException.class,
      () -> store.transition(wrongAdmin, order.id, OrderStatus.ACCEPTED));
    Assertions.assertEquals(40300, error.code());
    Assertions.assertEquals(OrderStatus.PAID, order.status);
  }

  @Test
  void reviewRejectsDuplicateSubmission() {
    DemoStore store = new DemoStore(new BCryptPasswordEncoder());
    User user = store.userByPhone("13800000001");
    User admin = store.userByPhone("13800000002");

    store.addCart(user.id(), 1001, 1);
    Order order = store.createDeliveryOrder(user, null);
    store.pay(user, order.id, "req-review-duplicate");
    store.transition(admin, order.id, OrderStatus.ACCEPTED);
    store.transition(admin, order.id, OrderStatus.DELIVERING);
    store.transition(admin, order.id, OrderStatus.COMPLETED);
    store.review(user, order.id, 5, 5, 5, "首次评价");

    BusinessException error = Assertions.assertThrows(BusinessException.class,
      () -> store.review(user, order.id, 4, 4, 4, "重复评价"));
    Assertions.assertEquals(40900, error.code());
    Assertions.assertEquals("同一订单不可重复评价", error.getMessage());
  }

  @Test
  void repositoryBackedStoreRestoresAllMutableBusinessAreas() {
    AtomicReference<String> payload = new AtomicReference<>();
    BusinessStateRepository repository = new BusinessStateRepository() {
      @Override
      public Optional<String> load() {
        return Optional.ofNullable(payload.get());
      }

      @Override
      public void save(String value) {
        payload.set(value);
      }
    };

    DemoStore firstStore = new DemoStore(new BCryptPasswordEncoder(), repository);
    User user = firstStore.userByPhone("13800000001");
    User admin = firstStore.userByPhone("13800000002");
    firstStore.saveAddress(user, null, "数据库用户", "13800000001", "持久化路 7 号", false);
    firstStore.saveProduct(admin, 1001L, "MySQL 藤椒鸡饭", "数据库持久化商品", 2880, 88, true);
    firstStore.addCart(user.id(), 1001L, 2);
    firstStore.sendUserMessage(user, 1L, "数据库重启后还能看到吗", ignored -> "可以");

    DemoStore restartedStore = new DemoStore(new BCryptPasswordEncoder(), repository);

    Assertions.assertTrue(restartedStore.addresses(restartedStore.userByPhone("13800000001")).stream()
      .anyMatch(address -> "持久化路 7 号".equals(address.detail())));
    Assertions.assertEquals("MySQL 藤椒鸡饭", restartedStore.merchantProducts(restartedStore.userByPhone("13800000002")).stream()
      .filter(product -> product.id() == 1001L).findFirst().orElseThrow().name());
    Assertions.assertEquals(2, restartedStore.cart(user.id()).get(0).quantity());
    Assertions.assertEquals(2, restartedStore.userConversation(restartedStore.userByPhone("13800000001"), 1L).size());
    Assertions.assertFalse(((List<?>) restartedStore.adminMetrics().get("logs")).isEmpty());
  }
}
