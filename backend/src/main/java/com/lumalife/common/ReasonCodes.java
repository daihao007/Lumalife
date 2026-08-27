package com.lumalife.common;

/** Stable machine-readable reasons shared by the three service boundaries. */
public final class ReasonCodes {
  private ReasonCodes() {}

  public static String forCode(int code) {
    return switch (code) {
      case 40000 -> "VALIDATION_FAILED";
      case 40100 -> "INVALID_CREDENTIALS";
      case 40300 -> "RESOURCE_FORBIDDEN";
      case 40400 -> "RESOURCE_NOT_FOUND";
      case 40900 -> "BUSINESS_CONFLICT";
      case 42900 -> "RATE_LIMITED";
      case 50200 -> "DOWNSTREAM_BAD_RESPONSE";
      case 50300 -> "DEPENDENCY_UNAVAILABLE";
      case 50400 -> "DEPENDENCY_TIMEOUT";
      default -> "INTERNAL_ERROR";
    };
  }

  public static String forBusiness(int code, String message) {
    if (code == 40100 && message != null && message.contains("token")) return "TOKEN_INVALID";
    if (code == 40300 && message != null && (message.contains("角色") || message.contains("账号"))) return "ROLE_FORBIDDEN";
    if (code == 40400 && message != null && message.contains("地址")) return "ADDRESS_NOT_FOUND";
    if (code == 40400 && message != null && message.contains("订单")) return "ORDER_NOT_FOUND";
    if (code == 40900 && message != null && message.contains("库存")) return "INVENTORY_INSUFFICIENT";
    if (code == 40900 && message != null && message.contains("状态")) return "ILLEGAL_STATE_TRANSITION";
    return forCode(code);
  }
}
