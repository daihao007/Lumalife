package com.lumalife.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
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
  void exposesOnlySafeAccountProjectionForRemoteMetrics() {
    ResponseEntity<Object[]> response = http.exchange("/internal/v1/admin/accounts", HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()), Object[].class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotEmpty();
    assertThat(response.getBody()[0].toString()).doesNotContain("passwordHash");
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
  void loadsLegacyNumericTokenStateAndMigratesItToTheCurrentFormat() throws Exception {
    Path state = tempDir.resolve("legacy-identity-state.json");
    Files.writeString(state, """
      {
        "users": [{"id": 1, "phone": "legacy-user", "passwordHash": "legacy-hash", "nickname": "旧用户", "avatarUrl": "", "role": "USER", "merchantId": null}],
        "addresses": {"1": []},
        "tokens": {"legacy-token": 1}
      }
      """, StandardCharsets.UTF_8);

    IdentityStore restarted = new IdentityStore(new BCryptPasswordEncoder(), state);

    assertThat(restarted.byToken("legacy-token").id()).isEqualTo(1L);
    var migrated = new ObjectMapper().readTree(Files.readString(state, StandardCharsets.UTF_8));
    assertThat(migrated.path("tokens").path("legacy-token").path("userId").asLong()).isEqualTo(1L);
    assertThat(migrated.path("tokens").path("legacy-token").path("expiresAtEpochMillis").asLong()).isPositive();
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

  @Test
  void rejectsUnknownAddressIdsAndCapsAddressCreationAtFive() {
    String phone = "ct-id-limit-" + UUID.randomUUID();
    ResponseEntity<Map> registration = http.postForEntity("/internal/v1/auth/register",
      new HttpEntity<>(Map.of("phone", phone, "password", "abc123456", "nickname", "地址上限用户", "role", "USER"), serviceHeaders()), Map.class);
    long userId = ((Number) ((Map<?, ?>) registration.getBody().get("user")).get("id")).longValue();
    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", Long.toString(userId));

    ResponseEntity<String> unknown = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
      new HttpEntity<>(Map.of("id", 999999999L, "contactName", "测试用户", "phone", phone, "detail", "不存在的地址"), headers), String.class);
    assertThat(unknown.getStatusCode().value()).isEqualTo(404);

    for (int i = 1; i <= 5; i++) {
      ResponseEntity<IdentityStore.Address> created = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
        new HttpEntity<>(Map.of("contactName", "测试用户", "phone", phone, "detail", "地址" + i), headers), IdentityStore.Address.class);
      assertThat(created.getStatusCode().value()).isEqualTo(200);
    }
    ResponseEntity<String> sixth = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
      new HttpEntity<>(Map.of("contactName", "测试用户", "phone", phone, "detail", "第六个地址"), headers), String.class);
    assertThat(sixth.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void ctRtId02RegistersOrdinaryUserAndRejectsDuplicatePhone() {
    String phone = "ct-id-register-" + UUID.randomUUID();

    Map<String, Object> request = Map.of(
      "phone", phone,
      "password", "abc123456",
      "nickname", "契约普通用户",
      "role", "USER"
    );

    ResponseEntity<Map> first = http.postForEntity(
      "/internal/v1/auth/register",
      new HttpEntity<>(request, serviceHeaders()),
      Map.class
    );

    assertThat(first.getStatusCode().value()).isEqualTo(200);
    Map<?, ?> user = (Map<?, ?>) first.getBody().get("user");
    assertThat(user.get("phone")).isEqualTo(phone);
    assertThat(user.get("role")).isEqualTo("USER");
    assertThat(user.containsKey("passwordHash")).isFalse();

    ResponseEntity<String> duplicate = http.postForEntity(
      "/internal/v1/auth/register",
      new HttpEntity<>(request, serviceHeaders()),
      String.class
    );

    assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
  }

  @Test
  void ctRtId03RejectsUnknownAndBlankPhoneQueries() {
    ResponseEntity<Map> unknown = http.exchange(
      "/internal/v1/users/by-phone?phone=19999999999",
      HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()),
      Map.class
    );

    assertThat(unknown.getStatusCode().value()).isEqualTo(401);

    ResponseEntity<Map> blank = http.exchange(
      "/internal/v1/users/by-phone?phone=",
      HttpMethod.GET,
      new HttpEntity<>(serviceHeaders()),
      Map.class
    );

    assertThat(blank.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void ctRtId04RejectsStaleBearerTokens(){
    HttpHeaders headers = serviceHeaders();
    headers.setBearerAuth("expired-token-for-contract-test");

    ResponseEntity<Map> response = http.exchange(
      "/internal/v1/users/me",
      HttpMethod.GET,
      new HttpEntity<>(headers),
      Map.class
    );

    assertThat(response.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void ctRtId05UpdatesOnlyTheCurrentUsersProfile() {
    String phone = "ct-id-profile-" + UUID.randomUUID();
    ResponseEntity<Map> registration = http.postForEntity("/internal/v1/auth/register",
      new HttpEntity<>(Map.of("phone", phone, "password", "abc123456", "nickname", "资料用户", "role", "USER"), serviceHeaders()), Map.class);
    long userId = ((Number) ((Map<?, ?>) registration.getBody().get("user")).get("id")).longValue();

    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", Long.toString(userId));
    ResponseEntity<Map> updated = http.exchange("/internal/v1/users/" + userId + "/profile", HttpMethod.PUT,
      new HttpEntity<>(Map.of("nickname", "更新后的昵称", "avatarUrl", "https://example.test/avatar.png"), headers), Map.class);

    assertThat(updated.getStatusCode().value()).isEqualTo(200);
    assertThat(updated.getBody().get("nickname")).isEqualTo("更新后的昵称");
    assertThat(updated.getBody().get("avatarUrl")).isEqualTo("https://example.test/avatar.png");
  }

  @Test
  void ctRtId07RejectsIncompleteAddressFields() {
    String phone = "ct-id-address-validation-" + UUID.randomUUID();
    ResponseEntity<Map> registration = http.postForEntity("/internal/v1/auth/register",
      new HttpEntity<>(Map.of("phone", phone, "password", "abc123456", "nickname", "地址校验用户", "role", "USER"), serviceHeaders()), Map.class);
    long userId = ((Number) ((Map<?, ?>) registration.getBody().get("user")).get("id")).longValue();

    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", Long.toString(userId));
    ResponseEntity<String> invalid = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
      new HttpEntity<>(Map.of("contactName", "", "phone", phone, "detail", ""), headers), String.class);

    assertThat(invalid.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void ctRtId08RejectsUnknownDefaultAddress() {
    String phone = "ct-id-default-" + UUID.randomUUID();
    ResponseEntity<Map> registration = http.postForEntity("/internal/v1/auth/register",
      new HttpEntity<>(Map.of("phone", phone, "password", "abc123456", "nickname", "默认地址用户", "role", "USER"), serviceHeaders()), Map.class);
    long userId = ((Number) ((Map<?, ?>) registration.getBody().get("user")).get("id")).longValue();

    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", Long.toString(userId));
    ResponseEntity<String> response = http.exchange("/internal/v1/users/" + userId + "/addresses/999999999/default",
      HttpMethod.POST, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void ctRtId09RejectsRepeatedAddressDeletion() {
    String phone = "ct-id-delete-" + UUID.randomUUID();
    ResponseEntity<Map> registration = http.postForEntity("/internal/v1/auth/register",
      new HttpEntity<>(Map.of("phone", phone, "password", "abc123456", "nickname", "删除地址用户", "role", "USER"), serviceHeaders()), Map.class);
    long userId = ((Number) ((Map<?, ?>) registration.getBody().get("user")).get("id")).longValue();

    HttpHeaders headers = serviceHeaders();
    headers.set("X-User-Id", Long.toString(userId));
    IdentityStore.Address address = http.exchange("/internal/v1/users/" + userId + "/addresses", HttpMethod.POST,
      new HttpEntity<>(Map.of("contactName", "删除用户", "phone", phone, "detail", "待删除地址"), headers), IdentityStore.Address.class).getBody();
    String path = "/internal/v1/users/" + userId + "/addresses/" + address.id();

    ResponseEntity<Void> first = http.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    ResponseEntity<String> repeated = http.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);

    assertThat(first.getStatusCode().value()).isEqualTo(200);
    assertThat(repeated.getStatusCode().value()).isEqualTo(404);
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
