package com.lumalife.order;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Keeps the internal contract private when a service token is configured. */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {
  private final String expected;
  public InternalTokenFilter(@Value("${lumalife.internal.service-token:}") String expected) { this.expected = expected == null ? "" : expected; }
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    if (request.getRequestURI().startsWith("/internal/") && !expected.isBlank()) {
      String supplied = request.getHeader("X-Internal-Service-Token");
      if (supplied == null || supplied.isBlank()) supplied = request.getHeader("X-Luma-Service-Token");
      if (!expected.equals(supplied)) { response.sendError(HttpStatus.UNAUTHORIZED.value(), "invalid internal service token"); return; }
    }
    chain.doFilter(request, response);
  }
}
