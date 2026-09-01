package com.lumalife.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumalife.service.boundary.AssistantAnswerPort;
import com.lumalife.service.boundary.AssistantAnswerPort.AssistantMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Explicit monolith-only adapter retained for local development and rollback. */
@Service
@ConditionalOnProperty(prefix = "lumalife.migration.assistant", name = {"enabled", "backfill-completed"},
    havingValue = "false", matchIfMissing = true)
public class LocalAssistantAnswerPort implements AssistantAnswerPort {
  private static final Logger log = LoggerFactory.getLogger(LocalAssistantAnswerPort.class);

  private final AssistantFallbackService fallback;
  private final ObjectMapper mapper;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final String endpoint;
  private final String apiKey;
  private final String model;

  public LocalAssistantAnswerPort(AssistantFallbackService fallback, ObjectMapper mapper,
      @Value("${agnes.endpoint:https://apihub.agnes-ai.com/v1/chat/completions}") String endpoint,
      @Value("${agnes.api-key:}") String apiKey,
      @Value("${agnes.model:agnes-2.0-flash}") String model) {
    this.fallback = fallback;
    this.mapper = mapper;
    this.endpoint = endpoint;
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model;
  }

  @Override
  public String answer(String mode, String question, String context, List<AssistantMessage> history) {
    String safeQuestion = question == null ? "" : question;
    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", systemPrompt(mode)));
    if (context != null && !context.isBlank()) messages.add(Map.of("role", "system", "content", context));
    if (history != null) {
      history.stream().skip(Math.max(0, history.size() - 8)).forEach(item ->
          messages.add(Map.of("role", normalizeRole(item.role()), "content", item.content() == null ? "" : item.content())));
    }
    if (history == null || history.isEmpty()) messages.add(Map.of("role", "user", "content", safeQuestion));
    return callAgnes(messages, fallback.answer(safeQuestion));
  }

  private String systemPrompt(String mode) {
    if ("MERCHANT".equals(mode) || "MERCHANT_CONVERSATION".equals(mode)) {
      return "你是 LumaLife 的商家 AI 客服助手。请根据店铺上下文生成可直接发给顾客的回复，礼貌、具体、简短。"
          + "只有订单履约、退款、库存实时状态无法确认时，才建议人工确认。";
    }
    return "你是 LumaLife 本地生活平台的 AI 客服，回答登录、下单、支付、评价、团购券核销和商家履约相关问题。请用简洁中文回复。";
  }

  private String normalizeRole(String role) {
    return "assistant".equalsIgnoreCase(role) || "MERCHANT_AI".equalsIgnoreCase(role) ? "assistant" : "user";
  }

  private String callAgnes(List<? extends Map<String, String>> messages, String localAnswer) {
    if (apiKey.isBlank()) {
      log.info("Agnes API key is not configured; using local assistant fallback");
      return localAnswer;
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("model", model);
      payload.put("messages", messages);
      payload.put("temperature", 0.45);
      payload.put("max_tokens", 384);
      HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(6))
          .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Agnes request failed with status {}", response.statusCode());
        return localAnswer;
      }
      String answer = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("");
      return answer.isBlank() ? localAnswer : answer;
    } catch (Exception error) {
      log.warn("Agnes request failed; using local assistant fallback: {}", error.getMessage());
      return localAnswer;
    }
  }
}
