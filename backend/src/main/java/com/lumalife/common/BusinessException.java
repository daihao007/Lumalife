package com.lumalife.common;

public class BusinessException extends RuntimeException {
  private final int code;

  public BusinessException(int code, String message) {
    super(message);
    this.code = code;
  }

  public int code() {
    return code;
  }
}
