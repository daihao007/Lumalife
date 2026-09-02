package com.lumalife.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumalife.service.BusinessStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/** Verifies that remote mode cannot accidentally instantiate the legacy store adapter. */
@SpringBootTest(properties = {
  "spring.profiles.active=prod,remote",
  "lumalife.persistence=mysql",
  "lumalife.internal.service-token=remote-mode-test-token",
  "lumalife.services.identity.base-url=http://127.0.0.1:1",
  "lumalife.services.merchant.base-url=http://127.0.0.1:1",
  "lumalife.services.order.base-url=http://127.0.0.1:1",
  "lumalife.services.assistant.base-url=http://127.0.0.1:1",
  "lumalife.migration.identity.enabled=true",
  "lumalife.migration.identity.backfill-completed=true",
  "lumalife.migration.merchant.enabled=true",
  "lumalife.migration.merchant.backfill-completed=true",
  "lumalife.migration.order.enabled=true",
  "lumalife.migration.order.backfill-completed=true",
  "lumalife.migration.assistant.enabled=true",
  "lumalife.migration.assistant.backfill-completed=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RemoteModeIntegrationTest {
  @Autowired
  private ApplicationContext context;

  @Test
  void remoteModeHasNoLegacyRepositoryOrMysqlHealthIndicator() {
    assertThat(context.getBeansOfType(BusinessStateRepository.class)).isEmpty();
    assertThat(context.containsBean("businessStateMysql")).isFalse();
  }
}
