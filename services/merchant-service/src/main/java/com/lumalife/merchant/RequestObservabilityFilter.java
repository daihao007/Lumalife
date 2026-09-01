package com.lumalife.merchant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestObservabilityFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestObservabilityFilter.class);
  private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
  private static final Pattern TRACE_PARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String requestId = requestId(request.getHeader("X-Request-Id")); String traceParent = traceParent(request.getHeader("traceparent")); String traceId = traceId(traceParent);
    if (traceParent == null) traceParent = "00-" + traceId + "-" + randomHex(16) + "-01";
    response.setHeader("X-Request-Id", requestId); MDC.put("requestId", requestId); MDC.put("traceId", traceId); MDC.put("traceparent", traceParent);
    long started = System.nanoTime();
    try { chain.doFilter(request, response); } finally {
      log.atInfo().addKeyValue("event", "http_request_completed").addKeyValue("method", request.getMethod()).addKeyValue("path", request.getRequestURI()).addKeyValue("status", response.getStatus()).addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000).log("HTTP request completed");
      clearMdc();
    }
  }
  private static String requestId(String value) { return value != null && REQUEST_ID.matcher(value).matches() ? value : UUID.randomUUID().toString(); }
  private static String traceParent(String value) { if (value == null) return null; String normalized = value.toLowerCase(Locale.ROOT); return TRACE_PARENT.matcher(normalized).matches() ? normalized : null; }
  private static String traceId(String value) { if (value != null) { Matcher matcher = TRACE_PARENT.matcher(value); if (matcher.matches()) return matcher.group(1); } return randomHex(32); }
  private static String randomHex(int length) { return (UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")).substring(0, length); }
  private static void clearMdc() { MDC.remove("traceparent"); MDC.remove("traceId"); MDC.remove("requestId"); }
}
