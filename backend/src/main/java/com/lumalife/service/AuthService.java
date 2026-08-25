package com.lumalife.service;

import com.lumalife.domain.Enums.UserRole;
import com.lumalife.domain.Models.Address;
import com.lumalife.domain.Models.User;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final DemoStore store;

  public AuthService(DemoStore store) {
    this.store = store;
  }

  public Map<String, Object> login(String phone, String password) {
    return store.login(phone, password);
  }

  public Map<String, Object> register(String phone, String password, String nickname, UserRole role) {
    return store.register(phone, password, nickname, role);
  }

  public User current(String phone) {
    return store.current(phone);
  }

  public Map<String, Object> safeUser(User user) {
    return store.safeUser(user);
  }

  public Map<String, Object> updateProfile(User user, String nickname, String avatarUrl) {
    return store.updateProfile(user, nickname, avatarUrl);
  }

  public List<Address> addresses(User user) {
    return store.addresses(user);
  }

  public Address saveAddress(User user, Long id, String contactName, String phone, String detail, boolean defaultAddress) {
    return store.saveAddress(user, id, contactName, phone, detail, defaultAddress);
  }

  public Address setDefaultAddress(User user, long id) {
    return store.setDefaultAddress(user, id);
  }

  public void deleteAddress(User user, long id) {
    store.deleteAddress(user, id);
  }
}
