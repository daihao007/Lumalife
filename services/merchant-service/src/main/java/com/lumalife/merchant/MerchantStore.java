package com.lumalife.merchant;

import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Locale;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Merchant-owned catalog slice. Product writes are kept behind this service boundary. */
@Service
public class MerchantStore {
  public record Merchant(long id, String name, long categoryId, String categoryName, String cover, double avgScore,
                         int avgPrice, int monthlySales, double distanceKm, String status, String address, String reason) {}
  public record Product(long id, long merchantId, String name, String description, long priceCent, int stock, boolean listed) {}
  public record GroupDeal(long id, long merchantId, String title, String description, long priceCent, int stock, boolean active) {}
  public record ReservationItem(String itemType, long itemId, int quantity, long expectedVersion) {}
  public record ReservationRequest(long orderId, Instant expiresAt, List<ReservationItem> items) {}
  public record InventoryReservation(long orderId, String status, Instant expiresAt, List<ReservationItem> items) {}
  private record ReservationHeader(long orderId, String idempotencyKey, String status, Instant expiresAt) {}
  public record ChatMessage(long id, long userId, long merchantId, String senderRole, String senderName,
                            String content, LocalDateTime createdAt) {}

  private final AtomicLong ids = new AtomicLong(3000);
  private final Map<Long, Merchant> merchants = new LinkedHashMap<>();
  private final Map<Long, List<Product>> products = new LinkedHashMap<>();
  private final Map<Long, GroupDeal> deals = new LinkedHashMap<>();
  private final Map<String, List<ChatMessage>> conversations = new LinkedHashMap<>();
  private final Map<Long, Set<Long>> favoriteIndex = new LinkedHashMap<>();
  private final Map<Long, InventoryReservation> reservations = new LinkedHashMap<>();
  private final Map<Long, String> reservationKeys = new LinkedHashMap<>();
  private final JdbcTemplate jdbc;

  @Autowired
  public MerchantStore(ObjectProvider<JdbcTemplate> jdbcProvider) {
    this.jdbc = jdbcProvider.getIfAvailable();
    seedMerchants();
    products.put(1L, new ArrayList<>(List.of(
      new Product(1001, 1, "藤椒鸡饭", "麻香鲜亮，适合午餐", 2680, 88, true),
      new Product(1002, 1, "毛血旺小锅", "课程演示热门搜索菜", 4280, 120, true))));
    deals.put(1L, new GroupDeal(1, 1, "双人川味套餐", "含招牌菜和饮品", 4880, 50, true));
    if (jdbc != null) {
      bumpIds("SELECT COALESCE(MAX(id),0) FROM merchant_catalog");
      bumpIds("SELECT COALESCE(MAX(id),0) FROM group_deal");
      bumpIds("SELECT COALESCE(MAX(id),0) FROM chat_message");
    }
  }

  public MerchantStore() {
    this.jdbc = null;
    seedMerchants();
    products.put(1L, new ArrayList<>(List.of(
      new Product(1001, 1, "藤椒鸡饭", "麻香鲜亮，适合午餐", 2680, 88, true),
      new Product(1002, 1, "毛血旺小锅", "课程演示热门搜索菜", 4280, 120, true))));
    deals.put(1L, new GroupDeal(1, 1, "双人川味套餐", "含招牌菜和饮品", 4880, 50, true));
  }

