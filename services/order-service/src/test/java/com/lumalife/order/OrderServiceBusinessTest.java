package com.lumalife.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = "lumalife.internal.service-token=test-internal-token")
class OrderServiceBusinessTest {
  @Autowired private TestRestTemplate http;

  @Test
  void continuesAfterTheHighestPersistedOrderId() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("SELECT COALESCE(MAX(id), 4000) FROM order_record", Long.class)).thenReturn(4007L);

    OrderStore store = new OrderStore(jdbc);
    OrderStore.Order order = store.create(new OrderStore.CreateOrderRequest(1, 1, 1001, 1, 2680));

    assertThat(order.id()).isEqualTo(4008L);
  }

  @Test
  void rejectsPaymentForACancelledOrder() {
    OrderStore store = new OrderStore();
    OrderStore.Order order = store.create(new OrderStore.CreateOrderRequest(1, 1, 1001, 1, 2680));
    store.cancel(1, order.id());

    assertThatThrownBy(() -> store.pay(1, order.id(), 0, "cancelled-order-payment"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("当前订单不可支付");
  }

  @Test
  void preservesEveryLineWhenCreatingAMultiProductDeliveryOrder() {
    OrderStore store = new OrderStore();
    OrderStore.Order order = store.createDeliveryOrders(new OrderStore.DeliveryRequest(1, 2101L, "测试用户 13800000001 契约测试地址",
      List.of(new OrderStore.DeliveryLine(1001, 1, 2680, 1, "藤椒鸡饭", "巷口川味研究所"),
        new OrderStore.DeliveryLine(1002, 1, 4280, 2, "毛血旺小锅", "巷口川味研究所")))).get(0);

    assertThat(order.quantity()).isEqualTo(3);
    assertThat(order.totalCent()).isEqualTo(11240);
    assertThat(order.lines()).extracting(OrderStore.OrderLine::itemId).containsExactly(1001L, 1002L);
    assertThat(order.lines()).extracting(OrderStore.OrderLine::quantity).containsExactly(1, 2);
    assertThat(order.lines()).extracting(OrderStore.OrderLine::name).containsExactly("藤椒鸡饭", "毛血旺小锅");
    assertThat(order.addressSnapshot()).isEqualTo("测试用户 13800000001 契约测试地址");
    assertThat(order.merchantName()).isEqualTo("巷口川味研究所");
  }

  @Test
  void ownsOrderCreationAndCancellation() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", "1");
    OrderStore.Order order = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", 1, "merchantId", 2, "productId", 1001, "quantity", 1, "totalCent", 2800), headers), OrderStore.Order.class).getBody();
    assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
    OrderStore.Order cancelled = http.exchange("/internal/v1/orders/" + order.id() + "/cancel", HttpMethod.POST,
      new HttpEntity<>(headers), OrderStore.Order.class).getBody();
    assertThat(cancelled.status()).isEqualTo("CANCELLED");
  }

  @Test
  void rejectsCallerSuppliedUserIdentity() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", "1");
    ResponseEntity<OrderStore.Order> response = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", 2, "merchantId", 2, "productId", 1001, "quantity", 1, "totalCent", 2800), headers), OrderStore.Order.class);
    assertThat(response.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void addsToCartWithoutOverwritingAnExistingQuantity() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", "1");

    http.exchange("/internal/v1/orders/cart/1001", HttpMethod.POST,
      new HttpEntity<>(Map.of("quantity", 1), headers), Map.class);
    Map<?, ?> cart = http.exchange("/internal/v1/orders/cart/1001/add", HttpMethod.POST,
      new HttpEntity<>(Map.of("quantity", 1), headers), Map.class).getBody();

    assertThat(cart.get("1001")).isEqualTo(2);
  }

  @Test
  void ctRtOrd04To08ReadsRemovesAndClearsOnlyTheCurrentUsersCart() {
    long userId = 91001L;
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", Long.toString(userId));
    http.exchange("/internal/v1/orders/cart/4001", HttpMethod.POST, new HttpEntity<>(Map.of("quantity", 2), headers), Map.class);
    Map<?, ?> cart = http.exchange("/internal/v1/orders/cart", HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
    assertThat(cart.get("4001")).isEqualTo(2);

    Map<?, ?> afterRemove = http.exchange("/internal/v1/orders/cart/4001", HttpMethod.DELETE, new HttpEntity<>(headers), Map.class).getBody();
    assertThat(afterRemove.containsKey("4001")).isFalse();
    http.exchange("/internal/v1/orders/cart/4002", HttpMethod.POST, new HttpEntity<>(Map.of("quantity", 1), headers), Map.class);
    http.exchange("/internal/v1/orders/cart", HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    Map<?, ?> afterClear = http.exchange("/internal/v1/orders/cart", HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
    assertThat(afterClear).isEmpty();
  }

  @Test
  void paysForTheOrderTotalAndReturnsThePaidOrder() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", "1");
    OrderStore.Order order = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", 1, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), headers), OrderStore.Order.class).getBody();

    OrderStore.Order paid = http.exchange("/internal/v1/orders/" + order.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 2680, "clientRequestId", "pay-contract-test"), headers), OrderStore.Order.class).getBody();

    assertThat(paid.status()).isEqualTo("PAID");
    assertThat(paid.totalCent()).isEqualTo(2680);
  }

  @Test
  void exposesOrderOwnedMetricsProjectionWithoutIdentityOrCatalogFields() {
    ResponseEntity<Map> response = http.exchange("/internal/v1/orders/metrics", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), Map.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsKeys("overview", "orderStatusDistribution", "merchantRanking", "health");
    assertThat(response.getBody()).doesNotContainKey("userAccounts").doesNotContainKey("merchants");
  }

  @Test
  void ctRtOrd02And09EnforcePaymentAmountIdempotencyAndCancelledState() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", "1");
    String runId = UUID.randomUUID().toString();
    OrderStore.Order payable = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", 1, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), headers), OrderStore.Order.class).getBody();

    ResponseEntity<String> wrongAmount = http.exchange("/internal/v1/orders/" + payable.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 1, "clientRequestId", "ct-wrong-" + runId), headers), String.class);
    assertThat(wrongAmount.getStatusCode().value()).isEqualTo(400);

    OrderStore.Order paid = http.exchange("/internal/v1/orders/" + payable.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 2680, "clientRequestId", "ct-paid-" + runId), headers), OrderStore.Order.class).getBody();
    OrderStore.Order replayed = http.exchange("/internal/v1/orders/" + payable.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 2680, "clientRequestId", "ct-paid-" + runId), headers), OrderStore.Order.class).getBody();
    assertThat(paid.status()).isEqualTo("PAID");
    assertThat(replayed.status()).isEqualTo("PAID");

    OrderStore.Order anotherOrder = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", 1, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), headers), OrderStore.Order.class).getBody();
    ResponseEntity<String> reusedForAnotherOrder = http.exchange("/internal/v1/orders/" + anotherOrder.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 2680, "clientRequestId", "ct-paid-" + runId), headers), String.class);
    assertThat(reusedForAnotherOrder.getStatusCode().value()).isEqualTo(409);

    OrderStore.Order cancelled = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", 1, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), headers), OrderStore.Order.class).getBody();
    http.exchange("/internal/v1/orders/" + cancelled.id() + "/cancel", HttpMethod.POST, new HttpEntity<>(headers), OrderStore.Order.class);
    ResponseEntity<String> cancelledPayment = http.exchange("/internal/v1/orders/" + cancelled.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 2680, "clientRequestId", "ct-cancelled-" + runId), headers), String.class);
    assertThat(cancelledPayment.getStatusCode().value()).isEqualTo(409);

    OrderStore.Order[] orders = http.exchange("/internal/v1/orders", HttpMethod.GET, new HttpEntity<>(headers), OrderStore.Order[].class).getBody();
    assertThat(orders).anySatisfy(order -> {
      assertThat(order.id()).isEqualTo(payable.id());
      assertThat(order.status()).isEqualTo("PAID");
    });
    assertThat(orders).anySatisfy(order -> {
      assertThat(order.id()).isEqualTo(cancelled.id());
      assertThat(order.status()).isEqualTo("CANCELLED");
    });
    assertThat(orders).anySatisfy(order -> {
      assertThat(order.id()).isEqualTo(anotherOrder.id());
      assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
    });
  }

  @Test
  void ctRtOrd10And14CreatesPaysAndVerifiesGroupBuyOnlyOnce() {
    long userId = 91002L;
    String runId = UUID.randomUUID().toString();
    HttpHeaders userHeaders = serviceHeaders();
    userHeaders.set("X-User-Id", Long.toString(userId));
    OrderStore.Order groupOrder = http.exchange("/internal/v1/orders/group-buy", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "dealId", 1, "merchantId", 1, "priceCent", 4880, "quantity", 1,
        "title", "双人川味套餐", "merchantName", "巷口川味研究所"), userHeaders), OrderStore.Order.class).getBody();
    assertThat(groupOrder.status()).isEqualTo("PENDING_PAYMENT");
    assertThat(groupOrder.merchantName()).isEqualTo("巷口川味研究所");
    OrderStore.Order paid = http.exchange("/internal/v1/orders/" + groupOrder.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 4880, "clientRequestId", "ct-group-" + runId), userHeaders), OrderStore.Order.class).getBody();
    assertThat(paid.status()).isEqualTo("PAID");

    HttpHeaders merchantHeaders = serviceHeaders();
    merchantHeaders.set("X-Merchant-Id", "1");
    String code = String.format("%012d", groupOrder.id());
    OrderStore.Order used = http.exchange("/internal/v1/orders/coupons/verify", HttpMethod.POST,
      new HttpEntity<>(Map.of("code", code), merchantHeaders), OrderStore.Order.class).getBody();
    ResponseEntity<String> repeated = http.exchange("/internal/v1/orders/coupons/verify", HttpMethod.POST,
      new HttpEntity<>(Map.of("code", code), merchantHeaders), String.class);
    assertThat(used.status()).isEqualTo("USED");
    assertThat(repeated.getStatusCode().value()).isEqualTo(409);
  }

  @Test
  void ctRtOrd11To17CreatesDeliveryOrderAndCompletesMerchantAndUserFlow() {
    long userId = 91003L;
    String runId = UUID.randomUUID().toString();
    HttpHeaders userHeaders = serviceHeaders();
    userHeaders.set("X-User-Id", Long.toString(userId));
    OrderStore.Order[] created = http.exchange("/internal/v1/orders/delivery", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "addressId", 2101, "addressSnapshot", "契约用户 13800000001 契约测试地址",
        "lines", List.of(Map.of("productId", 1001, "merchantId", 1, "priceCent", 2680, "quantity", 1,
          "name", "藤椒鸡饭", "merchantName", "巷口川味研究所"))), userHeaders), OrderStore.Order[].class).getBody();
    assertThat(created).hasSize(1);
    OrderStore.Order deliveryOrder = created[0];
    assertThat(deliveryOrder.addressSnapshot()).isEqualTo("契约用户 13800000001 契约测试地址");
    assertThat(deliveryOrder.merchantName()).isEqualTo("巷口川味研究所");
    http.exchange("/internal/v1/orders/" + deliveryOrder.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 2680, "clientRequestId", "ct-delivery-" + runId), userHeaders), OrderStore.Order.class);

    HttpHeaders merchantHeaders = serviceHeaders();
    merchantHeaders.set("X-Merchant-Id", "1");
    http.exchange("/internal/v1/orders/" + deliveryOrder.id() + "/transition", HttpMethod.POST,
      new HttpEntity<>(Map.of("next", "ACCEPTED"), merchantHeaders), OrderStore.Order.class);
    http.exchange("/internal/v1/orders/" + deliveryOrder.id() + "/transition", HttpMethod.POST,
      new HttpEntity<>(Map.of("next", "DELIVERING"), merchantHeaders), OrderStore.Order.class);
    http.exchange("/internal/v1/orders/" + deliveryOrder.id() + "/transition", HttpMethod.POST,
      new HttpEntity<>(Map.of("next", "COMPLETED"), merchantHeaders), OrderStore.Order.class);
    OrderStore.Order received = http.exchange("/internal/v1/orders/" + deliveryOrder.id() + "/receive", HttpMethod.POST,
      new HttpEntity<>(userHeaders), OrderStore.Order.class).getBody();
    assertThat(received.status()).isEqualTo("RECEIVED");

    Map<String, Object> reviewRequest = Map.of("userId", userId, "orderId", deliveryOrder.id(), "userName", "契约用户",
      "score", 5, "tasteScore", 5, "serviceScore", 5, "content", "契约测试评价");
    OrderStore.Review review = http.exchange("/internal/v1/orders/reviews", HttpMethod.POST,
      new HttpEntity<>(reviewRequest, userHeaders), OrderStore.Review.class).getBody();
    ResponseEntity<String> duplicateReview = http.exchange("/internal/v1/orders/reviews", HttpMethod.POST,
      new HttpEntity<>(reviewRequest, userHeaders), String.class);
    OrderStore.Order[] merchantOrders = http.exchange("/internal/v1/orders/merchant", HttpMethod.GET,
      new HttpEntity<>(merchantHeaders), OrderStore.Order[].class).getBody();
    OrderStore.Review[] merchantReviews = http.exchange("/internal/v1/orders/merchant/reviews", HttpMethod.GET,
      new HttpEntity<>(merchantHeaders), OrderStore.Review[].class).getBody();
    assertThat(review.orderId()).isEqualTo(deliveryOrder.id());
    assertThat(duplicateReview.getStatusCode().value()).isEqualTo(409);
    assertThat(merchantOrders).anySatisfy(order -> assertThat(order.id()).isEqualTo(deliveryOrder.id()));
    assertThat(merchantReviews).anySatisfy(item -> assertThat(item.orderId()).isEqualTo(deliveryOrder.id()));
  }

  @Test
  void exposesMerchantReviewProjectionAtTheFrozenInternalPath() {
    ResponseEntity<OrderStore.Review[]> response = http.exchange("/internal/v1/merchants/1/reviews", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), OrderStore.Review[].class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
  }

  @Test
  void requiresServiceTokenForInternalOrderReads() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-User-Id", "92020");

    ResponseEntity<String> response = http.exchange("/internal/v1/orders", HttpMethod.GET,
      new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void ctRtOrd02And18RestrictOrderListsAndDetailsToTheOwner() {
    long ownerId = 92021L;
    long otherUserId = 92022L;
    HttpHeaders ownerHeaders = serviceHeaders();
    ownerHeaders.set("X-User-Id", Long.toString(ownerId));
    OrderStore.Order created = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", ownerId, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), ownerHeaders),
      OrderStore.Order.class).getBody();

    OrderStore.Order[] ownerOrders = http.exchange("/internal/v1/orders", HttpMethod.GET,
      new HttpEntity<>(ownerHeaders), OrderStore.Order[].class).getBody();
    HttpHeaders otherHeaders = serviceHeaders();
    otherHeaders.set("X-User-Id", Long.toString(otherUserId));
    OrderStore.Order[] otherOrders = http.exchange("/internal/v1/orders", HttpMethod.GET,
      new HttpEntity<>(otherHeaders), OrderStore.Order[].class).getBody();
    ResponseEntity<String> otherDetail = http.exchange("/internal/v1/orders/" + created.id(), HttpMethod.GET,
      new HttpEntity<>(otherHeaders), String.class);
    ResponseEntity<String> missingUser = http.exchange("/internal/v1/orders/" + created.id(), HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), String.class);

    assertThat(ownerOrders).anySatisfy(order -> assertThat(order.id()).isEqualTo(created.id()));
    assertThat(otherOrders).noneMatch(order -> order.id() == created.id());
    assertThat(otherDetail.getStatusCode().value()).isEqualTo(404);
    assertThat(missingUser.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  void ctRtOrd04To08RejectInvalidCartRequestsAndKeepUsersIsolated() {
    long ownerId = 92023L;
    HttpHeaders ownerHeaders = serviceHeaders();
    ownerHeaders.set("X-User-Id", Long.toString(ownerId));

    ResponseEntity<String> zeroQuantity = http.exchange("/internal/v1/orders/cart/1001", HttpMethod.POST,
      new HttpEntity<>(Map.of("quantity", 0), ownerHeaders), String.class);
    ResponseEntity<String> negativeIncrement = http.exchange("/internal/v1/orders/cart/1001/add", HttpMethod.POST,
      new HttpEntity<>(Map.of("quantity", -1), ownerHeaders), String.class);
    ResponseEntity<String> absentProduct = http.exchange("/internal/v1/orders/cart/999999999", HttpMethod.DELETE,
      new HttpEntity<>(ownerHeaders), String.class);
    ResponseEntity<String> missingUser = http.exchange("/internal/v1/orders/cart", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), String.class);

    http.exchange("/internal/v1/orders/cart/1001", HttpMethod.POST,
      new HttpEntity<>(Map.of("quantity", 2), ownerHeaders), Map.class);
    HttpHeaders anotherUser = serviceHeaders();
    anotherUser.set("X-User-Id", "92024");
    Map<?, ?> anotherCart = http.exchange("/internal/v1/orders/cart", HttpMethod.GET,
      new HttpEntity<>(anotherUser), Map.class).getBody();
    Map<?, ?> ownerCart = http.exchange("/internal/v1/orders/cart", HttpMethod.GET,
      new HttpEntity<>(ownerHeaders), Map.class).getBody();
    ResponseEntity<Void> firstClear = http.exchange("/internal/v1/orders/cart", HttpMethod.DELETE,
      new HttpEntity<>(ownerHeaders), Void.class);
    ResponseEntity<Void> repeatedClear = http.exchange("/internal/v1/orders/cart", HttpMethod.DELETE,
      new HttpEntity<>(ownerHeaders), Void.class);

    assertThat(zeroQuantity.getStatusCode().value()).isEqualTo(400);
    assertThat(negativeIncrement.getStatusCode().value()).isEqualTo(400);
    assertThat(absentProduct.getStatusCode().value()).isEqualTo(400);
    assertThat(missingUser.getStatusCode().is4xxClientError()).isTrue();
    assertThat(anotherCart.containsKey(1001L)).isFalse();
    assertThat(anotherCart.containsKey("1001")).isFalse();
    assertThat(ownerCart.get("1001")).isEqualTo(2);
    assertThat(firstClear.getStatusCode().value()).isEqualTo(200);
    assertThat(repeatedClear.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void ctRtOrd01And10To11RejectInvalidCreationRequests() {
    long userId = 92025L;
    HttpHeaders userHeaders = serviceHeaders();
    userHeaders.set("X-User-Id", Long.toString(userId));

    ResponseEntity<String> invalidOrder = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "merchantId", 1, "productId", 1001, "quantity", 0, "totalCent", 2680), userHeaders), String.class);
    ResponseEntity<String> groupBuyAsOtherUser = http.exchange("/internal/v1/orders/group-buy", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId + 1, "dealId", 1, "merchantId", 1, "priceCent", 4880, "quantity", 1), userHeaders), String.class);
    ResponseEntity<String> invalidGroupBuy = http.exchange("/internal/v1/orders/group-buy", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "dealId", 1, "merchantId", 1, "priceCent", 4880, "quantity", 0), userHeaders), String.class);
    ResponseEntity<String> deliveryAsOtherUser = http.exchange("/internal/v1/orders/delivery", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId + 1, "addressId", 2101, "addressSnapshot", "越权地址", "lines", List.of()), userHeaders), String.class);
    ResponseEntity<String> emptyDelivery = http.exchange("/internal/v1/orders/delivery", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "addressId", 2101, "addressSnapshot", "空购物车", "lines", List.of()), userHeaders), String.class);
    ResponseEntity<String> invalidDelivery = http.exchange("/internal/v1/orders/delivery", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "addressId", 2101, "addressSnapshot", "非法商品", "lines",
        List.of(Map.of("productId", 1001, "merchantId", 1, "priceCent", 2680, "quantity", 0))), userHeaders), String.class);

    assertThat(invalidOrder.getStatusCode().value()).isEqualTo(400);
    assertThat(groupBuyAsOtherUser.getStatusCode().value()).isEqualTo(403);
    assertThat(invalidGroupBuy.getStatusCode().value()).isEqualTo(409);
    assertThat(deliveryAsOtherUser.getStatusCode().value()).isEqualTo(403);
    assertThat(emptyDelivery.getStatusCode().value()).isEqualTo(400);
    assertThat(invalidDelivery.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void ctRtOrd03And12To13RejectInvalidStateAndMerchantOwnershipTransitions() {
    long userId = 92026L;
    HttpHeaders userHeaders = serviceHeaders();
    userHeaders.set("X-User-Id", Long.toString(userId));
    OrderStore.Order cancellable = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), userHeaders),
      OrderStore.Order.class).getBody();
    HttpHeaders otherUserHeaders = serviceHeaders();
    otherUserHeaders.set("X-User-Id", "92027");
    ResponseEntity<String> crossUserCancel = http.exchange("/internal/v1/orders/" + cancellable.id() + "/cancel", HttpMethod.POST,
      new HttpEntity<>(otherUserHeaders), String.class);
    ResponseEntity<OrderStore.Order> cancelled = http.exchange("/internal/v1/orders/" + cancellable.id() + "/cancel", HttpMethod.POST,
      new HttpEntity<>(userHeaders), OrderStore.Order.class);
    ResponseEntity<String> repeatedCancel = http.exchange("/internal/v1/orders/" + cancellable.id() + "/cancel", HttpMethod.POST,
      new HttpEntity<>(userHeaders), String.class);
    ResponseEntity<String> receiveBeforeDelivery = http.exchange("/internal/v1/orders/" + cancellable.id() + "/receive", HttpMethod.POST,
      new HttpEntity<>(userHeaders), String.class);

    OrderStore.Order payable = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), userHeaders),
      OrderStore.Order.class).getBody();
    http.exchange("/internal/v1/orders/" + payable.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 2680, "clientRequestId", "ct-status-" + UUID.randomUUID()), userHeaders), OrderStore.Order.class);
    HttpHeaders otherMerchant = serviceHeaders();
    otherMerchant.set("X-Merchant-Id", "2");
    HttpHeaders ownerMerchant = serviceHeaders();
    ownerMerchant.set("X-Merchant-Id", "1");
    ResponseEntity<String> crossMerchant = http.exchange("/internal/v1/orders/" + payable.id() + "/transition", HttpMethod.POST,
      new HttpEntity<>(Map.of("next", "ACCEPTED"), otherMerchant), String.class);
    http.exchange("/internal/v1/orders/" + payable.id() + "/transition", HttpMethod.POST,
      new HttpEntity<>(Map.of("next", "ACCEPTED"), ownerMerchant), OrderStore.Order.class);
    ResponseEntity<String> invalidTransition = http.exchange("/internal/v1/orders/" + payable.id() + "/transition", HttpMethod.POST,
      new HttpEntity<>(Map.of("next", "COMPLETED"), ownerMerchant), String.class);

    assertThat(crossUserCancel.getStatusCode().value()).isEqualTo(400);
    assertThat(cancelled.getStatusCode().value()).isEqualTo(200);
    assertThat(repeatedCancel.getStatusCode().value()).isEqualTo(409);
    assertThat(receiveBeforeDelivery.getStatusCode().value()).isEqualTo(409);
    assertThat(crossMerchant.getStatusCode().value()).isEqualTo(403);
    assertThat(invalidTransition.getStatusCode().value()).isEqualTo(409);
  }

  @Test
  void ctRtOrd14RejectsUnknownAndCrossMerchantCoupons() {
    long userId = 92028L;
    HttpHeaders userHeaders = serviceHeaders();
    userHeaders.set("X-User-Id", Long.toString(userId));
    OrderStore.Order groupOrder = http.exchange("/internal/v1/orders/group-buy", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "dealId", 1, "merchantId", 1, "priceCent", 4880, "quantity", 1), userHeaders),
      OrderStore.Order.class).getBody();
    http.exchange("/internal/v1/orders/" + groupOrder.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 4880, "clientRequestId", "ct-coupon-" + UUID.randomUUID()), userHeaders), OrderStore.Order.class);
    String code = String.format("%012d", groupOrder.id());
    HttpHeaders otherMerchant = serviceHeaders();
    otherMerchant.set("X-Merchant-Id", "2");
    HttpHeaders ownerMerchant = serviceHeaders();
    ownerMerchant.set("X-Merchant-Id", "1");

    ResponseEntity<String> crossMerchant = http.exchange("/internal/v1/orders/coupons/verify", HttpMethod.POST,
      new HttpEntity<>(Map.of("code", code), otherMerchant), String.class);
    ResponseEntity<String> unknownCode = http.exchange("/internal/v1/orders/coupons/verify", HttpMethod.POST,
      new HttpEntity<>(Map.of("code", "not-a-real-coupon"), ownerMerchant), String.class);

    assertThat(crossMerchant.getStatusCode().value()).isEqualTo(403);
    assertThat(unknownCode.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void ctRtOrd15RejectsInvalidAndPrematureReviews() {
    long userId = 92029L;
    HttpHeaders userHeaders = serviceHeaders();
    userHeaders.set("X-User-Id", Long.toString(userId));
    OrderStore.Order order = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), userHeaders),
      OrderStore.Order.class).getBody();
    Map<String, Object> review = Map.of("userId", userId, "orderId", order.id(), "userName", "契约评价用户",
      "score", 5, "tasteScore", 5, "serviceScore", 5, "content", "待完成订单不应评价");
    ResponseEntity<String> invalidScore = http.exchange("/internal/v1/orders/reviews", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "orderId", order.id(), "userName", "契约评价用户",
        "score", 0, "tasteScore", 5, "serviceScore", 5, "content", "非法评分"), userHeaders), String.class);
    ResponseEntity<String> premature = http.exchange("/internal/v1/orders/reviews", HttpMethod.POST,
      new HttpEntity<>(review, userHeaders), String.class);
    ResponseEntity<String> otherUser = http.exchange("/internal/v1/orders/reviews", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId + 1, "orderId", order.id(), "userName", "越权用户",
        "score", 5, "tasteScore", 5, "serviceScore", 5, "content", "越权评价"), userHeaders), String.class);

    assertThat(invalidScore.getStatusCode().value()).isEqualTo(400);
    assertThat(premature.getStatusCode().value()).isEqualTo(409);
    assertThat(otherUser.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void ctRtOrd16And17IsolateMerchantProjectionsAndRequireMerchantIdentity() {
    long userId = 92030L;
    HttpHeaders userHeaders = serviceHeaders();
    userHeaders.set("X-User-Id", Long.toString(userId));
    OrderStore.Order merchantOneOrder = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), userHeaders),
      OrderStore.Order.class).getBody();
    OrderStore.Order merchantTwoOrder = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "merchantId", 2, "productId", 2001, "quantity", 1, "totalCent", 1980), userHeaders),
      OrderStore.Order.class).getBody();

    HttpHeaders merchantOneHeaders = serviceHeaders();
    merchantOneHeaders.set("X-Merchant-Id", "1");
    HttpHeaders merchantTwoHeaders = serviceHeaders();
    merchantTwoHeaders.set("X-Merchant-Id", "2");
    OrderStore.Order[] merchantOneOrders = http.exchange("/internal/v1/orders/merchant", HttpMethod.GET,
      new HttpEntity<>(merchantOneHeaders), OrderStore.Order[].class).getBody();
    OrderStore.Order[] merchantTwoOrders = http.exchange("/internal/v1/orders/merchant", HttpMethod.GET,
      new HttpEntity<>(merchantTwoHeaders), OrderStore.Order[].class).getBody();
    OrderStore.Review[] merchantOneReviews = http.exchange("/internal/v1/orders/merchant/reviews", HttpMethod.GET,
      new HttpEntity<>(merchantOneHeaders), OrderStore.Review[].class).getBody();
    ResponseEntity<String> missingMerchant = http.exchange("/internal/v1/orders/merchant", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), String.class);
    ResponseEntity<String> missingReviewMerchant = http.exchange("/internal/v1/orders/merchant/reviews", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), String.class);

    assertThat(merchantOneOrders).anySatisfy(order -> assertThat(order.id()).isEqualTo(merchantOneOrder.id()));
    assertThat(merchantOneOrders).noneMatch(order -> order.id() == merchantTwoOrder.id());
    assertThat(merchantTwoOrders).anySatisfy(order -> assertThat(order.id()).isEqualTo(merchantTwoOrder.id()));
    assertThat(merchantTwoOrders).noneMatch(order -> order.id() == merchantOneOrder.id());
    assertThat(merchantOneReviews).noneMatch(review -> review.orderId() == merchantOneOrder.id()
      || review.orderId() == merchantTwoOrder.id());
    assertThat(missingMerchant.getStatusCode().is4xxClientError()).isTrue();
    assertThat(missingReviewMerchant.getStatusCode().is4xxClientError()).isTrue();
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
