package com.lumalife.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity-owned store. Production instances use MySQL; the file/in-memory
 * implementation remains available for isolated unit tests without a DB.
 */
@Service
public class IdentityStore {
  public record User(long id, String phone, String passwordHash, String nickname, String avatarUrl,
                     String role, Long merchantId) {}
  public record Address(long id, long userId, String contactName, String phone, String detail,
                        boolean defaultAddress) {}
  private record TokenSession(long userId, long expiresAtEpochMillis) {}
  private static final int TOKEN_DAYS = 30;
  private static final int MAX_ADDRESSES = 5;
  private static final long DEFAULT_TOKEN_TTL_MILLIS =
    java.time.Duration.ofDays(TOKEN_DAYS).toMillis();
  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path stateFile;
  private final JdbcTemplate jdbc;
  private final AtomicLong ids = new AtomicLong(1000);
  private final AtomicLong merchantIds = new AtomicLong(2);
  private final long tokenTtlMillis;
  private final Map<String, User> users = new LinkedHashMap<>();
  private final Map<String, TokenSession> tokens = new LinkedHashMap<>();
  private final Map<Long, List<Address>> addresses = new LinkedHashMap<>();

  @Autowired
  public IdentityStore(PasswordEncoder passwordEncoder,
                       @Value("${lumalife.identity.state-file:./data/identity-state.json}") String stateFile,
                       @Value("${lumalife.identity.token-ttl-millis:2592000000}") long tokenTtlMillis,
                       ObjectProvider<JdbcTemplate> jdbcProvider) {
    this(passwordEncoder,
      stateFile == null || stateFile.isBlank() ? null : Path.of(stateFile),
      jdbcProvider.getIfAvailable(),
      tokenTtlMillis
    );
  }

  public IdentityStore(PasswordEncoder passwordEncoder) {
    this(passwordEncoder, (Path) null, null, DEFAULT_TOKEN_TTL_MILLIS);
  }

  IdentityStore(PasswordEncoder passwordEncoder, Path stateFile) {
    this(passwordEncoder, stateFile, null, DEFAULT_TOKEN_TTL_MILLIS);
  }

  private IdentityStore(PasswordEncoder passwordEncoder, Path stateFile, JdbcTemplate jdbc, long tokenTtlMillis) {
    this.passwordEncoder = passwordEncoder;
    this.stateFile = stateFile;
    this.jdbc = jdbc;
    this.tokenTtlMillis = tokenTtlMillis;
    if (jdbc == null) {
      seed(6, "13800000000", "admin123456", "平台管理员", "PLATFORM_ADMIN", null);
      seed(1, "13800000001", "abc123456", "林夏", "USER", null);
      seed(2, "13800000002", "abc123456", "巷口川味研究所", "MERCHANT_ADMIN", 1L);
      seed(3, "13800000003", "abc123456", "晨雾咖啡局", "MERCHANT_ADMIN", 2L);
      seed(4, "13800000004", "abc123456", "绿盒轻食", "MERCHANT_ADMIN", 3L);
      seed(5, "13800000005", "abc123456", "栗香烘焙室", "MERCHANT_ADMIN", 4L);
      addresses.put(1L, new ArrayList<>(List.of(
        new Address(101, 1, "林夏", "13800000001", "梧桐路 18 号 2 单元 601", true))));
      loadPersistentState();
      if (stateFile != null && !Files.exists(stateFile)) persistState();
    }
  }

  public synchronized Map<String, Object> login(String phone, String password) {
    User user = byPhone(phone);
    if (!passwordEncoder.matches(password == null ? "" : password, user.passwordHash())) {
      throw new IdentityException(401, "密码错误");
    }
    String token = UUID.randomUUID().toString();
    if (jdbc != null) {
      jdbc.update("INSERT INTO auth_session(user_id,token_hash,expires_at) VALUES (?,?,?)",
        user.id(), hash(token), Timestamp.valueOf(LocalDateTime.now()
          .plus(java.time.Duration.ofMillis(tokenTtlMillis))));
    } else {
      long expiresAtEpochMillis = System.currentTimeMillis() + tokenTtlMillis;
      tokens.put(token, new TokenSession(user.id(), expiresAtEpochMillis));
      persistState();
    }
    return Map.of("token", token, "user", safe(user));
  }

