package com.lumalife.order;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order-owned transactional store. The canonical relational model is
 * order_main/order_item/order_status_timeline/payment_record/coupon/review.
 * The old service_* tables are read only for compatibility with an already
 * running database and are not used for new business writes.
 */
@Service
public class OrderStore {
  public record Order(long id, long userId, long merchantId, String merchantName, String type,
                      String status, long totalCent, String clientRequestId, String couponCode,
                      Long addressId, String addressSnapshot, boolean reviewed, boolean stockDeducted,
                      Instant createdAt, List<OrderLine> lines, Map<String, Instant> statusTimeline) {}
  public record OrderLine(Long itemId, String name, int quantity, long priceCent) {}

  private final AtomicLong ids = new AtomicLong(4000);
  private final Map<Long, Order> orders = new LinkedHashMap<>();
  private final JdbcTemplate jdbc;
  private final Map<Long, Map<Long, Integer>> carts = new LinkedHashMap<>();
  private final Map<String, Coupon> coupons = new LinkedHashMap<>();
  private final Map<Long, Review> reviews = new LinkedHashMap<>();

  @Autowired
  public OrderStore(ObjectProvider<JdbcTemplate> provider) {
    this.jdbc = provider.getIfAvailable();
    if (jdbc != null) {
      bumpIds("SELECT COALESCE(MAX(id),0) FROM order_main");
      bumpIds("SELECT COALESCE(MAX(id),0) FROM order_record");
      migrateLegacyOrders();
    }
  }

  public OrderStore() { this.jdbc = null; }

  @Transactional
  public synchronized Order create(CreateOrderRequest request) {
    if (request.quantity() <= 0 || request.totalCent() <= 0) throw new IllegalArgumentException("订单参数不合法");
    long id = nextId();
    Order order = newOrder(id, request.userId(), request.merchantId(), null, "DELIVERY", 0, null, null, null);
    order = withLine(order, new OrderLine(request.productId(), "商品 #" + request.productId(), request.quantity(), request.totalCent() / request.quantity()));
    saveOrder(order);
    return order;
  }

  public synchronized List<Order> byUser(long userId) {
    if (jdbc != null) return jdbc.query("SELECT id,user_id,merchant_id,merchant_name_snapshot,order_type,status,total_cent,client_request_id,coupon_code,address_id,address_snapshot,is_reviewed,is_stock_deducted,created_at FROM order_main WHERE user_id=? AND is_deleted=0 ORDER BY created_at DESC,id DESC", this::mapOrder, userId);
    return orders.values().stream().filter(item -> item.userId() == userId)
      .sorted(Comparator.comparing(Order::createdAt).reversed()).toList();
  }

