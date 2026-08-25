package com.lumalife.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumalife.domain.Models.ChatMessage;
import com.lumalife.domain.Models.GroupDeal;
import com.lumalife.domain.Models.Merchant;
import com.lumalife.domain.Models.Product;
import com.lumalife.domain.Models.User;
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

@Service
public class AssistantService {
  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

  private final DemoStore store;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final String endpoint;
  private final String apiKey;
  private final String model;

  public AssistantService(DemoStore store,
                          @Value("${agnes.endpoint:https://apihub.agnes-ai.com/v1/chat/completions}") String endpoint,
                          @Value("${agnes.api-key:}") String apiKey,
                          @Value("${agnes.model:agnes-2.0-flash}") String model) {
    this.store = store;
    this.endpoint = endpoint;
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model;
  }

  public String ask(String question) {
    String safeQuestion = question == null ? "" : question;
    String fallback = store.askAssistant(safeQuestion);
    return callAgnes(List.of(
      Map.of("role", "system", "content", "你是 LumaLife 本地生活平台的 AI 客服，回答登录、下单、支付、评价、团购券核销和商家履约相关问题。请用简洁中文回复。"),
      Map.of("role", "user", "content", safeQuestion)
    ), fallback);
  }

  public String askForMerchant(User admin, String question) {
    Map<String, Object> profile = store.merchantProfile(admin);
    Merchant merchant = (Merchant) profile.get("merchant");
    Map<String, Object> detail = store.merchantDetail(merchant.id());
    List<Product> products = castList(detail.get("products"));
    List<GroupDeal> deals = castList(detail.get("groupDeals"));
    String safeQuestion = question == null ? "" : question;
    return callAgnes(List.of(
      Map.of("role", "system", "content",
        "你是 LumaLife 的商家 AI 客服助手。请帮商家生成可直接发给顾客的回复，礼貌、具体、简短。"
          + "优先使用店铺商品和团购券上下文；只有订单、退款、库存实时状态无法确认时，才建议人工确认。"),
      Map.of("role", "system", "content", merchantContext(merchant, products, deals)),
      Map.of("role", "user", "content", safeQuestion)
    ), localMerchantQuestionReply(safeQuestion, merchant, products, deals));
  }

  public List<Map<String, Object>> userConversations(User user) {
    return store.userConversationSummaries(user);
  }

  public List<Map<String, Object>> merchantConversations(User admin) {
    return store.merchantConversationSummaries(admin);
  }

  public List<ChatMessage> userMessages(User user, long merchantId) {
    return store.userConversation(user, merchantId);
  }

  public List<ChatMessage> merchantMessages(User admin, long userId) {
    return store.merchantConversation(admin, userId);
  }

  public List<ChatMessage> sendUserMessage(User user, long merchantId, String content) {
    return store.sendUserMessage(user, merchantId, content, this::merchantAiReply);
  }

  public List<ChatMessage> sendMerchantMessage(User admin, long userId, String content) {
    return store.sendMerchantMessage(admin, userId, content);
  }

