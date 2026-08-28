package com.lumalife.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityServiceHealthTest {
  @Autowired private TestRestTemplate http;

  @Test
  void exposesHealthAndLivenessProbes() {
    assertThat(http.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    assertThat(http.getForObject("/actuator/health/liveness", String.class)).contains("\"status\":\"UP\"");
    assertThat(http.getForObject("/actuator/health/readiness", String.class)).contains("\"status\":\"UP\"");
  }

  @Test
  void ownsLoginTokenAndAddressOperations() {
    Map<String, Object> login = http.postForObject("/internal/v1/auth/login",
      Map.of("phone", "13800000001", "password", "abc123456"), Map.class);
    assertThat(login.keySet()).contains("token", "user");
    String token = String.valueOf(login.get("token"));
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    ResponseEntity<Map> me = http.exchange("/internal/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertThat(me.getBody()).containsEntry("role", "USER");
    ResponseEntity<Object[]> addresses = http.exchange("/internal/v1/users/1/addresses", HttpMethod.GET, HttpEntity.EMPTY, Object[].class);
    assertThat(addresses.getBody()).hasSize(1);
  }
}
