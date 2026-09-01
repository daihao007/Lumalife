package com.lumalife.merchant;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stable service metadata endpoint used by deployment health checks. */
@RestController
public class ServiceInfoController {
  private final String version;
  private final String commit;

  public ServiceInfoController(
      @Value("${SERVICE_VERSION:dev}") String version,
      @Value("${GIT_COMMIT:unknown}") String commit) {
    this.version = version;
    this.commit = commit;
  }

  @GetMapping("/actuator/info")
  Map<String, String> info() {
    return Map.of("name", "merchant-service", "version", version,
      "contract-version", "v1", "commit", commit);
  }
}
