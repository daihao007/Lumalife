package com.lumalife.service;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {
  private final DemoStore store;

  public AdminDashboardService(DemoStore store) {
    this.store = store;
  }

  public Map<String, Object> metrics() {
    return store.adminMetricsV2();
  }
}
