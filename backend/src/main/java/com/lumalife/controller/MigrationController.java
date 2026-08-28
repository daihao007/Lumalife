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

  @GetMapping("/status")
  Map<String, Object> status() {
    return Map.of(
      "identity", identityRoute(),
      "merchant", "not-wired",
      "order", "not-wired",
      "rollback", "set LUMALIFE_IDENTITY_REMOTE_ENABLED=false",
      "note", "merchant/order switches are intentionally unavailable until their adapters own real traffic");
  }

  private String identityRoute() {
    if (!identity) return "monolith";
    return identityBackfillCompleted ? "remote-service" : "blocked-backfill-required";
  }
}
