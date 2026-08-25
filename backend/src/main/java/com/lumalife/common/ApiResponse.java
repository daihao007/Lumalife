package com.lumalife.common;

public record ApiResponse<T>(int code, String message, T data) {
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(200, "success", data);
  }

  public static <T> ApiResponse<T> fail(int code, String message) {
    return new ApiResponse<>(code, message, null);
  }
}
