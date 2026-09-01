package com.lumalife.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
  "spring.profiles.active=monolith,remote",
  "lumalife.state-file=",
  "lumalife.migration.identity.enabled=true",
  "lumalife.migration.identity.backfill-completed=true",
  "lumalife.services.identity.base-url=http://127.0.0.1:1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IdentityAvailabilityIntegrationTest {
  @Autowired
  private MockMvc mvc;

  @Test
  void identityOutageRemovesGatewayReadiness() throws Exception {
    mvc.perform(get("/actuator/health/readiness"))
      .andExpect(status().isServiceUnavailable())
      .andExpect(jsonPath("$.status").value("DOWN"));
  }

  @Test
  void identityOutageUsesServiceUnavailableHttpStatus() throws Exception {
    mvc.perform(post("/api/v1/auth/login")
        .contentType("application/json")
        .content("{\"phone\":\"13800000001\",\"password\":\"abc123456\"}"))
      .andExpect(status().isServiceUnavailable())
      .andExpect(jsonPath("$.code").value(50300))
      .andExpect(jsonPath("$.reason").value("IDENTITY_SERVICE_UNAVAILABLE"));
  }
}
