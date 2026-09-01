package com.lumalife.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.lumalife.service.boundary.MetricsServicePort;

/**
 * Composes read-only projections from the owning services.  In remote mode
 * the dashboard no longer reads DemoStore's user, merchant, order or review
 * collections; each source is fetched over its internal HTTP contract.
 */
@Configuration
@ConditionalOnProperty(prefix = "lumalife.migration.order", name = {"enabled", "backfill-completed"}, havingValue = "true")
public class RemoteMetricsServicePort {
  @Bean
  @Primary
  MetricsServicePort remoteMetricsPort(
      RestClient.Builder builder,
      @Value("${lumalife.services.identity.base-url:http://localhost:8081}") String identityUrl,
      @Value("${lumalife.services.merchant.base-url:http://localhost:8082}") String merchantUrl,
      @Value("${lumalife.services.order.base-url:http://localhost:8083}") String orderUrl,
      @Value("${lumalife.internal.service-token:}") String serviceToken) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(3));
    RestClient identity = client(builder, requestFactory, identityUrl, serviceToken);
    RestClient merchant = client(builder, requestFactory, merchantUrl, serviceToken);
    RestClient order = client(builder, requestFactory, orderUrl, serviceToken);
    return () -> compose(identity, merchant, order);
  }

  private static RestClient client(RestClient.Builder builder, JdkClientHttpRequestFactory requestFactory,
                                   String baseUrl, String token) {
    return builder.requestFactory(requestFactory).baseUrl(baseUrl)
      .defaultHeader("X-Luma-Service-Token", token == null ? "" : token).build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> compose(RestClient identity, RestClient merchant, RestClient order) {
    Map<String, Object> orderProjection = requiredMap(order.get().uri("/internal/v1/orders/metrics").retrieve().body(Map.class));
    List<Map<String, Object>> accounts = rows(identity.get().uri("/internal/v1/admin/accounts").retrieve().body(List.class));
    List<Map<String, Object>> merchants = rows(merchant.get().uri("/internal/v1/merchants").retrieve().body(List.class));

    Map<String, Object> result = new LinkedHashMap<>(orderProjection);
    Map<String, Object> overview = new LinkedHashMap<>(map(orderProjection.get("overview")));
    overview.put("users", accounts.stream().filter(row -> "USER".equals(String.valueOf(row.get("role")))).count());
    overview.put("merchants", merchants.size());
    result.put("overview", overview);
    result.put("userAccounts", accounts.stream().filter(row -> "USER".equals(String.valueOf(row.get("role")))).toList());
    result.put("merchantAccounts", accounts.stream().filter(row -> "MERCHANT_ADMIN".equals(String.valueOf(row.get("role")))).toList());
    result.put("merchantRanking", mergeMerchantNames(mapList(orderProjection.get("merchantRanking")), merchants));
    return result;
  }

  private static List<Map<String, Object>> mergeMerchantNames(List<Map<String, Object>> orderRanking,
                                                                List<Map<String, Object>> merchants) {
    Map<Long, Map<String, Object>> knownMerchants = new LinkedHashMap<>();
    for (Map<String, Object> merchant : merchants) {
      Number id = number(merchant.get("id"));
      if (id != null) knownMerchants.put(id.longValue(), merchant);
    }
    Map<Long, Map<String, Object>> ranking = new LinkedHashMap<>();
    for (Map<String, Object> row : orderRanking) {
      Number id = number(row.get("merchantId"));
      if (id == null) continue;
      Map<String, Object> merged = new LinkedHashMap<>(row);
      Map<String, Object> merchant = knownMerchants.get(id.longValue());
      if (merchant != null) {
        merged.put("name", merchant.getOrDefault("name", row.get("name")));
        Number score = number(row.get("avgScore"));
        if (score == null || score.doubleValue() == 0) merged.put("avgScore", merchant.getOrDefault("avgScore", 0.0));
      }
      ranking.put(id.longValue(), merged);
    }
    for (Map.Entry<Long, Map<String, Object>> entry : knownMerchants.entrySet()) {
      ranking.computeIfAbsent(entry.getKey(), id -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("merchantId", id);
        row.put("name", entry.getValue().getOrDefault("name", "商家 #" + id));
        row.put("orderCount", 0);
        row.put("revenueCent", 0);
        row.put("avgScore", entry.getValue().getOrDefault("avgScore", 0.0));
        return row;
      });
    }
    return ranking.values().stream()
      .sorted(Comparator.<Map<String, Object>, Long>comparing(row -> number(row.get("revenueCent")).longValue()).reversed())
      .toList();
  }

  private static Map<String, Object> requiredMap(Map<?, ?> value) {
    if (value == null) throw new IllegalStateException("远程指标投影为空");
    return map(value);
  }

  private static List<Map<String, Object>> rows(List<?> value) {
    if (value == null) return List.of();
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : value) if (item instanceof Map<?, ?> row) result.add(map(row));
    return result;
  }

  private static List<Map<String, Object>> mapList(Object value) { return rows(value instanceof List<?> list ? list : List.of()); }

  private static Map<String, Object> map(Object value) {
    if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, item) -> result.put(String.valueOf(key), item));
    return result;
  }

  private static Number number(Object value) { return value instanceof Number number ? number : null; }
}