  public synchronized User byPhone(String phone) {
    String normalized = phone == null ? "" : phone.trim();
    if (normalized.isBlank()) {
      throw new IdentityException(400, "手机号不能为空");
    }
    if (jdbc != null) {
      List<User> rows = jdbc.query("SELECT id,phone,password_hash,nickname,avatar_url,role,merchant_id "
          + "FROM user_account WHERE phone=? AND is_deleted=0", this::mapUser, normalized);
      if (!rows.isEmpty()) return rows.get(0);
      throw new IdentityException(401, "用户名不存在");
    }
    User user = users.get(normalized);
    if (user == null) throw new IdentityException(401, "用户名不存在");
    return user;
  }

  public synchronized User byToken(String token) {
    if (token == null || token.isBlank()) throw new IdentityException(401, "登录状态已失效");
    if (jdbc != null) {
      List<User> rows = jdbc.query("SELECT u.id,u.phone,u.password_hash,u.nickname,u.avatar_url,u.role,u.merchant_id "
          + "FROM auth_session s JOIN user_account u ON u.id=s.user_id "
          + "WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP "
          + "AND u.is_deleted=0", this::mapUser, hash(token));
      if (!rows.isEmpty()) return rows.get(0);
      throw new IdentityException(401, "登录状态已失效");
    }
    TokenSession session = tokens.get(token);
    if (session == null || session.expiresAtEpochMillis() <= System.currentTimeMillis()) {
      if (session != null) {
        tokens.remove(token);
        persistState();
      }

      throw new IdentityException(401, "登录状态已失效");
    }
    return users.values().stream().filter(item -> item.id() == session.userId()).findFirst()
      .orElseThrow(() -> new IdentityException(401, "登录状态已失效"));
  }

  @Transactional
  public synchronized Map<String, Object> register(String phone, String password, String nickname, String role) {
    String normalized = phone == null ? "" : phone.trim();
    if (normalized.isBlank() || password == null || password.length() < 6) {
      throw new IdentityException(400, "用户名和至少 6 位密码不能为空");
    }
    String normalizedRole = "MERCHANT_ADMIN".equals(role) ? "MERCHANT_ADMIN" : "USER";
    String displayName = nickname == null || nickname.isBlank() ? "新用户" : nickname.trim();
    if (jdbc != null) {
      if (!jdbc.query("SELECT id FROM user_account WHERE phone=? AND is_deleted=0", (rs, n) -> rs.getLong(1), normalized).isEmpty()) {
        throw new IdentityException(409, "用户名已注册");
      }
      // Merchant provisioning is a separate saga. The account is durable first;
      // merchant-service can bind the profile after the registration event.
      jdbc.update("INSERT INTO user_account(phone,password_hash,nickname,avatar_url,role,merchant_id) VALUES (?,?,?,?,?,NULL)",
        normalized, passwordEncoder.encode(password), displayName, "", normalizedRole);
      return login(normalized, password);
    }
    if (users.containsKey(normalized)) throw new IdentityException(409, "用户名已注册");
    seed(ids.incrementAndGet(), normalized, password, displayName, normalizedRole,
      "MERCHANT_ADMIN".equals(normalizedRole) ? merchantIds.incrementAndGet() : null);
    return login(normalized, password);
  }

  @Transactional
  public synchronized User updateProfile(long id, String nickname, String avatarUrl) {
    User old = byId(id);
    String nextNickname = nickname == null || nickname.isBlank() ? old.nickname() : nickname.trim();
    String nextAvatar = avatarUrl == null ? old.avatarUrl() : avatarUrl.trim();
    if (nextAvatar.length() > 2_000_000) throw new IdentityException(400, "头像文件不能超过 2MB");
    if (jdbc != null) {
      jdbc.update("UPDATE user_account SET nickname=?,avatar_url=? WHERE id=? AND is_deleted=0",
        nextNickname, nextAvatar, id);
    } else {
      users.put(old.phone(), new User(old.id(), old.phone(), old.passwordHash(), nextNickname, nextAvatar, old.role(), old.merchantId()));
      persistState();
    }
    return byId(id);
  }

  @Transactional
  public synchronized User bindMerchant(long userId, long merchantId) {
    User old = byId(userId);
    if (!"MERCHANT_ADMIN".equals(old.role())) throw new IdentityException(400, "只有商家账号可以绑定商家资料");
    if (merchantId <= 0) throw new IdentityException(400, "商家编号不合法");
    if (jdbc != null) {
      jdbc.update("UPDATE user_account SET merchant_id=? WHERE id=? AND is_deleted=0", merchantId, userId);
      return byId(userId);
    }
    User updated = new User(old.id(), old.phone(), old.passwordHash(), old.nickname(), old.avatarUrl(), old.role(), merchantId);
    users.put(old.phone(), updated);
    merchantIds.updateAndGet(current -> Math.max(current, merchantId));
    persistState();
    return updated;
  }

