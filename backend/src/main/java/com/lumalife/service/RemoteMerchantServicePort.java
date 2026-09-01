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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Value;
import com.lumalife.common.BusinessException;

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
      try {
      if (method.getName().equals("merchants")) {
        List<Map> rows = client.get().uri(uri -> uri.path("/internal/v1/merchants")
          .queryParam("keyword", args[0] == null ? "" : args[0])
          .queryParam("categoryId", args[1] == null ? "" : args[1])
          .queryParam("sort", args[2] == null ? "recommend" : args[2])
          .queryParam("minPrice", args[3] == null ? "" : args[3])
          .queryParam("maxPrice", args[4] == null ? "" : args[4])
          .queryParam("minScore", args[5] == null ? "" : args[5]).build()).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Merchant.class)).toList();
      }
      if (method.getName().equals("merchantDetail")) {
        Map row = client.get().uri("/internal/v1/merchants/{id}", args[0]).retrieve().body(Map.class);
        Map<?, ?> fallbackMerchant = Map.of();
        List<?> reviews = List.of();
        try {
          Map<String, Object> fallbackDetail = fallback.merchantDetail((long) args[0]);
          fallbackMerchant = mapper.convertValue(fallbackDetail.get("merchant"), Map.class);
          Object fallbackReviews = fallbackDetail.get("reviews");
          if (fallbackReviews instanceof List<?> list) reviews = list;
        } catch (RuntimeException ignored) {
          // A newly migrated merchant may not exist in the compatibility store.
        }
        List products = client.get().uri("/internal/v1/merchants/{id}/products", args[0]).retrieve().body(List.class);
        List groupDeals = client.get().uri("/internal/v1/merchants/{id}/deals", args[0]).retrieve().body(List.class);
        return normalizeMerchantDetail(row, fallbackMerchant, products, groupDeals, reviews);
      }
      if (method.getName().equals("merchantsForUser")) {
        List<Map> rows = client.get().uri(uri -> uri.path("/internal/v1/merchants")
          .queryParam("keyword", args[1] == null ? "" : args[1])
          .queryParam("categoryId", args[2] == null ? "" : args[2])
          .queryParam("sort", args[3] == null ? "recommend" : args[3])
          .queryParam("minPrice", args[4] == null ? "" : args[4])
          .queryParam("maxPrice", args[5] == null ? "" : args[5])
          .queryParam("minScore", args[6] == null ? "" : args[6]).build()).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Map.class)).toList();
      }
      if (method.getName().equals("merchantProducts")) {
        com.lumalife.domain.Models.User user = (com.lumalife.domain.Models.User) args[0];
        List<Map> rows = client.get().uri("/internal/v1/merchants/{id}/products", user.merchantId()).retrieve().body(List.class);
        return rows.stream().map(row -> mapper.convertValue(row, Product.class)).toList();
      }
      if (method.getName().equals("updateMerchantNickname")) {
        com.lumalife.domain.Models.User user = (com.lumalife.domain.Models.User) args[0];
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("nickname", args[1]);
        Map row = client.put().uri("/internal/v1/merchants/{id}/profile", user.merchantId())
          .header("X-Merchant-Id", String.valueOf(user.merchantId()))
          .body(profile).retrieve().body(Map.class);
        var updatedUser = new com.lumalife.domain.Models.User(user.id(), user.phone(), user.password(),
          String.valueOf(args[1]).trim(), user.avatarUrl(), user.role(), user.merchantId());
        return Map.of("user", fallback.safeUser(updatedUser), "merchant", mapper.convertValue(row, Merchant.class));
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
      if (method.getName().equals("sendUserMessage")) {
        // Conversations still live in the compatibility store until their own
        // service slice is migrated. Keep the local reply callback bounded so
        // a catalog/AI context failure cannot reject the user's message.
        Object[] forwarded = args.clone();
        fallback.ensureExternalUser((com.lumalife.domain.Models.User) args[0]);
        java.util.function.Function responder = (java.util.function.Function) args[3];
        forwarded[3] = (java.util.function.Function<Object, String>) history -> {
          try {
            return (String) responder.apply(history);
          } catch (RuntimeException error) {
            return "您好，店家客服已收到您的消息，稍后会为您处理。";
          }
        };
        return method.invoke(fallback, forwarded);
      }
      return method.invoke(fallback, args);
      } catch (java.lang.reflect.InvocationTargetException error) {
        Throwable cause = error.getCause();
        if (cause instanceof RuntimeException runtime) throw runtime;
        throw error;
      } catch (RestClientResponseException error) {
        throw remoteError(error);
      } catch (RestClientException error) {
        throw new BusinessException(50300, "商家服务暂时不可用", "MERCHANT_SERVICE_UNAVAILABLE");
      }
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
    String message = switch (status) {
      case 400 -> "商家请求参数错误";
      case 401 -> "商家服务认证失败";
      case 403 -> "商家操作未授权";
      case 404 -> "商家资源不存在";
      case 409 -> "商家资源冲突";
      default -> "商家服务暂时不可用";
    };
    return new BusinessException(code, message, code == 50300 ? "MERCHANT_SERVICE_UNAVAILABLE" : "MERCHANT_REMOTE_ERROR");
  }

}
