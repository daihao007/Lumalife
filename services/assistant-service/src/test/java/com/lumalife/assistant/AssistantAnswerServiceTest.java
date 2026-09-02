package com.lumalife.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpEntity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "lumalife.internal.service-token=assistant-test-token",
      "agnes.api-key="
    })
@AutoConfigureMockMvc
class AssistantAnswerServiceTest {
  @Autowired private TestRestTemplate http;
  @Autowired private MockMvc mockMvc;

  @Test
  void answersWithoutProviderCredentialsUsingOwnedFallback() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Luma-Service-Token", "assistant-test-token");
    var request = new AssistantAnswerService.AssistantRequest("PLATFORM", "支付怎么办", "", List.of());
    var response = http.postForEntity("/internal/v1/assistant/answer", new HttpEntity<>(request, headers), String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("支付接口使用 clientRequestId 保证幂等");
  }

  @Test
  void rejectsUnauthenticatedInternalCalls() throws Exception {
    mockMvc.perform(post("/internal/v1/assistant/answer")
            .contentType("application/json")
            .content("{\"mode\":\"PLATFORM\",\"question\":\"你好\",\"context\":\"\",\"history\":[]}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string("服务调用未认证"));
  }
}
