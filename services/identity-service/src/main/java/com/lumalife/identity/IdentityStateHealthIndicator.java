package com.lumalife.identity;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports whether the configured identity state path remains usable. */
@Component("identityState")
class IdentityStateHealthIndicator implements HealthIndicator {
  private final Path stateFile;

  IdentityStateHealthIndicator(@Value("${lumalife.identity.state-file:./data/identity-state.json}") String stateFile) {
    this.stateFile = stateFile == null || stateFile.isBlank() ? null : Path.of(stateFile).toAbsolutePath();
  }

  @Override
  public Health health() {
    if (stateFile == null) return Health.up().withDetail("storage", "memory").build();
    Path parent = stateFile.getParent();
    boolean usable = Files.exists(stateFile) ? Files.isReadable(stateFile) && Files.isWritable(stateFile)
      : parent != null && Files.isDirectory(parent) && Files.isWritable(parent);
    return usable
      ? Health.up().withDetail("stateFile", stateFile.toString()).build()
      : Health.down().withDetail("stateFile", stateFile.toString()).withDetail("reason", "not-readable-or-writable").build();
  }
}
