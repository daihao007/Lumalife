package com.lumalife.service;

import com.lumalife.domain.Enums.UserRole;
import com.lumalife.domain.Models.Address;
import com.lumalife.domain.Models.User;
import java.util.List;
import java.util.Map;
import com.lumalife.service.boundary.IdentityServicePort;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final IdentityServicePort identity;

  public AuthService(IdentityServicePort identity) {
    this.identity = identity;
  }

  public Map<String, Object> login(String phone, String password) {
    return identity.login(phone, password);
  }

  public Map<String, Object> register(String phone, String password, String nickname, UserRole role) {
    if (role != null) {
      throw new com.lumalife.common.BusinessException(40000, "普通注册不允许指定角色", "ROLE_NOT_ALLOWED");
    }
    return identity.registerUser(phone, password, nickname);
  }

  public Map<String, Object> registerMerchant(String phone, String password, String nickname) {
    return identity.registerMerchant(phone, password, nickname);
  }

  public User current(String phone) {
    return identity.current(phone);
  }

  public Map<String, Object> safeUser(User user) {
    return identity.safeUser(user);
  }

  public Map<String, Object> updateProfile(User user, String nickname, String avatarUrl) {
    return identity.updateProfile(user, nickname, avatarUrl);
  }

  public List<Address> addresses(User user) {
    return identity.addresses(user);
  }

  public Address saveAddress(User user, Long id, String contactName, String phone, String detail, boolean defaultAddress) {
    return identity.saveAddress(user, id, contactName, phone, detail, defaultAddress);
  }

  public Address setDefaultAddress(User user, long id) {
    return identity.setDefaultAddress(user, id);
  }

  public void deleteAddress(User user, long id) {
    identity.deleteAddress(user, id);
  }
}