  public synchronized List<Merchant> search(String keyword, Long categoryId, String sort,
                                            Integer minPrice, Integer maxPrice, Double minScore) {
    String normalized = keyword == null ? "" : keyword.trim();
    List<Merchant> result;
    if (jdbc != null) {
      result = jdbc.query("SELECT m.id,m.name,m.category_id,c.name,m.cover_url,m.avg_score,m.avg_price_cent,m.monthly_sales,m.distance_km,m.status,m.address,m.recommend_reason FROM merchant m JOIN category c ON c.id=m.category_id WHERE m.is_deleted=0", this::mapMerchant).stream()
        .filter(item -> categoryId == null || item.categoryId() == categoryId)
        .filter(item -> minPrice == null || item.avgPrice() >= minPrice)
        .filter(item -> maxPrice == null || item.avgPrice() <= maxPrice)
        .filter(item -> minScore == null || item.avgScore() >= minScore)
        .filter(item -> normalized.isBlank() || item.name().contains(normalized)
          || products(item.id(), true).stream().anyMatch(product -> product.name().contains(normalized)))
        .toList();
    } else {
      result = merchants.values().stream()
        .filter(item -> categoryId == null || item.categoryId() == categoryId)
        .filter(item -> minPrice == null || item.avgPrice() >= minPrice)
        .filter(item -> maxPrice == null || item.avgPrice() <= maxPrice)
        .filter(item -> minScore == null || item.avgScore() >= minScore)
        .filter(item -> normalized.isBlank() || item.name().contains(normalized)
          || products(item.id(), true).stream().anyMatch(product -> product.name().contains(normalized)))
        .toList();
    }
    Comparator<Merchant> comparator = switch (sort == null ? "recommend" : sort) {
      case "priceAsc" -> Comparator.comparingInt(Merchant::avgPrice);
      case "priceDesc" -> Comparator.comparingInt(Merchant::avgPrice).reversed();
      case "scoreAsc" -> Comparator.comparingDouble(Merchant::avgScore);
      case "scoreDesc" -> Comparator.comparingDouble(Merchant::avgScore).reversed();
      case "salesAsc" -> Comparator.comparingInt(Merchant::monthlySales);
      case "salesDesc" -> Comparator.comparingInt(Merchant::monthlySales).reversed();
      case "distanceAsc", "distance" -> Comparator.comparingDouble(Merchant::distanceKm);
      case "distanceDesc" -> Comparator.comparingDouble(Merchant::distanceKm).reversed();
      default -> Comparator.comparingDouble((Merchant item) ->
        -(0.6 * item.avgScore() / 5.0 + 0.4 * Math.max(0, 1 - item.distanceKm() / 5.0)));
    };
    return result.stream().sorted(comparator.thenComparingLong(Merchant::id)).toList();
  }

  public synchronized List<Merchant> search(String keyword) {
    return search(keyword, null, "recommend", null, null, null);
  }

  public synchronized List<Map<String, Object>> categories() {
    if (jdbc == null) return List.of(
      Map.of("id", 1L, "name", "川湘菜", "icon", "flame"),
      Map.of("id", 2L, "name", "咖啡茶饮", "icon", "coffee"),
      Map.of("id", 3L, "name", "轻食简餐", "icon", "salad"),
      Map.of("id", 4L, "name", "甜品烘焙", "icon", "cake"),
      Map.of("id", 5L, "name", "生活服务", "icon", "sparkle"));
    return jdbc.query("SELECT id,name,icon FROM category WHERE is_deleted=0 ORDER BY id",
      (rs, n) -> Map.of("id", rs.getLong("id"), "name", rs.getString("name"), "icon", rs.getString("icon")));
  }

  public synchronized void addFavorite(long userId, long merchantId) {
    merchant(merchantId);
    if (jdbc != null) {
      if (jdbc.update("INSERT IGNORE INTO merchant_favorite(user_id,merchant_id) VALUES (?,?)", userId, merchantId) == 0) throw new IllegalStateException("已收藏该商家");
      return;
    }
    if (!favoriteIndex.computeIfAbsent(userId, ignored -> new java.util.LinkedHashSet<>()).add(merchantId)) throw new IllegalStateException("已收藏该商家");
  }

  public synchronized void removeFavorite(long userId, long merchantId) {
    if (jdbc != null) {
      if (jdbc.update("DELETE FROM merchant_favorite WHERE user_id=? AND merchant_id=?", userId, merchantId) == 0) throw new IllegalArgumentException("未收藏该商家");
      return;
    }
    Set<Long> owned = favoriteIndex.get(userId);
    if (owned == null || !owned.remove(merchantId)) throw new IllegalArgumentException("未收藏该商家");
  }

  public synchronized List<Long> favorites(long userId) {
    if (jdbc != null) return jdbc.query("SELECT merchant_id FROM merchant_favorite WHERE user_id=? ORDER BY created_at DESC", (rs, n) -> rs.getLong(1), userId);
    return new ArrayList<>(favoriteIndex.getOrDefault(userId, Set.of()));
  }

  public synchronized List<Map<String, Object>> favoriteMerchants(long userId) {
    return favorites(userId).stream().map(this::merchant).map(this::merchantMap).toList();
  }

  public synchronized List<ChatMessage> conversation(long userId, long merchantId) {
    merchant(merchantId);
    if (jdbc != null) return jdbc.query("SELECT id,user_id,merchant_id,sender_role,sender_name,content,created_at FROM chat_message WHERE user_id=? AND merchant_id=? ORDER BY created_at,id", this::mapMessage, userId, merchantId);
    return new ArrayList<>(conversations.getOrDefault(key(userId, merchantId), List.of()));
  }

  public synchronized List<ChatMessage> merchantConversation(long merchantId, long userId) {
    List<ChatMessage> messages = conversation(userId, merchantId);
    if (messages.isEmpty()) throw new IllegalArgumentException("商家会话不存在");
    return messages;
  }

