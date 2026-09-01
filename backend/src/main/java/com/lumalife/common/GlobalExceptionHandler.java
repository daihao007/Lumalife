package com.lumalife.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> business(BusinessException ex, HttpServletRequest request) {
    HttpStatus status = switch (ex.code()) {
      case 40000 -> HttpStatus.BAD_REQUEST;
      case 40100 -> HttpStatus.UNAUTHORIZED;
      case 40300 -> HttpStatus.FORBIDDEN;
      case 40400 -> HttpStatus.NOT_FOUND;
      case 50300 -> HttpStatus.SERVICE_UNAVAILABLE;
      default -> HttpStatus.CONFLICT;
    };
    String requestId = requestId(request);
    return ResponseEntity.status(status).header("X-Request-Id", requestId)
      .body(ErrorResponse.of(ex, requestId));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> malformedRequest(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    String requestId = requestId(request);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).header("X-Request-Id", requestId)
      .body(ErrorResponse.of(40000, "请求体格式错误", requestId));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> system(Exception ex, HttpServletRequest request) {
    log.error("Unhandled request failure for {} {}", request.getMethod(), request.getRequestURI(), ex);
    String requestId = requestId(request);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).header("X-Request-Id", requestId)
      .body(ErrorResponse.of(50000, "服务暂时不可用", requestId));
  }

  private String requestId(HttpServletRequest request) {
    String requestId = request.getHeader("X-Request-Id");
    return requestId == null || requestId.isBlank() ? java.util.UUID.randomUUID().toString() : requestId;
  }
}
