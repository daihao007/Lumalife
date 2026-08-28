package com.lumalife.identity;

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

/** Prevents the identity HTTP port from being used as an unauthenticated public API. */
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
    if (expectedToken.isBlank() || actual == null
        || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "服务调用未认证");
      return;
    }
    chain.doFilter(request, response);
  }
}