  public synchronized List<ChatMessage> sendUserMessage(long userId, long merchantId, String content,
                                                         Function<List<ChatMessage>, String> aiResponder) {
    Merchant merchant = merchant(merchantId);
    String text = normalizeMessage(content);
    List<ChatMessage> messages = new ArrayList<>(conversation(userId, merchantId));
    ChatMessage question = new ChatMessage(nextMessageId(), userId, merchantId, "USER", "用户", text, LocalDateTime.now());
    saveMessage(question);
    messages.add(question);
    String answer = aiResponder == null ? null : normalizeMessage(aiResponder.apply(messages));
    if (answer != null && !answer.isBlank()) {
      ChatMessage response = new ChatMessage(nextMessageId(), userId, merchantId, "MERCHANT_AI", merchant.name(), answer, LocalDateTime.now());
      saveMessage(response);
      messages.add(response);
    }
    return messages;
  }

  public synchronized List<ChatMessage> sendMerchantMessage(long merchantId, long userId, String content, String senderName) {
    merchant(merchantId);
    String text = normalizeMessage(content);
    if (merchantConversation(merchantId, userId).isEmpty()) throw new IllegalArgumentException("商家会话不存在");
    ChatMessage message = new ChatMessage(nextMessageId(), userId, merchantId, "MERCHANT", senderName, text, LocalDateTime.now());
    saveMessage(message);
    return merchantConversation(merchantId, userId);
  }

  public synchronized List<Map<String, Object>> conversationSummaries(long userId, Long merchantId, boolean userSide) {
    List<ChatMessage> messages;
    if (jdbc != null) {
      String sql = userSide
        ? "SELECT id,user_id,merchant_id,sender_role,sender_name,content,created_at FROM chat_message WHERE user_id=? ORDER BY created_at,id"
        : "SELECT id,user_id,merchant_id,sender_role,sender_name,content,created_at FROM chat_message WHERE merchant_id=? ORDER BY created_at,id";
      messages = jdbc.query(sql, this::mapMessage, userId == 0 ? merchantId : userId);
    } else {
      messages = conversations.values().stream().flatMap(List::stream).filter(item -> userSide ? item.userId() == userId : item.merchantId() == merchantId).toList();
    }
    Map<String, List<ChatMessage>> grouped = new LinkedHashMap<>();
    messages.forEach(message -> grouped.computeIfAbsent(key(message.userId(), message.merchantId()), ignored -> new ArrayList<>()).add(message));
    return grouped.values().stream().map(items -> {
      ChatMessage first = items.get(0); ChatMessage last = items.get(items.size() - 1);
      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("userId", first.userId()); summary.put("merchantId", first.merchantId());
      summary.put("title", userSide ? merchant(first.merchantId()).name() : "用户" + first.userId());
      summary.put("merchantName", merchant(first.merchantId()).name()); summary.put("lastMessage", last.content()); summary.put("updatedAt", last.createdAt());
      return summary;
    }).toList();
  }

  public synchronized Merchant merchant(long id) {
    if (jdbc != null) {
      var rows = jdbc.query("SELECT m.id,m.name,m.category_id,c.name,m.cover_url,m.avg_score,m.avg_price_cent,m.monthly_sales,m.distance_km,m.status,m.address,m.recommend_reason FROM merchant m JOIN category c ON c.id=m.category_id WHERE m.id=? AND m.is_deleted=0", this::mapMerchant, id);
      if (!rows.isEmpty()) return rows.get(0);
      throw new IllegalArgumentException("商家不存在");
    }
    Merchant merchant = merchants.get(id);
    if (merchant == null) throw new IllegalArgumentException("商家不存在");
    return merchant;
  }

  public synchronized Map<String, Object> profile(long merchantId) {
    return Map.of("merchant", merchant(merchantId));
  }

