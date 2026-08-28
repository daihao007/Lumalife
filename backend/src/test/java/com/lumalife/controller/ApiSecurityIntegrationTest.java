package com.lumalife.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(properties = "lumalife.state-file=")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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

    mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
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
    String merchantToken = login("13800000002", "abc123456");
    int stockBeforePayment = merchantProductStock(merchantToken, 1001);

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

    String secondPayment = mvc.perform(post("/api/v1/payments")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"orderId\":%d,\"clientRequestId\":\"api-payment-%d\"}".formatted(orderId, orderId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(objectMapper.readTree(firstPayment).path("data").path("id").asLong()))
      .andExpect(jsonPath("$.data.status").value("PAID"))
      .andReturn()
      .getResponse()
      .getContentAsString();

    JsonNode firstPaymentData = objectMapper.readTree(firstPayment).path("data");
    JsonNode secondPaymentData = objectMapper.readTree(secondPayment).path("data");
    Assertions.assertEquals(firstPaymentData.path("statusTimeline").path("PAID").asText(),
      secondPaymentData.path("statusTimeline").path("PAID").asText());
    Assertions.assertEquals(2, firstPaymentData.path("statusTimeline").size());
    Assertions.assertEquals(2, secondPaymentData.path("statusTimeline").size());
    Assertions.assertEquals(stockBeforePayment - 1, merchantProductStock(merchantToken, 1001));

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
  void anonymousUserCannotAccessCurrentUserEndpoint() throws Exception {
    mvc.perform(get("/api/v1/auth/me"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void nonUserRolesCannotAccessUserScopedApis() throws Exception {
    List<String> privilegedTokens = List.of(
      bearer(login("13800000002", "abc123456")),
      bearer(login("13800000000", "admin123456")));

    for (String token : privilegedTokens) {
      mvc.perform(get("/api/v1/user/addresses").header("Authorization", token))
        .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/cart").header("Authorization", token))
        .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/orders").header("Authorization", token))
        .andExpect(status().isForbidden());
      mvc.perform(post("/api/v1/payments")
          .header("Authorization", token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"orderId\":1,\"clientRequestId\":\"privileged-payment\"}"))
        .andExpect(status().isForbidden());
    }
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
    String actuatorResponse = mvc.perform(get("/actuator/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").isNotEmpty())
      .andReturn()
      .getResponse()
      .getContentAsString();
    String actuatorStatus = objectMapper.readTree(actuatorResponse).path("status").asText();

    String response = mvc.perform(get("/api/v1/admin/metrics")
        .header("Authorization", bearer(login("13800000000", "admin123456"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.health.status").value(actuatorStatus))
      .andExpect(jsonPath("$.data.health.source").value("/actuator/health"))
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
  void publicRegistrationCannotChoosePrivilegedRole() throws Exception {
    String phone = "role-injection-" + System.nanoTime();

    mvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"phone\":\"%s\",\"password\":\"abc123456\",\"nickname\":\"普通用户\",\"role\":\"MERCHANT_ADMIN\"}".formatted(phone)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(40000))
      .andExpect(jsonPath("$.reason").value("ROLE_NOT_ALLOWED"))
      .andExpect(jsonPath("$.data").value(nullValue()));
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
  void groupBuyApiGeneratesCouponCanBeReviewedAfterUseAndMerchantVerifiesOnlyOnceForOwnStore() throws Exception {
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

    mvc.perform(post("/api/v1/reviews")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"orderId\":%d,\"score\":5,\"tasteScore\":5,\"serviceScore\":5,\"content\":\"团购核销后评价\"}".formatted(orderId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.orderId").value(orderId));

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

  @Test
  void groupBuyApiRejectsInvalidQuantityAndUnknownCoupon() throws Exception {
    String userToken = login("13800000001", "abc123456");
    String merchantToken = login("13800000002", "abc123456");

    mvc.perform(post("/api/v1/orders/group-buy")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"dealId\":1,\"quantity\":0}"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(40900));

    mvc.perform(post("/api/v1/merchant-admin/coupons/verify")
        .header("Authorization", bearer(merchantToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"000000000000\"}"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value(40400));
  }

  @Test
  void reviewApiRejectsInvalidScoreAndDuplicateSubmission() throws Exception {
    String userToken = login("13800000001", "abc123456");
    String merchantToken = login("13800000002", "abc123456");

    mvc.perform(post("/api/v1/cart/items")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"productId\":1002,\"quantity\":1}"))
      .andExpect(status().isOk());
    String orderResponse = mvc.perform(post("/api/v1/orders/delivery")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"addressId\":101}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    long orderId = objectMapper.readTree(orderResponse).path("data").get(0).path("id").asLong();
    mvc.perform(post("/api/v1/payments")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"orderId\":%d,\"clientRequestId\":\"api-review-%d\"}".formatted(orderId, orderId)))
      .andExpect(status().isOk());
    transition(merchantToken, orderId, "ACCEPTED").andExpect(status().isOk());
    transition(merchantToken, orderId, "DELIVERING").andExpect(status().isOk());
    transition(merchantToken, orderId, "COMPLETED").andExpect(status().isOk());
    mvc.perform(post("/api/v1/orders/{id}/receive", orderId)
        .header("Authorization", bearer(userToken)))
      .andExpect(status().isOk());

    mvc.perform(post("/api/v1/reviews")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"orderId\":%d,\"score\":6,\"tasteScore\":5,\"serviceScore\":5,\"content\":\"非法评分\"}".formatted(orderId)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(40000));
    String reviewBody = "{\"orderId\":%d,\"score\":5,\"tasteScore\":5,\"serviceScore\":4,\"content\":\"API 评价\"}".formatted(orderId);
    mvc.perform(post("/api/v1/reviews")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(reviewBody))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.orderId").value(orderId));
    mvc.perform(post("/api/v1/reviews")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(reviewBody))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(40900));
  }

  @Test
  void merchantApiRejectsCrossStoreOrderAndGroupDealMutation() throws Exception {
    String userToken = login("13800000001", "abc123456");
    String merchantToken = login("13800000002", "abc123456");

    mvc.perform(post("/api/v1/cart/items")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"productId\":1004,\"quantity\":1}"))
      .andExpect(status().isOk());
    String orderResponse = mvc.perform(post("/api/v1/orders/delivery")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"addressId\":101}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    long orderId = objectMapper.readTree(orderResponse).path("data").get(0).path("id").asLong();
    mvc.perform(post("/api/v1/payments")
        .header("Authorization", bearer(userToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"orderId\":%d,\"clientRequestId\":\"api-cross-store-%d\"}".formatted(orderId, orderId)))
      .andExpect(status().isOk());

    transition(merchantToken, orderId, "ACCEPTED")
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value(40300));
    mvc.perform(post("/api/v1/merchant-admin/group-deals")
        .header("Authorization", bearer(merchantToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"id\":2,\"title\":\"越权套餐\",\"description\":\"不应修改\",\"priceCent\":4990,\"stock\":1,\"active\":true}"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value(40300));
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

  private int merchantProductStock(String token, long productId) throws Exception {
    String response = mvc.perform(get("/api/v1/merchant-admin/products")
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    JsonNode products = objectMapper.readTree(response).path("data");
    for (JsonNode product : products) {
      if (product.path("id").asLong() == productId) return product.path("stock").asInt();
    }
    throw new AssertionError("Product " + productId + " was not returned for merchant");
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
