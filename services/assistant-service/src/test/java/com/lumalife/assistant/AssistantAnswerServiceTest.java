package com.lumalife.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "lumalife.internal.service-token=assistant-test-token")
class AssistantAnswerServiceTest {
  @Autowired private TestRestTemplate http;

  @Test
  void answersWithoutProviderCredentialsUsingOwnedFallback() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "assistant-test-token");
    var request = new AssistantAnswerService.AssistantRequest("PLATFORM", "支付怎么办", "", List.of());
    var response = http.postForEntity("/internal/v1/assistant/answer", new HttpEntity<>(request, headers), String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("clientRequestId");
  }

  @Test
  void rejectsUnauthenticatedInternalCalls() {
    var request = new AssistantAnswerService.AssistantRequest("PLATFORM", "你好", "", List.of());
    var response = http.postForEntity("/internal/v1/assistant/answer", request, String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(401);
  }
}