  public synchronized Merchant updateName(long merchantId, String name) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("商家名称不能为空");
    Merchant old = merchant(merchantId);
    String next = name.trim();
    if (jdbc != null) jdbc.update("UPDATE merchant SET name=? WHERE id=? AND is_deleted=0", next, merchantId);
    Merchant updated = new Merchant(old.id(), next, old.categoryId(), old.categoryName(), old.cover(), old.avgScore(), old.avgPrice(), old.monthlySales(), old.distanceKm(), old.status(), old.address(), old.reason());
    merchants.put(merchantId, updated);
    return updated;
  }

  @Transactional
  public synchronized Merchant provision(String name) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("商家名称不能为空");
    String nextName = name.trim();
    long id = nextId();
    if (jdbc != null) {
      jdbc.update("INSERT INTO merchant(id,category_id,name,cover_url,avg_score,avg_price_cent,monthly_sales,distance_km,status,address,recommend_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        id, 1L, nextName, "", 0.0, 0L, 0, 0.0, "审核中", "", "新商家待完善资料");
      return merchant(id);
    }
    Merchant created = new Merchant(id, nextName, 1, "川湘菜", "", 0.0, 0, 0, 0.0, "审核中", "", "新商家待完善资料");
    merchants.put(id, created);
    return created;
  }

  public synchronized List<Product> products(long merchantId) {
    return products(merchantId, false);
  }

  public synchronized List<Product> products(long merchantId, boolean listedOnly) {
    merchant(merchantId);
    if (jdbc != null) {
      String filter = listedOnly ? " AND listed=1" : "";
      List<Product> owned = jdbc.query("SELECT id,merchant_id,name,description,price_cent,stock,listed FROM merchant_catalog WHERE merchant_id=?" + filter + " ORDER BY id", this::mapProduct, merchantId);
      return owned;
    }
    return new ArrayList<>(products.getOrDefault(merchantId, List.of()).stream()
      .filter(item -> !listedOnly || item.listed()).toList());
  }

  @Transactional
  public synchronized Product saveProduct(long merchantId, ProductRequest request) {
    merchant(merchantId);
    List<Product> owned = products.computeIfAbsent(merchantId, ignored -> new ArrayList<>());
    long id = request.id() == null ? ids.incrementAndGet() : request.id();
    Product product = new Product(id, merchantId, request.name(), request.description(), request.priceCent(), request.stock(), request.listed());
    if (jdbc != null) {
      jdbc.update("INSERT INTO merchant_catalog(id,merchant_id,name,description,price_cent,stock,listed) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),price_cent=VALUES(price_cent),stock=VALUES(stock),listed=VALUES(listed)", id, merchantId, request.name(), request.description(), request.priceCent(), request.stock(), request.listed());
    }
    owned.removeIf(item -> item.id() == id);
    owned.add(product);
    return product;
  }

  public synchronized GroupDeal deal(long id) {
    if (jdbc != null) {
      var rows = jdbc.query("SELECT id,merchant_id,title,description,price_cent,stock,is_active FROM group_deal WHERE id=? AND is_deleted=0", this::mapDeal, id);
      if (!rows.isEmpty()) return rows.get(0);
      throw new IllegalArgumentException("团购套餐不存在");
    }
    GroupDeal deal = deals.get(id);
    if (deal == null) throw new IllegalArgumentException("团购套餐不存在");
    return deal;
  }

  public synchronized Product product(long id) {
    if (jdbc != null) {
      var rows = jdbc.query("SELECT id,merchant_id,name,description,price_cent,stock,listed FROM merchant_catalog WHERE id=?", this::mapProduct, id);
      if (!rows.isEmpty()) return rows.get(0);
      throw new IllegalArgumentException("商品不存在");
    }
    return products.values().stream().flatMap(List::stream).filter(item -> item.id() == id).findFirst()
      .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
  }

  @Transactional
  public synchronized InventoryReservation reserveInventory(ReservationRequest request, String idempotencyKey) {
    List<ReservationItem> items = normalizeReservationItems(request);
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
      throw new IllegalArgumentException("Idempotency-Key 不能为空且长度不能超过 128");
    }
    if (jdbc != null) {
      List<ReservationHeader> existing = jdbc.query(
        "SELECT order_id,idempotency_key,status,expires_at FROM inventory_reservation WHERE order_id=? OR idempotency_key=? FOR UPDATE",
        (rs, n) -> new ReservationHeader(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
        request.orderId(), idempotencyKey);
      if (!existing.isEmpty()) {
        ReservationHeader header = existing.get(0);
        if (header.orderId() != request.orderId() || !idempotencyKey.equals(header.idempotencyKey())
            || !itemsEqual(items, reservationItems(header.orderId()))) {
          throw new IllegalStateException("库存预占幂等键或商品载荷不一致");
        }
        return loadReservation(header.orderId());
      }

      for (ReservationItem item : items) reserveJdbcItem(item);
      jdbc.update("INSERT INTO inventory_reservation(order_id,idempotency_key,status,expires_at) VALUES (?,?, 'RESERVED', ?)",
        request.orderId(), idempotencyKey, java.sql.Timestamp.from(request.expiresAt()));
      for (ReservationItem item : items) {
        jdbc.update("INSERT INTO inventory_reservation_item(order_id,item_type,item_id,quantity,expected_version) VALUES (?,?,?,?,?)",
          request.orderId(), item.itemType(), item.itemId(), item.quantity(), item.expectedVersion());
      }
      return loadReservation(request.orderId());
    }

    InventoryReservation existing = reservations.get(request.orderId());
    if (existing != null) {
      if (!idempotencyKey.equals(reservationKeys.get(request.orderId())) || !itemsEqual(items, existing.items())) {
        throw new IllegalStateException("库存预占幂等键或商品载荷不一致");
      }
      return existing;
    }
    if (request.expiresAt() == null || !request.expiresAt().isAfter(Instant.now())) {
      throw new IllegalArgumentException("库存预占过期时间必须晚于当前时间");
    }
    List<Product> productUpdates = new ArrayList<>();
    List<GroupDeal> dealUpdates = new ArrayList<>();
    for (ReservationItem item : items) {
      if ("PRODUCT".equals(item.itemType())) {
        Product current = product(item.itemId());
        if (!current.listed() || current.stock() < item.quantity()) throw new IllegalStateException("商品库存不足");
        productUpdates.add(new Product(current.id(), current.merchantId(), current.name(), current.description(), current.priceCent(), current.stock() - item.quantity(), current.listed()));
      } else {
        GroupDeal current = deal(item.itemId());
        if (!current.active() || current.stock() < item.quantity()) throw new IllegalStateException("套餐库存不足");
        dealUpdates.add(new GroupDeal(current.id(), current.merchantId(), current.title(), current.description(), current.priceCent(), current.stock() - item.quantity(), current.active()));
      }
    }
    productUpdates.forEach(this::replaceProduct);
    dealUpdates.forEach(updated -> deals.put(updated.id(), updated));
    InventoryReservation created = new InventoryReservation(request.orderId(), "RESERVED", request.expiresAt(), items);
    reservations.put(request.orderId(), created);
    reservationKeys.put(request.orderId(), idempotencyKey);
    return created;
  }

  public synchronized InventoryReservation inventoryReservation(long orderId) {
    if (jdbc == null) {
      InventoryReservation reservation = reservations.get(orderId);
      if (reservation == null) throw new IllegalArgumentException("库存预占不存在");
      return reservation;
    }
    return loadReservation(orderId);
  }

  @Transactional
  public synchronized InventoryReservation releaseInventory(long orderId, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
      throw new IllegalArgumentException("Idempotency-Key 不能为空且长度不能超过 128");
    }
    if (jdbc == null) {
      InventoryReservation current = inventoryReservation(orderId);
      if ("CONFIRMED".equals(current.status())) throw new IllegalStateException("已确认的库存不可释放");
      if (!"RESERVED".equals(current.status())) return current;
      current.items().forEach(item -> restoreMemoryItem(item));
      InventoryReservation released = new InventoryReservation(orderId, "RELEASED", current.expiresAt(), current.items());
      reservations.put(orderId, released);
      return released;
    }
    ReservationHeader header = lockedReservation(orderId);
    if ("CONFIRMED".equals(header.status())) throw new IllegalStateException("已确认的库存不可释放");
    if (!"RESERVED".equals(header.status())) return loadReservation(orderId);
    boolean restorable = true;
    for (ReservationItem item : reservationItems(orderId)) {
      restorable &= restoreJdbcItem(item);
    }
    String status = restorable ? "RELEASED" : "CHECK_REQUIRED";
    jdbc.update("UPDATE inventory_reservation SET status=? WHERE order_id=?", status, orderId);
    return loadReservation(orderId);
  }

  @Transactional
  public synchronized InventoryReservation confirmInventory(long orderId) {
    if (jdbc == null) {
      InventoryReservation current = inventoryReservation(orderId);
      if ("RELEASED".equals(current.status())) throw new IllegalStateException("已释放的库存不可确认");
      InventoryReservation confirmed = new InventoryReservation(orderId, "CONFIRMED", current.expiresAt(), current.items());
      reservations.put(orderId, confirmed);
      return confirmed;
    }
    ReservationHeader header = lockedReservation(orderId);
    if ("RELEASED".equals(header.status())) throw new IllegalStateException("已释放的库存不可确认");
    if (!"CONFIRMED".equals(header.status())) jdbc.update("UPDATE inventory_reservation SET status='CONFIRMED' WHERE order_id=?", orderId);
    return loadReservation(orderId);
  }

  private List<ReservationItem> normalizeReservationItems(ReservationRequest request) {
    if (request == null || request.orderId() <= 0) throw new IllegalArgumentException("订单号必须为正数");
    if (request.expiresAt() == null || !request.expiresAt().isAfter(Instant.now())) {
      throw new IllegalArgumentException("库存预占过期时间必须晚于当前时间");
    }
    if (request.items() == null || request.items().isEmpty()) throw new IllegalArgumentException("库存预占至少需要一件商品");
    List<ReservationItem> normalized = request.items().stream().map(item -> {
      if (item == null || item.itemType() == null) throw new IllegalArgumentException("库存预占商品类型不能为空");
      String itemType = item.itemType().trim().toUpperCase(Locale.ROOT);
      if (!"PRODUCT".equals(itemType) && !"GROUP_DEAL".equals(itemType)) throw new IllegalArgumentException("库存预占商品类型不支持");
      if (item.itemId() <= 0 || item.quantity() <= 0 || item.expectedVersion() < 0) throw new IllegalArgumentException("库存预占商品参数不合法");
      return new ReservationItem(itemType, item.itemId(), item.quantity(), item.expectedVersion());
    }).sorted(Comparator.comparing(ReservationItem::itemType).thenComparingLong(ReservationItem::itemId)).toList();
    for (int i = 1; i < normalized.size(); i++) {
      ReservationItem previous = normalized.get(i - 1);
      ReservationItem current = normalized.get(i);
      if (previous.itemType().equals(current.itemType()) && previous.itemId() == current.itemId()) {
        throw new IllegalArgumentException("库存预占不能重复包含同一商品");
      }
    }
    return normalized;
  }

  private boolean itemsEqual(List<ReservationItem> left, List<ReservationItem> right) {
    return left.equals(right);
  }

  private List<ReservationItem> reservationItems(long orderId) {
    if (jdbc == null) return reservations.getOrDefault(orderId, new InventoryReservation(orderId, "", Instant.EPOCH, List.of())).items();
    return jdbc.query("SELECT item_type,item_id,quantity,expected_version FROM inventory_reservation_item WHERE order_id=? ORDER BY item_type,item_id",
      (rs, n) -> new ReservationItem(rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getLong(4)), orderId);
  }

  private InventoryReservation loadReservation(long orderId) {
    List<ReservationHeader> headers = jdbc.query(
      "SELECT order_id,idempotency_key,status,expires_at FROM inventory_reservation WHERE order_id=?",
      (rs, n) -> new ReservationHeader(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()), orderId);
    if (headers.isEmpty()) throw new IllegalArgumentException("库存预占不存在");
    ReservationHeader header = headers.get(0);
    return new InventoryReservation(header.orderId(), header.status(), header.expiresAt(), reservationItems(orderId));
  }

  private ReservationHeader lockedReservation(long orderId) {
    List<ReservationHeader> headers = jdbc.query(
      "SELECT order_id,idempotency_key,status,expires_at FROM inventory_reservation WHERE order_id=? FOR UPDATE",
      (rs, n) -> new ReservationHeader(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()), orderId);
    if (headers.isEmpty()) throw new IllegalArgumentException("库存预占不存在");
    return headers.get(0);
  }

  private void reserveJdbcItem(ReservationItem item) {
    if ("PRODUCT".equals(item.itemType())) {
      List<StockRow> rows = jdbc.query("SELECT stock,listed,version FROM merchant_catalog WHERE id=? FOR UPDATE",
        (rs, n) -> new StockRow(rs.getInt(1), rs.getBoolean(2), rs.getLong(3)), item.itemId());
      if (rows.isEmpty() || !rows.get(0).saleable()) throw new IllegalStateException("商品不存在或未上架");
      StockRow row = rows.get(0);
      if (item.expectedVersion() > 0 && item.expectedVersion() != row.version()) throw new IllegalStateException("商品库存版本已变化");
      int updated = item.expectedVersion() > 0
        ? jdbc.update("UPDATE merchant_catalog SET stock=stock-?,version=version+1 WHERE id=? AND listed=1 AND stock>=? AND version=?", item.quantity(), item.itemId(), item.quantity(), item.expectedVersion())
        : jdbc.update("UPDATE merchant_catalog SET stock=stock-?,version=version+1 WHERE id=? AND listed=1 AND stock>=?", item.quantity(), item.itemId(), item.quantity());
      if (updated != 1) throw new IllegalStateException("商品库存不足或状态已变化");
      return;
    }
    List<StockRow> rows = jdbc.query("SELECT stock,is_active,version FROM group_deal WHERE id=? AND is_deleted=0 FOR UPDATE",
      (rs, n) -> new StockRow(rs.getInt(1), rs.getBoolean(2), rs.getLong(3)), item.itemId());
    if (rows.isEmpty() || !rows.get(0).saleable()) throw new IllegalStateException("团购套餐不存在或未上架");
    StockRow row = rows.get(0);
    if (item.expectedVersion() > 0 && item.expectedVersion() != row.version()) throw new IllegalStateException("团购库存版本已变化");
    int updated = item.expectedVersion() > 0
      ? jdbc.update("UPDATE group_deal SET stock=stock-?,version=version+1 WHERE id=? AND is_active=1 AND is_deleted=0 AND stock>=? AND version=?", item.quantity(), item.itemId(), item.quantity(), item.expectedVersion())
      : jdbc.update("UPDATE group_deal SET stock=stock-?,version=version+1 WHERE id=? AND is_active=1 AND is_deleted=0 AND stock>=?", item.quantity(), item.itemId(), item.quantity());
    if (updated != 1) throw new IllegalStateException("团购库存不足或状态已变化");
  }

  private boolean restoreJdbcItem(ReservationItem item) {
    String table = "PRODUCT".equals(item.itemType()) ? "merchant_catalog" : "group_deal";
    String deletedFilter = "PRODUCT".equals(item.itemType()) ? "" : " AND is_deleted=0";
    return jdbc.update("UPDATE " + table + " SET stock=stock+?,version=version+1 WHERE id=?" + deletedFilter,
      item.quantity(), item.itemId()) == 1;
  }

  private record StockRow(int stock, boolean saleable, long version) {}

  private void replaceProduct(Product updated) {
    products.computeIfAbsent(updated.merchantId(), ignored -> new ArrayList<>()).removeIf(item -> item.id() == updated.id());
    products.computeIfAbsent(updated.merchantId(), ignored -> new ArrayList<>()).add(updated);
  }

  private void restoreMemoryItem(ReservationItem item) {
    if ("PRODUCT".equals(item.itemType())) {
      Product current = product(item.itemId());
      replaceProduct(new Product(current.id(), current.merchantId(), current.name(), current.description(), current.priceCent(), current.stock() + item.quantity(), current.listed()));
      return;
    }
    GroupDeal current = deal(item.itemId());
    deals.put(item.itemId(), new GroupDeal(current.id(), current.merchantId(), current.title(), current.description(), current.priceCent(), current.stock() + item.quantity(), current.active()));
  }

  public synchronized List<GroupDeal> deals(long merchantId) {
    return deals(merchantId, false);
  }

  public synchronized List<GroupDeal> deals(long merchantId, boolean activeOnly) {
    if (jdbc != null) {
      var rows = jdbc.query("SELECT id,merchant_id,title,description,price_cent,stock,is_active FROM group_deal WHERE merchant_id=? AND is_deleted=0" + (activeOnly ? " AND is_active=1" : "") + " ORDER BY id", this::mapDeal, merchantId);
      return rows;
    }
    return deals.values().stream().filter(item -> item.merchantId() == merchantId)
      .filter(item -> !activeOnly || item.active()).toList();
  }

  @Transactional
  public synchronized GroupDeal saveDeal(long merchantId, DealRequest request) {
    merchant(merchantId);
    if (request.priceCent() <= 0 || request.stock() < 0) throw new IllegalArgumentException("套餐价格和库存必须合法");
    long id = request.id() == null ? ids.incrementAndGet() : request.id();
    GroupDeal deal = new GroupDeal(id, merchantId, request.title(), request.description(), request.priceCent(), request.stock(), request.active());
    if (jdbc != null) jdbc.update("INSERT INTO group_deal(id,merchant_id,title,description,price_cent,stock,is_active,is_deleted) VALUES (?,?,?,?,?,?,?,0) ON DUPLICATE KEY UPDATE title=VALUES(title),description=VALUES(description),price_cent=VALUES(price_cent),stock=VALUES(stock),is_active=VALUES(is_active),is_deleted=0", id, merchantId, request.title(), request.description(), request.priceCent(), request.stock(), request.active());
    deals.put(id, deal); return deal;
  }

  public synchronized GroupDeal toggleDeal(long merchantId, long id) {
    GroupDeal old = deal(id); if (old.merchantId() != merchantId) throw new SecurityException("无权维护该套餐");
    GroupDeal updated = new GroupDeal(old.id(), old.merchantId(), old.title(), old.description(), old.priceCent(), old.stock(), !old.active());
    if (jdbc != null) jdbc.update("UPDATE group_deal SET is_active=? WHERE id=? AND merchant_id=? AND is_deleted=0", updated.active(), id, merchantId);
    deals.put(id, updated); return updated;
  }

  @Transactional
  public synchronized void deleteDeal(long merchantId, long id) {
    GroupDeal old = deal(id); if (old.merchantId() != merchantId) throw new SecurityException("无权维护该套餐");
    if (jdbc != null) jdbc.update("UPDATE group_deal SET is_deleted=1,is_active=0 WHERE id=? AND merchant_id=?", id, merchantId);
    deals.remove(id);
  }

  public synchronized Product toggleProduct(long merchantId, long id) {
    Product old = product(id); if (old.merchantId() != merchantId) throw new SecurityException("无权维护该商品");
    return saveProduct(merchantId, new ProductRequest(id, old.name(), old.description(), old.priceCent(), old.stock(), !old.listed()));
  }

  @Transactional
  public synchronized void deleteProduct(long merchantId, long id) {
    Product old = product(id); if (old.merchantId() != merchantId) throw new SecurityException("无权维护该商品");
    products.getOrDefault(merchantId, List.of()).removeIf(item -> item.id() == id);
    if (jdbc != null) {
      jdbc.update("DELETE FROM merchant_catalog WHERE id=? AND merchant_id=?", id, merchantId);
    }
  }

  record ProductRequest(Long id, String name, String description, long priceCent, int stock, boolean listed) {}
  record DealRequest(Long id, String title, String description, long priceCent, int stock, boolean active) {}

  private void seedMerchants() {
    merchants.put(1L, new Merchant(1, "巷口川味研究所", 1, "川湘菜", "https://images.unsplash.com/photo-1585032226651-759b368d7246?auto=format&fit=crop&w=1000&q=80", 4.8, 38, 386, 1.2, "营业中", "梧桐路 18 号", "评分高、距离近、近期销量较好"));
    merchants.put(2L, new Merchant(2, "晨雾咖啡局", 2, "咖啡茶饮", "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=1000&q=80", 4.7, 32, 268, 0.7, "营业中", "湖畔街 3 号", "距离近、评价稳定"));
    merchants.put(3L, new Merchant(3, "绿盒轻食", 3, "轻食简餐", "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?auto=format&fit=crop&w=1000&q=80", 4.5, 29, 189, 2.4, "营业中", "学院路 66 号", "热量标注清晰、复购高"));
    merchants.put(4L, new Merchant(4, "栗香烘焙室", 4, "甜品烘焙", "https://images.unsplash.com/photo-1486427944299-d1955d23e34d?auto=format&fit=crop&w=1000&q=80", 4.6, 26, 142, 3.1, "营业中", "银杏街 9 号", "甜品评分高"));
  }

  private Merchant mapMerchant(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new Merchant(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getDouble(6), rs.getInt(7) / 100, rs.getInt(8), rs.getDouble(9), rs.getString(10), rs.getString(11), rs.getString(12));
  }

  private Product mapProduct(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new Product(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getBoolean(7));
  }

  private GroupDeal mapDeal(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new GroupDeal(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getBoolean(7));
  }

  private ChatMessage mapMessage(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new ChatMessage(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime());
  }

  private Map<String, Object> merchantMap(Merchant value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", value.id()); result.put("name", value.name()); result.put("categoryId", value.categoryId()); result.put("categoryName", value.categoryName());
    result.put("cover", value.cover()); result.put("avgScore", value.avgScore()); result.put("avgPrice", value.avgPrice()); result.put("monthlySales", value.monthlySales()); result.put("distanceKm", value.distanceKm()); result.put("status", value.status()); result.put("address", value.address()); result.put("reason", value.reason());
    return result;
  }

  private void saveMessage(ChatMessage message) {
    if (jdbc != null) {
      jdbc.update("INSERT INTO chat_message(id,user_id,merchant_id,sender_role,sender_name,content,created_at) VALUES (?,?,?,?,?,?,?)", message.id(), message.userId(), message.merchantId(), message.senderRole(), message.senderName(), message.content(), message.createdAt());
      return;
    }
    conversations.computeIfAbsent(key(message.userId(), message.merchantId()), ignored -> new ArrayList<>()).add(message);
  }

  private long nextMessageId() {
    long candidate = ids.incrementAndGet();
    if (jdbc != null) {
      Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM chat_message", Long.class);
      return Math.max(candidate, max == null ? 0 : max + 1);
    }
    return candidate;
  }

  private void bumpIds(String sql) {
    Long max = jdbc.queryForObject(sql, Long.class);
    if (max != null) ids.updateAndGet(current -> Math.max(current, max));
  }

  private long nextId() { return ids.incrementAndGet(); }

  private String key(long userId, long merchantId) { return userId + ":" + merchantId; }

  private String normalizeMessage(String content) {
    if (content == null || content.isBlank()) throw new IllegalArgumentException("消息不能为空");
    String result = content.trim();
    return result.length() > 500 ? result.substring(0, 500) : result;
  }

}
