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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    HttpHeaders headers = serviceHeaders();
    headers.setBearerAuth(token);
    ResponseEntity<Map> me = http.exchange("/internal/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertThat(me.getBody()).containsEntry("role", "USER");
    headers.set("X-User-Id", "1");
    ResponseEntity<Object[]> addresses = http.exchange("/internal/v1/users/1/addresses", HttpMethod.GET, new HttpEntity<>(headers), Object[].class);
    assertThat(addresses.getBody()).hasSize(2);
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
  void rejectsInternalCallsWithoutTraceContextUsingTheUniformErrorEnvelope() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    ResponseEntity<Map> response = http.exchange("/internal/v1/users/by-phone?phone=13800000001",
      HttpMethod.GET, new HttpEntity<>(headers), Map.class);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).containsEntry("code", 40000).containsEntry("reason", "INVALID_CALL_CONTEXT")
      .containsKey("requestId").containsKey("details");
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
    String phone = "ct-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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

  @Test
  void rejectsRoleEscalationAndDoesNotCreateThePrivilegedAccount() {
    String phone = "ct-role-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    ResponseEntity<Map> response = http.postForEntity("/internal/v1/auth/register",
      new HttpEntity<>(Map.of("phone", phone, "password", "abc123456", "nickname", "普通用户", "role", "PLATFORM_ADMIN"), serviceHeaders()), Map.class);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).containsEntry("code", 40000).containsEntry("reason", "ROLE_NOT_ALLOWED");
    ResponseEntity<Map> lookup = http.exchange("/internal/v1/users/by-phone?phone=" + phone,
      HttpMethod.GET, new HttpEntity<>(serviceHeaders()), Map.class);
    assertThat(lookup.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void introspectsActiveTokenAndReturnsUniformErrorForInvalidToken() {
    Map<String, Object> login = http.postForObject("/internal/v1/auth/login",
      new HttpEntity<>(Map.of("phone", "13800000001", "password", "abc123456"), serviceHeaders()), Map.class);
    String token = String.valueOf(login.get("token"));

    ResponseEntity<Map> claims = http.postForEntity("/internal/v1/tokens/introspect",
      new HttpEntity<>(Map.of("token", token), serviceHeaders()), Map.class);
    assertThat(claims.getStatusCode().value()).isEqualTo(200);
    assertThat(claims.getBody()).containsEntry("active", true).containsEntry("userId", 1).containsEntry("role", "USER");
    assertThat(((Number) claims.getBody().get("exp")).longValue()).isGreaterThan(Instant.now().getEpochSecond());

    HttpHeaders invalidHeaders = serviceHeaders();
    invalidHeaders.setBearerAuth("not-a-real-token");
    ResponseEntity<Map> invalid = http.exchange("/internal/v1/users/me", HttpMethod.GET,
      new HttpEntity<>(invalidHeaders), Map.class);
    assertThat(invalid.getStatusCode().value()).isEqualTo(401);
    assertThat(invalid.getBody()).containsEntry("code", 40100).containsEntry("reason", "TOKEN_INVALID");
  }

  @Test
  void enforcesAddressLimitAndOwnedAddressSnapshotAccess() {
    String phone = "ct-addr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    Map<String, Object> registration = http.postForObject("/internal/v1/auth/register",
      new HttpEntity<>(Map.of("phone", phone, "password", "abc123456", "nickname", "地址上限用户", "role", "USER"), serviceHeaders()), Map.class);
    long userId = ((Number) ((Map<?, ?>) registration.get("user")).get("id")).longValue();
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", Long.toString(userId));

    long lastAddressId = 0;
    for (int i = 1; i <= IdentityStore.MAX_ADDRESSES_PER_USER; i++) {
      IdentityStore.Address address = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
        new HttpEntity<>(Map.of("contactName", "测试用户", "phone", phone, "detail", "契约地址" + i, "defaultAddress", i == 1), headers), IdentityStore.Address.class).getBody();
      lastAddressId = address.id();
    }

    ResponseEntity<Map> overflow = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
      new HttpEntity<>(Map.of("contactName", "测试用户", "phone", phone, "detail", "超出上限", "defaultAddress", false), headers), Map.class);
    assertThat(overflow.getStatusCode().value()).isEqualTo(409);
    assertThat(overflow.getBody()).containsEntry("code", 40900).containsEntry("reason", "ADDRESS_LIMIT_REACHED");

    ResponseEntity<Map> forgedId = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
      new HttpEntity<>(Map.of("id", 999999, "contactName", "测试用户", "phone", phone,
        "detail", "伪造编号地址", "defaultAddress", false), headers), Map.class);
    assertThat(forgedId.getStatusCode().value()).isEqualTo(404);
    assertThat(forgedId.getBody()).containsEntry("code", 40400).containsEntry("reason", "ADDRESS_NOT_FOUND");

    IdentityStore.Address snapshot = http.exchange("/internal/v1/users/" + userId + "/addresses/" + lastAddressId,
      HttpMethod.GET, new HttpEntity<>(serviceHeaders()), IdentityStore.Address.class).getBody();
    assertThat(snapshot.detail()).isEqualTo("契约地址" + IdentityStore.MAX_ADDRESSES_PER_USER);
  }

  @Test
  void expiresTokensAfterConfiguredTtl() {
    Path state = tempDir.resolve("expiring-identity.json");
    Instant issuedAt = Instant.parse("2026-09-01T00:00:00Z");
    IdentityStore first = new IdentityStore(new BCryptPasswordEncoder(), state, null, 60,
      Clock.fixed(issuedAt, ZoneOffset.UTC));
    String token = String.valueOf(first.login("13800000001", "abc123456").get("token"));

    IdentityStore expired = new IdentityStore(new BCryptPasswordEncoder(), state, null, 60,
      Clock.fixed(issuedAt.plusSeconds(60), ZoneOffset.UTC));
    assertThatThrownBy(() -> expired.byToken(token))
      .isInstanceOfSatisfying(IdentityStore.IdentityException.class,
        error -> assertThat(error.reason()).isEqualTo("TOKEN_INVALID"));
  }

  @Test
  void backfillsLegacySnapshotAndPersistsOnlySessionHashes() throws Exception {
    Path state = tempDir.resolve("backfilled-identity.json");
    Path legacy = tempDir.resolve("legacy-state.json");
    Files.writeString(legacy, """
      {
        "accounts": [{
          "id": 41,
          "phone": "13800000999",
          "password": "$2a$10$Srh6L3GMusEoK/Y3Plgew.2jd1Xnl3BxlgKYHrE6B35wtxv2XdK86",
          "nickname": "历史用户",
          "avatarUrl": "",
          "role": "USER",
          "merchantId": null
        }],
        "addresses": [{
          "id": 401,
          "userId": 41,
          "contactName": "历史用户",
          "phone": "13800000999",
          "detail": "历史地址",
          "defaultAddress": true
        }]
      }
      """, StandardCharsets.UTF_8);

    Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
    IdentityStore first = new IdentityStore(new BCryptPasswordEncoder(), state, legacy, 3600, clock);
    assertThat(first.byPhone("13800000999").nickname()).isEqualTo("历史用户");
    String token = String.valueOf(first.login("13800000999", "abc123456").get("token"));
    assertThat(first.addresses(41)).singleElement().extracting(IdentityStore.Address::detail).isEqualTo("历史地址");
    String persisted = Files.readString(state);
    assertThat(persisted).doesNotContain(token).contains("sessions");
    IdentityStore restarted = new IdentityStore(new BCryptPasswordEncoder(), state, null, 3600, clock);
    assertThat(restarted.byToken(token).phone()).isEqualTo("13800000999");
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    headers.set("X-Request-Id", "ct-identity-" + UUID.randomUUID());
    headers.set("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    headers.set("X-Caller-Service", "api-gateway");
    return headers;
  }
}
