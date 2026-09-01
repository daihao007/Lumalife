package com.lumalife.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Identity-owned store for the first migration slice.
 *
 * <p>The service deliberately owns only accounts, sessions and addresses. The
 * state file is a transitional service-owned persistence boundary; it is not
 * the monolith's aggregate snapshot and contains only session hashes, never
 * bearer tokens.</p>
 */
@Service
public class IdentityStore {
  public static final int MAX_ADDRESSES_PER_USER = 5;
  private static final long DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 24 * 60 * 60;

  public record User(long id, String phone, String passwordHash, String nickname, String avatarUrl,
                     String role, Long merchantId) {}

  public record Address(long id, long userId, String contactName, String phone, String detail,
                        boolean defaultAddress) {}

  private record Session(long userId, long expiresAtEpochSecond, Long revokedAtEpochSecond) {}

  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path stateFile;
  private final Path backfillSourceFile;
  private final long accessTokenTtlSeconds;
  private final Clock clock;
  private final AtomicLong ids = new AtomicLong(1000);
  private final AtomicLong merchantIds = new AtomicLong(4);
  private final Map<String, User> users = new LinkedHashMap<>();
  private final Map<String, Session> sessions = new LinkedHashMap<>();
  private final Map<Long, List<Address>> addresses = new LinkedHashMap<>();

  @Autowired
  public IdentityStore(
      PasswordEncoder passwordEncoder,
      @Value("${lumalife.identity.state-file:./data/identity-state.json}") String stateFile,
      @Value("${lumalife.identity.backfill-source-file:}") String backfillSourceFile,
      @Value("${lumalife.identity.access-token-ttl-seconds:86400}") long accessTokenTtlSeconds) {
    this(passwordEncoder, path(stateFile), path(backfillSourceFile), accessTokenTtlSeconds, Clock.systemUTC());
  }

  public IdentityStore(PasswordEncoder passwordEncoder) {
    this(passwordEncoder, null, null, DEFAULT_ACCESS_TOKEN_TTL_SECONDS, Clock.systemUTC());
  }

  IdentityStore(PasswordEncoder passwordEncoder, Path stateFile) {
    this(passwordEncoder, stateFile, null, DEFAULT_ACCESS_TOKEN_TTL_SECONDS, Clock.systemUTC());
  }

  IdentityStore(PasswordEncoder passwordEncoder, Path stateFile, Path backfillSourceFile,
               long accessTokenTtlSeconds, Clock clock) {
    this.passwordEncoder = passwordEncoder;
    this.stateFile = stateFile;
    this.backfillSourceFile = backfillSourceFile;
    this.accessTokenTtlSeconds = Math.max(1, accessTokenTtlSeconds);
    this.clock = clock == null ? Clock.systemUTC() : clock;
    seed(6, "13800000000", "admin123456", "平台管理员", "PLATFORM_ADMIN", null);
    seed(1, "13800000001", "abc123456", "林夏", "USER", null);
    seed(2, "13800000002", "abc123456", "巷口川味研究所", "MERCHANT_ADMIN", 1L);
    seed(3, "13800000003", "abc123456", "晨雾咖啡局", "MERCHANT_ADMIN", 2L);
    seed(4, "13800000004", "abc123456", "绿盒轻食", "MERCHANT_ADMIN", 3L);
    seed(5, "13800000005", "abc123456", "栗香烘焙室", "MERCHANT_ADMIN", 4L);
    addresses.put(1L, new ArrayList<>(List.of(
        new Address(101, 1, "林夏", "13800000001", "梧桐路 18 号 2 单元 601", true),
        new Address(102, 1, "林夏", "13800000001", "学院路 66 号软件楼", false))));

    boolean loaded = loadPersistentState();
    if (!loaded && backfillSourceFile != null && Files.exists(backfillSourceFile)) {
      backfillFromLegacySnapshot(backfillSourceFile);
      persistState();
    } else if (stateFile != null && !Files.exists(stateFile)) {
      persistState();
    }
    ensureDemoMerchantAccounts();
  }

