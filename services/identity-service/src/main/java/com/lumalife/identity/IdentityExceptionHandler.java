package com.lumalife.identity;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class IdentityExceptionHandler {
  @ExceptionHandler(IdentityStore.IdentityException.class)
  ResponseEntity<Map<String, Object>> identity(IdentityStore.IdentityException error) {
    return ResponseEntity.status(error.status()).body(Map.of(
      "code", error.status(), "message", error.getMessage(), "reason", error.reason()));
  }
}
