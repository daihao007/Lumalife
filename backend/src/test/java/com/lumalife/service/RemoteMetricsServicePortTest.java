package com.lumalife.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RemoteMetricsServicePortTest {
  @Test
  void keepsOrderProjectionAndJoinsOwningMerchantNames() throws Exception {
    Method merge = RemoteMetricsServicePort.class.getDeclaredMethod("mergeMerchantNames", List.class, List.class);
    merge.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) merge.invoke(null,
      List.of(Map.of("merchantId", 1L, "name", "商家 #1", "orderCount", 2, "revenueCent", 5000L, "avgScore", 0.0)),
      List.of(Map.of("id", 1L, "name", "远程商家", "avgScore", 4.8)));

    assertEquals("远程商家", result.get(0).get("name"));
    assertEquals(4.8, result.get(0).get("avgScore"));
  }
}
