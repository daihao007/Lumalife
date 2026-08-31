package com.lumalife.order;

import static org.assertj.core.api.Assertions.assertThat;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = "lumalife.internal.service-token=test-internal-token")
class OrderServiceBusinessTest {
  @Autowired private TestRestTemplate http;

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
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
