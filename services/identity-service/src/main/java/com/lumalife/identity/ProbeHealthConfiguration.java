package com.lumalife.identity;

import java.util.Map;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.boot.actuate.health.StatusAggregator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Makes the liveness/readiness groups available in standalone deployments. */
@Configuration
public class ProbeHealthConfiguration {
  @Bean(name = "livenessState") @ConditionalOnMissingBean(name = "livenessState") HealthIndicator livenessState() { return () -> Health.up().build(); }
  @Bean(name = "readinessState") @ConditionalOnMissingBean(name = "readinessState") HealthIndicator readinessState() { return () -> Health.up().build(); }

  @Bean
  @ConditionalOnMissingBean(HealthEndpointGroups.class)
  HealthEndpointGroups probeGroups() {
    HealthEndpointGroup primary = group("*");
    return HealthEndpointGroups.of(primary, Map.of("liveness", group("livenessState"), "readiness", group("readinessState")));
  }

  private HealthEndpointGroup group(String member) {
    return new HealthEndpointGroup() {
      public boolean isMember(String name) { return "*".equals(member) || member.equals(name); }
      public boolean showComponents(SecurityContext context) { return false; }
      public boolean showDetails(SecurityContext context) { return false; }
      public StatusAggregator getStatusAggregator() { return StatusAggregator.getDefault(); }
      public HttpCodeStatusMapper getHttpCodeStatusMapper() { return HttpCodeStatusMapper.DEFAULT; }
      public org.springframework.boot.actuate.health.AdditionalHealthEndpointPath getAdditionalPath() { return null; }
    };
  }
}
