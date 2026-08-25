package com.lumalife.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
    HttpStatus status = switch (ex.code()) {
      case 40000 -> HttpStatus.BAD_REQUEST;
      case 40100 -> HttpStatus.UNAUTHORIZED;
      case 40300 -> HttpStatus.FORBIDDEN;
      case 40400 -> HttpStatus.NOT_FOUND;
      default -> HttpStatus.CONFLICT;
    };
    return ResponseEntity.status(status).body(ApiResponse.fail(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> system(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(50000, ex.getMessage()));
  }
}