  private String merchantAiReply(List<ChatMessage> history) {
    long merchantId = history.isEmpty() ? 0 : history.get(history.size() - 1).merchantId();
    Merchant merchant = null;
    List<Product> products = List.of();
    List<GroupDeal> deals = List.of();
    if (merchantId > 0) {
      Map<String, Object> detail = store.merchantDetail(merchantId);
      merchant = (Merchant) detail.get("merchant");
      products = castList(detail.get("products"));
      deals = castList(detail.get("groupDeals"));
    }

    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content",
      "你是 LumaLife 平台上的店家 AI 客服，代表商家直接回复用户咨询。"
        + "请优先根据店铺、商品和团购券信息回答，语气礼貌、具体、简短。"
        + "只有订单履约、退款、库存实时状态等无法从上下文确认的问题，才说明需要店家人工确认。"
        + "不要用“已记录、稍后跟进”替代已知信息；不要代写与店铺服务无关的内容。"));
    if (merchant != null) {
      messages.add(Map.of("role", "system", "content", merchantContext(merchant, products, deals)));
    }
    history.stream().skip(Math.max(0, history.size() - 8)).forEach(message -> {
      String role = "USER".equals(message.senderRole()) ? "user" : "assistant";
      messages.add(Map.of("role", role, "content", message.content()));
    });
    return callAgnes(messages, localMerchantReply(history, merchant, products, deals));
  }

  private String callAgnes(List<? extends Map<String, String>> messages, String fallback) {
    if (apiKey == null || apiKey.isBlank()) {
      log.info("Agnes API key is not configured; using local assistant fallback");
      return fallback;
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("model", model);
      payload.put("messages", messages);
      payload.put("temperature", 0.45);
      payload.put("max_tokens", 384);
      String body = objectMapper.writeValueAsString(payload);
      HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
        .timeout(Duration.ofSeconds(6))
        .header("Authorization", "Bearer " + apiKey.trim())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Agnes request failed with status {}", response.statusCode());
        return fallback;
      }
      JsonNode root = objectMapper.readTree(response.body());
      String content = root.path("choices").path(0).path("message").path("content").asText("");
      return content.isBlank() ? fallback : content;
    } catch (Exception error) {
      log.warn("Agnes request failed; using local assistant fallback: {}", error.getMessage());
      return fallback;
    }
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> castList(Object value) {
    return value instanceof List<?> list ? (List<T>) list : List.of();
  }

  private String merchantContext(Merchant merchant, List<Product> products, List<GroupDeal> deals) {
    StringBuilder context = new StringBuilder();
    context.append("店铺：").append(merchant.name())
      .append("；品类：").append(merchant.categoryName())
      .append("；状态：").append(merchant.status())
      .append("；地址：").append(merchant.address()).append("。\n");
    if (!products.isEmpty()) {
      context.append("在售商品：");
      products.stream().limit(8).forEach(product -> context.append(product.name())
        .append("（").append(product.description()).append("，")
        .append(formatMoney(product.priceCent())).append("，库存").append(product.stock()).append("）; "));
      context.append("\n");
    }
    if (!deals.isEmpty()) {
      context.append("团购券：");
      deals.stream().limit(6).forEach(deal -> context.append(deal.title())
        .append("（").append(deal.description()).append("，")
        .append(formatMoney(deal.priceCent())).append("，剩余").append(deal.stock()).append("）; "));
    }
    return context.toString();
  }

  private String localMerchantReply(List<ChatMessage> history, Merchant merchant, List<Product> products, List<GroupDeal> deals) {
    String question = history.isEmpty() ? "" : history.get(history.size() - 1).content();
    return localMerchantQuestionReply(question, merchant, products, deals);
  }

  private String localMerchantQuestionReply(String question, Merchant merchant, List<Product> products, List<GroupDeal> deals) {
    String name = merchant == null ? "店家" : merchant.name();
    if (question.contains("推荐") && question.contains("咖啡")) {
      Product product = products.stream()
        .filter(item -> item.listed() && item.name().contains("咖啡"))
        .findFirst()
        .orElse(products.stream().filter(Product::listed).findFirst().orElse(null));
      if (product != null) {
        return "推荐您试试" + product.name() + "，" + product.description() + "，当前价格 "
          + formatMoney(product.priceCent()) + "。如果想搭配下午茶，也可以看看店里的咖啡下午茶券。";
      }
      return name + "目前没有可确认的咖啡单品，我可以先帮您咨询店家今日推荐。";
    }
    if (question.contains("下午茶券") || question.contains("团购券") || question.contains("券")) {
      GroupDeal deal = deals.stream()
        .filter(item -> item.active() && (question.contains(item.title()) || item.title().contains("券")))
        .findFirst()
        .orElse(deals.stream().filter(GroupDeal::active).findFirst().orElse(null));
      if (deal != null) {
        return deal.title() + "当前可在平台购买，内容是" + deal.description() + "，价格 "
          + formatMoney(deal.priceCent()) + "。当前演示数据没有配置固定禁用时段，通常可按店铺营业状态使用；到店前建议避开临近打烊时段，并以券详情和店家确认为准。";
      }
      return "目前没有查到可用团购券，建议您刷新店铺详情页查看最新券包。";
    }
    if (question.contains("谢谢") || question.contains("感谢")) {
      return "不客气，祝您用餐愉快！还有咖啡、团购券或订单问题都可以继续问我。";
    }
    if (question.contains("你好") || question.contains("您好")) {
      return "您好，这里是" + name + "客服。您可以咨询咖啡推荐、团购券使用、营业状态或订单问题。";
    }
    return "您好，关于这个问题我先按店铺信息为您确认："
      + (merchant == null ? "请补充想咨询的店铺或商品。" : name + "当前为" + merchant.status() + "，可咨询商品推荐、团购券和订单相关问题。");
  }

  private String formatMoney(long priceCent) {
    return "¥" + (priceCent / 100) + "." + String.format("%02d", Math.abs(priceCent % 100));
  }
}
