package com.lumalife.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "lumalife.internal.service-token=test-internal-token",
    "lumalife.identity.state-file=",
    "lumalife.identity.token-ttl-millis=0"
  })
class IdentityTokenExpiryContractTest {
  @Autowired private TestRestTemplate http;

  @Test
  void rejectsTokenAfterItsConfiguredExpiry() {
    Map<String, Object> login = http.postForObject(
      "/internal/v1/auth/login",
      new HttpEntity<>(Map.of(
        "phone", "13800000001",
        "password", "abc123456"
      ), serviceHeaders()),
      Map.class);

    HttpHeaders headers = serviceHeaders();
    headers.setBearerAuth(String.valueOf(login.get("token")));
    ResponseEntity<Map> response = http.exchange(
      "/internal/v1/users/me",
      HttpMethod.GET,
      new HttpEntity<>(headers),
      Map.class);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
  }

  private HttpHeaders serviceHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "test-internal-token");
    return headers;
  }
}
