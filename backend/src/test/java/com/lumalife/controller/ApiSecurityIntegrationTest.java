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
      .andExpect(jsonPath("$.data.overview.users").value(1))
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

  @Test
  void userOnlyApisRejectMerchantAndPlatformAdminTokens() throws Exception {
    String merchantToken = login("13800000002", "abc123456");
    String platformToken = login("13800000000", "admin123456");

    mvc.perform(get("/api/v1/cart/detail")
        .header("Authorization", bearer(merchantToken)))
      .andExpect(status().isForbidden());

    mvc.perform(post("/api/v1/orders/group-buy")
        .header("Authorization", bearer(platformToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"dealId\":1,\"quantity\":1}"))
      .andExpect(status().isForbidden());

    mvc.perform(get("/api/v1/user/addresses")
        .header("Authorization", bearer(merchantToken)))
      .andExpect(status().isForbidden());
  }

  @Test
  void userDeliveryOrderApiCoversCartPaymentMerchantWorkflowAndReview() throws Exception {
    String userToken = login("13800000001", "abc123456");
    String merchantToken = login("13800000002", "abc123456");

    mvc.perform(post("/api/v1/cart/items")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"productId\":1001,\"quantity\":1}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].productId").value(1001))
      .andExpect(jsonPath("$.data[0].quantity").value(1));

    mvc.perform(get("/api/v1/cart/detail")
        .header("Authorization", bearer(userToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].merchantName").isNotEmpty())
      .andExpect(jsonPath("$.data[0].subtotalCent").isNumber());

    JsonNode createdOrders = data(postJson("/api/v1/orders/delivery", userToken, "{\"addressId\":101}"));
    long orderId = createdOrders.get(0).path("id").asLong();
    Assertions.assertTrue(orderId > 0);
    Assertions.assertEquals("PENDING_PAYMENT", createdOrders.get(0).path("status").asText());

    JsonNode paid = data(postJson("/api/v1/payments", userToken,
      "{\"orderId\":%d,\"clientRequestId\":\"api-delivery-%d\"}".formatted(orderId, System.nanoTime())));
    Assertions.assertEquals("PAID", paid.path("status").asText());

    transition(merchantToken, orderId, "ACCEPTED")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    transition(merchantToken, orderId, "DELIVERING")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("DELIVERING"));
    transition(merchantToken, orderId, "COMPLETED")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("COMPLETED"));

    mvc.perform(post("/api/v1/orders/%d/receive".formatted(orderId))
        .header("Authorization", bearer(userToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("RECEIVED"));

    mvc.perform(post("/api/v1/reviews")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"orderId":%d,"score":5,"tasteScore":5,"serviceScore":5,"content":"接口闭环体验很好"}
          """.formatted(orderId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.orderId").value(orderId))
      .andExpect(jsonPath("$.data.content").value("接口闭环体验很好"));

    mvc.perform(get("/api/v1/merchant-admin/reviews")
        .header("Authorization", bearer(merchantToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[?(@.orderId == %d)]".formatted(orderId)).isNotEmpty());
  }

  @Test
  void groupBuyApiGeneratesCouponAndMerchantVerifiesOnlyOnceForOwnStore() throws Exception {
    String userToken = login("13800000001", "abc123456");
    String ownerMerchantToken = login("13800000002", "abc123456");
    String otherMerchantToken = login("13800000003", "abc123456");

    JsonNode paid = paidGroupOrder(userToken, 1, "api-group-owner-" + System.nanoTime());
    long orderId = paid.path("id").asLong();
    String couponCode = paid.path("couponCode").asText();
    Assertions.assertEquals(12, couponCode.length());

    transition(ownerMerchantToken, orderId, "ACCEPTED")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(40900))
      .andExpect(jsonPath("$.message").value("团购订单只能通过券码核销"));

    mvc.perform(post("/api/v1/merchant-admin/coupons/verify")
        .header("Authorization", bearer(ownerMerchantToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"%s\"}".formatted(couponCode)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(orderId))
      .andExpect(jsonPath("$.data.status").value("USED"));

    mvc.perform(post("/api/v1/merchant-admin/coupons/verify")
        .header("Authorization", bearer(ownerMerchantToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"%s\"}".formatted(couponCode)))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(40900));

    JsonNode otherPaid = paidGroupOrder(userToken, 1, "api-group-other-" + System.nanoTime());
    String otherCouponCode = otherPaid.path("couponCode").asText();
    mvc.perform(post("/api/v1/merchant-admin/coupons/verify")
        .header("Authorization", bearer(otherMerchantToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"%s\"}".formatted(otherCouponCode)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value(40300));
  }

  @Test
  void merchantAdminCanMaintainProductAndGroupDealThroughApi() throws Exception {
    String merchantToken = login("13800000003", "abc123456");
    String productName = "api-product-" + System.nanoTime();
    String dealTitle = "api-deal-" + System.nanoTime();

    JsonNode product = data(postJson("/api/v1/merchant-admin/products", merchantToken, """
      {"name":"%s","description":"api product","priceCent":1880,"stock":8,"listed":true}
      """.formatted(productName)));
    long productId = product.path("id").asLong();
    Assertions.assertEquals(productName, product.path("name").asText());
    Assertions.assertTrue(product.path("listed").asBoolean());

    mvc.perform(post("/api/v1/merchant-admin/products/%d/toggle".formatted(productId))
        .header("Authorization", bearer(merchantToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(productId))
      .andExpect(jsonPath("$.data.listed").value(false));

    mvc.perform(post("/api/v1/merchant-admin/products")
        .header("Authorization", bearer(merchantToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"bad\",\"description\":\"bad\",\"priceCent\":0,\"stock\":1,\"listed\":true}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(40000));

    JsonNode deal = data(postJson("/api/v1/merchant-admin/group-deals", merchantToken, """
      {"title":"%s","description":"api deal","priceCent":4990,"stock":6,"active":true}
      """.formatted(dealTitle)));
    long dealId = deal.path("id").asLong();
    Assertions.assertEquals(dealTitle, deal.path("title").asText());
    Assertions.assertTrue(deal.path("active").asBoolean());

    mvc.perform(post("/api/v1/merchant-admin/group-deals/%d/toggle".formatted(dealId))
        .header("Authorization", bearer(merchantToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(dealId))
      .andExpect(jsonPath("$.data.active").value(false));
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

  private JsonNode getMerchantRecords(String path) throws Exception {
    String response = mvc.perform(get(path))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andReturn()
      .getResponse()
      .getContentAsString();
    return objectMapper.readTree(response).path("data").path("records");
  }

  private org.springframework.test.web.servlet.ResultActions transition(String token, long orderId, String next) throws Exception {
    return mvc.perform(post("/api/v1/merchant-admin/orders/%d/transition".formatted(orderId))
      .header("Authorization", bearer(token))
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"next\":\"%s\"}".formatted(next)));
  }

  private String postJson(String path, String token, String body) throws Exception {
    return mvc.perform(post(path)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andReturn()
      .getResponse()
      .getContentAsString();
  }

  private JsonNode data(String response) throws Exception {
    return objectMapper.readTree(response).path("data");
  }

  private JsonNode paidGroupOrder(String userToken, long dealId, String clientRequestId) throws Exception {
    JsonNode order = data(postJson("/api/v1/orders/group-buy", userToken,
      "{\"dealId\":%d,\"quantity\":1}".formatted(dealId)));
    return data(postJson("/api/v1/payments", userToken,
      "{\"orderId\":%d,\"clientRequestId\":\"%s\"}".formatted(order.path("id").asLong(), clientRequestId)));
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
