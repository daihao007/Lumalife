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

  @Test
  void enrichesRemoteOrderWithMerchantAndProductDetails() {
    Map<String, Object> row = Map.of(
      "id", 42L,
      "userId", 1L,
      "merchantId", 2L,
      "productId", 1004L,
      "quantity", 2,
      "totalCent", 5600L,
      "status", "PENDING_PAYMENT",
      "createdAt", "2026-08-31T08:00:00Z");

    com.lumalife.domain.Models.Order order = RemoteOrderMapper.map(row,
      Map.of("name", "晨雾咖啡局"),
      Map.of("id", 1004L, "name", "桂花拿铁", "priceCent", 2800L),
      null);

    Assertions.assertEquals("晨雾咖啡局", order.merchantName);
    Assertions.assertEquals("桂花拿铁", order.lines.get(0).name());
    Assertions.assertEquals(2, order.lines.get(0).quantity());
    Assertions.assertEquals(com.lumalife.domain.Enums.OrderStatus.PENDING_PAYMENT, order.status);
  }
}
