package com.lumalife.order;

import java.util.Map;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit probe resources keep the contract stable on Spring Boot versions that do not auto-create probe groups. */
@RestController
public class ProbeController {
  private final HealthEndpoint health;
  public ProbeController(HealthEndpoint health) { this.health = health; }

  @GetMapping("/actuator/health")
  HealthComponent health() { return health.health(); }

  @GetMapping("/actuator/health/liveness")
  Map<String, String> liveness() { return Map.of("status", health.health().getStatus().getCode()); }

  @GetMapping("/actuator/health/readiness")
  Map<String, String> readiness() { return Map.of("status", health.health().getStatus().getCode()); }
}
