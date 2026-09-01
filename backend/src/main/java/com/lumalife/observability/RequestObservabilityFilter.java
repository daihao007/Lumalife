package com.lumalife.observability;

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

/** Establishes the correlation context before security and application filters run. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestObservabilityFilter extends OncePerRequestFilter {
  public static final String REQUEST_ID_ATTRIBUTE = "lumalife.requestId";
  private static final Logger log = LoggerFactory.getLogger(RequestObservabilityFilter.class);
  private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
  private static final Pattern TRACE_PARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = validRequestId(request.getHeader("X-Request-Id"));
    String traceParent = validTraceParent(request.getHeader("traceparent"));
    String traceId = traceId(traceParent);
    if (traceParent == null) traceParent = "00-" + traceId + "-" + randomHex(16) + "-01";

    request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
    response.setHeader("X-Request-Id", requestId);
    MDC.put("requestId", requestId);
    MDC.put("traceId", traceId);
    MDC.put("traceparent", traceParent);
    long started = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - started) / 1_000_000;
      log.atInfo()
        .addKeyValue("event", "http_request_completed")
        .addKeyValue("method", request.getMethod())
        .addKeyValue("path", request.getRequestURI())
        .addKeyValue("status", response.getStatus())
        .addKeyValue("durationMs", durationMs)
        .log("HTTP request completed");
      MDC.remove("traceparent");
      MDC.remove("traceId");
      MDC.remove("requestId");
    }
  }

  private static String validRequestId(String value) {
    return value != null && REQUEST_ID.matcher(value).matches() ? value : UUID.randomUUID().toString();
  }

  private static String validTraceParent(String value) {
    if (value == null) return null;
    String normalized = value.toLowerCase(Locale.ROOT);
    return TRACE_PARENT.matcher(normalized).matches() ? normalized : null;
  }

  private static String traceId(String traceParent) {
    if (traceParent != null) {
      Matcher matcher = TRACE_PARENT.matcher(traceParent);
      if (matcher.matches()) return matcher.group(1);
    }
    return randomHex(32);
  }

  private static String randomHex(int length) {
    String value = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    return value.substring(0, length);
  }
}
