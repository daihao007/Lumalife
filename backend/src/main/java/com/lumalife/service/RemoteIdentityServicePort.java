package com.lumalife.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumalife.common.BusinessException;
import com.lumalife.domain.Enums.UserRole;
import com.lumalife.domain.Models.Address;
import com.lumalife.domain.Models.User;
import com.lumalife.service.boundary.IdentityServicePort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** HTTP adapter for identity, enabled only after the explicit backfill gate is complete. */
@Primary
@Service
@ConditionalOnProperty(prefix = "lumalife.migration.identity", name = "enabled", havingValue = "true")
public class RemoteIdentityServicePort implements IdentityServicePort {
  private final DemoStore fallback;
  private final RestClient client;
  private final ObjectMapper objectMapper;
  private final String serviceToken;
  private final boolean backfillCompleted;

  public RemoteIdentityServicePort(DemoStore fallback, RestClient.Builder builder, ObjectMapper objectMapper,
                                   @Value("${lumalife.services.identity.base-url:http://localhost:8081}") String baseUrl,
                                   @Value("${lumalife.internal.service-token:}") String serviceToken,
                                   @Value("${lumalife.migration.identity.backfill-completed:false}") boolean backfillCompleted) {
    this.fallback = fallback;
    this.client = builder.baseUrl(baseUrl).build();
    this.objectMapper = objectMapper;
    this.serviceToken = serviceToken == null ? "" : serviceToken;
    this.backfillCompleted = backfillCompleted;
  }

  @Override public Optional<User> userByToken(String token) {
    try {
      return Optional.of(user(get("/internal/v1/users/me", Map.class, token, null)));
    } catch (RuntimeException error) {
      return Optional.empty();
    }
  }

  @Override public User userByPhone(String phone) {
    return user(get("/internal/v1/users/by-phone?phone={phone}", Map.class, null, null, phone));
  }

  @Override public User current(String phone) { return userByPhone(phone); }

  @Override public Map<String, Object> login(String phone, String password) {
    return post("/internal/v1/auth/login", Map.of("phone", phone, "password", password));
  }

  @Override public Map<String, Object> registerUser(String phone, String password, String nickname) {
    return post("/internal/v1/auth/register", Map.of("phone", phone, "password", password, "nickname", nickname, "role", "USER"));
  }

  @Override public Map<String, Object> registerMerchant(String phone, String password, String nickname) {
    return post("/internal/v1/auth/register", Map.of("phone", phone, "password", password, "nickname", nickname, "role", "MERCHANT_ADMIN"));
  }

  @Override public Map<String, Object> safeUser(User user) { return fallback.safeUser(user); }

