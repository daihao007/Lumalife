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
    OrderStore.Order order = store.createDeliveryOrders(new OrderStore.DeliveryRequest(1, 2101L,
      List.of(new OrderStore.DeliveryLine(1001, 1, 2680, 1), new OrderStore.DeliveryLine(1002, 1, 4280, 2)))).get(0);

    assertThat(order.quantity()).isEqualTo(3);
    assertThat(order.totalCent()).isEqualTo(11240);
    assertThat(order.lines()).extracting(OrderStore.OrderLine::itemId).containsExactly(1001L, 1002L);
    assertThat(order.lines()).extracting(OrderStore.OrderLine::quantity).containsExactly(1, 2);
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
      new HttpEntity<>(Map.of("userId", userId, "dealId", 1, "merchantId", 1, "priceCent", 4880, "quantity", 1), userHeaders), OrderStore.Order.class).getBody();
    assertThat(groupOrder.status()).isEqualTo("PENDING_PAYMENT");
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
      new HttpEntity<>(Map.of("userId", userId, "addressId", 2101,
        "lines", List.of(Map.of("productId", 1001, "merchantId", 1, "priceCent", 2680, "quantity", 1))), userHeaders), OrderStore.Order[].class).getBody();
    assertThat(created).hasSize(1);
    OrderStore.Order deliveryOrder = created[0];
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

    OrderStore.Review review = http.exchange("/internal/v1/orders/reviews", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", userId, "orderId", deliveryOrder.id(), "userName", "契约用户", "score", 5, "tasteScore", 5, "serviceScore", 5, "content", "契约测试评价"), userHeaders), OrderStore.Review.class).getBody();
    OrderStore.Order[] merchantOrders = http.exchange("/internal/v1/orders/merchant", HttpMethod.GET,
      new HttpEntity<>(merchantHeaders), OrderStore.Order[].class).getBody();
    OrderStore.Review[] merchantReviews = http.exchange("/internal/v1/orders/merchant/reviews", HttpMethod.GET,
      new HttpEntity<>(merchantHeaders), OrderStore.Review[].class).getBody();
    assertThat(review.orderId()).isEqualTo(deliveryOrder.id());
    assertThat(merchantOrders).anySatisfy(order -> assertThat(order.id()).isEqualTo(deliveryOrder.id()));
    assertThat(merchantReviews).anySatisfy(item -> assertThat(item.orderId()).isEqualTo(deliveryOrder.id()));
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
