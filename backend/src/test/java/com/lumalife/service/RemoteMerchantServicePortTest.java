package com.lumalife.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumalife.domain.Models.User;
import com.lumalife.service.boundary.MerchantServicePort;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestClient;

class RemoteMerchantServicePortTest {
  @Test
  void routesMerchantNicknameUpdatesToTheRemoteService() throws Exception {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> actor = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/internal/v1/merchants/3/profile", exchange -> {
      method.set(exchange.getRequestMethod());
      path.set(exchange.getRequestURI().getPath());
      actor.set(exchange.getRequestHeaders().getFirst("X-Merchant-Id"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = "{\"id\":3,\"name\":\"远程新昵称\",\"categoryId\":3,\"categoryName\":\"轻食简餐\",\"cover\":\"cover\",\"avgScore\":4.5,\"avgPrice\":29,\"monthlySales\":189,\"distanceKm\":2.4,\"status\":\"营业中\",\"address\":\"学院路 66 号\",\"reason\":\"复购高\"}"
        .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      DemoStore fallback = new DemoStore(new BCryptPasswordEncoder());
      User admin = fallback.userByPhone("13800000004");
      MerchantServicePort port = new RemoteMerchantServicePort().remoteMerchantPort(
        fallback, new ObjectMapper(), RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), "token");

      Map<String, Object> result = port.updateMerchantNickname(admin, "远程新昵称");

      Assertions.assertEquals("PUT", method.get());
      Assertions.assertEquals("/internal/v1/merchants/3/profile", path.get());
      Assertions.assertEquals("3", actor.get());
      Assertions.assertTrue(body.get().contains("远程新昵称"));
      Assertions.assertEquals("远程新昵称", ((Map<?, ?>) result.get("user")).get("nickname"));
      Assertions.assertEquals("远程新昵称", ((com.lumalife.domain.Models.Merchant) result.get("merchant")).name());
    } finally {
      server.stop(0);
    }
  }

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
