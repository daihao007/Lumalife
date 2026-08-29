package com.lumalife.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operational evidence for progressive migration and rollback. */
@RestController
@RequestMapping("/internal/migration")
public class MigrationController {
  @Value("${lumalife.migration.identity.enabled:false}") private boolean identity;
  @Value("${lumalife.migration.identity.backfill-completed:false}") private boolean identityBackfillCompleted;
  @Value("${lumalife.migration.merchant.enabled:false}") private boolean merchant;
  @Value("${lumalife.migration.merchant.backfill-completed:false}") private boolean merchantBackfillCompleted;
  @Value("${lumalife.migration.order.enabled:false}") private boolean order;
  @Value("${lumalife.migration.order.backfill-completed:false}") private boolean orderBackfillCompleted;

  @GetMapping("/status")
  Map<String, Object> status() {
    return Map.of(
      "identity", identityRoute(),
      "merchant", route(merchant, merchantBackfillCompleted),
      "order", route(order, orderBackfillCompleted),
      "rollback", "set LUMALIFE_MERCHANT_REMOTE_ENABLED=false and LUMALIFE_ORDER_REMOTE_ENABLED=false",
      "note", "catalog read/product write and order query/cancel are remotely routed; remaining capabilities use the monolith until their contracts are migrated");
  }

  private String identityRoute() {
    if (!identity) return "monolith";
    return identityBackfillCompleted ? "remote-service" : "blocked-backfill-required";
  }

  private String route(boolean enabled, boolean backfill) {
    if (!enabled) return "monolith";
    return backfill ? "remote-service" : "blocked-backfill-required";
  }
}
