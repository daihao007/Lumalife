package com.lumalife.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Executable v1 contract examples for the identity/order service boundaries. */
@SpringBootTest(properties = "lumalife.state-file=")
@AutoConfigureMockMvc
class ServiceBoundaryContractTest {
  @Autowired
  private MockMvc mvc;

  @Test
  void anonymousCurrentUserReturnsTheSharedErrorEnvelope() throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("X-Request-Id", "contract-anon-001"))
      .andExpect(status().isUnauthorized())
      .andExpect(header().string("X-Request-Id", "contract-anon-001"))
      .andExpect(jsonPath("$.code").value(40100))
      .andExpect(jsonPath("$.data").value(nullValue()))
      .andExpect(jsonPath("$.reason").value("TOKEN_INVALID"))
      .andExpect(jsonPath("$.requestId").value("contract-anon-001"));
  }

  @Test
  void publicRegistrationCannotCrossTheIdentityBoundaryWithAPrivilegedRole() throws Exception {
    mvc.perform(post("/api/v1/auth/register")
        .header("X-Request-Id", "contract-role-001")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"phone\":\"contract-user-001\",\"password\":\"abc123456\",\"nickname\":\"边界测试\",\"role\":\"MERCHANT_ADMIN\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(header().string("X-Request-Id", "contract-role-001"))
      .andExpect(jsonPath("$.code").value(40000))
      .andExpect(jsonPath("$.reason").value("ROLE_NOT_ALLOWED"))
      .andExpect(jsonPath("$.data").value(nullValue()));
  }

  @Test
  void publicRegistrationAcceptsTheUserRoleSentByExistingClients() throws Exception {
    mvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"phone\":\"contract-user-role-user-001\",\"password\":\"abc123456\",\"nickname\":\"普通用户\",\"role\":\"USER\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(200))
      .andExpect(jsonPath("$.data.user.role").value("USER"))
      .andExpect(jsonPath("$.data.token").isNotEmpty());
  }

  @Test
  void invalidRoleReturnsBadRequestInsteadOfInternalServerError() throws Exception {
    mvc.perform(post("/api/v1/auth/register")
        .header("X-Request-Id", "contract-invalid-role-001")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"phone\":\"contract-invalid-role-001\",\"password\":\"abc123456\",\"nickname\":\"非法角色\",\"role\":\"NOT_A_ROLE\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(header().string("X-Request-Id", "contract-invalid-role-001"))
      .andExpect(jsonPath("$.code").value(40000))
      .andExpect(jsonPath("$.message").value("请求体格式错误"))
      .andExpect(jsonPath("$.data").value(nullValue()))
      .andExpect(jsonPath("$.requestId").value("contract-invalid-role-001"))
      .andExpect(jsonPath("$.reason").value("VALIDATION_FAILED"));
  }
}
