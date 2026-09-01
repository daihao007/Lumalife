package com.lumalife.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumalife.domain.Models.Merchant;
import com.lumalife.domain.Models.Product;
import com.lumalife.domain.Models.Category;
import com.lumalife.service.boundary.MerchantServicePort;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;

/** Routes merchant/catalog capabilities to merchant-service during final cutover. */
@Configuration
@ConditionalOnProperty(prefix = "lumalife.migration.merchant", name = {"enabled", "backfill-completed"}, havingValue = "true")
public class RemoteMerchantServicePort {
  @Bean
  @Primary
  MerchantServicePort remoteMerchantPort(DemoStore fallback, ObjectMapper mapper,
      RestClient.Builder builder, @Value("${lumalife.services.merchant.base-url:http://localhost:8082}") String baseUrl,
      @Value("${lumalife.services.order.base-url:http://localhost:8083}") String orderBaseUrl,
      @Value("${lumalife.internal.service-token:}") String token) {
    RestClient client = builder.baseUrl(baseUrl).defaultHeader("X-Internal-Service-Token", token).build();
    RestClient orderClient = builder.baseUrl(orderBaseUrl).defaultHeader("X-Internal-Service-Token", token).build();
    InvocationHandler handler = (proxy, method, args) -> {
      if (method.getName().equals("categories")) {
        List<Map> rows = client.get().uri("/internal/v1/categories").retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Category.class)).toList();
      }
      if (method.getName().equals("merchants")) {
        List<Map> rows = client.get().uri(uri -> {
          var builderUri = uri.path("/internal/v1/merchants").queryParam("keyword", args[0] == null ? "" : args[0]);
          if (args[1] != null) builderUri.queryParam("categoryId", args[1]);
          if (args[2] != null) builderUri.queryParam("sort", args[2]);
          if (args[3] != null) builderUri.queryParam("minPrice", args[3]);
          if (args[4] != null) builderUri.queryParam("maxPrice", args[4]);
          if (args[5] != null) builderUri.queryParam("minScore", args[5]);
          return builderUri.build();
        }).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Merchant.class)).toList();
      }
      if (method.getName().equals("merchantDetail")) {
        Map row = client.get().uri("/internal/v1/merchants/{id}", args[0]).retrieve().body(Map.class);
        List products = client.get().uri(uri -> uri.path("/internal/v1/merchants/{id}/products").queryParam("listedOnly", true).build(args[0])).retrieve().body(List.class);
        List groupDeals = client.get().uri(uri -> uri.path("/internal/v1/merchants/{id}/deals").queryParam("activeOnly", true).build(args[0])).retrieve().body(List.class);
        List reviews = orderClient.get().uri("/internal/v1/merchants/{id}/reviews", args[0]).retrieve().body(List.class);
        return normalizeMerchantDetail(row, Map.of(), products, groupDeals, reviews);
      }
      if (method.getName().equals("merchantProfile")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map<String, Object> result = client.get().uri("/internal/v1/merchants/{id}/profile", user.merchantId()).retrieve().body(Map.class);
        if (result != null) result.put("user", fallback.safeUser(user));
        return result;
      }
      if (method.getName().equals("updateMerchantNickname")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map<String, Object> body = Map.of("name", args[1]);
        Map<String, Object> result = client.put().uri("/internal/v1/merchants/{id}/profile", user.merchantId()).header("X-Merchant-Id", String.valueOf(user.merchantId())).body(body).retrieve().body(Map.class);
        if (result != null) result.put("user", fallback.safeUser(user));
        return result;
      }
      if (method.getName().equals("merchantsForUser")) {
        List<Map> rows = client.get().uri(uri -> {
          var builderUri = uri.path("/internal/v1/merchants").queryParam("keyword", args[1] == null ? "" : args[1]);
          builderUri.queryParam("userId", args[0]);
          if (args[2] != null) builderUri.queryParam("categoryId", args[2]);
          if (args[3] != null) builderUri.queryParam("sort", args[3]);
          if (args[4] != null) builderUri.queryParam("minPrice", args[4]);
          if (args[5] != null) builderUri.queryParam("maxPrice", args[5]);
          if (args[6] != null) builderUri.queryParam("minScore", args[6]);
          return builderUri.build();
        }).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Map.class)).toList();
      }
      if (method.getName().equals("merchantProducts")) {
        com.lumalife.domain.Models.User user = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/merchants/{id}/products", user.merchantId()).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Product.class)).toList();
      }
      if (method.getName().equals("saveProduct")) {
        com.lumalife.domain.Models.User user = (com.lumalife.domain.Models.User) args[0];
        Map<String,Object> body = new java.util.LinkedHashMap<>();
        body.put("id", args[1]); body.put("name", args[2]); body.put("description", args[3]);
        body.put("priceCent", args[4]); body.put("stock", args[5]); body.put("listed", args[6]);
        Map row = client.post().uri("/internal/v1/merchants/{id}/products", user.merchantId()).header("X-Merchant-Id", String.valueOf(user.merchantId())).body(body).retrieve().body(Map.class);
        return mapper.convertValue(row, Product.class);
      }
      if (method.getName().equals("toggleProduct") || method.getName().equals("deleteProduct")) {
        com.lumalife.domain.Models.User user = (com.lumalife.domain.Models.User) args[0];
        long merchantId = user.merchantId(); long productId = (long) args[1];
        if (method.getName().equals("deleteProduct")) { client.delete().uri("/internal/v1/merchants/{id}/products/{productId}", merchantId, productId).header("X-Merchant-Id", String.valueOf(merchantId)).retrieve().toBodilessEntity(); return null; }
        Map row = client.post().uri("/internal/v1/merchants/{id}/products/{productId}/toggle", merchantId, productId).header("X-Merchant-Id", String.valueOf(merchantId)).retrieve().body(Map.class);
        return mapper.convertValue(row, Product.class);
      }
      if (method.getName().equals("merchantDeals")) {
        com.lumalife.domain.Models.User user = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/merchants/{id}/deals", user.merchantId()).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, com.lumalife.domain.Models.GroupDeal.class)).toList();
      }
      if (method.getName().equals("addFavorite") || method.getName().equals("removeFavorite")) {
        long userId = (long) args[0]; long merchantId = (long) args[1];
        if (method.getName().equals("addFavorite")) client.post().uri("/internal/v1/users/{userId}/favorites/{merchantId}", userId, merchantId).header("X-User-Id", String.valueOf(userId)).body(Map.of()).retrieve().toBodilessEntity();
        else client.delete().uri("/internal/v1/users/{userId}/favorites/{merchantId}", userId, merchantId).header("X-User-Id", String.valueOf(userId)).retrieve().toBodilessEntity();
        return null;
      }
      if (method.getName().equals("listFavorites")) {
        long userId = (long) args[0];
        return client.get().uri("/internal/v1/users/{userId}/favorites", userId).header("X-User-Id", String.valueOf(userId)).retrieve().body(List.class);
      }
      if (method.getName().equals("listFavoriteMerchants")) {
        long userId = (long) args[0];
        return client.get().uri("/internal/v1/users/{userId}/favorite-merchants", userId).header("X-User-Id", String.valueOf(userId)).retrieve().body(List.class);
      }
      if (method.getName().equals("userConversationSummaries")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        return client.get().uri("/internal/v1/users/{id}/conversations", user.id()).header("X-User-Id", String.valueOf(user.id())).retrieve().body(List.class);
      }
      if (method.getName().equals("merchantConversationSummaries")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        return client.get().uri("/internal/v1/merchants/{id}/conversations", user.merchantId()).header("X-Merchant-Id", String.valueOf(user.merchantId())).retrieve().body(List.class);
      }
      if (method.getName().equals("userConversation")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/users/{userId}/conversations/{merchantId}", user.id(), args[1]).header("X-User-Id", String.valueOf(user.id())).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, com.lumalife.domain.Models.ChatMessage.class)).toList();
      }
      if (method.getName().equals("merchantConversation")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/merchants/{merchantId}/conversations/{userId}", user.merchantId(), args[1]).header("X-Merchant-Id", String.valueOf(user.merchantId())).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, com.lumalife.domain.Models.ChatMessage.class)).toList();
      }
      if (method.getName().equals("sendUserMessage")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        Map row = client.post().uri("/internal/v1/users/{userId}/conversations/{merchantId}/messages", user.id(), args[1]).header("X-User-Id", String.valueOf(user.id())).body(Map.of("content", args[2])).retrieve().body(Map.class);
        List<Map> rows = client.get().uri("/internal/v1/users/{userId}/conversations/{merchantId}", user.id(), args[1]).header("X-User-Id", String.valueOf(user.id())).retrieve().body(List.class);
        return rows.stream().map(item -> mapper.convertValue(item, com.lumalife.domain.Models.ChatMessage.class)).toList();
      }
      if (method.getName().equals("sendMerchantMessage")) {
        var user = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.post().uri("/internal/v1/merchants/{merchantId}/conversations/{userId}/messages", user.merchantId(), args[1]).header("X-Merchant-Id", String.valueOf(user.merchantId())).body(Map.of("content", args[2])).retrieve().body(List.class);
        return rows.stream().map(item -> mapper.convertValue(item, com.lumalife.domain.Models.ChatMessage.class)).toList();
      }
      if (method.getName().equals("assistantFallback")) return fallback.assistantFallback((String) args[0]);
      if (method.getName().equals("saveDeal")) {
        com.lumalife.domain.Models.User user = (com.lumalife.domain.Models.User) args[0];
        Map<String,Object> body = new java.util.LinkedHashMap<>(); body.put("id", args[1]); body.put("title", args[2]); body.put("description", args[3]); body.put("priceCent", args[4]); body.put("stock", args[5]); body.put("active", args[6]);
        Map row = client.post().uri("/internal/v1/merchants/{id}/deals", user.merchantId()).header("X-Merchant-Id", String.valueOf(user.merchantId())).body(body).retrieve().body(Map.class);
        return mapper.convertValue(row, com.lumalife.domain.Models.GroupDeal.class);
      }
      if (method.getName().equals("toggleDeal") || method.getName().equals("deleteDeal")) {
        com.lumalife.domain.Models.User user = (com.lumalife.domain.Models.User) args[0]; long merchantId = user.merchantId(); long dealId = (long) args[1];
        if (method.getName().equals("deleteDeal")) { client.delete().uri("/internal/v1/merchants/{id}/deals/{dealId}", merchantId, dealId).header("X-Merchant-Id", String.valueOf(merchantId)).retrieve().toBodilessEntity(); return null; }
        Map row = client.post().uri("/internal/v1/merchants/{id}/deals/{dealId}/toggle", merchantId, dealId).header("X-Merchant-Id", String.valueOf(merchantId)).retrieve().body(Map.class);
        return mapper.convertValue(row, com.lumalife.domain.Models.GroupDeal.class);
      }
      return method.invoke(fallback, args);
    };
    return (MerchantServicePort) Proxy.newProxyInstance(MerchantServicePort.class.getClassLoader(), new Class[]{MerchantServicePort.class}, handler);
  }

  static Map<String, Object> normalizeMerchantDetail(Map<?, ?> remoteMerchant, Map<?, ?> fallbackMerchant,
                                                       List<?> products, List<?> groupDeals, List<?> reviews) {
    Map<String, Object> merchant = new LinkedHashMap<>();
    copyEntries(fallbackMerchant, merchant);
    copyEntries(remoteMerchant, merchant);
    merchant.putIfAbsent("cover", "");
    merchant.putIfAbsent("avgScore", 0.0);
    merchant.putIfAbsent("avgPrice", 0);
    merchant.putIfAbsent("monthlySales", 0);
    merchant.putIfAbsent("distanceKm", 0.0);
    merchant.putIfAbsent("address", "");
    merchant.putIfAbsent("reason", "");

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("merchant", merchant);
    detail.put("products", products == null ? List.of() : products);
    detail.put("groupDeals", groupDeals == null ? List.of() : groupDeals);
    detail.put("reviews", reviews == null ? List.of() : reviews);
    return detail;
  }

  private static void copyEntries(Map<?, ?> source, Map<String, Object> target) {
    if (source == null) return;
    source.forEach((key, value) -> target.put(String.valueOf(key), value));
  }

}
