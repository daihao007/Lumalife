package com.lumalife.order;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Order-owned transactional slice. The user/merchant ids are references only. */
@Service
public class OrderStore {
  public record Order(long id, long userId, long merchantId, long productId, int quantity,
                      long totalCent, String status, Instant createdAt, Map<String, Instant> statusTimeline,
                      String couponCode, String type, boolean reviewed, List<OrderLine> lines,
                      String addressSnapshot) {
    public Order(long id, long userId, long merchantId, long productId, int quantity,
                 long totalCent, String status, Instant createdAt) {
      this(id, userId, merchantId, productId, quantity, totalCent, status, createdAt,
        Map.of(status, createdAt), null, "DELIVERY", false, List.of(), null);
    }

    public Order(long id, long userId, long merchantId, long productId, int quantity,
                 long totalCent, String status, Instant createdAt, Map<String, Instant> statusTimeline) {
      this(id, userId, merchantId, productId, quantity, totalCent, status, createdAt,
        statusTimeline, null, "DELIVERY", false, List.of(), null);
    }

    public Order(long id, long userId, long merchantId, long productId, int quantity,
                 long totalCent, String status, Instant createdAt, Map<String, Instant> statusTimeline,
                 String couponCode) {
      this(id, userId, merchantId, productId, quantity, totalCent, status, createdAt,
        statusTimeline, couponCode, "DELIVERY", false, List.of(), null);
    }

    public Order(long id, long userId, long merchantId, long productId, int quantity,
                 long totalCent, String status, Instant createdAt, Map<String, Instant> statusTimeline,
                 String couponCode, String type, boolean reviewed, List<OrderLine> lines) {
      this(id, userId, merchantId, productId, quantity, totalCent, status, createdAt,
        statusTimeline, couponCode, type, reviewed, lines, null);
    }

    public Order(long id, long userId, long merchantId, long productId, int quantity,
                 long totalCent, String status, Instant createdAt, Map<String, Instant> statusTimeline,
                 String couponCode, String type, boolean reviewed, List<OrderLine> lines,
                 String addressSnapshot) {
      this.id = id;
      this.userId = userId;
      this.merchantId = merchantId;
      this.productId = productId;
      this.quantity = quantity;
      this.totalCent = totalCent;
      this.status = status;
      this.createdAt = createdAt;
      this.statusTimeline = statusTimeline == null ? Map.of() : Map.copyOf(statusTimeline);
      this.couponCode = couponCode;
      this.type = type == null ? "DELIVERY" : type;
      this.reviewed = reviewed;
      this.lines = lines == null ? List.of() : List.copyOf(lines);
      this.addressSnapshot = addressSnapshot;
    }
  }

  public record OrderLine(long itemId, String name, int quantity, long priceCent) {}

  private final AtomicLong ids = new AtomicLong(4000);
  private final Map<Long, Order> orders = new LinkedHashMap<>();
  private final JdbcTemplate jdbc;
  private final Map<Long, Map<Long, Integer>> carts = new LinkedHashMap<>();
  private final Map<String, Coupon> coupons = new LinkedHashMap<>();
  private final Map<String, Payment> payments = new LinkedHashMap<>();
  private final Map<Long, Review> reviews = new LinkedHashMap<>();
  private final Map<Long, String> orderTypes = new HashMap<>();

  @Autowired
  public OrderStore(ObjectProvider<JdbcTemplate> provider) { this(provider.getIfAvailable()); }

  OrderStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    refreshIdSequence();
  }

  public OrderStore() { this((JdbcTemplate) null); }

  private void refreshIdSequence() {
    if (jdbc == null) return;
    try {
      Long maxId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 4000) FROM order_record", Long.class);
      if (maxId != null) ids.updateAndGet(current -> Math.max(current, maxId));
    } catch (DataAccessException ignored) {
      // The service may start before the migration has been applied.
    }
  }

  private long nextOrderId() {
    refreshIdSequence();
    return ids.incrementAndGet();
  }

  public synchronized Order create(CreateOrderRequest request) {
    if (request.quantity() <= 0) throw new IllegalArgumentException("数量必须大于 0");
    Order order = new Order(nextOrderId(), request.userId(), request.merchantId(), request.productId(),
      request.quantity(), request.totalCent(), "PENDING_PAYMENT", Instant.now());
    orders.put(order.id(), order);
    orderTypes.put(order.id(), "DELIVERY");
    if (jdbc != null) jdbc.update("INSERT INTO order_record(id,user_id,merchant_id,product_id,quantity,total_cent,status,order_type,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
      order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), order.status(), "DELIVERY", java.sql.Timestamp.from(order.createdAt()));
    return order;
  }

  public synchronized List<Order> byUser(long userId) {
    if (jdbc != null) return jdbc.query("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at,order_type,coupon_code,reviewed,address_snapshot FROM order_record WHERE user_id=? ORDER BY id", this::map, userId);
    return orders.values().stream().filter(item -> item.userId() == userId).toList();
  }

  public synchronized Order cancel(long userId, long id) {
    Order order = findOrder(id).orElse(null);
    if (order == null || order.userId() != userId) throw new IllegalArgumentException("订单不存在");
    if (!"PENDING_PAYMENT".equals(order.status())) throw new IllegalStateException("当前状态不可取消");
    Order cancelled = withStatus(order, "CANCELLED");
    orders.put(id, cancelled);
    if (jdbc != null) jdbc.update("UPDATE order_record SET status=? WHERE id=? AND user_id=? AND status='PENDING_PAYMENT'", "CANCELLED", id, userId);
    appendEvent(id, userId, "CANCELLED");
    return cancelled;
  }

  public synchronized Map<Long, Integer> cart(long userId) {
    if (jdbc != null) return jdbc.query("SELECT product_id,quantity FROM service_cart_item WHERE user_id=?", rs -> {
      Map<Long, Integer> out = new LinkedHashMap<>();
      while (rs.next()) out.put(rs.getLong(1), rs.getInt(2));
      return out;
    }, userId);
    return new LinkedHashMap<>(carts.getOrDefault(userId, Map.of()));
  }

  public synchronized Map<Long, Integer> putCart(long userId, long productId, int quantity) {
    if (quantity <= 0) throw new IllegalArgumentException("数量必须大于 0");
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
    Order current = jdbc == null
      ? findOrder(orderId).orElseThrow(() -> new IllegalArgumentException("订单不存在"))
      : lockedOrder(orderId);
    if (current.userId() != userId) throw new IllegalArgumentException("订单不存在");
    String paymentKey = userId + ":" + requestId;
    Payment cachedPayment = payments.get(paymentKey);
    if (cachedPayment != null) return replayPayment(current, orderId, amount, cachedPayment);

    long chargedAmount = current.totalCent();
    if (jdbc != null) {
      List<Payment> existing = jdbc.query("SELECT user_id,order_id,client_request_id,amount_cent,status FROM service_payment WHERE user_id=? AND client_request_id=?",
        (rs, n) -> new Payment(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5)), userId, requestId);
      if (!existing.isEmpty()) {
        payments.put(paymentKey, existing.get(0));
        return replayPayment(current, orderId, amount, existing.get(0));
      }
      List<Long> requestOwners = jdbc.query("SELECT id FROM order_record WHERE user_id=? AND client_request_id=? FOR UPDATE",
        (rs, n) -> rs.getLong(1), userId, requestId);
      if (!requestOwners.isEmpty()) throw new IllegalStateException("clientRequestId 已用于其他订单");
    }
    if (!"PENDING_PAYMENT".equals(current.status())) throw new IllegalStateException("当前订单不可支付");
    if (amount != chargedAmount) throw new IllegalArgumentException("支付金额不匹配");

    boolean groupBuy = "GROUP_BUY".equals(current.type()) || "GROUP_BUY".equals(orderType(orderId));
    String couponCode = groupBuy ? String.format("%012d", orderId) : null;
    if (jdbc != null) {
      jdbc.update("INSERT INTO service_payment(user_id,order_id,client_request_id,amount_cent,status,paid_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
        userId, orderId, requestId, chargedAmount, "SUCCESS");
      int updated = jdbc.update("UPDATE order_record SET status='PAID', client_request_id=?, coupon_code=?, version=version+1 WHERE id=? AND user_id=? AND status='PENDING_PAYMENT'",
        requestId, couponCode, orderId, userId);
      if (updated != 1) throw new IllegalStateException("订单状态已变化，支付未完成");
      if (groupBuy) jdbc.update("INSERT INTO service_coupon(code,order_id,merchant_id,status) VALUES (?,?,?,'UNUSED') ON DUPLICATE KEY UPDATE order_id=VALUES(order_id), merchant_id=VALUES(merchant_id)",
        couponCode, orderId, current.merchantId());
      appendEvent(orderId, userId, "PAID");
    }
    Order paid = withStatusAndCoupon(current, "PAID", couponCode);
    orders.put(orderId, paid);
    if (groupBuy) coupons.putIfAbsent(couponCode, new Coupon(couponCode, orderId, current.merchantId(), "UNUSED"));
    Payment payment = new Payment(userId, orderId, requestId, chargedAmount, "SUCCESS");
    payments.put(paymentKey, payment);
    return payment;
  }

  private Payment replayPayment(Order current, long requestedOrderId, long amount, Payment existing) {
    if (existing.orderId() != requestedOrderId) throw new IllegalStateException("clientRequestId 已用于其他订单");
    if (existing.amountCent() != amount) throw new IllegalStateException("clientRequestId 请求金额不一致");
    if (!"SUCCESS".equals(existing.status())) throw new IllegalStateException("支付记录状态异常");
    if (!"PAID".equals(current.status())) throw new IllegalStateException("支付记录与订单状态不一致");
    return existing;
  }

  private Order lockedOrder(long id) {
    List<Order> rows = jdbc.query("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at,order_type,coupon_code,reviewed,address_snapshot FROM order_record WHERE id=? FOR UPDATE", this::map, id);
    if (rows.isEmpty()) throw new IllegalArgumentException("订单不存在");
    return rows.get(0);
  }

  public synchronized Order order(long id) {
    return findOrder(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
  }

  private String orderType(long id) {
    if (jdbc == null) return orderTypes.getOrDefault(id, "DELIVERY");
    var types = jdbc.query("SELECT order_type FROM order_record WHERE id=?", (rs, n) -> rs.getString(1), id);
    return types.isEmpty() ? "DELIVERY" : types.get(0);
  }

  public synchronized Order createGroupOrder(GroupOrderRequest request) {
    if (request.quantity() <= 0 || request.merchantId() <= 0 || request.priceCent() <= 0) throw new IllegalStateException("团购参数不合法");
    long id = nextOrderId();
    List<OrderLine> lines = List.of(new OrderLine(request.dealId(), "团购套餐 " + request.dealId(), request.quantity(), request.priceCent()));
    Order order = new Order(id, request.userId(), request.merchantId(), request.dealId(), request.quantity(),
      request.priceCent() * request.quantity(), "PENDING_PAYMENT", Instant.now(), Map.of("PENDING_PAYMENT", Instant.now()), null, "GROUP_BUY", false, lines, null);
    orders.put(id, order);
    orderTypes.put(id, "GROUP_BUY");
    if (jdbc != null) {
      jdbc.update("INSERT INTO order_record(id,user_id,merchant_id,product_id,quantity,total_cent,status,order_type,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
        id, request.userId(), request.merchantId(), request.dealId(), request.quantity(), order.totalCent(), order.status(), "GROUP_BUY", java.sql.Timestamp.from(order.createdAt()));
      insertLines(id, lines);
    }
    appendEvent(id, request.userId(), "PENDING_PAYMENT");
    return order;
  }

  public synchronized List<Order> createDeliveryOrders(DeliveryRequest request) {
    if (request.lines() == null || request.lines().isEmpty()) throw new IllegalArgumentException("购物车为空");
    Map<Long, List<DeliveryLine>> grouped = new LinkedHashMap<>();
    for (DeliveryLine line : request.lines()) {
      if (line.quantity() <= 0 || line.merchantId() <= 0 || line.priceCent() <= 0) throw new IllegalArgumentException("订单商品参数不合法");
      grouped.computeIfAbsent(line.merchantId(), ignored -> new ArrayList<>()).add(line);
    }

    List<Order> result = new ArrayList<>();
    for (Map.Entry<Long, List<DeliveryLine>> entry : grouped.entrySet()) {
      List<DeliveryLine> sourceLines = entry.getValue();
      DeliveryLine first = sourceLines.get(0);
      int quantity = sourceLines.stream().mapToInt(DeliveryLine::quantity).sum();
      long total = sourceLines.stream().mapToLong(line -> line.priceCent() * line.quantity()).sum();
      List<OrderLine> lines = sourceLines.stream().map(line -> new OrderLine(line.productId(), "商品 " + line.productId(), line.quantity(), line.priceCent())).toList();
      long id = nextOrderId();
      Order order = new Order(id, request.userId(), entry.getKey(), first.productId(), quantity, total,
        "PENDING_PAYMENT", Instant.now(), Map.of("PENDING_PAYMENT", Instant.now()), null, "DELIVERY", false, lines, request.addressSnapshot());
      orders.put(id, order);
      orderTypes.put(id, "DELIVERY");
      if (jdbc != null) {
        jdbc.update("INSERT INTO order_record(id,user_id,merchant_id,product_id,quantity,total_cent,status,order_type,address_id,address_snapshot,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
          id, order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), order.status(), "DELIVERY", request.addressId(), request.addressSnapshot(), java.sql.Timestamp.from(order.createdAt()));
        insertLines(id, lines);
      }
      appendEvent(id, request.userId(), "PENDING_PAYMENT");
      result.add(order);
    }
    clearCart(request.userId());
    return result;
  }

  private void insertLines(long orderId, List<OrderLine> lines) {
    if (jdbc == null) return;
    jdbc.update("DELETE FROM service_order_line WHERE order_id=?", orderId);
    for (int i = 0; i < lines.size(); i++) {
      OrderLine line = lines.get(i);
      jdbc.update("INSERT INTO service_order_line(order_id,line_no,item_id,item_name,quantity,price_cent) VALUES (?,?,?,?,?,?)",
        orderId, i + 1, line.itemId(), line.name(), line.quantity(), line.priceCent());
    }
  }

  public synchronized Order receive(long userId, long id) {
    Order order = findOrder(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (order.userId() != userId) throw new IllegalArgumentException("订单不存在");
    if (!("DELIVERING".equals(order.status()) || "COMPLETED".equals(order.status()))) throw new IllegalStateException("订单尚未配送，不能确认收货");
    return setStatus(order, userId, "RECEIVED");
  }

  public synchronized Order transition(long merchantId, long id, String next) {
    Order order = findOrder(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (order.merchantId() != merchantId) throw new SecurityException("不能处理其他商家的订单");
    boolean allowed = ("PAID".equals(order.status()) && "ACCEPTED".equals(next))
      || ("ACCEPTED".equals(order.status()) && "DELIVERING".equals(next))
      || ("DELIVERING".equals(order.status()) && "COMPLETED".equals(next));
    if (!allowed) throw new IllegalStateException("非法订单状态流转");
    return setStatus(order, merchantId, next);
  }

  public synchronized Coupon issueCoupon(long userId, long orderId) {
    Order order = findOrder(orderId).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (order.userId() != userId || !"PAID".equals(order.status())) throw new IllegalStateException("订单不可生成券码");
    String code = order.couponCode() == null ? String.format("%012d", orderId) : order.couponCode();
    Coupon coupon = new Coupon(code, orderId, order.merchantId(), "UNUSED");
    coupons.putIfAbsent(code, coupon);
    if (jdbc != null) {
      jdbc.update("INSERT INTO service_coupon(code,order_id,merchant_id,status) VALUES (?,?,?,'UNUSED') ON DUPLICATE KEY UPDATE order_id=VALUES(order_id), merchant_id=VALUES(merchant_id)", code, orderId, order.merchantId());
      jdbc.update("UPDATE order_record SET coupon_code=? WHERE id=?", code, orderId);
    }
    return coupons.get(code);
  }

  public synchronized Order verifyCoupon(long merchantId, String code) {
    Coupon coupon = coupons.get(code);
    if (coupon == null && jdbc != null) {
      var rows = jdbc.query("SELECT code,order_id,merchant_id,status FROM service_coupon WHERE code=?", (rs, n) -> new Coupon(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getString(4)), code);
      if (!rows.isEmpty()) coupon = rows.get(0);
    }
    if (coupon == null) throw new IllegalArgumentException("券码不存在");
    if (coupon.merchantId() != merchantId) throw new SecurityException("不能核销其他商家的券码");
    if (!"UNUSED".equals(coupon.status())) throw new IllegalStateException("券码不可重复核销");
    Order order = findOrder(coupon.orderId()).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    Order used = setStatus(order, merchantId, "USED");
    Coupon redeemed = new Coupon(code, coupon.orderId(), coupon.merchantId(), "USED");
    coupons.put(code, redeemed);
    if (jdbc != null) jdbc.update("UPDATE service_coupon SET status='USED', redeemed_at=CURRENT_TIMESTAMP WHERE code=? AND status='UNUSED'", code);
    return used;
  }

  public synchronized Review addReview(ReviewRequest request) {
    if (request.score() < 1 || request.score() > 5 || request.tasteScore() < 1 || request.tasteScore() > 5
      || request.serviceScore() < 1 || request.serviceScore() > 5 || request.content() == null || request.content().isBlank()) {
      throw new IllegalArgumentException("评价参数不合法");
    }
    if (reviews.values().stream().anyMatch(r -> r.orderId() == request.orderId())) throw new IllegalStateException("同一订单不可重复评价");
    if (jdbc != null && !jdbc.query("SELECT order_id FROM service_review WHERE order_id=?", (rs, n) -> rs.getLong(1), request.orderId()).isEmpty()) {
      throw new IllegalStateException("同一订单不可重复评价");
    }
    Order order = findOrder(request.orderId()).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (order.userId() != request.userId() || !("RECEIVED".equals(order.status()) || "COMPLETED".equals(order.status()) || "USED".equals(order.status()))) {
      throw new IllegalStateException("订单未完成不可评价");
    }
    String content = request.content().trim();
    Review review = new Review(nextReviewId(), order.id(), order.merchantId(), request.userName(), request.score(), request.tasteScore(), request.serviceScore(), content, LocalDateTime.now());
    if (jdbc != null) {
      jdbc.update("INSERT INTO service_review(order_id,user_id,merchant_id,score,taste_score,service_score,content) VALUES (?,?,?,?,?,?,?)",
        order.id(), request.userId(), order.merchantId(), request.score(), request.tasteScore(), request.serviceScore(), content);
      jdbc.update("UPDATE order_record SET reviewed=TRUE WHERE id=?", order.id());
    }
    reviews.put(review.id(), review);
    return review;
  }

  private long nextReviewId() {
    return ids.incrementAndGet();
  }

  public synchronized List<Review> reviews(long merchantId) {
    if (jdbc != null) return jdbc.query("SELECT order_id,user_id,merchant_id,score,taste_score,service_score,content,created_at FROM service_review WHERE merchant_id=? ORDER BY created_at DESC",
      (rs, n) -> new Review(nextReviewId(), rs.getLong(1), rs.getLong(3), "用户" + rs.getLong(2), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getTimestamp(8).toLocalDateTime()), merchantId);
    return reviews.values().stream().filter(r -> r.merchantId() == merchantId).toList();
  }

  public synchronized List<Order> merchantOrders(long merchantId) {
    if (jdbc != null) return jdbc.query("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at,order_type,coupon_code,reviewed,address_snapshot FROM order_record WHERE merchant_id=? ORDER BY id DESC", this::map, merchantId);
    return orders.values().stream().filter(o -> o.merchantId() == merchantId).toList();
  }

  private Optional<Order> findOrder(long id) {
    Order cached = orders.get(id);
    if (cached != null || jdbc == null) return Optional.ofNullable(cached);
    var rows = jdbc.query("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at,order_type,coupon_code,reviewed,address_snapshot FROM order_record WHERE id=?", this::map, id);
    return rows.stream().findFirst();
  }

  private Order setStatus(Order order, long actor, String status) {
    Order updated = withStatus(order, status);
    orders.put(order.id(), updated);
    if (jdbc != null) jdbc.update("UPDATE order_record SET status=?, version=version+1 WHERE id=? AND status=?", status, order.id(), order.status());
    appendEvent(order.id(), actor, status);
    return updated;
  }

  private Order withStatus(Order order, String status) {
    Map<String, Instant> timeline = new LinkedHashMap<>(order.statusTimeline());
    timeline.put(status, Instant.now());
    return new Order(order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), status,
      order.createdAt(), timeline, order.couponCode(), order.type(), order.reviewed(), order.lines(), order.addressSnapshot());
  }

  private Order withStatusAndCoupon(Order order, String status, String couponCode) {
    Map<String, Instant> timeline = new LinkedHashMap<>(order.statusTimeline());
    timeline.put(status, Instant.now());
    return new Order(order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), status,
      order.createdAt(), timeline, couponCode, order.type(), order.reviewed(), order.lines(), order.addressSnapshot());
  }

  private void appendEvent(long orderId, long actor, String status) {
    if (jdbc != null) {
      Integer nextVersion = jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM service_order_event WHERE order_id=?", Integer.class, orderId);
      jdbc.update("INSERT INTO service_order_event(order_id,version,status,actor_id) VALUES (?,?,?,?)", orderId, nextVersion == null ? 1 : nextVersion, status, actor);
    }
  }

  private Order map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    long id = rs.getLong("id");
    return new Order(id, rs.getLong("user_id"), rs.getLong("merchant_id"), rs.getLong("product_id"),
      rs.getInt("quantity"), rs.getLong("total_cent"), rs.getString("status"),
      rs.getTimestamp("created_at").toInstant(), timeline(id), rs.getString("coupon_code"),
      rs.getString("order_type"), rs.getBoolean("reviewed"), lines(id), rs.getString("address_snapshot"));
  }

  private Map<String, Instant> timeline(long orderId) {
    if (jdbc == null) return Map.of();
    return jdbc.query("SELECT status,occurred_at FROM service_order_event WHERE order_id=? ORDER BY version",
      (rs, n) -> Map.entry(rs.getString(1), rs.getTimestamp(2).toInstant()), orderId)
      .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, ignored) -> first, LinkedHashMap::new));
  }

  private List<OrderLine> lines(long orderId) {
    if (jdbc == null) return List.of();
    return jdbc.query("SELECT item_id,item_name,quantity,price_cent FROM service_order_line WHERE order_id=? ORDER BY line_no",
      (rs, n) -> new OrderLine(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getLong(4)), orderId);
  }

  public record Payment(long userId, long orderId, String clientRequestId, long amountCent, String status) {}
  public record GroupOrderRequest(long userId, long dealId, long merchantId, long priceCent, int quantity) {}
  public record DeliveryLine(long productId, long merchantId, long priceCent, int quantity) {}
  public record DeliveryRequest(long userId, Long addressId, String addressSnapshot, List<DeliveryLine> lines) {
    public DeliveryRequest(long userId, Long addressId, List<DeliveryLine> lines) {
      this(userId, addressId, null, lines);
    }
  }
  public record Coupon(String code, long orderId, long merchantId, String status) {}
  public record ReviewRequest(long userId, long orderId, String userName, int score, int tasteScore, int serviceScore, String content) {}
  public record Review(long id, long orderId, long merchantId, String userName, int score, int tasteScore, int serviceScore, String content, LocalDateTime createdAt) {}
  record CreateOrderRequest(long userId, long merchantId, long productId, int quantity, long totalCent) {}
}
