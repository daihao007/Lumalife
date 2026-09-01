package com.lumalife.observability;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientObservabilityConfigurationTest {
  @AfterEach void clearMdc() { MDC.clear(); }

  @Test
  void propagatesRequestAndTraceContextToDownstreamServices() {
    RestClient.Builder builder = RestClient.builder();
    new RestClientObservabilityConfiguration().correlationRestClientCustomizer().customize(builder);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    MDC.put("requestId", "d07-propagation-001");
    MDC.put("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");

    server.expect(requestTo("http://identity-service/internal/v1/ping"))
      .andExpect(header("X-Request-Id", "d07-propagation-001"))
      .andExpect(header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"))
      .andExpect(header("X-Caller-Service", "lumalife-backend"))
      .andRespond(withSuccess());

    builder.build().get().uri("http://identity-service/internal/v1/ping").retrieve().toBodilessEntity();
    server.verify();
  }
}
