package com.lumalife.service;

import java.util.LinkedHashMap;
import java.util.Map;
import com.lumalife.service.boundary.MetricsServicePort;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {
  private final MetricsServicePort metrics;
  private final HealthEndpoint healthEndpoint;

  public AdminDashboardService(MetricsServicePort metrics, HealthEndpoint healthEndpoint) {
    this.metrics = metrics;
    this.healthEndpoint = healthEndpoint;
  }

  public Map<String, Object> metrics() {
    Map<String, Object> result = new LinkedHashMap<>(metrics.metrics());
    Map<String, Object> health = new LinkedHashMap<>();
    Object existingHealth = result.get("health");
    if (existingHealth instanceof Map<?, ?> existing) {
      existing.forEach((key, value) -> health.put(String.valueOf(key), value));
    }

    HealthComponent actuatorHealth = healthEndpoint.health();
    health.put("status", actuatorHealth.getStatus().getCode());
    health.put("source", "/actuator/health");
    result.put("health", health);
    return result;
  }
}
