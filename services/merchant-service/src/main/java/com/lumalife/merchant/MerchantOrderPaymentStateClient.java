package com.lumalife.merchant;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Reads the order-owned payment projection before an expired reservation is released. */
interface MerchantOrderPaymentStateClient {
  String paymentState(long orderId);
}

@Component
final class HttpMerchantOrderPaymentStateClient implements MerchantOrderPaymentStateClient {
  private final RestClient client;

  HttpMerchantOrderPaymentStateClient(
      @Value("${lumalife.services.order.base-url:http://localhost:8083}") String baseUrl,
      @Value("${lumalife.internal.service-token:}") String serviceToken) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(2));
    this.client = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .defaultHeader("X-Internal-Service-Token", serviceToken)
        .defaultHeader("X-Caller-Service", "merchant-service")
        .build();
  }

  @Override
  public String paymentState(long orderId) {
    try {
      PaymentStateResponse response = client.get()
          .uri("/internal/v1/orders/{orderId}/payment-state", orderId)
          .header("X-Request-Id", "merchant-expiry-" + orderId + "-" + UUID.randomUUID())
          .retrieve()
          .body(PaymentStateResponse.class);
      if (response == null || response.paymentState() == null || response.paymentState().isBlank()) {
        throw new IllegalStateException("order-service 返回空支付状态");
      }
      return response.paymentState().trim().toUpperCase(java.util.Locale.ROOT);
    } catch (RestClientResponseException error) {
      throw new IllegalStateException("order-service 状态查询失败: HTTP " + error.getStatusCode().value(), error);
    } catch (RestClientException error) {
      throw new IllegalStateException("order-service 暂时不可用", error);
    }
  }

  private record PaymentStateResponse(long orderId, String paymentState) {}
}
