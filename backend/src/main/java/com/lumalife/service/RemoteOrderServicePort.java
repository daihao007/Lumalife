package com.lumalife.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumalife.domain.Models.Order;
import com.lumalife.domain.Models.CartItem;
import com.lumalife.domain.Models.Review;
import com.lumalife.domain.Models.CartLine;
import com.lumalife.domain.Enums.OrderStatus;
import com.lumalife.service.boundary.OrderServicePort;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/** Routes every order-domain operation through the order-service contract. The fallback remains available only when the migration flags are disabled. */
@Configuration
@ConditionalOnProperty(prefix = "lumalife.migration.order", name = {"enabled", "backfill-completed"}, havingValue = "true")
public class RemoteOrderServicePort {
  @Bean
  @Primary
  OrderServicePort remoteOrderPort(DemoStore fallback, ObjectMapper mapper, RestClient.Builder builder,
      @Value("${lumalife.services.order.base-url:http://localhost:8083}") String baseUrl,
      @Value("${lumalife.services.merchant.base-url:http://localhost:8082}") String merchantBaseUrl,
      @Value("${lumalife.services.identity.base-url:http://localhost:8081}") String identityBaseUrl,
      @Value("${lumalife.internal.service-token:}") String token) {
    RestClient client = builder.baseUrl(baseUrl).defaultHeader("X-Internal-Service-Token", token).build();
    RestClient merchantClient = builder.baseUrl(merchantBaseUrl).defaultHeader("X-Internal-Service-Token", token).build();
    RestClient identityClient = builder.baseUrl(identityBaseUrl).defaultHeader("X-Internal-Service-Token", token).build();
    java.util.function.Function<Map, Order> toOrder = row -> {
      Map<String, Object> enriched = new java.util.LinkedHashMap<>();
      if (row != null) row.forEach((key, value) -> enriched.put(String.valueOf(key), value));
      Object merchantId = enriched.get("merchantId");
      String merchantName = String.valueOf(enriched.getOrDefault("merchantName", ""));
      if (merchantId != null && (merchantName.isBlank() || merchantName.startsWith("商家 #"))) {
        try {
          Map remoteMerchant = merchantClient.get().uri("/internal/v1/merchants/{id}", ((Number) merchantId).longValue()).retrieve().body(Map.class);
          if (remoteMerchant != null && remoteMerchant.get("name") != null) enriched.put("merchantName", remoteMerchant.get("name"));
        } catch (RuntimeException ignored) {
          // Keep the order snapshot when the catalog is temporarily unavailable.
        }
      }
      normalizeTemporalValues(enriched);
      return mapper.convertValue(enriched, Order.class);
    };
    var handler = (java.lang.reflect.InvocationHandler) (proxy, method, args) -> {
      if (method.getName().equals("userOrders")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/orders").header("X-User-Id", String.valueOf(user.id())).retrieve().body(List.class);
        return rows.stream().map(toOrder).toList();
      }
      if (method.getName().equals("cancel")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/{id}/cancel", args[1]).header("X-User-Id", String.valueOf(user.id())).retrieve().body(Map.class);
        return toOrder.apply(row);
      }
      if (method.getName().equals("cart") || method.getName().equals("addCart") || method.getName().equals("updateCartItem") || method.getName().equals("removeCartItem") || method.getName().equals("clearCart")) {
        var userId = method.getName().equals("cart") ? (long) args[0] : (long) args[0];
        if (method.getName().equals("clearCart")) { client.delete().uri("/internal/v1/orders/cart").header("X-User-Id", String.valueOf(userId)).retrieve().toBodilessEntity(); return null; }
        if (method.getName().equals("removeCartItem")) {
          Map row = client.delete().uri("/internal/v1/orders/cart/{productId}", args[1]).header("X-User-Id", String.valueOf(userId)).retrieve().body(Map.class);
          return cartItems(row);
        }
        if (method.getName().equals("cart")) return cartItems(client.get().uri("/internal/v1/orders/cart").header("X-User-Id", String.valueOf(userId)).retrieve().body(Map.class));
        String path = method.getName().equals("addCart") ? "/internal/v1/orders/cart/{productId}/add" : "/internal/v1/orders/cart/{productId}";
        Map row = client.post().uri(path, args[1]).header("X-User-Id", String.valueOf(userId)).body(Map.of("quantity", args[2])).retrieve().body(Map.class);
        return cartItems(row);
      }
      if (method.getName().equals("cartDetail")) {
        long userId = (long) args[0];
        Map cart = client.get().uri("/internal/v1/orders/cart").header("X-User-Id", String.valueOf(userId)).retrieve().body(Map.class);
        List<CartLine> lines = new ArrayList<>();
        if (cart != null) for (Object key : cart.keySet()) {
          long productId = Long.parseLong(String.valueOf(key)); int quantity = ((Number) cart.get(key)).intValue();
          Map product = merchantClient.get().uri("/internal/v1/products/{id}", productId).retrieve().body(Map.class);
          long merchantId = ((Number) product.get("merchantId")).longValue(); long price = ((Number) product.get("priceCent")).longValue();
          Map merchant = merchantClient.get().uri("/internal/v1/merchants/{id}", merchantId).retrieve().body(Map.class);
          lines.add(new CartLine(productId, merchantId, String.valueOf(merchant.get("name")), String.valueOf(product.get("name")), price, quantity, price * quantity));
        }
        return lines;
      }
      if (method.getName().equals("createDeliveryOrders")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map cart = client.get().uri("/internal/v1/orders/cart").header("X-User-Id", String.valueOf(user.id())).retrieve().body(Map.class);
        List<Map<String,Object>> lines = new ArrayList<>();
        if (cart != null) for (Object key : cart.keySet()) {
          long productId = Long.parseLong(String.valueOf(key)); int quantity = ((Number) cart.get(key)).intValue();
          Map product = merchantClient.get().uri("/internal/v1/products/{id}", productId).retrieve().body(Map.class);
          Map merchant = merchantClient.get().uri("/internal/v1/merchants/{id}", product.get("merchantId")).retrieve().body(Map.class);
          lines.add(Map.of("productId", productId, "merchantId", product.get("merchantId"), "priceCent", product.get("priceCent"), "quantity", quantity, "name", product.getOrDefault("name", "商品 #" + productId), "merchantName", merchant.getOrDefault("name", "商家 #" + product.get("merchantId"))));
        }
        Map<String,Object> delivery = new java.util.LinkedHashMap<>(); delivery.put("userId", user.id()); delivery.put("addressId", args[1]); delivery.put("addressSnapshot", addressSnapshot(identityClient, user.id(), (Long) args[1])); delivery.put("lines", lines);
        List<Map> rows = client.post().uri("/internal/v1/orders/delivery").header("X-User-Id", String.valueOf(user.id())).body(delivery).retrieve().body(List.class);
        return rows.stream().map(toOrder).toList();
      }
      if (method.getName().equals("pay")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        var orderId = (long) args[1];
        Map row = client.post().uri("/internal/v1/orders/{id}/pay", orderId).header("X-User-Id", String.valueOf(user.id())).body(Map.of("amountCent", 0, "clientRequestId", args[2])).retrieve().body(Map.class);
        return toOrder.apply(row);
      }
      if (method.getName().equals("receive")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/{id}/receive", args[1]).header("X-User-Id", String.valueOf(user.id())).retrieve().body(Map.class);
        return toOrder.apply(row);
      }
      if (method.getName().equals("createGroupOrder")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map deal = merchantClient.get().uri("/internal/v1/deals/{id}", args[1]).retrieve().body(Map.class);
        Map merchant = merchantClient.get().uri("/internal/v1/merchants/{id}", deal.get("merchantId")).retrieve().body(Map.class);
        Map row = client.post().uri("/internal/v1/orders/group-buy").header("X-User-Id", String.valueOf(user.id())).body(Map.of("userId", user.id(), "dealId", deal.get("id"), "merchantId", deal.get("merchantId"), "priceCent", deal.get("priceCent"), "quantity", args[2], "title", deal.getOrDefault("title", "团购套餐"), "merchantName", merchant.getOrDefault("name", "商家 #" + deal.get("merchantId")))).retrieve().body(Map.class);
        return toOrder.apply(row);
      }
      if (method.getName().equals("review")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/reviews").header("X-User-Id", String.valueOf(user.id())).body(Map.of("userId", user.id(), "orderId", args[1], "userName", user.nickname(), "score", args[2], "tasteScore", args[3], "serviceScore", args[4], "content", args[5])).retrieve().body(Map.class);
        return mapper.convertValue(row, Review.class);
      }
      if (method.getName().equals("merchantOrders")) {
        var admin = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/orders/merchant").header("X-Merchant-Id", String.valueOf(admin.merchantId())).retrieve().body(List.class);
        return rows.stream().map(toOrder).toList();
      }
      if (method.getName().equals("merchantReviews")) {
        var admin = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/orders/merchant/reviews").header("X-Merchant-Id", String.valueOf(admin.merchantId())).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Review.class)).toList();
      }
      if (method.getName().equals("transition")) {
        var admin = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/{id}/transition", args[1]).header("X-Merchant-Id", String.valueOf(admin.merchantId())).body(Map.of("next", ((OrderStatus) args[2]).name())).retrieve().body(Map.class);
        return toOrder.apply(row);
      }
      if (method.getName().equals("verifyCoupon")) {
        var admin = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/coupons/verify").header("X-Merchant-Id", String.valueOf(admin.merchantId())).body(Map.of("code", args[1])).retrieve().body(Map.class);
        return toOrder.apply(row);
      }
      return method.invoke(fallback, args);
    };
    return (OrderServicePort) Proxy.newProxyInstance(OrderServicePort.class.getClassLoader(), new Class[]{OrderServicePort.class}, handler);
  }