  public synchronized Map<String, Object> login(String phone, String password) {
    String normalized = normalizePhone(phone);
    User user = users.get(normalized);
    if (user == null || password == null || !passwordEncoder.matches(password, user.passwordHash())) {
      throw invalidCredentials();
    }

    String token = UUID.randomUUID() + "-" + UUID.randomUUID();
    long expiresAt = clock.instant().getEpochSecond() + accessTokenTtlSeconds;
    sessions.put(hash(token), new Session(user.id(), expiresAt, null));
    persistState();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("token", token);
    result.put("expiresIn", accessTokenTtlSeconds);
    result.put("user", safe(user));
    return result;
  }

  public synchronized User byPhone(String phone) {
    String normalized = normalizePhone(phone);
    User user = users.get(normalized);
    if (user == null) throw invalidCredentials();
    return user;
  }

  public synchronized User byToken(String token) {
    Session session = activeSession(token);
    return byId(session.userId());
  }

  public synchronized Map<String, Object> introspect(String token) {
    Session session = activeSession(token);
    User user = byId(session.userId());
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("active", true);
    claims.put("sub", user.phone());
    claims.put("userId", user.id());
    claims.put("role", user.role());
    claims.put("merchantId", user.merchantId());
    claims.put("exp", session.expiresAtEpochSecond());
    return claims;
  }

  public synchronized Map<String, Object> register(String phone, String password, String nickname, String role) {
    String normalized = normalizePhone(phone);
    validatePhone(normalized);
    validatePassword(password);
    String nextNickname = validateNickname(nickname);
    if (users.containsKey(normalized)) {
      throw new IdentityException(409, "手机号已注册", "PHONE_ALREADY_REGISTERED");
    }

    String nextRole = role == null || role.isBlank() ? "USER" : role.trim();
    if (!"USER".equals(nextRole) && !"MERCHANT_ADMIN".equals(nextRole)) {
      throw new IdentityException(400, "注册角色不受支持", "ROLE_NOT_ALLOWED");
    }
    Long merchantId = "MERCHANT_ADMIN".equals(nextRole) ? merchantIds.incrementAndGet() : null;
    User user = seed(ids.incrementAndGet(), normalized, password, nextNickname, nextRole, merchantId);
    persistState();
    return login(user.phone(), password);
  }

  public synchronized User updateProfile(long id, String nickname, String avatarUrl) {
    User old = byId(id);
    String nextNickname = nickname == null || nickname.isBlank() ? old.nickname() : validateNickname(nickname);
    String nextAvatar = avatarUrl == null ? old.avatarUrl() : avatarUrl.trim();
    if (nextAvatar.length() > 2048) {
      throw new IdentityException(400, "头像地址不能超过 2048 个字符", "VALIDATION_FAILED");
    }
    User updated = new User(old.id(), old.phone(), old.passwordHash(), nextNickname, nextAvatar, old.role(), old.merchantId());
    users.put(updated.phone(), updated);
    persistState();
    return updated;
  }

  public synchronized void requireActor(long targetUserId, Long actorUserId) {
    if (actorUserId == null) {
      throw new IdentityException(403, "缺少用户归属信息", "RESOURCE_FORBIDDEN");
    }
    if (targetUserId != actorUserId) {
      throw new IdentityException(403, "不能操作其他用户的数据", "RESOURCE_FORBIDDEN");
    }
    byId(targetUserId);
  }

  public synchronized List<Address> addresses(long userId) {
    byId(userId);
    return new ArrayList<>(addresses.getOrDefault(userId, List.of()));
  }

  public synchronized Address address(long userId, long addressId) {
    byId(userId);
    return addresses.getOrDefault(userId, List.of()).stream()
        .filter(item -> item.id() == addressId)
        .findFirst()
        .orElseThrow(() -> new IdentityException(404, "地址不存在", "ADDRESS_NOT_FOUND"));
  }

