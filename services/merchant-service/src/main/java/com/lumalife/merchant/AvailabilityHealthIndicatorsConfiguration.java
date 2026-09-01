package com.lumalife.merchant;

import java.util.Map;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.availability.LivenessStateHealthIndicator;
import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.boot.actuate.health.StatusAggregator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AvailabilityHealthIndicatorsConfiguration {

    @Bean(name = "livenessState")
    @ConditionalOnMissingBean(name = "livenessState")
    LivenessStateHealthIndicator livenessStateHealthIndicator(ApplicationAvailability availability) {
        return new LivenessStateHealthIndicator(availability);
    }

    @Bean(name = "readinessState")
    @ConditionalOnMissingBean(name = "readinessState")
    ReadinessStateHealthIndicator readinessStateHealthIndicator(ApplicationAvailability availability) {
        return new ReadinessStateHealthIndicator(availability);
    }

    @Bean
    @ConditionalOnMissingBean(HealthEndpointGroups.class)
    HealthEndpointGroups probeGroups() {
        return HealthEndpointGroups.of(group("*"), Map.of(
            "liveness", group("livenessState"),
            "readiness", group("readinessState")));
    }

    private HealthEndpointGroup group(String... members) {
        return new HealthEndpointGroup() {
            public boolean isMember(String name) {
                return java.util.Arrays.asList(members).contains("*") || java.util.Arrays.asList(members).contains(name);
            }
            public boolean showComponents(SecurityContext context) { return false; }
            public boolean showDetails(SecurityContext context) { return false; }
            public StatusAggregator getStatusAggregator() { return StatusAggregator.getDefault(); }
            public HttpCodeStatusMapper getHttpCodeStatusMapper() { return HttpCodeStatusMapper.DEFAULT; }
            public org.springframework.boot.actuate.health.AdditionalHealthEndpointPath getAdditionalPath() { return null; }
        };
    }
}