  public synchronized Order cancel(long userId, long id) {
    Order order = findOrder(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (order.userId() != userId) throw new IllegalArgumentException("订单不存在");
    if (!"PENDING_PAYMENT".equals(order.status())) throw new IllegalStateException("当前状态不可取消");
    return changeStatus(order, userId, "CANCELLED");
  }

  public synchronized Map<Long, Integer> cart(long userId) {
    if (jdbc != null) {
      Map<Long, Integer> result = queryCart("service_cart_item", userId);
      if (result.isEmpty()) {
        result = queryCart("cart_item", userId);
        for (Map.Entry<Long, Integer> entry : result.entrySet()) {
          jdbc.update("INSERT INTO service_cart_item(user_id,product_id,quantity) VALUES (?,?,?) ON DUPLICATE KEY UPDATE quantity=VALUES(quantity)", userId, entry.getKey(), entry.getValue());
        }
      }
      return result;
    }
    return new LinkedHashMap<>(carts.getOrDefault(userId, Map.of()));
  }

  public synchronized Map<Long, Integer> putCart(long userId, long productId, int quantity) {
    if (quantity <= 0 || quantity > 99) throw new IllegalArgumentException("数量必须在 1 到 99 之间");
    carts.computeIfAbsent(userId, ignored -> new LinkedHashMap<>()).put(productId, quantity);
    if (jdbc != null) jdbc.update("INSERT INTO service_cart_item(user_id,product_id,quantity) VALUES (?,?,?) ON DUPLICATE KEY UPDATE quantity=VALUES(quantity)", userId, productId, quantity);
    return cart(userId);
  }

  public synchronized Map<Long, Integer> addToCart(long userId, long productId, int quantity) {
    if (quantity <= 0) throw new IllegalArgumentException("数量必须大于 0");
    return putCart(userId, productId, cart(userId).getOrDefault(productId, 0) + quantity);
  }

  public synchronized Map<Long, Integer> removeCart(long userId, long productId) {
    Map<Long, Integer> current = cart(userId);
    if (!current.containsKey(productId)) throw new IllegalArgumentException("购物车商品不存在");
    current.remove(productId);
    carts.put(userId, new LinkedHashMap<>(current));
    if (jdbc != null) jdbc.update("DELETE FROM service_cart_item WHERE user_id=? AND product_id=?", userId, productId);
    return current;
  }

  public synchronized void clearCart(long userId) {
    carts.remove(userId);
    if (jdbc != null) jdbc.update("DELETE FROM service_cart_item WHERE user_id=?", userId);
  }

  @Transactional
  public synchronized Payment pay(long userId, long orderId, long amount, String requestId) {
    if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("clientRequestId 不能为空");
    Order current = findOrder(orderId).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (current.userId() != userId) throw new IllegalArgumentException("订单不存在");
    long chargedAmount = current.totalCent();
    if (amount > 0 && amount != chargedAmount) throw new IllegalArgumentException("支付金额与订单金额不一致");
    if (jdbc != null) {
      List<Payment> sameRequest = jdbc.query("SELECT user_id,order_id,client_request_id,amount_cent,status FROM payment_record WHERE user_id=? AND client_request_id=? ORDER BY id DESC", this::mapPayment, userId, requestId);
      if (!sameRequest.isEmpty() && sameRequest.get(0).orderId() != orderId) throw new IllegalStateException("clientRequestId 已用于其他订单");
      List<Payment> existing = jdbc.query("SELECT user_id,order_id,client_request_id,amount_cent,status FROM payment_record WHERE user_id=? AND order_id=? AND client_request_id=?", this::mapPayment, userId, orderId, requestId);
      if (!existing.isEmpty()) return existing.get(0);
      if (!"PENDING_PAYMENT".equals(current.status())) throw new IllegalStateException("订单状态不允许支付");
      int changed = jdbc.update("UPDATE order_main SET status='PAID',client_request_id=?,version=version+1 WHERE id=? AND user_id=? AND status='PENDING_PAYMENT'", requestId, orderId, userId);
      if (changed != 1) {
        Order latest = findOrder(orderId).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!"PAID".equals(latest.status())) throw new IllegalStateException("订单状态不允许支付");
      }
      try {
        jdbc.update("INSERT INTO payment_record(user_id,order_id,client_request_id,amount_cent,status,paid_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)", userId, orderId, requestId, chargedAmount, "SUCCESS");
      } catch (DuplicateKeyException duplicate) {
        return jdbc.query("SELECT user_id,order_id,client_request_id,amount_cent,status FROM payment_record WHERE user_id=? AND order_id=? AND client_request_id=?", this::mapPayment, userId, orderId, requestId).stream().findFirst().orElseThrow();
      }
      if ("GROUP_BUY".equals(current.type())) ensureCoupon(orderId, current.merchantId());
      appendTimeline(orderId, userId, "PAID");
      return new Payment(userId, orderId, requestId, chargedAmount, "SUCCESS");
    }
    Order requestOrder = orders.values().stream().filter(item -> item.userId() == userId && requestId.equals(item.clientRequestId())).findFirst().orElse(null);
    if (requestOrder != null) {
      if (requestOrder.id() != orderId) throw new IllegalStateException("clientRequestId 已用于其他订单");
      return new Payment(userId, orderId, requestId, chargedAmount, "SUCCESS");
    }
    if (!"PENDING_PAYMENT".equals(current.status())) throw new IllegalStateException("订单状态不允许支付");
    Order paid = copyOrder(current, "PAID", requestId, current.couponCode());
    if ("GROUP_BUY".equals(current.type())) paid = copyOrder(paid, paid.status(), paid.clientRequestId(), String.format("%012d", orderId));
    orders.put(orderId, paid);
    if (paid.couponCode() != null) coupons.putIfAbsent(paid.couponCode(), new Coupon(paid.couponCode(), orderId, paid.merchantId(), "UNUSED"));
    return new Payment(userId, orderId, requestId, chargedAmount, "SUCCESS");
  }

