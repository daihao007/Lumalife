package com.lumalife.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumalife.domain.Models.ChatMessage;
import com.lumalife.domain.Models.GroupDeal;
import com.lumalife.domain.Models.Merchant;
import com.lumalife.domain.Models.Product;
import com.lumalife.domain.Models.User;
import com.lumalife.service.boundary.AssistantAnswerPort;
import com.lumalife.service.boundary.MerchantServicePort;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** BFF orchestration for conversations; provider calls live behind AssistantAnswerPort. */
@Service
public class AssistantService {
  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
  private final MerchantServicePort merchant;
  private final AssistantAnswerPort assistant;
  private final ObjectMapper objectMapper;

  public AssistantService(MerchantServicePort merchant, AssistantAnswerPort assistant, ObjectMapper objectMapper) {
    this.merchant = merchant;
    this.assistant = assistant;
    this.objectMapper = objectMapper;
  }

  public String ask(String question) {
    return assistant.answer("PLATFORM", question == null ? "" : question, "", List.of());
  }

  public String askForMerchant(User admin, String question) {
    MerchantContext context = merchantContextForAdmin(admin);
    return assistant.answer("MERCHANT", question == null ? "" : question, context.text(), List.of());
  }

  public List<Map<String, Object>> userConversations(User user) { return merchant.userConversationSummaries(user); }
  public List<Map<String, Object>> merchantConversations(User admin) { return merchant.merchantConversationSummaries(admin); }
  public List<ChatMessage> userMessages(User user, long merchantId) { return merchant.userConversation(user, merchantId); }
  public List<ChatMessage> merchantMessages(User admin, long userId) { return merchant.merchantConversation(admin, userId); }

  public List<ChatMessage> sendUserMessage(User user, long merchantId, String content) {
    return merchant.sendUserMessage(user, merchantId, content, this::merchantAiReply);
  }

  public List<ChatMessage> sendMerchantMessage(User admin, long userId, String content) {
    return merchant.sendMerchantMessage(admin, userId, content);
  }

  private String merchantAiReply(List<ChatMessage> history) {
    long merchantId = history.isEmpty() ? 0 : history.get(history.size() - 1).merchantId();
    MerchantContext context = merchantContext(merchantId);
    String question = history.isEmpty() ? "" : history.get(history.size() - 1).content();
    List<AssistantAnswerPort.AssistantMessage> messages = history.stream()
        .skip(Math.max(0, history.size() - 8))
        .map(item -> new AssistantAnswerPort.AssistantMessage(
            "MERCHANT_AI".equals(item.senderRole()) || "MERCHANT".equals(item.senderRole()) ? "assistant" : "user",
            item.content()))
        .toList();
    return assistant.answer("MERCHANT_CONVERSATION", question, context.text(), messages);
  }

  private MerchantContext merchantContextForAdmin(User admin) {
    long merchantId = admin.merchantId() == null ? 0 : admin.merchantId();
    return merchantContext(merchantId, admin.nickname());
  }

  private MerchantContext merchantContext(long merchantId) { return merchantContext(merchantId, "店家"); }

  private MerchantContext merchantContext(long merchantId, String fallbackName) {
    if (merchantId <= 0) return new MerchantContext("店铺：" + fallbackName + "；店铺资料待确认。");
    try {
      Map<String, Object> detail = merchant.merchantDetail(merchantId);
      Merchant value = asMerchant(detail.get("merchant"));
      if (value == null) return new MerchantContext("店铺：" + fallbackName + "；店铺资料待确认。");
      return new MerchantContext(merchantContext(value, asProducts(detail.get("products")), asDeals(detail.get("groupDeals"))));
    } catch (RuntimeException error) {
      log.warn("Merchant context unavailable; AI service will answer with a safe generic context: {}", error.getMessage());
      return new MerchantContext("店铺：" + fallbackName + "；店铺资料暂时不可用。");
    }
  }

  private String merchantContext(Merchant merchant, List<Product> products, List<GroupDeal> deals) {
    StringBuilder context = new StringBuilder();
    context.append("店铺：").append(merchant.name()).append("；品类：").append(merchant.categoryName())
        .append("；状态：").append(merchant.status()).append("；地址：").append(merchant.address()).append("。\n");
    if (!products.isEmpty()) {
      context.append("在售商品：");
      products.stream().limit(8).forEach(item -> context.append(item.name()).append("（")
          .append(item.description()).append("，").append(formatMoney(item.priceCent()))
          .append("，库存").append(item.stock()).append("）；"));
      context.append("\n");
    }
    if (!deals.isEmpty()) {
      context.append("团购券：");
      deals.stream().limit(6).forEach(item -> context.append(item.title()).append("（")
          .append(item.description()).append("，").append(formatMoney(item.priceCent()))
          .append("，剩余").append(item.stock()).append("）；"));
    }
    return context.toString();
  }

  private List<Product> asProducts(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    return list.stream().map(item -> objectMapper.convertValue(item, Product.class)).toList();
  }

  private List<GroupDeal> asDeals(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    return list.stream().map(item -> objectMapper.convertValue(item, GroupDeal.class)).toList();
  }

  private Merchant asMerchant(Object value) {
    if (value instanceof Merchant item) return item;
    if (value == null) return null;
    try { return objectMapper.convertValue(value, Merchant.class); }
    catch (IllegalArgumentException ignored) { return null; }
  }

  private String formatMoney(long priceCent) {
    return "¥" + (priceCent / 100) + "." + String.format("%02d", Math.abs(priceCent % 100));
  }

  private record MerchantContext(String text) {}
}
