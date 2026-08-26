package com.lumalife.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@SpringBootTest(properties = "lumalife.state-file=")
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {
  @Autowired
  private MockMvc mvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void anonymousUserCanBrowsePublicMerchantList() throws Exception {
    mvc.perform(get("/api/v1/merchants"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200));
  }

  @Test
  void merchantListAppliesSortingAndFilters() throws Exception {
    JsonNode sorted = getMerchantRecords("/api/v1/merchants?sort=priceAsc");
    for (int index = 1; index < sorted.size(); index++) {
      Assertions.assertTrue(sorted.get(index - 1).path("avgPrice").asInt() <= sorted.get(index).path("avgPrice").asInt());
    }

    JsonNode filtered = getMerchantRecords("/api/v1/merchants?minPrice=30&maxPrice=40&minScore=4.6");
    Assertions.assertTrue(filtered.size() > 0);
    for (JsonNode merchant : filtered) {
      Assertions.assertTrue(merchant.path("avgPrice").asInt() >= 30);
      Assertions.assertTrue(merchant.path("avgPrice").asInt() <= 40);
      Assertions.assertTrue(merchant.path("avgScore").asDouble() >= 4.6);
    }
  }

  @Test
  void userCanRegisterReadAndUpdateProfileThroughApi() throws Exception {
    JsonNode registered = registerUser("API 测试用户");
    String token = registered.path("token").asText();

    Assertions.assertFalse(token.isBlank());
    Assertions.assertEquals("USER", registered.path("user").path("role").asText());

    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.nickname").value("API 测试用户"))
      .andExpect(jsonPath("$.data.role").value("USER"))
      .andExpect(jsonPath("$.data.password").doesNotExist());

    mvc.perform(post("/api/v1/user/profile")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"nickname\":\"API 更新用户\",\"avatarUrl\":\"https://example.com/avatar.png\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.nickname").value("API 更新用户"))
      .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"));
  }

  @Test
  void userCanManageAddressesThroughApi() throws Exception {
    String token = registerUser("地址 API 用户").path("token").asText();

    String firstResponse = mvc.perform(post("/api/v1/user/addresses")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"contactName\":\"林夏\",\"phone\":\"13800000001\",\"detail\":\"测试路 1 号\",\"defaultAddress\":true}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.defaultAddress").value(true))
      .andReturn()
      .getResponse()
      .getContentAsString();
    long firstAddressId = objectMapper.readTree(firstResponse).path("data").path("id").asLong();

    mvc.perform(post("/api/v1/user/addresses")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"contactName\":\"林夏\",\"phone\":\"13800000001\",\"detail\":\"测试路 2 号\",\"defaultAddress\":true}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.defaultAddress").value(true));

    mvc.perform(get("/api/v1/user/addresses").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()").value(2))
      .andExpect(jsonPath("$.data[0].defaultAddress").value(false))
      .andExpect(jsonPath("$.data[1].defaultAddress").value(true));

    mvc.perform(post("/api/v1/user/addresses/{id}/delete", firstAddressId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk());

    mvc.perform(get("/api/v1/user/addresses").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()").value(1))
      .andExpect(jsonPath("$.data[0].defaultAddress").value(true))
      .andExpect(jsonPath("$.data[0].detail").value("测试路 2 号"));
  }

  @Test
  void publicCatalogApiReturnsCategoriesSearchAndMerchantDetail() throws Exception {
    mvc.perform(get("/api/v1/categories"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(4)));

    mvc.perform(get("/api/v1/merchants")
        .param("keyword", "藤椒鸡饭")
        .param("categoryId", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.records[0].id").value(1))
      .andExpect(jsonPath("$.data.records[0].categoryId").value(1));

    mvc.perform(get("/api/v1/merchants/1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.merchant.id").value(1))
      .andExpect(jsonPath("$.data.products").isArray())
      .andExpect(jsonPath("$.data.products[0].merchantId").value(1))
      .andExpect(jsonPath("$.data.groupDeals[0].merchantId").value(1))
      .andExpect(jsonPath("$.data.reviews").isArray());
  }

  @Test
  void userCanCompleteCartOrderAndIdempotentPaymentThroughApi() throws Exception {
    String token = registerUser("下单 API 用户").path("token").asText();
    long addressId = createAddress(token, "下单测试路 8 号");

    mvc.perform(post("/api/v1/cart/items")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"productId\":1001,\"quantity\":2}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].productId").value(1001))
      .andExpect(jsonPath("$.data[0].quantity").value(2));

    mvc.perform(post("/api/v1/cart/items/1001")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"quantity\":1}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].quantity").value(1));

    String orderResponse = mvc.perform(post("/api/v1/orders/delivery")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"addressId\":%d}".formatted(addressId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].status").value("PENDING_PAYMENT"))
      .andExpect(jsonPath("$.data[0].addressId").value(addressId))
      .andExpect(jsonPath("$.data[0].addressSnapshot").value("下单 API 用户 13900000001 下单测试路 8 号"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    long orderId = objectMapper.readTree(orderResponse).path("data").get(0).path("id").asLong();

    String firstPayment = mvc.perform(post("/api/v1/payments")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"orderId\":%d,\"clientRequestId\":\"api-payment-%d\"}".formatted(orderId, orderId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("PAID"))
      .andExpect(jsonPath("$.data.clientRequestId").value("api-payment-%d".formatted(orderId)))
      .andReturn()
      .getResponse()
      .getContentAsString();

    mvc.perform(post("/api/v1/payments")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"orderId\":%d,\"clientRequestId\":\"api-payment-%d\"}".formatted(orderId, orderId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(objectMapper.readTree(firstPayment).path("data").path("id").asLong()))
      .andExpect(jsonPath("$.data.status").value("PAID"));

    mvc.perform(get("/api/v1/orders").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()").value(1))
      .andExpect(jsonPath("$.data[0].status").value("PAID"));

    mvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(40900));
  }

  @Test
  void userCanCancelPendingOrderAndCannotPayItAfterwards() throws Exception {
    String token = registerUser("取消 API 用户").path("token").asText();
    long addressId = createAddress(token, "取消测试路 9 号");
    mvc.perform(post("/api/v1/cart/items")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"productId\":1002,\"quantity\":1}"))
      .andExpect(status().isOk());

    String orderResponse = mvc.perform(post("/api/v1/orders/delivery")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"addressId\":%d}".formatted(addressId)))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    long orderId = objectMapper.readTree(orderResponse).path("data").get(0).path("id").asLong();

    mvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("CANCELLED"));

    mvc.perform(post("/api/v1/payments")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"orderId\":%d,\"clientRequestId\":\"cancelled-order-payment\"}".formatted(orderId)))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(40900));
  }

  @Test
  void anonymousUserCannotAccessCart() throws Exception {
    mvc.perform(get("/api/v1/cart"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void normalUserCannotAccessMerchantAdminApis() throws Exception {
    mvc.perform(get("/api/v1/merchant-admin/orders")
        .header("Authorization", bearer(login("13800000001", "abc123456"))))
      .andExpect(status().isForbidden());
  }

  @Test
  void merchantAdminCannotAccessPlatformAdminApis() throws Exception {
    mvc.perform(get("/api/v1/admin/metrics")
        .header("Authorization", bearer(login("13800000002", "abc123456"))))
      .andExpect(status().isForbidden());
  }

  @Test
  void platformAdminCanAccessMetrics() throws Exception {
    String response = mvc.perform(get("/api/v1/admin/metrics")
        .header("Authorization", bearer(login("13800000000", "admin123456"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.health.status").value("UP"))
      .andExpect(jsonPath("$.data.overview.users").value(greaterThanOrEqualTo(1)))
      .andExpect(jsonPath("$.data.userAccounts[0].username").value("13800000001"))
      .andExpect(jsonPath("$.data.userAccounts[0].nickname").value("林夏"))
      .andExpect(jsonPath("$.data.merchantAccounts[0].username").value("13800000002"))
      .andExpect(jsonPath("$.data.merchantAccounts[0].nickname").value("巷口川味研究所"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    Assertions.assertFalse(response.contains("password"));
    Assertions.assertFalse(response.contains("平台管理员"));
  }

  @Test
  void merchantRegisterCreatesMerchantAdminAndCanAccessWorkbench() throws Exception {
    String phone = "shop-" + System.nanoTime();
    String token = registerMerchant(phone, "abc123456", "新店主");

    mvc.perform(get("/api/v1/merchant-admin/orders")
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200));
  }

  @Test
  void merchantCanUpdateNicknameAndPublicStoreName() throws Exception {
    String token = login("13800000004", "abc123456");
    mvc.perform(post("/api/v1/merchant-admin/profile")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"nickname\":\"LightFood Pro\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.user.nickname").value("LightFood Pro"))
      .andExpect(jsonPath("$.data.merchant.name").value("LightFood Pro"));

    String response = mvc.perform(get("/api/v1/merchants").param("keyword", "LightFood Pro"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andReturn()
      .getResponse()
      .getContentAsString();
    JsonNode records = objectMapper.readTree(response).path("data").path("records");
    Assertions.assertEquals("LightFood Pro", records.get(0).path("name").asText());

    mvc.perform(get("/api/v1/merchants/3"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.merchant.name").value("LightFood Pro"));
  }

  @Test
  void merchantProfilePutPersistsNicknameAcrossLoginAndDiscovery() throws Exception {
    String token = login("13800000002", "abc123456");
    mvc.perform(put("/api/v1/merchant-admin/profile")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"nickname\":\"巷口川菜馆\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.user.nickname").value("巷口川菜馆"))
      .andExpect(jsonPath("$.data.merchant.name").value("巷口川菜馆"));

    String loginResponse = mvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"phone\":\"13800000002\",\"password\":\"abc123456\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.user.nickname").value("巷口川菜馆"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    Assertions.assertFalse(objectMapper.readTree(loginResponse).path("data").path("token").asText().isBlank());

    mvc.perform(get("/api/v1/merchants").param("keyword", "巷口川菜馆"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.records[0].name").value("巷口川菜馆"));

    mvc.perform(get("/api/v1/merchants/1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.merchant.name").value("巷口川菜馆"));
  }

  @Test
  void merchantAssistantEndpointReturnsReplyForMerchantAdmin() throws Exception {
    String token = login("13800000002", "abc123456");

    mvc.perform(post("/api/v1/merchant-admin/assistant/ask")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"顾客问能不能少辣怎么回复\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.answer").isNotEmpty());
  }

  private String login(String phone, String password) throws Exception {
    String body = """
      {"phone":"%s","password":"%s"}
      """.formatted(phone, password);
    String response = mvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("token").asText();
  }

  private String registerMerchant(String phone, String password, String nickname) throws Exception {
    String body = """
      {"phone":"%s","password":"%s","nickname":"%s"}
      """.formatted(phone, password, nickname);
    String response = mvc.perform(post("/api/v1/auth/register/merchant")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.user.role").value("MERCHANT_ADMIN"))
      .andExpect(jsonPath("$.data.user.merchantId").isNumber())
      .andReturn()
      .getResponse()
      .getContentAsString();
    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("token").asText();
  }

  private JsonNode registerUser(String nickname) throws Exception {
    String phone = "api-user-" + System.nanoTime();
    String body = "{\"phone\":\"%s\",\"password\":\"abc123456\",\"nickname\":\"%s\"}"
      .formatted(phone, nickname);
    String response = mvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.user.role").value("USER"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    return objectMapper.readTree(response).path("data");
  }

  private long createAddress(String token, String detail) throws Exception {
    String response = mvc.perform(post("/api/v1/user/addresses")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"contactName\":\"下单 API 用户\",\"phone\":\"13900000001\",\"detail\":\"%s\",\"defaultAddress\":true}".formatted(detail)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.defaultAddress").value(true))
      .andReturn()
      .getResponse()
      .getContentAsString();
    return objectMapper.readTree(response).path("data").path("id").asLong();
  }

  private JsonNode getMerchantRecords(String path) throws Exception {
    String response = mvc.perform(get(path))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andReturn()
      .getResponse()
      .getContentAsString();
    return objectMapper.readTree(response).path("data").path("records");
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
