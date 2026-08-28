package com.lumalife.service;

import com.lumalife.common.BusinessException;
import com.lumalife.domain.Models.Address;
import com.lumalife.domain.Models.User;
import com.lumalife.domain.Enums.UserRole;
import com.lumalife.service.boundary.IdentityServicePort;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** HTTP adapter for the identity-owned capability. Disabled by default for safe rollback. */
@Primary
@Service
@ConditionalOnProperty(prefix = "lumalife.migration.identity", name = "enabled", havingValue = "true")
public class RemoteIdentityServicePort implements IdentityServicePort {
  private final DemoStore fallback;
  private final RestClient client;

  public RemoteIdentityServicePort(DemoStore fallback, RestClient.Builder builder,
                                   @Value("${lumalife.services.identity.base-url:http://localhost:8081}") String baseUrl) {
    this.fallback = fallback;
    this.client = builder.baseUrl(baseUrl).build();
  }

  @Override public Optional<User> userByToken(String token) {
    try { return Optional.of(user(get("/internal/v1/users/me", token))); }
    catch (RuntimeException error) { return Optional.empty(); }
  }

  @Override public User userByPhone(String phone) { return user(get("/internal/v1/users/by-phone?phone={phone}", null, phone)); }

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
    return client.put().uri("/internal/v1/users/{id}/profile", user.id()).body(body).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  @Override public List<Address> addresses(User user) {
    List<Map<String, Object>> data = client.get().uri("/internal/v1/users/{id}/addresses", user.id()).retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return data == null ? List.of() : data.stream().map(this::address).toList();
  }

  @Override public Address saveAddress(User user, Long id, String contactName, String phone, String detail, boolean defaultAddress) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (id != null) body.put("id", id);
    body.put("contactName", contactName); body.put("phone", phone); body.put("detail", detail); body.put("defaultAddress", defaultAddress);
    Map<String, Object> data = client.post().uri("/internal/v1/users/{id}/addresses", user.id()).body(body).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return address(data);
  }

  @Override public Address setDefaultAddress(User user, long id) {
    Map<String, Object> data = client.post().uri("/internal/v1/users/{id}/addresses/{addressId}/default", user.id(), id).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return address(data);
  }

  @Override public void deleteAddress(User user, long id) {
    client.delete().uri("/internal/v1/users/{id}/addresses/{addressId}", user.id(), id).retrieve().toBodilessEntity();
  }

  private Map<String, Object> get(String uri, String token, Object... variables) {
    RestClient.RequestHeadersSpec<?> request = client.get().uri(uri, variables);
    if (token != null) request = request.header("Authorization", "Bearer " + token);
    return request.retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private Map<String, Object> post(String uri, Map<String, Object> body) {
    return client.post().uri(uri).body(body).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  @SuppressWarnings("unchecked") private User user(Map<String, Object> data) {
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
