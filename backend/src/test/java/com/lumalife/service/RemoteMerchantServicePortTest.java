package com.lumalife.service;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RemoteMerchantServicePortTest {
  @Test
  void normalizesFlatRemoteMerchantIntoFrontendDetailContract() {
    Map<String, Object> remoteMerchant = Map.of(
      "id", 1L,
      "name", "远程商家",
      "categoryId", 1L,
      "categoryName", "川湘菜",
      "status", "OPEN");
    Map<String, Object> fallbackMerchant = Map.of(
      "id", 1L,
      "name", "旧商家名称",
      "cover", "cover.jpg",
      "avgScore", 4.8,
      "avgPrice", 35,
      "monthlySales", 12,
      "distanceKm", 1.2,
      "address", "示例地址",
      "reason", "示例推荐理由");
    List<Map<String, Object>> products = List.of(Map.of("id", 1001L, "name", "藤椒鸡饭"));
    List<Map<String, Object>> groupDeals = List.of(Map.of("id", 1L, "title", "双人套餐"));
    List<Map<String, Object>> reviews = List.of(Map.of("id", 1L, "content", "很好吃"));

    Map<String, Object> detail = RemoteMerchantServicePort.normalizeMerchantDetail(
      remoteMerchant, fallbackMerchant, products, groupDeals, reviews);

    Assertions.assertEquals("远程商家", ((Map<?, ?>) detail.get("merchant")).get("name"));
    Assertions.assertEquals("cover.jpg", ((Map<?, ?>) detail.get("merchant")).get("cover"));
    Assertions.assertEquals("示例地址", ((Map<?, ?>) detail.get("merchant")).get("address"));
    Assertions.assertEquals(products, detail.get("products"));
    Assertions.assertEquals(groupDeals, detail.get("groupDeals"));
    Assertions.assertEquals(reviews, detail.get("reviews"));
  }
}
