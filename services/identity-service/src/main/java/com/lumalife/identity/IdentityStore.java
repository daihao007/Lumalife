package com.lumalife.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path stateFile;
  private final AtomicLong ids = new AtomicLong(1000);
  private final AtomicLong merchantIds = new AtomicLong(2);
  private final Map<String, User> users = new LinkedHashMap<>();
  private final Map<String, Long> tokens = new LinkedHashMap<>();
  private final Map<Long, List<Address>> addresses = new LinkedHashMap<>();

  @Autowired
  public IdentityStore(PasswordEncoder passwordEncoder,
                       @Value("${lumalife.identity.state-file:./data/identity-state.json}") String stateFile) {
    this(passwordEncoder, stateFile == null || stateFile.isBlank() ? null : Path.of(stateFile));
  }

  public IdentityStore(PasswordEncoder passwordEncoder) {
    this(passwordEncoder, (Path) null);
  }

  IdentityStore(PasswordEncoder passwordEncoder, Path stateFile) {
    this.passwordEncoder = passwordEncoder;
    this.stateFile = stateFile;
    seed(6, "13800000000", "admin123456", "平台管理员", "PLATFORM_ADMIN", null);
    seed(1, "13800000001", "abc123456", "林夏", "USER", null);
    seed(2, "13800000002", "abc123456", "巷口川味研究所", "MERCHANT_ADMIN", 1L);
    seed(3, "13800000003", "abc123456", "晨雾咖啡局", "MERCHANT_ADMIN", 2L);
    seed(4, "13800000004", "abc123456", "绿盒轻食", "MERCHANT_ADMIN", 3L);
    seed(5, "13800000005", "abc123456", "栗香烘焙室", "MERCHANT_ADMIN", 4L);
    addresses.put(1L, new ArrayList<>(List.of(
      new Address(2101, 1, "林夏", "13800000001", "梧桐路 18 号 2 单元 601", true))));
    loadPersistentState();
    ensureDemoMerchantAccounts();
    if (stateFile != null && !Files.exists(stateFile)) persistState();
  }

  public synchronized Map<String, Object> login(String phone, String password) {
    User user = byPhone(phone);
    if (!passwordEncoder.matches(password == null ? "" : password, user.passwordHash())) {
      throw new IdentityException(401, "密码错误");
    }
    String token = UUID.randomUUID().toString();
    tokens.put(token, user.id());
    persistState();
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
    boolean merchantAdmin = "MERCHANT_ADMIN".equals(role);
    User user = seed(ids.incrementAndGet(), normalized, password, nickname == null || nickname.isBlank() ? "新用户" : nickname.trim(),
      merchantAdmin ? "MERCHANT_ADMIN" : "USER", merchantAdmin ? merchantIds.incrementAndGet() : null);
    return login(normalized, password);
  }

  public synchronized User updateProfile(long id, String nickname, String avatarUrl) {
    User old = byId(id);
    User updated = new User(old.id(), old.phone(), old.passwordHash(),
      nickname == null || nickname.isBlank() ? old.nickname() : nickname.trim(),
      avatarUrl == null ? old.avatarUrl() : avatarUrl.trim(), old.role(), old.merchantId());
    users.put(updated.phone(), updated);
    persistState();
    return updated;
  }

  public synchronized void requireActor(long targetUserId, long actorUserId) {
    if (targetUserId != actorUserId) throw new IdentityException(403, "不能操作其他用户的数据");
    byId(targetUserId);
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
    persistState();
    return address;
  }

  public synchronized Address setDefault(long userId, long id) {
    List<Address> list = addresses(userId);
    Address found = list.stream().filter(item -> item.id() == id).findFirst()
      .orElseThrow(() -> new IdentityException(404, "地址不存在"));
    List<Address> owned = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());
    owned.replaceAll(item -> new Address(item.id(), item.userId(), item.contactName(), item.phone(), item.detail(), item.id() == id));
    persistState();
    return owned.stream().filter(item -> item.id() == id).findFirst().orElse(found);
  }

  public synchronized void deleteAddress(long userId, long id) {
    List<Address> list = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());
    list.removeIf(item -> item.id() == id);
    if (!list.isEmpty() && list.stream().noneMatch(Address::defaultAddress)) {
      Address first = list.get(0);
      list.set(0, new Address(first.id(), first.userId(), first.contactName(), first.phone(), first.detail(), true));
    }
    persistState();
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
    ids.updateAndGet(current -> Math.max(current, id));
    if (merchantId != null) merchantIds.updateAndGet(current -> Math.max(current, merchantId));
    return user;
  }

  public static class IdentityException extends RuntimeException {
    private final int status;
    public IdentityException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
    public String reason() {
      return switch (status) {
        case 400 -> "VALIDATION_FAILED";
        case 401 -> "AUTHENTICATION_FAILED";
        case 403 -> "FORBIDDEN";
        case 404 -> "NOT_FOUND";
        case 409 -> "CONFLICT";
        default -> "IDENTITY_ERROR";
      };
    }
  }

  private void loadPersistentState() {
    if (stateFile == null || !Files.exists(stateFile)) return;
    try {
      PersistentState state = objectMapper.readValue(stateFile.toFile(), PersistentState.class);
      users.clear();
      if (state.users() != null) state.users().forEach(user -> users.put(user.phone(), user));
      addresses.clear();
      if (state.addresses() != null) state.addresses().forEach((id, values) -> addresses.put(id, new ArrayList<>(values)));
      tokens.clear();
      if (state.tokens() != null) tokens.putAll(state.tokens());
      users.values().forEach(user -> {
        ids.updateAndGet(current -> Math.max(current, user.id()));
        if (user.merchantId() != null) merchantIds.updateAndGet(current -> Math.max(current, user.merchantId()));
      });
      addresses.values().stream().flatMap(List::stream).forEach(address -> ids.updateAndGet(current -> Math.max(current, address.id())));
    } catch (IOException error) {
      throw new IllegalStateException("Failed to load identity state from " + stateFile, error);
    }
  }

  /** Add newly introduced demo accounts without overwriting real accounts in a persistent state file. */
  private void ensureDemoMerchantAccounts() {
    boolean changed = seedIfMissing("13800000004", 4, "绿盒轻食", 3L)
      | seedIfMissing("13800000005", 5, "栗香烘焙室", 4L);
    if (changed) persistState();
  }

  private boolean seedIfMissing(String phone, long id, String nickname, long merchantId) {
    if (users.containsKey(phone)) return false;
    seed(id, phone, "abc123456", nickname, "MERCHANT_ADMIN", merchantId);
    return true;
  }

  private synchronized void persistState() {
    if (stateFile == null) return;
    try {
      Path parent = stateFile.getParent();
      if (parent != null) Files.createDirectories(parent);
      objectMapper.writeValue(stateFile.toFile(), new PersistentState(new ArrayList<>(users.values()), addresses, tokens));
    } catch (IOException error) {
      throw new IllegalStateException("Failed to persist identity state to " + stateFile, error);
    }
  }

  private record PersistentState(List<User> users, Map<Long, List<Address>> addresses, Map<String, Long> tokens) {}
}
