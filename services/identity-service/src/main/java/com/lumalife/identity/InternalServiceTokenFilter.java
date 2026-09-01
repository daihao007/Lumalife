package com.lumalife.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Prevents the identity HTTP port from being used as an unauthenticated public API. */
@Component
class InternalServiceTokenFilter extends OncePerRequestFilter {
  private final String expectedToken;
  private final ObjectMapper objectMapper;

  InternalServiceTokenFilter(@Value("${lumalife.internal.service-token:}") String expectedToken,
                             ObjectMapper objectMapper) {
    this.expectedToken = expectedToken == null ? "" : expectedToken;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/v1/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String actual = request.getHeader("X-Luma-Service-Token");
    if (actual == null || actual.isBlank()) actual = request.getHeader("X-Internal-Service-Token");
    if (expectedToken.isBlank() || actual == null
        || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
      writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED, 40100,
        "服务调用未认证", "SERVICE_TOKEN_INVALID");
      return;
    }
    String requestId = request.getHeader("X-Request-Id");
    String traceparent = request.getHeader("traceparent");
    String caller = request.getHeader("X-Caller-Service");
    if (requestId == null || requestId.isBlank() || requestId.length() < 8 || requestId.length() > 128
        || !validTraceparent(traceparent)
        || caller == null || !Set.of("api-gateway", "identity-service", "merchant-service", "order-service").contains(caller)) {
      writeError(response, request, HttpServletResponse.SC_BAD_REQUEST, 40000,
        "内部调用上下文无效", "INVALID_CALL_CONTEXT");
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean validTraceparent(String value) {
    return value != null && value.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
      && !value.substring(3, 35).equals("00000000000000000000000000000000")
      && !value.substring(36, 52).equals("0000000000000000");
  }

  private void writeError(HttpServletResponse response, HttpServletRequest request, int status, int code,
                          String message, String reason) throws IOException {
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null || requestId.isBlank()) requestId = java.util.UUID.randomUUID().toString();
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.setHeader("X-Request-Id", requestId);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", code);
    body.put("message", message);
    body.put("data", null);
    body.put("requestId", requestId);
    body.put("reason", reason);
    body.put("details", List.of());
    objectMapper.writeValue(response.getWriter(), body);
  }
}
