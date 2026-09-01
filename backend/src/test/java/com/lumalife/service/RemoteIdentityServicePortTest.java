package com.lumalife.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RemoteIdentityServicePortTest {
  @Test
  void replacesRequestIdsRejectedByTheIdentityContract() {
    assertThat(RemoteIdentityServicePort.normalizeRequestId("short"))
      .hasSize(36)
      .isNotEqualTo("short");
    assertThat(RemoteIdentityServicePort.normalizeRequestId("request-123"))
      .isEqualTo("request-123");
  }
}