  private static List<CartItem> cartItems(Map<?, ?> row) {
    List<CartItem> result = new ArrayList<>();
    if (row != null) row.forEach((key, value) -> result.add(new CartItem(Long.parseLong(String.valueOf(key)), ((Number) value).intValue())));
    return result;
  }

  private static String addressSnapshot(RestClient identityClient, long userId, Long addressId) {
    try {
      List<Map> addresses = identityClient.get().uri("/internal/v1/users/{id}/addresses", userId)
        .header("X-User-Id", String.valueOf(userId)).retrieve().body(List.class);
      if (addresses == null) return null;
      Map selected = addresses.stream()
        .filter(item -> addressId == null ? Boolean.TRUE.equals(item.get("defaultAddress")) : String.valueOf(item.get("id")).equals(String.valueOf(addressId)))
        .findFirst().orElse(null);
      if (selected == null) return null;
      return String.valueOf(selected.getOrDefault("contactName", "")) + " "
        + String.valueOf(selected.getOrDefault("phone", "")) + " "
        + String.valueOf(selected.getOrDefault("detail", ""));
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static void normalizeTemporalValues(Map<String, Object> order) {
    Object createdAt = order.get("createdAt");
    if (createdAt instanceof String value) order.put("createdAt", localDateTime(value));
    Object timeline = order.get("statusTimeline");
    if (timeline instanceof Map<?, ?> raw) {
      Map<String, String> normalized = new java.util.LinkedHashMap<>();
      raw.forEach((key, value) -> normalized.put(String.valueOf(key), value == null ? null : localDateTime(String.valueOf(value))));
      order.put("statusTimeline", normalized);
    }
  }

  private static String localDateTime(String value) {
    try {
      return java.time.OffsetDateTime.parse(value).toLocalDateTime().toString();
    } catch (java.time.format.DateTimeParseException ignored) {
      try {
        return java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString();
      } catch (java.time.format.DateTimeParseException ignoredAgain) {
        return value;
      }
    }
  }
}
