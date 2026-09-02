package com.lumalife.service;

import com.lumalife.service.boundary.AssistantAnswerPort;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Routes all production AI requests to the independently deployable assistant service. */
@Configuration
@ConditionalOnProperty(prefix = "lumalife.migration.assistant", name = {"enabled", "backfill-completed"},
    havingValue = "true")
public class RemoteAssistantAnswerPort {
  @Bean
  AssistantAnswerPort remoteAssistantPort(RestClient.Builder builder,
      @Value("${lumalife.services.assistant.base-url:http://localhost:8084}") String baseUrl,
      @Value("${lumalife.internal.service-token:}") String token) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(35));
    RestClient client = builder.baseUrl(baseUrl).requestFactory(requestFactory)
        .defaultHeader("X-Luma-Service-Token", token).build();
    return (mode, question, context, history) -> {
      List<Map<String, String>> messages = history == null ? List.of() : history.stream()
          .map(item -> Map.of("role", item.role(), "content", item.content() == null ? "" : item.content())).toList();
      Map<String, Object> response = client.post().uri("/internal/v1/assistant/answer")
          .body(Map.of("mode", mode == null ? "PLATFORM" : mode,
              "question", question == null ? "" : question,
              "context", context == null ? "" : context, "history", messages))
          .retrieve().body(Map.class);
      if (response == null || response.get("answer") == null || String.valueOf(response.get("answer")).isBlank()) {
        throw new IllegalStateException("AI 客服返回了空答案");
      }
      return String.valueOf(response.get("answer"));
    };
  }
}