  @Override public Map<String, Object> updateProfile(User user, String nickname, String avatarUrl) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("nickname", nickname); body.put("avatarUrl", avatarUrl == null ? "" : avatarUrl);
    return put("/internal/v1/users/{id}/profile", body, user.id());
  }

  @Override public List<Address> addresses(User user) {
    List<Map<String, Object>> data = get("/internal/v1/users/{id}/addresses", new ParameterizedTypeReference<>() {}, null, user.id(), user.id());
    return data == null ? List.of() : data.stream().map(this::address).toList();
  }

  @Override public Address saveAddress(User user, Long id, String contactName, String phone, String detail, boolean defaultAddress) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (id != null) body.put("id", id);
    body.put("contactName", contactName); body.put("phone", phone); body.put("detail", detail); body.put("defaultAddress", defaultAddress);
    return address(post("/internal/v1/users/{id}/addresses", body, user.id()));
  }

  @Override public Address setDefaultAddress(User user, long id) {
    return address(post("/internal/v1/users/{id}/addresses/{addressId}/default", Map.of(), user.id(), id));
  }

  @Override public void deleteAddress(User user, long id) {
    delete("/internal/v1/users/{id}/addresses/{addressId}", user.id(), id);
  }

  private <T> T get(String uri, Class<T> type, String bearer, Long userId, Object... variables) {
    return execute(() -> {
      RestClient.RequestHeadersSpec<?> request = client.get().uri(uri, variables);
      return body(request, type, bearer, userId);
    });
  }

  private <T> T get(String uri, ParameterizedTypeReference<T> type, String bearer, Long userId, Object... variables) {
    return execute(() -> {
      RestClient.RequestHeadersSpec<?> request = client.get().uri(uri, variables);
      return body(request, type, bearer, userId);
    });
  }

  private Map<String, Object> post(String uri, Map<String, Object> payload) {
    return post(uri, payload, new Long[0]);
  }

  private Map<String, Object> post(String uri, Map<String, Object> payload, Long... variables) {
    return execute(() -> {
      RestClient.RequestBodySpec request = client.post().uri(uri, (Object[]) variables).body(payload);
      return body(request, new ParameterizedTypeReference<Map<String, Object>>() {}, null,
        variables.length == 0 ? null : variables[0]);
    });
  }

  private <T> T put(String uri, Map<String, Object> payload, Long userId) {
    return execute(() -> {
      RestClient.RequestBodySpec request = client.put().uri(uri, userId).body(payload);
      return body(request, new ParameterizedTypeReference<T>() {}, null, userId);
    });
  }

  private void delete(String uri, Long... variables) {
    execute(() -> {
      RestClient.RequestHeadersSpec<?> request = client.delete().uri(uri, (Object[]) variables);
      secure(request, null, variables.length == 0 ? null : variables[0]).retrieve().toBodilessEntity();
      return null;
    });
  }

  private <T> T body(RestClient.RequestHeadersSpec<?> request, Class<T> type, String bearer, Long userId) {
    return secure(request, bearer, userId).retrieve().body(type);
  }

  private <T> T body(RestClient.RequestHeadersSpec<?> request, ParameterizedTypeReference<T> type,
                     String bearer, Long userId) {
    return secure(request, bearer, userId).retrieve().body(type);
  }

  private RestClient.RequestHeadersSpec<?> secure(RestClient.RequestHeadersSpec<?> request, String bearer, Long userId) {
    request.header("X-Luma-Service-Token", serviceToken);
    request.header("X-Request-Id", requestId());
    request.header("traceparent", traceparent());
    request.header("X-Caller-Service", "api-gateway");
    if (bearer != null) request.header("Authorization", "Bearer " + bearer);
    if (userId != null) request.header("X-User-Id", String.valueOf(userId));
    return request;
  }

  private String requestId() {
    return normalizeRequestId(incomingHeader("X-Request-Id"));
  }

  static String normalizeRequestId(String requestId) {
    return requestId == null || requestId.isBlank() || requestId.length() < 8 || requestId.length() > 128
      ? UUID.randomUUID().toString() : requestId;
  }

  private String traceparent() {
    String traceparent = incomingHeader("traceparent");
    if (traceparent != null && traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
        && !traceparent.substring(3, 35).equals("00000000000000000000000000000000")
        && !traceparent.substring(36, 52).equals("0000000000000000")) {
      return traceparent;
    }
    return "00-" + UUID.randomUUID().toString().replace("-", "") + "-"
      + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "-01";
  }

  private String incomingHeader(String name) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      return attributes.getRequest().getHeader(name);
    }
    return null;
  }

  private <T> T execute(Supplier<T> operation) {
    ensureMigrationReady();
    try {
      return operation.get();
    } catch (RestClientResponseException error) {
      throw remoteError(error);
    } catch (RestClientException error) {
      throw new BusinessException(50300, "身份服务暂时不可用", "IDENTITY_SERVICE_UNAVAILABLE");
    }
  }

  private void ensureMigrationReady() {
    if (!backfillCompleted) {
      throw new BusinessException(50300, "身份数据回填未完成，远程流量保持关闭", "IDENTITY_BACKFILL_REQUIRED");
    }
  }

  private BusinessException remoteError(RestClientResponseException error) {
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
      case 400 -> "身份请求参数错误";
      case 401 -> "身份认证失败";
      case 403 -> "身份操作未授权";
      case 404 -> "身份资源不存在";
      case 409 -> "身份资源冲突";
      default -> "身份服务暂时不可用";
    };
    String reason = code == 50300 ? "IDENTITY_SERVICE_UNAVAILABLE" : switch (status) {
      case 400 -> "VALIDATION_FAILED";
      case 401 -> "AUTHENTICATION_FAILED";
      case 403 -> "FORBIDDEN";
      case 404 -> "NOT_FOUND";
      case 409 -> "CONFLICT";
      default -> "IDENTITY_REMOTE_ERROR";
    };
    try {
      JsonNode root = objectMapper.readTree(error.getResponseBodyAsString());
      if (root.hasNonNull("code") && root.get("code").asInt() >= 10000) code = root.get("code").asInt(code);
      if (root.hasNonNull("message")) message = root.get("message").asText(message);
      if (root.hasNonNull("reason")) reason = root.get("reason").asText(reason);
    } catch (Exception ignored) {
      // Keep the status-derived contract when the remote error body is not JSON.
    }
    return new BusinessException(code, message, reason);
  }

  private User user(Map<String, Object> data) {
    if (data == null) throw new BusinessException(50300, "身份服务暂无响应", "IDENTITY_SERVICE_UNAVAILABLE");
    return new User(number(data.get("id")), string(data.get("phone")), "", string(data.get("nickname")),
      string(data.get("avatarUrl")), UserRole.valueOf(string(data.get("role"))), longOrNull(data.get("merchantId")));
  }

  private Address address(Map<String, Object> data) {
    return new Address(number(data.get("id")), number(data.get("userId")), string(data.get("contactName")),
      string(data.get("phone")), string(data.get("detail")), Boolean.TRUE.equals(data.get("defaultAddress")));
  }

  private long number(Object value) { return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); }
  private Long longOrNull(Object value) { return value == null ? null : number(value); }
  private String string(Object value) { return value == null ? "" : String.valueOf(value); }
}
