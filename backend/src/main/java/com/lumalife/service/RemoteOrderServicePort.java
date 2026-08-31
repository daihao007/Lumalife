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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.lumalife.common.BusinessException;

/** Routes every order-domain operation through the order-service contract. The fallback remains available only when the migration flags are disabled. */
@Configuration
@ConditionalOnProperty(prefix = "lumalife.migration.order", name = {"enabled", "backfill-completed"}, havingValue = "true")
public class RemoteOrderServicePort {
  @Bean
  @Primary
  OrderServicePort remoteOrderPort(DemoStore fallback, ObjectMapper mapper, RestClient.Builder builder,
      @Value("${lumalife.services.order.base-url:http://localhost:8083}") String baseUrl,
      @Value("${lumalife.services.merchant.base-url:http://localhost:8082}") String merchantBaseUrl,
      @Value("${lumalife.internal.service-token:}") String token) {
    RestClient client = builder.baseUrl(baseUrl).defaultHeader("X-Internal-Service-Token", token).build();
    RestClient merchantClient = builder.baseUrl(merchantBaseUrl).defaultHeader("X-Internal-Service-Token", token).build();
    var handler = (java.lang.reflect.InvocationHandler) (proxy, method, args) -> {
      try {
      if (method.getName().equals("userOrders")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/orders").header("X-User-Id", String.valueOf(user.id())).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(enrichOrder(row, merchantClient), Order.class)).toList();
      }
      if (method.getName().equals("cancel")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/{id}/cancel", args[1]).header("X-User-Id", String.valueOf(user.id())).retrieve().body(Map.class);
        return mapper.convertValue(enrichOrder(row, merchantClient), Order.class);
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
          lines.add(Map.of("productId", productId, "merchantId", product.get("merchantId"), "priceCent", product.get("priceCent"), "quantity", quantity));
        }
        Map<String,Object> delivery = new java.util.LinkedHashMap<>(); delivery.put("userId", user.id()); delivery.put("addressId", args[1]); delivery.put("lines", lines);
        List<Map> rows = client.post().uri("/internal/v1/orders/delivery").header("X-User-Id", String.valueOf(user.id())).body(delivery).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(enrichOrder(row, merchantClient), Order.class)).toList();
      }
      if (method.getName().equals("pay")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        var orderId = (long) args[1];
        Map current = client.get().uri("/internal/v1/orders/{id}", orderId).header("X-User-Id", String.valueOf(user.id())).retrieve().body(Map.class);
        Map row = client.post().uri("/internal/v1/orders/{id}/pay", orderId).header("X-User-Id", String.valueOf(user.id())).body(Map.of("amountCent", number(current.get("totalCent")), "clientRequestId", args[2])).retrieve().body(Map.class);
        return mapper.convertValue(enrichOrder(row, merchantClient), Order.class);
      }
      if (method.getName().equals("receive")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/{id}/receive", args[1]).header("X-User-Id", String.valueOf(user.id())).retrieve().body(Map.class);
        return mapper.convertValue(enrichOrder(row, merchantClient), Order.class);
      }
      if (method.getName().equals("createGroupOrder")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map deal = merchantClient.get().uri("/internal/v1/deals/{id}", args[1]).retrieve().body(Map.class);
        Map row = client.post().uri("/internal/v1/orders/group-buy").header("X-User-Id", String.valueOf(user.id())).body(Map.of("userId", user.id(), "dealId", args[1], "merchantId", deal.get("merchantId"), "priceCent", deal.get("priceCent"), "quantity", args[2])).retrieve().body(Map.class);
        return mapper.convertValue(enrichOrder(row, merchantClient), Order.class);
      }
      if (method.getName().equals("review")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/reviews").header("X-User-Id", String.valueOf(user.id())).body(Map.of("userId", user.id(), "orderId", args[1], "userName", user.nickname(), "score", args[2], "tasteScore", args[3], "serviceScore", args[4], "content", args[5])).retrieve().body(Map.class);
        return mapper.convertValue(row, Review.class);
      }
      if (method.getName().equals("merchantOrders")) {
        var admin = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/orders/merchant").header("X-Merchant-Id", String.valueOf(admin.merchantId())).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(enrichOrder(row, merchantClient), Order.class)).toList();
      }
      if (method.getName().equals("merchantReviews")) {
        var admin = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/orders/merchant/reviews").header("X-Merchant-Id", String.valueOf(admin.merchantId())).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Review.class)).toList();
      }
      if (method.getName().equals("transition")) {
        var admin = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/{id}/transition", args[1]).header("X-Merchant-Id", String.valueOf(admin.merchantId())).body(Map.of("next", ((OrderStatus) args[2]).name())).retrieve().body(Map.class);
        return mapper.convertValue(enrichOrder(row, merchantClient), Order.class);
      }
      if (method.getName().equals("verifyCoupon")) {
        var admin = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/orders/coupons/verify").header("X-Merchant-Id", String.valueOf(admin.merchantId())).body(Map.of("code", args[1])).retrieve().body(Map.class);
        return mapper.convertValue(row, Order.class);
      }
      return method.invoke(fallback, args);
      } catch (RestClientResponseException error) {
        throw remoteError(error);
      } catch (RestClientException error) {
        throw new BusinessException(50300, "订单服务暂时不可用", "ORDER_SERVICE_UNAVAILABLE");
      }
    };
    return (OrderServicePort) Proxy.newProxyInstance(OrderServicePort.class.getClassLoader(), new Class[]{OrderServicePort.class}, handler);
  }

  private static List<CartItem> cartItems(Map<?, ?> row) {
    List<CartItem> result = new ArrayList<>();
    if (row != null) row.forEach((key, value) -> result.add(new CartItem(Long.parseLong(String.valueOf(key)), ((Number) value).intValue())));
    return result;
  }

  /** Join order references with merchant-owned display data at the boundary. */
  private static Map<String, Object> enrichOrder(Map<?, ?> source, RestClient merchantClient) {
    Map<String, Object> row = new java.util.LinkedHashMap<>();
    if (source != null) source.forEach((key, value) -> row.put(String.valueOf(key), value));
    long merchantId = number(row.get("merchantId"));
    long productId = number(row.get("productId"));
    Map<?, ?> merchant = fetchMap(merchantClient, "/internal/v1/merchants/{id}", merchantId);
    if (string(row.get("merchantName")).isBlank()) row.put("merchantName", string(merchant.get("name")));
    String type = string(row.get("type"));
    boolean groupBuy = "GROUP_BUY".equals(type) || "GROUP_BUY".equals(string(row.get("orderType")));
    row.put("type", groupBuy ? "GROUP_BUY" : "DELIVERY");
    Object existingLines = row.get("lines");
    if (!(existingLines instanceof List<?> lines) || lines.isEmpty()) {
      int quantity = (int) number(row.get("quantity"));
      Map<?, ?> item = groupBuy
        ? fetchMap(merchantClient, "/internal/v1/deals/{id}", productId)
        : fetchMap(merchantClient, "/internal/v1/products/{id}", productId);
      long price = number(item.get("priceCent"));
      if (price <= 0 && quantity > 0) price = number(row.get("totalCent")) / quantity;
      String name = groupBuy ? string(item.get("title")) : string(item.get("name"));
      row.put("lines", List.of(Map.of(
        "itemId", productId,
        "name", name.isBlank() ? (groupBuy ? "团购套餐" : "商品") : name,
        "quantity", quantity,
        "priceCent", price)));
    }
    return row;
  }

  private static Map<?, ?> fetchMap(RestClient client, String uri, long id) {
    if (id <= 0) return Map.of();
    try {
      Map<?, ?> value = client.get().uri(uri, id).retrieve().body(Map.class);
      return value == null ? Map.of() : value;
    } catch (RuntimeException ignored) {
      return Map.of();
    }
  }

  private static long number(Object value) {
    if (value instanceof Number n) return n.longValue();
    if (value == null) return 0;
    try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ignored) { return 0; }
  }

  private static String string(Object value) { return value == null ? "" : String.valueOf(value); }

  private static BusinessException remoteError(RestClientResponseException error) {
    int status = error.getStatusCode().value();
    int code = switch (status) {
      case 400 -> 40000;
      case 401 -> 40100;
      case 403 -> 40300;
      case 404 -> 40400;
      case 409 -> 40900;
      default -> status >= 500 ? 50300 : 50000;
    };
    String message = error.getResponseBodyAsString();
    if (message == null || message.isBlank()) message = "订单服务请求失败";
    return new BusinessException(code, message, status >= 500 ? "ORDER_SERVICE_UNAVAILABLE" : "ORDER_REMOTE_ERROR");
  }
}
