package com.lumalife.service.boundary;

import com.lumalife.domain.Models.Address;
import com.lumalife.domain.Models.User;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Identity service boundary used by HTTP adapters and other in-process modules.
 * The port deliberately exposes identity/address operations only; merchant,
 * cart and order data must not be reached through this dependency.
 */
public interface IdentityServicePort {
  Optional<User> userByToken(String token);

  User userByPhone(String phone);

  User current(String phone);

  Map<String, Object> login(String phone, String password);

  Map<String, Object> registerUser(String phone, String password, String nickname);

  Map<String, Object> registerMerchant(String phone, String password, String nickname);

  Map<String, Object> safeUser(User user);

  Map<String, Object> updateProfile(User user, String nickname, String avatarUrl);

  List<Address> addresses(User user);

  Address saveAddress(User user, Long id, String contactName, String phone, String detail, boolean defaultAddress);

  Address setDefaultAddress(User user, long id);

  void deleteAddress(User user, long id);
}
