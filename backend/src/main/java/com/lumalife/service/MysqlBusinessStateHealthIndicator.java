package com.lumalife.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Makes Actuator readiness reflect the MySQL business-state dependency. */
@Component("businessStateMysql")
@ConditionalOnProperty(name = "lumalife.persistence", havingValue = "mysql")
public class MysqlBusinessStateHealthIndicator implements HealthIndicator {
  private final BusinessStateRepository repository;

  public MysqlBusinessStateHealthIndicator(BusinessStateRepository repository) {
    this.repository = repository;
  }

  @Override
  public Health health() {
    try {
      repository.load();
      return Health.up().withDetail("backend", "mysql").build();
    } catch (RuntimeException error) {
      return Health.down(error).withDetail("backend", "mysql").build();
    }
  }
}