  public synchronized Address saveAddress(long userId, Long id, String contactName, String phone,
                                          String detail, boolean defaultAddress) {
    byId(userId);
    String nextContactName = requiredField(contactName, "联系人", 64);
    String nextPhone = requiredField(phone, "联系电话", 32);
    String nextDetail = requiredField(detail, "详细地址", 255);
    List<Address> list = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());

    long addressId;
    if (id == null || id <= 0) {
      if (list.size() >= MAX_ADDRESSES_PER_USER) {
        throw new IdentityException(409, "每个用户最多保存 5 个地址", "ADDRESS_LIMIT_REACHED");
      }
      addressId = ids.incrementAndGet();
    } else {
      addressId = id;
      boolean owned = list.stream().anyMatch(item -> item.id() == addressId);
      if (!owned) {
        if (addresses.values().stream().flatMap(List::stream).anyMatch(item -> item.id() == addressId)) {
          throw new IdentityException(403, "不能操作其他用户的地址", "RESOURCE_FORBIDDEN");
        }
        throw new IdentityException(404, "地址不存在", "ADDRESS_NOT_FOUND");
      }
    }

    boolean shouldBeDefault = defaultAddress || list.isEmpty();
    if (shouldBeDefault) {
      list.replaceAll(a -> new Address(a.id(), a.userId(), a.contactName(), a.phone(), a.detail(), false));
    }
    Address address = new Address(addressId, userId, nextContactName, nextPhone, nextDetail, shouldBeDefault);
    list.removeIf(item -> item.id() == addressId);
    list.add(address);
    persistState();
    return address;
  }

  public synchronized Address setDefault(long userId, long id) {
    Address found = address(userId, id);
    List<Address> owned = addresses.computeIfAbsent(userId, ignored -> new ArrayList<>());
    owned.replaceAll(item -> new Address(item.id(), item.userId(), item.contactName(), item.phone(), item.detail(), item.id() == id));
    persistState();
    return owned.stream().filter(item -> item.id() == id).findFirst().orElse(found);
  }

  public synchronized void deleteAddress(long userId, long id) {
    address(userId, id);
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
        .orElseThrow(() -> new IdentityException(404, "用户不存在", "NOT_FOUND"));
  }

  public Map<String, Object> safe(User user) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", user.id());
    data.put("phone", user.phone());
    data.put("nickname", user.nickname());
    data.put("avatarUrl", user.avatarUrl());
    data.put("role", user.role());
    data.put("merchantId", user.merchantId());
    return data;
  }

  private Session activeSession(String token) {
    if (token == null || token.isBlank()) throw tokenInvalid();
    String key = hash(token.trim());
    Session session = sessions.get(key);
    long now = clock.instant().getEpochSecond();
    if (session == null || session.revokedAtEpochSecond() != null || session.expiresAtEpochSecond() <= now) {
      if (session != null) {
        sessions.remove(key);
        persistState();
      }
      throw tokenInvalid();
    }
    return session;
  }

  private User seed(long id, String phone, String password, String nickname, String role, Long merchantId) {
    User user = new User(id, phone, passwordEncoder.encode(password), nickname, "", role, merchantId);
    users.put(phone, user);
    ids.updateAndGet(current -> Math.max(current, id));
    if (merchantId != null) merchantIds.updateAndGet(current -> Math.max(current, merchantId));
    return user;
  }

  private void importUser(JsonNode node) {
    if (node == null || node.path("phone").asText().isBlank()) return;
    String phone = normalizePhone(node.path("phone").asText());
    String passwordHash = node.hasNonNull("passwordHash") ? node.path("passwordHash").asText() : node.path("password").asText();
    if (passwordHash.isBlank()) return;
    long id = node.path("id").asLong();
    if (id <= 0) id = ids.incrementAndGet();
    long importedId = id;
    String role = node.path("role").asText("USER");
    if (!"USER".equals(role) && !"MERCHANT_ADMIN".equals(role) && !"PLATFORM_ADMIN".equals(role)) role = "USER";
    Long merchantId = node.hasNonNull("merchantId") ? node.path("merchantId").asLong() : null;
    User user = new User(id, phone, passwordHash,
        node.path("nickname").asText("新用户"), node.path("avatarUrl").asText(""), role, merchantId);
    users.put(phone, user);
    ids.updateAndGet(current -> Math.max(current, importedId));
    if (merchantId != null) merchantIds.updateAndGet(current -> Math.max(current, merchantId));
  }

  /** Imports the legacy monolith snapshot once when the service state is absent. */
  private void backfillFromLegacySnapshot(Path source) {
    try {
      JsonNode root = objectMapper.readTree(source.toFile());
      List<JsonNode> importedUsers = new ArrayList<>();
      root.path("accounts").forEach(importedUsers::add);
      if (importedUsers.isEmpty()) root.path("users").forEach(importedUsers::add);
      if (importedUsers.isEmpty()) return;

      users.clear();
      addresses.clear();
      sessions.clear();
      importedUsers.forEach(this::importUser);
      root.path("addresses").forEach(node -> {
        if (!node.isObject()) return;
        long userId = node.path("userId").asLong();
        if (userId <= 0 || !users.values().stream().anyMatch(user -> user.id() == userId)) return;
        Address address = new Address(node.path("id").asLong(ids.incrementAndGet()), userId,
            node.path("contactName").asText(), node.path("phone").asText(), node.path("detail").asText(),
            node.path("defaultAddress").asBoolean());
        addresses.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(address);
      });
      normalizeDefaults();
    } catch (IOException error) {
      throw new IllegalStateException("Failed to backfill identity state from " + source, error);
    }
  }

  private void normalizeDefaults() {
    addresses.values().forEach(list -> {
      if (list.size() > MAX_ADDRESSES_PER_USER) {
        throw new IllegalStateException("Legacy identity data exceeds the address limit");
      }
      boolean foundDefault = false;
      for (int i = 0; i < list.size(); i++) {
        Address address = list.get(i);
        boolean isDefault = address.defaultAddress() && !foundDefault;
        foundDefault = foundDefault || isDefault;
        list.set(i, new Address(address.id(), address.userId(), address.contactName(), address.phone(), address.detail(), isDefault));
      }
      if (!list.isEmpty() && !foundDefault) {
        Address first = list.get(0);
        list.set(0, new Address(first.id(), first.userId(), first.contactName(), first.phone(), first.detail(), true));
      }
    });
  }

  private boolean loadPersistentState() {
    if (stateFile == null || !Files.exists(stateFile)) return false;
    try {
      JsonNode root = objectMapper.readTree(stateFile.toFile());
      users.clear();
      root.path("users").forEach(this::importUser);
      addresses.clear();
      JsonNode addressNode = root.path("addresses");
      if (addressNode.isObject()) {
        addressNode.fields().forEachRemaining(entry -> {
          long userId = Long.parseLong(entry.getKey());
          List<Address> values = new ArrayList<>();
          entry.getValue().forEach(node -> values.add(address(node, userId)));
          addresses.put(userId, values);
        });
      } else if (addressNode.isArray()) {
        addressNode.forEach(node -> {
          long userId = node.path("userId").asLong();
          addresses.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(address(node, userId));
        });
      }
      sessions.clear();
      JsonNode storedSessions = root.path("sessions");
      if (storedSessions.isObject()) {
        storedSessions.fields().forEachRemaining(entry -> sessions.put(entry.getKey(), session(entry.getValue())));
      }
      // Migrate the old raw-token map once. The next write stores hashes only.
      JsonNode oldTokens = root.path("tokens");
      if (storedSessions.isMissingNode() && oldTokens.isObject()) {
        oldTokens.fields().forEachRemaining(entry -> {
          long userId = entry.getValue().asLong();
          sessions.put(hash(entry.getKey()), new Session(userId,
              clock.instant().getEpochSecond() + accessTokenTtlSeconds, null));
        });
      }
      normalizeDefaults();
      users.values().forEach(user -> {
        ids.updateAndGet(current -> Math.max(current, user.id()));
        if (user.merchantId() != null) merchantIds.updateAndGet(current -> Math.max(current, user.merchantId()));
      });
      addresses.values().stream().flatMap(List::stream)
          .forEach(address -> ids.updateAndGet(current -> Math.max(current, address.id())));
      return true;
    } catch (IOException | RuntimeException error) {
      throw new IllegalStateException("Failed to load identity state from " + stateFile, error);
    }
  }

  private Address address(JsonNode node, long userId) {
    return new Address(node.path("id").asLong(), userId, node.path("contactName").asText(),
        node.path("phone").asText(), node.path("detail").asText(), node.path("defaultAddress").asBoolean());
  }

  private Session session(JsonNode node) {
    Long revokedAt = node.hasNonNull("revokedAtEpochSecond") ? node.path("revokedAtEpochSecond").asLong() : null;
    return new Session(node.path("userId").asLong(), node.path("expiresAtEpochSecond").asLong(), revokedAt);
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
      Path temporary = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
      objectMapper.writeValue(temporary.toFile(), new PersistentState(
          new ArrayList<>(users.values()), addresses, sessions));
      try {
        Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException error) {
      throw new IllegalStateException("Failed to persist identity state to " + stateFile, error);
    }
  }

  private String requiredField(String value, String label, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IdentityException(400, label + "不能为空", "VALIDATION_FAILED");
    }
    String trimmed = value.trim();
    if (trimmed.length() > maxLength) {
      throw new IdentityException(400, label + "不能超过 " + maxLength + " 个字符", "VALIDATION_FAILED");
    }
    return trimmed;
  }

  private String validateNickname(String value) {
    return requiredField(value, "昵称", 64);
  }

  private void validatePhone(String phone) {
    if (phone.isBlank() || phone.length() > 32) {
      throw new IdentityException(400, "用户名长度不合法", "VALIDATION_FAILED");
    }
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 6 || password.length() > 128) {
      throw new IdentityException(400, "密码长度必须为 6 到 128 位", "VALIDATION_FAILED");
    }
  }

  private String normalizePhone(String phone) {
    return phone == null ? "" : phone.trim();
  }

  private IdentityException invalidCredentials() {
    return new IdentityException(401, "账号或密码错误", "INVALID_CREDENTIALS");
  }

  private IdentityException tokenInvalid() {
    return new IdentityException(401, "登录状态已失效", "TOKEN_INVALID");
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }

  private static Path path(String value) {
    return value == null || value.isBlank() ? null : Path.of(value);
  }

  private record PersistentState(List<User> users, Map<Long, List<Address>> addresses,
                                Map<String, Session> sessions) {}

  public static class IdentityException extends RuntimeException {
    private final int status;
    private final int code;
    private final String reason;

    public IdentityException(int status, String message) {
      this(status, message, defaultReason(status));
    }

    public IdentityException(int status, String message, String reason) {
      super(message);
      this.status = status;
      this.code = switch (status) {
        case 400 -> 40000;
        case 401 -> 40100;
        case 403 -> 40300;
        case 404 -> 40400;
        case 409 -> 40900;
        default -> 50000;
      };
      this.reason = reason;
    }

    public int status() { return status; }
    public int code() { return code; }
    public String reason() { return reason; }

    private static String defaultReason(int status) {
      return switch (status) {
        case 400 -> "VALIDATION_FAILED";
        case 401 -> "AUTHENTICATION_FAILED";
        case 403 -> "RESOURCE_FORBIDDEN";
        case 404 -> "NOT_FOUND";
        case 409 -> "BUSINESS_CONFLICT";
        default -> "IDENTITY_ERROR";
      };
    }
  }
}
