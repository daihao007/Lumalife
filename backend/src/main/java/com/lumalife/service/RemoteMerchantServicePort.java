package com.lumalife.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumalife.domain.Models.Merchant;
import com.lumalife.domain.Models.Product;
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

/** Routes the migrated catalog slice to merchant-service; unsupported capabilities remain safely on the monolith. */
@Configuration
@ConditionalOnProperty(prefix = "lumalife.migration.merchant", name = {"enabled", "backfill-completed"}, havingValue = "true")
public class RemoteMerchantServicePort {
  @Bean
  @Primary
  MerchantServicePort remoteMerchantPort(DemoStore fallback, ObjectMapper mapper,
      RestClient.Builder builder, @Value("${lumalife.services.merchant.base-url:http://localhost:8082}") String baseUrl,
      @Value("${lumalife.internal.service-token:}") String token) {
    RestClient client = builder.baseUrl(baseUrl).defaultHeader("X-Internal-Service-Token", token).build();
    InvocationHandler handler = (proxy, method, args) -> {
      if (method.getName().equals("merchants")) {
        List<Map> rows = client.get().uri(uri -> merchantListUri(uri, args[0], args[1], args[2], args[3], args[4], args[5])).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Merchant.class)).toList();
      }
      if (method.getName().equals("merchantDetail")) {
        Map row = client.get().uri("/internal/v1/merchants/{id}", args[0]).retrieve().body(Map.class);
        Map<?, ?> fallbackMerchant = Map.of();
        List<?> fallbackProducts = List.of();
        List<?> fallbackGroupDeals = List.of();
        List<?> reviews = List.of();
        try {
          Map<String, Object> fallbackDetail = fallback.merchantDetail((long) args[0]);
          fallbackMerchant = mapper.convertValue(fallbackDetail.get("merchant"), Map.class);
          Object fallbackProductData = fallbackDetail.get("products");
          if (fallbackProductData instanceof List<?> list) fallbackProducts = list;
          Object fallbackDealData = fallbackDetail.get("groupDeals");
          if (fallbackDealData instanceof List<?> list) fallbackGroupDeals = list;
          Object fallbackReviews = fallbackDetail.get("reviews");
          if (fallbackReviews instanceof List<?> list) reviews = list;
        } catch (RuntimeException ignored) {
          // A newly migrated merchant may not exist in the compatibility store.
        }
        List products = client.get().uri("/internal/v1/merchants/{id}/products", args[0]).retrieve().body(List.class);
        List groupDeals = client.get().uri("/internal/v1/merchants/{id}/deals", args[0]).retrieve().body(List.class);
        List<?> visibleProducts = products == null || products.isEmpty() ? fallbackProducts : products;
        List<?> visibleGroupDeals = groupDeals == null || groupDeals.isEmpty() ? fallbackGroupDeals : groupDeals;
        return normalizeMerchantDetail(row, fallbackMerchant, visibleProducts, visibleGroupDeals, reviews);
      }
      if (method.getName().equals("merchantsForUser")) {
        List<Map> rows = client.get().uri(uri -> merchantListUri(uri, args[1], args[2], args[3], args[4], args[5], args[6])).retrieve().body(List.class);
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

  private static java.net.URI merchantListUri(
      org.springframework.web.util.UriBuilder uri, Object keyword, Object categoryId, Object sort,
      Object minPrice, Object maxPrice, Object minScore) {
    uri.path("/internal/v1/merchants").queryParam("keyword", keyword == null ? "" : keyword);
    if (categoryId != null) uri.queryParam("categoryId", categoryId);
    if (sort != null) uri.queryParam("sort", sort);
    if (minPrice != null) uri.queryParam("minPrice", minPrice);
    if (maxPrice != null) uri.queryParam("maxPrice", maxPrice);
    if (minScore != null) uri.queryParam("minScore", minScore);
    return uri.build();
  }

}
