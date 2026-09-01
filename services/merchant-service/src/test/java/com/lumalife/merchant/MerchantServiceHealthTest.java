package com.lumalife.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MerchantServiceHealthTest {
  @Autowired private TestRestTemplate http;

  @Test
  void exposesHealthAndLivenessProbes() {
    assertThat(http.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    assertThat(http.getForObject("/actuator/health/liveness", String.class)).contains("\"status\":\"UP\"");
    assertThat(http.getForObject("/actuator/health/readiness", String.class)).contains("\"status\":\"UP\"");
    assertThat(http.getForObject("/actuator/info", String.class))
      .contains("\"name\":\"merchant-service\"")
      .contains("\"version\":\"dev\"")
      .contains("\"contract-version\":\"v1\"")
      .contains("\"commit\":\"unknown\"");
  }

  @Test
  void echoesAValidRequestIdOnSuccessfulProbeResponses() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Request-Id", "d07-merchant-001");
    ResponseEntity<String> response = http.exchange("/actuator/health/readiness", HttpMethod.GET,
      new HttpEntity<>(headers), String.class);
    assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("d07-merchant-001");
  }
}
