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
  @Value("${lumalife.migration.merchant.enabled:false}") private boolean merchant;
  @Value("${lumalife.migration.order.enabled:false}") private boolean order;

  @GetMapping("/status")
  Map<String, Object> status() {
    return Map.of("identity", route(identity), "merchant", route(merchant), "order", route(order), "rollback", "set corresponding LUMALIFE_*_REMOTE_ENABLED=false");
  }

  private String route(boolean remote) { return remote ? "remote-service" : "monolith"; }
}
