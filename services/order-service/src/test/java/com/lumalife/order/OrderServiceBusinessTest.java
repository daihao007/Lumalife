package com.lumalife.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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
  void paysForTheOrderTotalAndReturnsThePaidOrder() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", "1");
    OrderStore.Order order = http.exchange("/internal/v1/orders", HttpMethod.POST,
      new HttpEntity<>(Map.of("userId", 1, "merchantId", 1, "productId", 1001, "quantity", 1, "totalCent", 2680), headers), OrderStore.Order.class).getBody();

    OrderStore.Order paid = http.exchange("/internal/v1/orders/" + order.id() + "/pay", HttpMethod.POST,
      new HttpEntity<>(Map.of("amountCent", 0, "clientRequestId", "pay-contract-test"), headers), OrderStore.Order.class).getBody();

    assertThat(paid.status()).isEqualTo("PAID");
    assertThat(paid.totalCent()).isEqualTo(2680);
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
