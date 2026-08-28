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
import java.util.Map;

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

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
