package com.lumalife.assistant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class InternalServiceTokenFilter extends OncePerRequestFilter {
  private final String expectedToken;

  InternalServiceTokenFilter(@Value("${lumalife.internal.service-token:}") String expectedToken) {
    this.expectedToken = expectedToken == null ? "" : expectedToken;
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
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("text/plain;charset=UTF-8");
      response.getWriter().write("服务调用未认证");
      return;
    }
    chain.doFilter(request, response);
  }
}
