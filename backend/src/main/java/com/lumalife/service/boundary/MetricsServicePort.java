package com.lumalife.service.boundary;

import java.util.Map;

/** Read-only platform metrics boundary. It owns projections, not identity/catalog/order source data. */
public interface MetricsServicePort {
  Map<String, Object> metrics();
}
