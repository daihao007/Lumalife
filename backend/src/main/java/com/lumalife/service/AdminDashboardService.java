package com.lumalife.service;

import java.util.Map;
import com.lumalife.service.boundary.MetricsServicePort;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {
  private final MetricsServicePort metrics;

  public AdminDashboardService(MetricsServicePort metrics) {
    this.metrics = metrics;
  }

  public Map<String, Object> metrics() {
    return metrics.metrics();
  }
}
