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

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
