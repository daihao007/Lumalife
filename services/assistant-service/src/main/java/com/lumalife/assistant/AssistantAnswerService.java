package com.lumalife.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Service;

/** Owns model-provider integration and deterministic degradation for the AI boundary. */
@Service
public class AssistantAnswerService {
  private static final Logger log = LoggerFactory.getLogger(AssistantAnswerService.class);
  private final ObjectMapper mapper;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final String endpoint;
  private final String apiKey;
  private final String model;

  public AssistantAnswerService(ObjectMapper mapper,
      @Value("${agnes.endpoint:https://apihub.agnes-ai.com/v1/chat/completions}") String endpoint,
      @Value("${agnes.api-key:}") String apiKey,
      @Value("${agnes.model:agnes-2.0-flash}") String model) {
    this.mapper = mapper;
    this.endpoint = endpoint;
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model;
  }

  public String answer(AssistantRequest request) {
    String question = request.question() == null ? "" : request.question().trim();
    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", systemPrompt(request.mode())));
    if (request.context() != null && !request.context().isBlank()) {
      messages.add(Map.of("role", "system", "content", request.context()));
    }
    if (request.history() != null) {
      request.history().stream().skip(Math.max(0, request.history().size() - 8)).forEach(item ->
          messages.add(Map.of("role", normalizeRole(item.role()), "content", item.content() == null ? "" : item.content())));
    }
    if (request.history() == null || request.history().isEmpty()) {
      messages.add(Map.of("role", "user", "content", question));
    }
    return callProvider(messages, fallback(question, request.mode(), request.context()));
  }

  private String systemPrompt(String mode) {
    if ("MERCHANT".equals(mode) || "MERCHANT_CONVERSATION".equals(mode)) {
      return "你是 LumaLife 的商家 AI 客服助手。请根据店铺上下文生成可直接发给顾客的回复，礼貌、具体、简短。"
          + "只有订单履约、退款、库存实时状态无法确认时，才建议人工确认。";
    }
    return "你是 LumaLife 本地生活平台的 AI 客服，回答登录、下单、支付、评价、团购券核销和商家履约相关问题。请用简洁中文回复。";
  }

  private String normalizeRole(String role) {
    return "assistant".equalsIgnoreCase(role) || "MERCHANT_AI".equalsIgnoreCase(role) || "MERCHANT".equalsIgnoreCase(role)
        ? "assistant" : "user";
  }

  private String callProvider(List<? extends Map<String, String>> messages, String localAnswer) {
    if (apiKey.isBlank()) return localAnswer;
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
        log.warn("AI provider returned HTTP {}", response.statusCode());
        return localAnswer;
      }
      String answer = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("");
      return answer.isBlank() ? localAnswer : answer;
    } catch (Exception error) {
      log.warn("AI provider call failed; using deterministic fallback: {}", error.getMessage());
      return localAnswer;
    }
  }

  private String fallback(String question, String mode, String context) {
    if (question.contains("评价")) return "只有已完成的外卖订单或已核销的团购订单可以评价，且一单只能评价一次。";
    if (question.contains("支付")) return "当前系统为课程演示版模拟支付，支付接口使用 clientRequestId 保证幂等。";
    if (question.contains("券")) return "团购支付成功后会生成 12 位券码，商家只能核销自己店铺的券码。";
    if ("MERCHANT".equals(mode) || "MERCHANT_CONVERSATION".equals(mode)) {
      return context == null || context.isBlank() ? "店铺资料暂时不可用，请联系商家人工确认。" : "您好，关于这个问题请以店铺详情和当前订单状态为准；实时履约问题建议联系商家人工确认。";
    }
    return "我可以解答登录、下单、支付、评价、团购券核销和商家履约相关问题。";
  }

  public record AssistantRequest(String mode, String question, String context, List<AssistantMessage> history) {}
  public record AssistantMessage(String role, String content) {}
}