  public synchronized Order order(long id) {
    return findOrder(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
  }

  @Transactional
  public synchronized Order createGroupOrder(GroupOrderRequest request) {
    if (request.quantity() <= 0 || request.merchantId() <= 0 || request.priceCent() <= 0) throw new IllegalArgumentException("团购参数不合法");
    long id = nextId();
    Order order = newOrder(id, request.userId(), request.merchantId(), request.merchantName(), "GROUP_BUY", request.priceCent() * request.quantity(), null, null, null);
    order = withLine(order, new OrderLine(request.dealId(), request.title() == null ? "团购套餐 #" + request.dealId() : request.title(), request.quantity(), request.priceCent()));
    saveOrder(order);
    return order;
  }

  @Transactional
  public synchronized List<Order> createDeliveryOrders(DeliveryRequest request) {
    if (request.lines() == null || request.lines().isEmpty()) throw new IllegalArgumentException("购物车为空");
    Map<Long, Order> grouped = new LinkedHashMap<>();
    for (DeliveryLine line : request.lines()) {
      if (line.quantity() <= 0 || line.merchantId() <= 0 || line.priceCent() <= 0) throw new IllegalArgumentException("订单商品参数不合法");
      Order order = grouped.get(line.merchantId());
      if (order == null) {
        order = newOrder(nextId(), request.userId(), line.merchantId(), line.merchantName(), "DELIVERY", 0, request.addressId(), request.addressSnapshot(), null);
      }
      order = withLine(order, new OrderLine(line.productId(), line.name() == null || line.name().isBlank() ? "商品 #" + line.productId() : line.name(), line.quantity(), line.priceCent()));
      order = copyOrder(order, order.status(), order.clientRequestId(), order.couponCode());
      grouped.put(line.merchantId(), order);
    }
    if (jdbc != null) {
      for (Order order : grouped.values()) saveOrder(order);
    } else {
      grouped.values().forEach(order -> orders.put(order.id(), order));
    }
    clearCart(request.userId());
    return new ArrayList<>(grouped.values());
  }

  public synchronized Order receive(long userId, long id) {
    Order order = findOrder(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (order.userId() != userId) throw new IllegalArgumentException("订单不存在");
    if (!("DELIVERING".equals(order.status()) || "COMPLETED".equals(order.status()))) throw new IllegalStateException("订单尚未配送，不能确认收货");
    return changeStatus(order, userId, "RECEIVED");
  }

  @Transactional
  public synchronized Order transition(long merchantId, long id, String next) {
    Order order = findOrder(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (order.merchantId() != merchantId) throw new SecurityException("不能处理其他商家的订单");
    boolean allowed = ("PAID".equals(order.status()) && "ACCEPTED".equals(next))
      || ("ACCEPTED".equals(order.status()) && "DELIVERING".equals(next))
      || ("DELIVERING".equals(order.status()) && "COMPLETED".equals(next));
    if (!allowed) throw new IllegalStateException("非法订单状态流转");
    return changeStatus(order, merchantId, next);
  }

  @Transactional
  public synchronized Order verifyCoupon(long merchantId, String code) {
    Coupon coupon = coupons.get(code);
    if (jdbc != null) {
      List<Coupon> rows = jdbc.query("SELECT code,order_id,merchant_id,status FROM coupon WHERE code=?", this::mapCoupon, code);
      if (!rows.isEmpty()) coupon = rows.get(0);
      if (coupon == null) rows = jdbc.query("SELECT code,order_id,merchant_id,status FROM service_coupon WHERE code=?", this::mapCoupon, code);
      if (coupon == null && !rows.isEmpty()) coupon = rows.get(0);
    }
    if (coupon == null) throw new IllegalArgumentException("券码不存在");
    if (coupon.merchantId() != merchantId) throw new SecurityException("不能核销其他商家的券码");
    if (!"UNUSED".equals(coupon.status())) throw new IllegalStateException("券码不可重复核销");
    Order order = findOrder(coupon.orderId()).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    Order used = changeStatus(order, merchantId, "USED");
    coupons.put(code, new Coupon(code, coupon.orderId(), coupon.merchantId(), "USED"));
    if (jdbc != null) {
      jdbc.update("UPDATE coupon SET status='USED',redeemed_at=CURRENT_TIMESTAMP WHERE code=? AND status='UNUSED'", code);
      jdbc.update("UPDATE service_coupon SET status='USED',redeemed_at=CURRENT_TIMESTAMP WHERE code=? AND status='UNUSED'", code);
    }
    return used;
  }

  @Transactional
  public synchronized Review addReview(ReviewRequest request) {
    if (request.score() < 1 || request.score() > 5 || request.tasteScore() < 1 || request.tasteScore() > 5 || request.serviceScore() < 1 || request.serviceScore() > 5 || request.content() == null || request.content().isBlank()) throw new IllegalArgumentException("评价参数不合法");
    Order order = findOrder(request.orderId()).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (order.userId() != request.userId() || !("RECEIVED".equals(order.status()) || "COMPLETED".equals(order.status()) || "USED".equals(order.status()))) throw new IllegalStateException("订单未完成不可评价");
    if (jdbc != null) {
      if (!jdbc.query("SELECT id FROM review WHERE order_id=? AND is_deleted=0", (rs, n) -> rs.getLong(1), order.id()).isEmpty()) throw new IllegalStateException("同一订单不可重复评价");
      jdbc.update("INSERT INTO review(order_id,user_id,merchant_id,user_name_snapshot,score,taste_score,service_score,content) VALUES (?,?,?,?,?,?,?,?)", order.id(), request.userId(), order.merchantId(), request.userName(), request.score(), request.tasteScore(), request.serviceScore(), request.content().trim());
      jdbc.update("UPDATE order_main SET is_reviewed=1 WHERE id=?", order.id());
      return jdbc.query("SELECT id,order_id,merchant_id,user_name_snapshot,score,taste_score,service_score,content,created_at FROM review WHERE order_id=?", this::mapReview, order.id()).get(0);
    }
    if (reviews.values().stream().anyMatch(r -> r.orderId() == request.orderId())) throw new IllegalStateException("同一订单不可重复评价");
    Review review = new Review(nextId(), order.id(), order.merchantId(), request.userName(), request.score(), request.tasteScore(), request.serviceScore(), request.content().trim(), LocalDateTime.now());
    reviews.put(review.id(), review);
    orders.put(order.id(), copyOrder(order, order.status(), order.clientRequestId(), order.couponCode(), true));
    return review;
  }

  public synchronized List<Review> reviews(long merchantId) {
    if (jdbc != null) return jdbc.query("SELECT id,order_id,merchant_id,user_name_snapshot,score,taste_score,service_score,content,created_at FROM review WHERE merchant_id=? AND is_deleted=0 ORDER BY created_at DESC", this::mapReview, merchantId);
    return reviews.values().stream().filter(r -> r.merchantId() == merchantId).sorted(Comparator.comparing(Review::createdAt).reversed()).toList();
  }

  public synchronized List<Order> merchantOrders(long merchantId) {
    if (jdbc != null) return jdbc.query("SELECT id,user_id,merchant_id,merchant_name_snapshot,order_type,status,total_cent,client_request_id,coupon_code,address_id,address_snapshot,is_reviewed,is_stock_deducted,created_at FROM order_main WHERE merchant_id=? AND is_deleted=0 ORDER BY created_at DESC,id DESC", this::mapOrder, merchantId);
    return orders.values().stream().filter(o -> o.merchantId() == merchantId).sorted(Comparator.comparing(Order::createdAt).reversed()).toList();
  }

  private Optional<Order> findOrder(long id) {
    Order cached = orders.get(id);
    if (cached != null || jdbc == null) return Optional.ofNullable(cached);
    List<Order> rows = jdbc.query("SELECT id,user_id,merchant_id,merchant_name_snapshot,order_type,status,total_cent,client_request_id,coupon_code,address_id,address_snapshot,is_reviewed,is_stock_deducted,created_at FROM order_main WHERE id=? AND is_deleted=0", this::mapOrder, id);
    if (!rows.isEmpty()) return Optional.of(rows.get(0));
    return jdbc.query("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at FROM order_record WHERE id=?", this::mapLegacyOrder, id).stream().findFirst();
  }

  private Order changeStatus(Order order, long actor, String next) {
    if (jdbc != null) {
      int changed = jdbc.update("UPDATE order_main SET status=?,version=version+1 WHERE id=? AND status=?", next, order.id(), order.status());
      if (changed != 1) return findOrder(order.id()).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
      appendTimeline(order.id(), actor, next);
      return findOrder(order.id()).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    }
    Order updated = copyOrder(order, next, order.clientRequestId(), order.couponCode());
    orders.put(order.id(), updated);
    return updated;
  }

  private void saveOrder(Order order) {
    if (jdbc == null) {
      orders.put(order.id(), order);
      return;
    }
    jdbc.update("INSERT INTO order_main(id,user_id,merchant_id,merchant_name_snapshot,order_type,status,total_cent,client_request_id,coupon_code,address_id,address_snapshot,is_reviewed,is_stock_deducted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", order.id(), order.userId(), order.merchantId(), order.merchantName(), order.type(), order.status(), order.totalCent(), order.clientRequestId(), order.couponCode(), order.addressId(), order.addressSnapshot(), order.reviewed(), order.stockDeducted());
    for (OrderLine line : order.lines()) jdbc.update("INSERT INTO order_item(order_id,item_type,item_id,item_name_snapshot,quantity,unit_price_cent) VALUES (?,?,?,?,?,?)", order.id(), "GROUP_BUY".equals(order.type()) ? "GROUP_DEAL" : "PRODUCT", line.itemId(), line.name(), line.quantity(), line.priceCent());
    appendTimeline(order.id(), order.userId(), "PENDING_PAYMENT");
  }

  private void appendTimeline(long orderId, long actor, String status) {
    if (jdbc != null) {
      jdbc.update("INSERT IGNORE INTO order_status_timeline(order_id,status,occurred_at) VALUES (?,?,CURRENT_TIMESTAMP)", orderId, status);
      jdbc.update("INSERT INTO service_order_event(order_id,version,status,actor_id) SELECT ?,COALESCE(MAX(version),0)+1,?,? FROM service_order_event WHERE order_id=?", orderId, status, actor, orderId);
    }
  }

  private void ensureCoupon(long orderId, long merchantId) {
    String code = String.format("%012d", orderId);
    if (jdbc != null) jdbc.update("INSERT INTO coupon(order_id,merchant_id,code,status) VALUES (?,?,?,'UNUSED') ON DUPLICATE KEY UPDATE code=VALUES(code)", orderId, merchantId, code);
    coupons.putIfAbsent(code, new Coupon(code, orderId, merchantId, "UNUSED"));
  }

  private Order newOrder(long id, long userId, long merchantId, String merchantName, String type, long total, Long addressId, String addressSnapshot, String couponCode) {
    return new Order(id, userId, merchantId, merchantName == null || merchantName.isBlank() ? "商家 #" + merchantId : merchantName, type, "PENDING_PAYMENT", total, null, couponCode, addressId, addressSnapshot, false, false, Instant.now(), new ArrayList<>(), new LinkedHashMap<>(Map.of("PENDING_PAYMENT", Instant.now())));
  }

  private Order withLine(Order order, OrderLine line) {
    List<OrderLine> lines = new ArrayList<>(order.lines());
    lines.add(line);
    return new Order(order.id(), order.userId(), order.merchantId(), order.merchantName(), order.type(), order.status(), order.totalCent() + line.priceCent() * line.quantity(), order.clientRequestId(), order.couponCode(), order.addressId(), order.addressSnapshot(), order.reviewed(), order.stockDeducted(), order.createdAt(), lines, order.statusTimeline());
  }

  private Order copyOrder(Order order, String status, String requestId, String couponCode) {
    return copyOrder(order, status, requestId, couponCode, order.reviewed());
  }

  private Order copyOrder(Order order, String status, String requestId, String couponCode, boolean reviewed) {
    Map<String, Instant> timeline = new LinkedHashMap<>(order.statusTimeline());
    timeline.putIfAbsent(status, Instant.now());
    return new Order(order.id(), order.userId(), order.merchantId(), order.merchantName(), order.type(), status, order.totalCent(), requestId, couponCode, order.addressId(), order.addressSnapshot(), reviewed, order.stockDeducted(), order.createdAt(), order.lines(), timeline);
  }

  private Map<Long, Integer> queryCart(String table, long userId) {
    return jdbc.query("SELECT product_id,quantity FROM " + table + " WHERE user_id=?", rs -> {
      Map<Long, Integer> result = new LinkedHashMap<>();
      while (rs.next()) result.put(rs.getLong(1), rs.getInt(2));
      return result;
    }, userId);
  }

  private void migrateLegacyOrders() {
    List<Map<String, Object>> legacy = jdbc.queryForList("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,order_type,client_request_id,coupon_code,address_id,reviewed,version,created_at FROM order_record");
    for (Map<String, Object> row : legacy) {
      long id = ((Number) row.get("id")).longValue();
      try {
        jdbc.update("INSERT IGNORE INTO order_main(id,user_id,merchant_id,merchant_name_snapshot,order_type,status,total_cent,client_request_id,coupon_code,address_id,is_reviewed,version,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", id, row.get("user_id"), row.get("merchant_id"), "商家 #" + row.get("merchant_id"), row.getOrDefault("order_type", "DELIVERY"), row.get("status"), row.get("total_cent"), row.get("client_request_id"), row.get("coupon_code"), row.get("address_id"), row.getOrDefault("reviewed", false), row.getOrDefault("version", 0), row.get("created_at"));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM order_item WHERE order_id=?", Integer.class, id);
        if (count == null || count == 0) jdbc.update("INSERT INTO order_item(order_id,item_type,item_id,item_name_snapshot,quantity,unit_price_cent) VALUES (?,?,?,?,?,?)", id, "GROUP_BUY".equals(row.get("order_type")) ? "GROUP_DEAL" : "PRODUCT", row.get("product_id"), "商品 #" + row.get("product_id"), row.get("quantity"), ((Number) row.get("total_cent")).longValue() / Math.max(1, ((Number) row.get("quantity")).intValue()));
        jdbc.update("INSERT IGNORE INTO order_status_timeline(order_id,status,occurred_at) VALUES (?,?,?)", id, row.get("status"), row.get("created_at"));
      } catch (RuntimeException ignored) {
        // A legacy row with an invalid foreign reference must not prevent the
        // service from starting; it remains available through order_record.
      }
    }
    jdbc.update("UPDATE order_main om JOIN merchant m ON m.id=om.merchant_id SET om.merchant_name_snapshot=m.name WHERE om.merchant_name_snapshot LIKE '商家 #%'");
  }

  private void bumpIds(String sql) {
    Long max = jdbc.queryForObject(sql, Long.class);
    if (max != null) ids.updateAndGet(current -> Math.max(current, max));
  }

  private long nextId() { return ids.incrementAndGet(); }

  private Order mapOrder(ResultSet rs, int row) throws SQLException {
    long id = rs.getLong("id");
    return new Order(id, rs.getLong("user_id"), rs.getLong("merchant_id"), rs.getString("merchant_name_snapshot"), rs.getString("order_type"), rs.getString("status"), rs.getLong("total_cent"), rs.getString("client_request_id"), rs.getString("coupon_code"), nullableLong(rs, "address_id"), rs.getString("address_snapshot"), rs.getBoolean("is_reviewed"), rs.getBoolean("is_stock_deducted"), rs.getTimestamp("created_at").toInstant(), readLines(id), readTimeline(id));
  }

  private Order mapLegacyOrder(ResultSet rs, int row) throws SQLException {
    long id = rs.getLong("id");
    int quantity = rs.getInt("quantity");
    Map<String, Instant> timeline = new LinkedHashMap<>();
    timeline.put(rs.getString("status"), rs.getTimestamp("created_at").toInstant());
    return new Order(id, rs.getLong("user_id"), rs.getLong("merchant_id"), "商家 #" + rs.getLong("merchant_id"), "DELIVERY", rs.getString("status"), rs.getLong("total_cent"), null, null, null, null, false, false, rs.getTimestamp("created_at").toInstant(), List.of(new OrderLine(rs.getLong("product_id"), "商品 #" + rs.getLong("product_id"), quantity, rs.getLong("total_cent") / Math.max(1, quantity))), timeline);
  }

  private List<OrderLine> readLines(long orderId) {
    return jdbc.query("SELECT item_id,item_name_snapshot,quantity,unit_price_cent FROM order_item WHERE order_id=? ORDER BY id", (rs, n) -> new OrderLine((Long) rs.getObject("item_id"), rs.getString("item_name_snapshot"), rs.getInt("quantity"), rs.getLong("unit_price_cent")), orderId);
  }

  private Map<String, Instant> readTimeline(long orderId) {
    Map<String, Instant> timeline = new LinkedHashMap<>();
    jdbc.query("SELECT status,occurred_at FROM order_status_timeline WHERE order_id=? ORDER BY occurred_at,id", rs -> { while (rs.next()) timeline.put(rs.getString(1), rs.getTimestamp(2).toInstant()); return timeline; }, orderId);
    return timeline;
  }

  private Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private Payment mapPayment(ResultSet rs, int row) throws SQLException { return new Payment(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5)); }
  private Coupon mapCoupon(ResultSet rs, int row) throws SQLException { return new Coupon(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getString(4)); }
  private Review mapReview(ResultSet rs, int row) throws SQLException { return new Review(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getString(8), rs.getTimestamp(9).toLocalDateTime()); }

  public record Payment(long userId, long orderId, String clientRequestId, long amountCent, String status) {}
  public record GroupOrderRequest(long userId, long dealId, long merchantId, long priceCent, int quantity, String title, String merchantName) {
    public GroupOrderRequest(long userId, long dealId, long merchantId, long priceCent, int quantity) { this(userId, dealId, merchantId, priceCent, quantity, null, null); }
    public GroupOrderRequest(long userId, long dealId, long merchantId, long priceCent, int quantity, String title) { this(userId, dealId, merchantId, priceCent, quantity, title, null); }
  }
  public record DeliveryLine(long productId, long merchantId, long priceCent, int quantity, String name, String merchantName) {
    public DeliveryLine(long productId, long merchantId, long priceCent, int quantity) { this(productId, merchantId, priceCent, quantity, null, null); }
    public DeliveryLine(long productId, long merchantId, long priceCent, int quantity, String name) { this(productId, merchantId, priceCent, quantity, name, null); }
  }
  public record DeliveryRequest(long userId, Long addressId, String addressSnapshot, List<DeliveryLine> lines) {
    public DeliveryRequest(long userId, Long addressId, List<DeliveryLine> lines) { this(userId, addressId, null, lines); }
  }
  public record Coupon(String code, long orderId, long merchantId, String status) {}
  public record ReviewRequest(long userId, long orderId, String userName, int score, int tasteScore, int serviceScore, String content) {}
  public record Review(long id, long orderId, long merchantId, String userName, int score, int tasteScore, int serviceScore, String content, LocalDateTime createdAt) {}
  public record CreateOrderRequest(long userId, long merchantId, long productId, int quantity, long totalCent) {}
}
