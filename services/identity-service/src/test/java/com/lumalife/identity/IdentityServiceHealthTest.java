package com.lumalife.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {"lumalife.internal.service-token=test-internal-token", "lumalife.identity.state-file="})
class IdentityServiceHealthTest {
  @Autowired private TestRestTemplate http;
  @Autowired private IdentityStore store;
  @TempDir Path tempDir;

  @Test
  void exposesHealthAndLivenessProbes() {
    assertThat(http.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    assertThat(http.getForObject("/actuator/health/liveness", String.class)).contains("\"status\":\"UP\"");
    assertThat(http.getForObject("/actuator/health/readiness", String.class)).contains("\"status\":\"UP\"");
  }

  @Test
  void ownsLoginTokenAndAddressOperations() {
    HttpHeaders loginHeaders = serviceHeaders();
    Map<String, Object> login = http.postForObject("/internal/v1/auth/login",
      new HttpEntity<>(Map.of("phone", "13800000001", "password", "abc123456"), loginHeaders), Map.class);
    assertThat(login.keySet()).contains("token", "user");
    String token = String.valueOf(login.get("token"));
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.set("X-Luma-Service-Token", "test-internal-token");
    ResponseEntity<Map> me = http.exchange("/internal/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertThat(me.getBody()).containsEntry("role", "USER");
    headers.set("X-User-Id", "1");
    ResponseEntity<Object[]> addresses = http.exchange("/internal/v1/users/1/addresses", HttpMethod.GET, new HttpEntity<>(headers), Object[].class);
    assertThat(addresses.getBody()).hasSize(1);
  }

  @Test
  void rejectsUnauthenticatedInternalCallsAndPreservesDomainStatusCodes() {
    ResponseEntity<String> missingToken = http.getForEntity("/internal/v1/users/1/addresses", String.class);
    assertThat(missingToken.getStatusCode().value()).isEqualTo(401);

    assertThatThrownBy(() -> store.login("13800000001", "wrong-password"))
      .isInstanceOfSatisfying(IdentityStore.IdentityException.class,
        error -> assertThat(error.status()).isEqualTo(401));
  }

  @Test
  void merchantRegistrationAllocatesMerchantCapability() {
    ResponseEntity<Map> response = http.postForEntity("/internal/v1/auth/register",
      new HttpEntity<>(Map.of("phone", "13900000009", "password", "abc123456", "nickname", "新店主", "role", "MERCHANT_ADMIN"), serviceHeaders()), Map.class);
    Map user = (Map) response.getBody().get("user");
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(user).containsEntry("role", "MERCHANT_ADMIN").containsKey("merchantId");
    assertThat(user.get("merchantId")).isNotNull();
  }

  @Test
  void rejectsCrossUserProfileAccess() {
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", "2");
    ResponseEntity<Map> response = http.exchange("/internal/v1/users/1/profile", HttpMethod.PUT,
      new HttpEntity<>(Map.of("nickname", "越权"), headers), Map.class);
    assertThat(response.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void ctRtId03FindsOnlySafeUserDataByPhone() {
    ResponseEntity<Map> response = http.exchange("/internal/v1/users/by-phone?phone=13800000001", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), Map.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("id", 1).containsEntry("phone", "13800000001").doesNotContainKey("passwordHash");
  }

  @Test
  void persistsAccountsTokensAndAddressesAcrossRestart() {
    Path state = tempDir.resolve("identity-state.json");
    IdentityStore first = new IdentityStore(new BCryptPasswordEncoder(), state);
    Map<String, Object> login = first.register("13900000010", "abc123456", "持久化用户", "USER");
    String token = String.valueOf(login.get("token"));
    first.saveAddress(1001, null, "持久化用户", "13900000010", "持久化地址", true);

    IdentityStore restarted = new IdentityStore(new BCryptPasswordEncoder(), state);
    assertThat(restarted.byToken(token).phone()).isEqualTo("13900000010");
    assertThat(restarted.addresses(1001)).hasSize(1);
  }

  @Test
  void ctRtId07To09MaintainsOnlyTheCurrentUsersAddresses() {
    String phone = "ct-id-" + UUID.randomUUID();
    ResponseEntity<Map> registration = http.postForEntity("/internal/v1/auth/register",
      new HttpEntity<>(Map.of("phone", phone, "password", "abc123456", "nickname", "契约地址用户", "role", "USER"), serviceHeaders()), Map.class);
    assertThat(registration.getStatusCode().value()).isEqualTo(200);
    Map<?, ?> user = (Map<?, ?>) registration.getBody().get("user");
    long userId = ((Number) user.get("id")).longValue();

    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", Long.toString(userId));
    IdentityStore.Address first = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
      new HttpEntity<>(Map.of("contactName", "测试用户", "phone", phone, "detail", "契约测试地址一", "defaultAddress", true), headers), IdentityStore.Address.class).getBody();
    IdentityStore.Address second = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
      new HttpEntity<>(Map.of("contactName", "测试用户", "phone", phone, "detail", "契约测试地址二", "defaultAddress", false), headers), IdentityStore.Address.class).getBody();

    IdentityStore.Address defaulted = http.exchange("/internal/v1/users/" + userId + "/addresses/" + second.id() + "/default", HttpMethod.POST,
      new HttpEntity<>(headers), IdentityStore.Address.class).getBody();
    IdentityStore.Address[] addresses = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.GET,
      new HttpEntity<>(headers), IdentityStore.Address[].class).getBody();
    assertThat(defaulted.defaultAddress()).isTrue();
    assertThat(Arrays.stream(addresses).filter(IdentityStore.Address::defaultAddress)).hasSize(1);
    assertThat(Arrays.stream(addresses).filter(IdentityStore.Address::defaultAddress).findFirst().orElseThrow().id()).isEqualTo(second.id());

    HttpHeaders anotherUserHeaders = serviceHeaders();
    anotherUserHeaders.set("X-User-Id", Long.toString(userId + 1));
    ResponseEntity<String> denied = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.GET,
      new HttpEntity<>(anotherUserHeaders), String.class);
    assertThat(denied.getStatusCode().value()).isEqualTo(403);

    http.exchange("/internal/v1/users/" + userId + "/addresses/" + second.id(), HttpMethod.DELETE,
      new HttpEntity<>(headers), Void.class);
    IdentityStore.Address[] afterDelete = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.GET,
      new HttpEntity<>(headers), IdentityStore.Address[].class).getBody();
    assertThat(afterDelete).hasSize(1);
    assertThat(afterDelete[0].id()).isEqualTo(first.id());
    assertThat(afterDelete[0].defaultAddress()).isTrue();
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
