package com.lumalife.service;

import org.springframework.stereotype.Service;

/** Deterministic local answer used when the external AI provider is unavailable. */
@Service
public class AssistantFallbackService {
  public String answer(String question) {
    String safeQuestion = question == null ? "" : question;
    if (safeQuestion.contains("评价")) return "只有已完成的外卖订单或已核销的团购订单可以评价，且一单只能评价一次。";
    if (safeQuestion.contains("支付")) return "当前系统为课程演示版模拟支付，支付接口使用 clientRequestId 保证幂等。";
    if (safeQuestion.contains("券")) return "团购支付成功后会生成 12 位券码，商家只能核销自己店铺的券码。";
    return "我可以解答登录、下单、支付、评价、团购券核销和商家履约相关问题。";
  }
}
