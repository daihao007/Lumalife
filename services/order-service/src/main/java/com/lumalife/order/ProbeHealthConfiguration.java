package com.lumalife.order;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/** Explicit contributors make the liveness/readiness groups available in standalone service tests and local runs. */
@Configuration
public class ProbeHealthConfiguration {
  @Bean(name = "livenessState") @ConditionalOnMissingBean(name = "livenessState") HealthIndicator livenessState() { return () -> Health.up().build(); }
  @Bean(name = "readinessState") @ConditionalOnMissingBean(name = "readinessState") HealthIndicator readinessState() { return () -> Health.up().build(); }
}
