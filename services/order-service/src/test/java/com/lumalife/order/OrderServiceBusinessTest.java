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

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
