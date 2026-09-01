package com.lumalife.observability;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Propagates the current correlation context to every service-to-service HTTP call. */
@Configuration
public class RestClientObservabilityConfiguration {
  @Bean
  RestClientCustomizer correlationRestClientCustomizer() {
    return builder -> builder.requestInterceptor((request, body, execution) -> {
      setIfPresent(request.getHeaders(), "X-Request-Id", MDC.get("requestId"));
      setIfPresent(request.getHeaders(), "traceparent", MDC.get("traceparent"));
      request.getHeaders().set("X-Caller-Service", "lumalife-backend");
      return execution.execute(request, body);
    });
  }

  private static void setIfPresent(org.springframework.http.HttpHeaders headers, String name, String value) {
    if (value != null && !value.isBlank()) headers.set(name, value);
  }
}
