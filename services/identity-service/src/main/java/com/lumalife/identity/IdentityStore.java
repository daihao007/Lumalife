package com.lumalife.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Identity-owned store used by the first migration slice. No catalog/order data is kept here. */
@Service
public class IdentityStore {
  public record User(long id, String phone, String passwordHash, String nickname, String avatarUrl,
                     String role, Long merchantId) {}
  public record Address(long id, long userId, String contactName, String phone, String detail,
                        boolean defaultAddress) {}

  private final PasswordEncoder passwordEncoder;
  private final AtomicLong ids = new AtomicLong(1000);
  private final Map<String, User> users = new LinkedHashMap<>();
  private final Map<String, Long> tokens = new LinkedHashMap<>();
  private final Map<Long, List<Address>> addresses = new LinkedHashMap<>();

  public IdentityStore(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
    seed(6, "13800000000", "admin123456", "平台管理员", "PLATFORM_ADMIN", null);
    seed(1, "13800000001", "abc123456", "林夏", "USER", null);
    seed(2, "13800000002", "abc123456", "巷口川味研究所", "MERCHANT_ADMIN", 1L);
    seed(3, "13800000003", "abc123456", "晨雾咖啡局", "MERCHANT_ADMIN", 2L);
    addresses.put(1L, new ArrayList<>(List.of(
      new Address(2101, 1, "林夏", "13800000001", "梧桐路 18 号 2 单元 601", true))));
  }

  public synchronized Map<String, Object> login(String phone, String password) {
    User user = byPhone(phone);
    if (!passwordEncoder.matches(password == null ? "" : password, user.passwordHash())) {
      throw new IdentityException(401, "密码错误");
    }
    String token = UUID.randomUUID().toString();
    tokens.put(token, user.id());
    return Map.of("token", token, "user", safe(user));
  }

  public synchronized User byPhone(String phone) {
    User user = users.get(phone == null ? "" : phone.trim());
    if (user == null) throw new IdentityException(401, "用户名不存在");
    return user;
  }

  public synchronized User byToken(String token) {
    Long id = tokens.get(token);
    if (id == null) throw new IdentityException(401, "登录状态已失效");
    return users.values().stream().filter(item -> item.id() == id).findFirst()
      .orElseThrow(() -> new IdentityException(401, "登录状态已失效"));
  }

  public synchronized Map<String, Object> register(String phone, String password, String nickname, String role) {
    String normalized = phone == null ? "" : phone.trim();
    if (normalized.isBlank() || password == null || password.length() < 6) {
      throw new IdentityException(400, "用户名和至少 6 位密码不能为空");
    }
    if (users.containsKey(normalized)) throw new IdentityException(409, "用户名已注册");
    User user = seed(ids.incrementAndGet(), normalized, password, nickname == null || nickname.isBlank() ? "新用户" : nickname.trim(),
      "MERCHANT_ADMIN".equals(role) ? "MERCHANT_ADMIN" : "USER", null);
    return login(normalized, password);
  }

  public synchronized User updateProfile(long id, String nickname, String avatarUrl) {
    User old = byId(id);
    User updated = new User(old.id(), old.phone(), old.passwordHash(),
      nickname == null || nickname.isBlank() ? old.nickname() : nickname.trim(),
      avatarUrl == null ? old.avatarUrl() : avatarUrl.trim(), old.role(), old.merchantId());
    users.put(updated.phone(), updated);
    return updated;
  }

  public synchronized List<Address> addresses(long userId) {
    return new ArrayList<>(addresses.getOrDefault(userId, List.of()));
  }

  public synchronized Address saveAddress(long userId, Long id, String contactName, String phone,
                                          String detail, boolean defaultAddress) {
    List<Address> list = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());
    if (id == null || id <= 0) id = ids.incrementAndGet();
    if (defaultAddress || list.isEmpty()) list.replaceAll(a -> new Address(a.id(), a.userId(), a.contactName(), a.phone(), a.detail(), false));
    Address address = new Address(id, userId, contactName, phone, detail, defaultAddress || list.isEmpty());
    long addressId = id;
    list.removeIf(item -> item.id() == addressId);
    list.add(address);
    return address;
  }

  public synchronized Address setDefault(long userId, long id) {
    List<Address> list = addresses(userId);
    Address found = list.stream().filter(item -> item.id() == id).findFirst()
      .orElseThrow(() -> new IdentityException(404, "地址不存在"));
    List<Address> owned = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());
    owned.replaceAll(item -> new Address(item.id(), item.userId(), item.contactName(), item.phone(), item.detail(), item.id() == id));
    return owned.stream().filter(item -> item.id() == id).findFirst().orElse(found);
  }

  public synchronized void deleteAddress(long userId, long id) {
    List<Address> list = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());
    list.removeIf(item -> item.id() == id);
    if (!list.isEmpty() && list.stream().noneMatch(Address::defaultAddress)) {
      Address first = list.get(0);
      list.set(0, new Address(first.id(), first.userId(), first.contactName(), first.phone(), first.detail(), true));
    }
  }

  public synchronized User byId(long id) {
    return users.values().stream().filter(item -> item.id() == id).findFirst()
      .orElseThrow(() -> new IdentityException(404, "用户不存在"));
  }

  public Map<String, Object> safe(User user) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", user.id()); data.put("phone", user.phone()); data.put("nickname", user.nickname());
    data.put("avatarUrl", user.avatarUrl()); data.put("role", user.role()); data.put("merchantId", user.merchantId());
    return data;
  }

  private User seed(long id, String phone, String password, String nickname, String role, Long merchantId) {
    User user = new User(id, phone, passwordEncoder.encode(password), nickname, "", role, merchantId);
    users.put(phone, user);
    return user;
  }

  public static class IdentityException extends RuntimeException {
    private final int status;
    public IdentityException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
  }
}
