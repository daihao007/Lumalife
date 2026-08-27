package com.lumalife.common;

import java.util.List;
import java.util.Map;

public class BusinessException extends RuntimeException {
  private final int code;
  private final String reason;
  private final List<Map<String, Object>> details;

  public BusinessException(int code, String message) {
    this(code, message, ReasonCodes.forCode(code), List.of());
  }

  public BusinessException(int code, String message, String reason) {
    this(code, message, reason, List.of());
  }

  public BusinessException(int code, String message, String reason, List<Map<String, Object>> details) {
    super(message);
    this.code = code;
    this.reason = reason;
    this.details = details == null ? List.of() : List.copyOf(details);
  }

  public int code() {
    return code;
  }

  public String reason() {
    return reason;
  }

  public List<Map<String, Object>> details() {
    return details;
  }
}
