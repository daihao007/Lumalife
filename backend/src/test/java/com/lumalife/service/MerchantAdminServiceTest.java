package com.lumalife.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumalife.domain.Enums.UserRole;
import com.lumalife.domain.Models.User;
import com.lumalife.service.boundary.IdentityServicePort;
import com.lumalife.service.boundary.MerchantServicePort;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MerchantAdminServiceTest {
  private final User admin = new User(2, "13800000002", "", "旧昵称", "avatar", UserRole.MERCHANT_ADMIN, 1L);

  @Test
  void updatesMerchantAndIdentityProfilesTogether() {
    MerchantServicePort merchant = mock(MerchantServicePort.class);
    IdentityServicePort identity = mock(IdentityServicePort.class);
    when(merchant.updateMerchantNickname(admin, "新昵称")).thenReturn(Map.of("merchant", Map.of("name", "新昵称")));

    new MerchantAdminService(merchant, identity).updateMerchantNickname(admin, "新昵称");

    InOrder ordered = inOrder(merchant, identity);
    ordered.verify(merchant).updateMerchantNickname(admin, "新昵称");
    ordered.verify(identity).updateProfile(admin, "新昵称", "avatar");
  }

  @Test
  void rollsBackMerchantNicknameWhenIdentityUpdateFails() {
    MerchantServicePort merchant = mock(MerchantServicePort.class);
    IdentityServicePort identity = mock(IdentityServicePort.class);
    when(merchant.updateMerchantNickname(admin, "新昵称")).thenReturn(Map.of());
    when(identity.updateProfile(admin, "新昵称", "avatar")).thenThrow(new IllegalStateException("identity unavailable"));

    assertThatThrownBy(() -> new MerchantAdminService(merchant, identity).updateMerchantNickname(admin, "新昵称"))
      .isInstanceOf(IllegalStateException.class);
    verify(merchant).updateMerchantNickname(admin, "旧昵称");
  }
}
