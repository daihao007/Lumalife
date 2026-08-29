package com.lumalife.order;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit probe resources keep the contract stable on Spring Boot versions that do not auto-create probe groups. */
@RestController
public class ProbeController {
  @GetMapping("/actuator/health/liveness")
  Map<String, String> liveness() { return Map.of("status", "UP"); }

  @GetMapping("/actuator/health/readiness")
  Map<String, String> readiness() { return Map.of("status", "UP"); }
}
