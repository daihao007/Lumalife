package com.lumalife.identity;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class IdentitySecurityBeans {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}

@RestController
@RequestMapping("/internal/v1")
public class IdentityApi {
  private final IdentityStore store;
  public IdentityApi(IdentityStore store) { this.store = store; }

  @PostMapping("/auth/login")
  Map<String, Object> login(@RequestBody LoginRequest request) {
    if (request == null || request.phone() == null || request.password() == null) throw invalidRequest();
    return store.login(request.phone(), request.password());
  }

  @PostMapping("/auth/register")
  Map<String, Object> register(@RequestBody RegisterRequest request) {
    if (request == null) throw invalidRequest();
    return store.register(request.phone(), request.password(), request.nickname(), request.role());
  }

  @GetMapping("/users/by-phone")
  Map<String, Object> byPhone(@RequestParam String phone) { return store.safe(store.byPhone(phone)); }

  @GetMapping("/users/me")
  Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
    return store.safe(store.byToken(token(authorization)));
  }

  @PostMapping("/tokens/introspect")
  Map<String, Object> introspect(@RequestBody TokenRequest request) {
    if (request == null || request.token() == null || request.token().isBlank()) throw invalidRequest();
    return store.introspect(request.token());
  }

  @PutMapping("/users/{id}/profile")
  Map<String, Object> profile(@PathVariable long id, @RequestHeader(value = "X-User-Id", required = false) Long actorId,
                              @RequestBody ProfileRequest request) {
    if (request == null) throw invalidRequest();
    store.requireActor(id, actorId);
    return store.safe(store.updateProfile(id, request.nickname(), request.avatarUrl()));
  }

  @GetMapping("/users/{id}/addresses")
  List<IdentityStore.Address> addresses(@PathVariable long id, @RequestHeader(value = "X-User-Id", required = false) Long actorId) {
    store.requireActor(id, actorId);
    return store.addresses(id);
  }

  @GetMapping("/users/{id}/addresses/{addressId}")
  IdentityStore.Address address(@PathVariable long id, @PathVariable long addressId) {
    return store.address(id, addressId);
  }

  @PostMapping("/users/{id}/addresses")
  IdentityStore.Address saveAddress(@PathVariable long id, @RequestHeader(value = "X-User-Id", required = false) Long actorId,
                                     @RequestBody AddressRequest request) {
    if (request == null) throw invalidRequest();
    store.requireActor(id, actorId);
    return store.saveAddress(id, request.id(), request.contactName(), request.phone(), request.detail(), request.defaultAddress());
  }

  @PostMapping("/users/{id}/addresses/{addressId}/default")
  IdentityStore.Address setDefault(@PathVariable long id, @PathVariable long addressId,
                                   @RequestHeader(value = "X-User-Id", required = false) Long actorId) {
    store.requireActor(id, actorId);
    return store.setDefault(id, addressId);
  }

  @DeleteMapping("/users/{id}/addresses/{addressId}")
  void deleteAddress(@PathVariable long id, @PathVariable long addressId,
                     @RequestHeader(value = "X-User-Id", required = false) Long actorId) {
    store.requireActor(id, actorId);
    store.deleteAddress(id, addressId);
  }

  private String token(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new IdentityStore.IdentityException(401, "登录状态已失效", "TOKEN_INVALID");
    }
    return authorization.substring(7).trim();
  }

  private IdentityStore.IdentityException invalidRequest() {
    return new IdentityStore.IdentityException(400, "请求参数格式错误", "VALIDATION_FAILED");
  }

  record LoginRequest(String phone, String password) {}
  record RegisterRequest(String phone, String password, String nickname, String role) {}
  record ProfileRequest(String nickname, String avatarUrl) {}
  record AddressRequest(Long id, String contactName, String phone, String detail, boolean defaultAddress) {}
  record TokenRequest(String token) {}
}