  public synchronized void requireActor(long targetUserId, long actorUserId) {
    if (targetUserId != actorUserId) throw new IdentityException(403, "不能操作其他用户的数据");
    byId(targetUserId);
  }

  public synchronized List<Address> addresses(long userId) {
    byId(userId);
    if (jdbc != null) return jdbc.query("SELECT id,user_id,contact_name,phone,detail,is_default "
      + "FROM user_address WHERE user_id=? AND is_deleted=0 ORDER BY is_default DESC,id", this::mapAddress, userId);
    return new ArrayList<>(addresses.getOrDefault(userId, List.of()));
  }

  @Transactional
  public synchronized Address saveAddress(long userId, Long id, String contactName, String phone,
                                          String detail, boolean defaultAddress) {
    requireActor(userId, userId);
    if (contactName == null || contactName.isBlank() || phone == null || phone.isBlank()
        || detail == null || detail.isBlank()) throw new IdentityException(400, "地址信息不能为空");
    if (jdbc != null) {
      List<Address> current = addresses(userId);
      if (id == null || id <= 0) {
        if (current.size() >= MAX_ADDRESSES) throw new IdentityException(400, "最多保存 5 个地址");
        boolean makeDefault = defaultAddress || current.isEmpty();
        if (makeDefault) jdbc.update("UPDATE user_address SET is_default=0 WHERE user_id=? AND is_deleted=0", userId);
        jdbc.update("INSERT INTO user_address(user_id,contact_name,phone,detail,is_default) VALUES (?,?,?,?,?)",
          userId, contactName.trim(), phone.trim(), detail.trim(), makeDefault);
        return addresses(userId).stream().findFirst().orElseThrow();
      }
      List<Address> existing = jdbc.query("SELECT id,user_id,contact_name,phone,detail,is_default FROM user_address WHERE id=? AND user_id=? AND is_deleted=0", this::mapAddress, id, userId);
      if (existing.isEmpty()) throw new IdentityException(404, "地址不存在");
      if (defaultAddress) jdbc.update("UPDATE user_address SET is_default=0 WHERE user_id=? AND is_deleted=0", userId);
      jdbc.update("UPDATE user_address SET contact_name=?,phone=?,detail=?,is_default=? WHERE id=? AND user_id=?",
        contactName.trim(), phone.trim(), detail.trim(), defaultAddress, id, userId);
      return addresses(userId).stream().filter(address -> address.id() == id).findFirst().orElseThrow();
    }
    List<Address> list = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());
    if (id != null && id > 0 && list.stream().noneMatch(item -> item.id() == id)) {
      throw new IdentityException(404, "地址不存在");
    }
    if ((id == null || id <= 0) && list.size() >= MAX_ADDRESSES) {
      throw new IdentityException(400, "最多保存 5 个地址");
    }
    long addressId = id == null || id <= 0 ? ids.incrementAndGet() : id;
    if (defaultAddress || list.isEmpty()) list.replaceAll(a -> new Address(a.id(), a.userId(), a.contactName(), a.phone(), a.detail(), false));
    Address address = new Address(addressId, userId, contactName.trim(), phone.trim(), detail.trim(), defaultAddress || list.isEmpty());
    list.removeIf(item -> item.id() == addressId);
    list.add(address);
    persistState();
    return address;
  }

  @Transactional
  public synchronized Address setDefault(long userId, long id) {
    requireActor(userId, userId);
    if (jdbc != null) {
      if (addresses(userId).stream().noneMatch(address -> address.id() == id)) throw new IdentityException(404, "地址不存在");
      jdbc.update("UPDATE user_address SET is_default=(id=?) WHERE user_id=? AND is_deleted=0", id, userId);
      return addresses(userId).stream().filter(address -> address.id() == id).findFirst().orElseThrow();
    }
    List<Address> owned = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());
    if (owned.stream().noneMatch(item -> item.id() == id)) throw new IdentityException(404, "地址不存在");
    owned.replaceAll(item -> new Address(item.id(), item.userId(), item.contactName(), item.phone(), item.detail(), item.id() == id));
    persistState();
    return owned.stream().filter(item -> item.id() == id).findFirst().orElseThrow();
  }

  @Transactional
  public synchronized void deleteAddress(long userId, long id) {
    requireActor(userId, userId);
    if (jdbc != null) {
      int deleted = jdbc.update("UPDATE user_address SET is_deleted=1,is_default=0 WHERE id=? AND user_id=? AND is_deleted=0", id, userId);
      if (deleted == 0) throw new IdentityException(404, "地址不存在");
      List<Address> remaining = addresses(userId);
      if (!remaining.isEmpty() && remaining.stream().noneMatch(Address::defaultAddress)) {
        jdbc.update("UPDATE user_address SET is_default=1 WHERE id=? AND user_id=?", remaining.get(0).id(), userId);
      }
      return;
    }
    List<Address> list = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());
    boolean removed = list.removeIf(item -> item.id() == id);
    if (!removed) throw new IdentityException(404, "地址不存在");
    if (!list.isEmpty() && list.stream().noneMatch(Address::defaultAddress)) {
      Address first = list.get(0);
      list.set(0, new Address(first.id(), first.userId(), first.contactName(), first.phone(), first.detail(), true));
    }
    persistState();
  }

  public synchronized User byId(long id) {
    if (jdbc != null) {
      List<User> rows = jdbc.query("SELECT id,phone,password_hash,nickname,avatar_url,role,merchant_id FROM user_account WHERE id=? AND is_deleted=0", this::mapUser, id);
      if (!rows.isEmpty()) return rows.get(0);
      throw new IdentityException(404, "用户不存在");
    }
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

  private User mapUser(ResultSet rs, int row) throws SQLException {
    long merchantId = rs.getLong("merchant_id");
    return new User(rs.getLong("id"), rs.getString("phone"), rs.getString("password_hash"),
      rs.getString("nickname"), rs.getString("avatar_url"), rs.getString("role"), rs.wasNull() ? null : merchantId);
  }

  private Address mapAddress(ResultSet rs, int row) throws SQLException {
    return new Address(rs.getLong("id"), rs.getLong("user_id"), rs.getString("contact_name"),
      rs.getString("phone"), rs.getString("detail"), rs.getBoolean("is_default"));
  }

  private String hash(String token) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
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
      JsonNode root = objectMapper.readTree(stateFile.toFile());
      List<User> restoredUsers = root.hasNonNull("users")
        ? objectMapper.convertValue(root.get("users"), new TypeReference<List<User>>() {})
        : List.of();
      Map<Long, List<Address>> restoredAddresses = root.hasNonNull("addresses")
        ? objectMapper.convertValue(root.get("addresses"), new TypeReference<Map<Long, List<Address>>>() {})
        : Map.of();
      TokenState restoredTokens = readTokens(root.get("tokens"));
      users.clear();
      restoredUsers.forEach(user -> users.put(user.phone(), user));
      addresses.clear();
      restoredAddresses.forEach((id, values) -> addresses.put(id, new ArrayList<>(values)));
      tokens.clear();
      tokens.putAll(restoredTokens.tokens());
      users.values().forEach(user -> {
        ids.updateAndGet(current -> Math.max(current, user.id()));
        if (user.merchantId() != null) merchantIds.updateAndGet(current -> Math.max(current, user.merchantId()));
      });
      if (restoredTokens.migrated()) persistState();
    } catch (IOException error) {
      throw new IllegalStateException("Failed to load identity state from " + stateFile, error);
    } catch (IllegalArgumentException error) {
      throw new IllegalStateException("Failed to load identity state from " + stateFile, error);
    }
  }

  private TokenState readTokens(JsonNode node) throws IOException {
    Map<String, TokenSession> restored = new LinkedHashMap<>();
    boolean migrated = false;
    if (node == null || !node.isObject()) return new TokenState(restored, false);
    var fields = node.fields();
    while (fields.hasNext()) {
      var entry = fields.next();
      if (entry.getValue().isIntegralNumber()) {
        // The legacy format stored only the user id; preserve the session with the default TTL.
        long userId = entry.getValue().asLong();
        restored.put(entry.getKey(), new TokenSession(userId, System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MILLIS));
        migrated = true;
      } else if (entry.getValue().isObject()) {
        restored.put(entry.getKey(), objectMapper.treeToValue(entry.getValue(), TokenSession.class));
      } else {
        throw new IllegalArgumentException("invalid token session for " + entry.getKey());
      }
    }
    return new TokenState(restored, migrated);
  }

  private synchronized void persistState() {
    if (stateFile == null || jdbc != null) return;
    try {
      Path parent = stateFile.getParent();
      if (parent != null) Files.createDirectories(parent);
      objectMapper.writeValue(stateFile.toFile(), new PersistentState(new ArrayList<>(users.values()), addresses, tokens));
    } catch (IOException error) {
      throw new IllegalStateException("Failed to persist identity state to " + stateFile, error);
    }
  }

  private record TokenState(Map<String, TokenSession> tokens, boolean migrated) {}
  private record PersistentState(List<User> users, Map<Long, List<Address>> addresses, Map<String, TokenSession> tokens) {}
}
