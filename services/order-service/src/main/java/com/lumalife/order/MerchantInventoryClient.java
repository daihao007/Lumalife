package com.lumalife.order;

import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Client for the merchant-owned inventory reservation boundary. */
@Component
final class MerchantInventoryClient {
  private final RestClient client;

  MerchantInventoryClient(RestClient.Builder builder,
      @Value("${lumalife.services.merchant.base-url:http://localhost:8082}") String baseUrl,
      @Value("${lumalife.internal.service-token:}") String serviceToken) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(3));
    this.client = builder.baseUrl(baseUrl)
      .requestFactory(requestFactory)
      .defaultHeader("X-Internal-Service-Token", serviceToken)
      .build();
  }

  void reserve(OrderStore.Order order, String clientRequestId) {
    invoke(() -> client.post()
        .uri("/internal/v1/inventory/reservations")
        .header("Idempotency-Key", reservationKey(order, clientRequestId))
        .header("X-Request-Id", clientRequestId)
        .body(new ReservationRequest(order.id(), Instant.now().plusSeconds(900), items(order)))
        .retrieve()
        .body(Reservation.class));
  }

  void release(OrderStore.Order order, String clientRequestId) {
    invoke(() -> client.post()
        .uri("/internal/v1/inventory/reservations/{orderId}:release", order.id())
        .header("Idempotency-Key", reservationKey(order, clientRequestId))
        .header("X-Request-Id", clientRequestId)
        .retrieve()
        .body(Reservation.class));
  }

  void releaseIfPresent(OrderStore.Order order, String clientRequestId) {
    try {
      release(order, clientRequestId);
    } catch (IllegalStateException error) {
      if (!error.getMessage().contains("HTTP 404")) throw error;
    }
  }

  void confirm(OrderStore.Order order, String clientRequestId) {
    invoke(() -> client.post()
        .uri("/internal/v1/inventory/reservations/{orderId}:confirm", order.id())
        .header("X-Request-Id", clientRequestId)
        .retrieve()
        .body(Reservation.class));
  }

  private List<ReservationItem> items(OrderStore.Order order) {
    String itemType = "GROUP_BUY".equals(order.type()) ? "GROUP_DEAL" : "PRODUCT";
    List<OrderStore.OrderLine> lines = order.lines().isEmpty()
      ? List.of(new OrderStore.OrderLine(order.productId(), "", order.quantity(), 0))
      : order.lines();
    return lines.stream()
      .map(line -> new ReservationItem(itemType, line.itemId(), line.quantity(), 0))
      .toList();
  }

  private String reservationKey(OrderStore.Order order, String clientRequestId) {
    String value = order.id() + ":" + clientRequestId;
    return "pay-" + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
  }

  private <T> T invoke(RemoteCall<T> call) {
    try {
      return call.run();
    } catch (RestClientResponseException error) {
      throw new IllegalStateException("库存服务拒绝请求: HTTP " + error.getStatusCode().value(), error);
    } catch (RestClientException error) {
      throw new IllegalStateException("库存服务暂时不可用", error);
    }
  }

  @FunctionalInterface
  private interface RemoteCall<T> { T run(); }

  record ReservationRequest(long orderId, Instant expiresAt, List<ReservationItem> items) {}
  record ReservationItem(String itemType, long itemId, int quantity, long expectedVersion) {}
  record Reservation(long orderId, String status, Instant expiresAt, List<ReservationItem> items) {}
}
