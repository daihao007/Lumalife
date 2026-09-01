package com.lumalife.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class ServiceInfoContributor implements InfoContributor {
  private final String version;
  private final String commit;

  public ServiceInfoContributor(
      @Value("${SERVICE_VERSION:dev}") String version,
      @Value("${GIT_COMMIT:unknown}") String commit) {
    this.version = version;
    this.commit = commit;
  }

  @Override
  public void contribute(Info.Builder builder) {
    builder.withDetail("name", "identity-service")
      .withDetail("version", version)
      .withDetail("contract-version", "v1")
      .withDetail("commit", commit);
  }
}
