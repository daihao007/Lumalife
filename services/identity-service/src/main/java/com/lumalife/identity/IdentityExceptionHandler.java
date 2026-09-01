package com.lumalife.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class IdentityExceptionHandler {
  @ExceptionHandler(IdentityStore.IdentityException.class)
  ResponseEntity<Map<String, Object>> identity(IdentityStore.IdentityException error, HttpServletRequest request) {
    return error(error.status(), error.code(), error.getMessage(), error.reason(), request);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
      MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<Map<String, Object>> malformed(Exception error, HttpServletRequest request) {
    return error(400, 40000, "请求参数格式错误", "VALIDATION_FAILED", request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> system(Exception error, HttpServletRequest request) {
    return error(500, 50000, "身份服务暂时不可用", "INTERNAL_ERROR", request);
  }

  private ResponseEntity<Map<String, Object>> error(int status, int code, String message, String reason,
                                                      HttpServletRequest request) {
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null || requestId.isBlank()) requestId = java.util.UUID.randomUUID().toString();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", code);
    body.put("message", message);
    body.put("data", null);
    body.put("requestId", requestId);
    body.put("reason", reason);
    body.put("details", List.of());
    return ResponseEntity.status(HttpStatus.valueOf(status)).header("X-Request-Id", requestId).body(body);
  }
}
