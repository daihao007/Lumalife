package com.lumalife.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceBusinessTest {
  @Autowired private TestRestTemplate http;

  @Test
  void ownsOrderCreationAndCancellation() {
    OrderStore.Order order = http.postForObject("/internal/v1/orders",
      Map.of("userId", 1, "merchantId", 2, "productId", 1001, "quantity", 1, "totalCent", 2800), OrderStore.Order.class);
    assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-User-Id", "1");
    OrderStore.Order cancelled = http.postForObject("/internal/v1/orders/" + order.id() + "/cancel",
      new HttpEntity<>(headers), OrderStore.Order.class);
    assertThat(cancelled.status()).isEqualTo("CANCELLED");
  }
}
