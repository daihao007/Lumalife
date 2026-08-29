package com.lumalife.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/** Order-owned transactional slice. The user/merchant ids are references only. */
@Service
public class OrderStore {
  public record Order(long id, long userId, long merchantId, long productId, int quantity,
                      long totalCent, String status, Instant createdAt) {}

  private final AtomicLong ids = new AtomicLong(4000);
  private final Map<Long, Order> orders = new LinkedHashMap<>();
  private final JdbcTemplate jdbc;
  private final Map<Long, Map<Long,Integer>> carts = new LinkedHashMap<>();
  private final Map<String, Coupon> coupons = new LinkedHashMap<>();
  private final Map<Long, Review> reviews = new LinkedHashMap<>();
  private final Map<Long, Integer> orderVersions = new HashMap<>();
  private final Map<Long, String> orderTypes = new HashMap<>();

  @Autowired
  public OrderStore(ObjectProvider<JdbcTemplate> provider) { this.jdbc = provider.getIfAvailable(); }
  public OrderStore() { this.jdbc = null; }

  public synchronized Order create(CreateOrderRequest request) {
    if (request.quantity() <= 0) throw new IllegalArgumentException("数量必须大于 0");
    Order order = new Order(ids.incrementAndGet(), request.userId(), request.merchantId(), request.productId(),
      request.quantity(), request.totalCent(), "PENDING_PAYMENT", Instant.now());
    orders.put(order.id(), order);
    orderTypes.put(order.id(), "DELIVERY");
    if (jdbc != null) jdbc.update("INSERT INTO order_record(id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at) VALUES (?,?,?,?,?,?,?,?)", order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), order.status(), java.sql.Timestamp.from(order.createdAt()));
    return order;
  }

  public synchronized List<Order> byUser(long userId) {
    if (jdbc != null) return jdbc.query("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at FROM order_record WHERE user_id=? ORDER BY id", this::map, userId);
    return orders.values().stream().filter(item -> item.userId() == userId).toList();
  }

  public synchronized Order cancel(long userId, long id) {
    Order order = findOrder(id).orElse(null);
    if (order == null || order.userId() != userId) throw new IllegalArgumentException("订单不存在");
    if (!"PENDING_PAYMENT".equals(order.status())) throw new IllegalStateException("当前状态不可取消");
    Order cancelled = new Order(order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), "CANCELLED", order.createdAt());
    orders.put(id, cancelled);
    if (jdbc != null) jdbc.update("UPDATE order_record SET status=? WHERE id=?", "CANCELLED", id);
    appendEvent(id, userId, "CANCELLED");
    return cancelled;
  }

  public synchronized Map<Long,Integer> cart(long userId) {
    if (jdbc != null) return jdbc.query("SELECT product_id,quantity FROM service_cart_item WHERE user_id=?", rs -> { Map<Long,Integer> out=new LinkedHashMap<>(); while(rs.next()) out.put(rs.getLong(1),rs.getInt(2)); return out; }, userId);
    return new LinkedHashMap<>(carts.getOrDefault(userId, Map.of()));
  }

  public synchronized Map<Long,Integer> putCart(long userId, long productId, int quantity) {
    if (quantity <= 0) throw new IllegalArgumentException("数量必须大于 0");
    carts.computeIfAbsent(userId, ignored -> new LinkedHashMap<>()).put(productId, quantity);
    if (jdbc != null) jdbc.update("INSERT INTO service_cart_item(user_id,product_id,quantity) VALUES (?,?,?) ON DUPLICATE KEY UPDATE quantity=VALUES(quantity)", userId, productId, quantity);
    return cart(userId);
  }

  public synchronized Map<Long,Integer> removeCart(long userId, long productId) {
    Map<Long,Integer> current = cart(userId);
    if (!current.containsKey(productId)) throw new IllegalArgumentException("购物车商品不存在");
    current.remove(productId);
    if (jdbc != null) jdbc.update("DELETE FROM service_cart_item WHERE user_id=? AND product_id=?", userId, productId);
    return current;
  }

  public synchronized void clearCart(long userId) {
    carts.remove(userId);
    if (jdbc != null) jdbc.update("DELETE FROM service_cart_item WHERE user_id=?", userId);
  }

  public synchronized Payment pay(long userId, long orderId, long amount, String requestId) {
    if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("clientRequestId 不能为空");
    if (jdbc != null) {
      List<Payment> existing = jdbc.query("SELECT user_id,order_id,client_request_id,amount_cent,status FROM service_payment WHERE user_id=? AND order_id=? AND client_request_id=?", (rs,n) -> new Payment(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5)), userId, orderId, requestId);
      if (!existing.isEmpty()) return existing.get(0);
      jdbc.update("INSERT INTO service_payment(user_id,order_id,client_request_id,amount_cent,status,paid_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)", userId, orderId, requestId, amount, "SUCCESS");
      jdbc.update("UPDATE order_record SET status='PAID', client_request_id=?, version=version+1 WHERE id=? AND user_id=? AND status='PENDING_PAYMENT'", requestId, orderId, userId);
    }
    Order current = findOrder(orderId).orElse(null);
    if (current != null && current.userId() == userId && "PENDING_PAYMENT".equals(current.status())) {
      Order paid = new Order(current.id(), current.userId(), current.merchantId(), current.productId(), current.quantity(), current.totalCent(), "PAID", current.createdAt());
      orders.put(orderId, paid);
      appendEvent(orderId, userId, "PAID");
    }
    if (current != null && "GROUP_BUY".equals(orderType(orderId))) {
      String code = String.format("%012d", orderId);
      coupons.putIfAbsent(code, new Coupon(code, orderId, current.merchantId(), "UNUSED"));
      if (jdbc != null) jdbc.update("INSERT INTO service_coupon(code,order_id,merchant_id,status) VALUES (?,?,?,'UNUSED') ON DUPLICATE KEY UPDATE order_id=VALUES(order_id), merchant_id=VALUES(merchant_id)", code, orderId, current.merchantId());
    }
    return new Payment(userId, orderId, requestId, amount, "SUCCESS");
  }

  private String orderType(long id) {
    if (jdbc == null) return orderTypes.getOrDefault(id, "DELIVERY");
    var types = jdbc.query("SELECT order_type FROM order_record WHERE id=?", (rs,n) -> rs.getString(1), id);
    return types.isEmpty() ? "DELIVERY" : types.get(0);
  }

  public synchronized Order createGroupOrder(GroupOrderRequest request) {
    if (request.quantity() <= 0 || request.merchantId() <= 0 || request.priceCent() <= 0) {
      throw new IllegalArgumentException("团购参数不合法");
    }
    long id = ids.incrementAndGet();
    Order order = new Order(id, request.userId(), request.merchantId(), request.dealId(), request.quantity(),
      request.priceCent() * request.quantity(), "PENDING_PAYMENT", Instant.now());
    orders.put(id, order);
    orderTypes.put(id, "GROUP_BUY");
    if (jdbc != null) jdbc.update("INSERT INTO order_record(id,user_id,merchant_id,product_id,quantity,total_cent,status,order_type,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
      id, request.userId(), request.merchantId(), request.dealId(), request.quantity(), order.totalCent(), order.status(), "GROUP_BUY", java.sql.Timestamp.from(order.createdAt()));
    appendEvent(id, request.userId(), "PENDING_PAYMENT");
    return order;
  }

  public synchronized List<Order> createDeliveryOrders(DeliveryRequest request) {
    if (request.lines() == null || request.lines().isEmpty()) throw new IllegalArgumentException("购物车为空");
    Map<Long, Order> grouped = new LinkedHashMap<>();
    for (DeliveryLine line : request.lines()) {
      if (line.quantity() <= 0 || line.merchantId() <= 0 || line.priceCent() <= 0) throw new IllegalArgumentException("订单商品参数不合法");
      Order order = grouped.computeIfAbsent(line.merchantId(), merchant -> {
        long id = ids.incrementAndGet();
        Order next = new Order(id, request.userId(), merchant, line.productId(), line.quantity(), 0, "PENDING_PAYMENT", Instant.now());
        orders.put(id, next);
        orderTypes.put(id, "DELIVERY");
        return next;
      });
      Order updated = new Order(order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity() + line.quantity(), order.totalCent() + line.priceCent() * line.quantity(), order.status(), order.createdAt());
      grouped.put(line.merchantId(), updated); orders.put(updated.id(), updated);
      if (jdbc != null) jdbc.update("INSERT INTO order_record(id,user_id,merchant_id,product_id,quantity,total_cent,status,order_type,address_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE quantity=VALUES(quantity),total_cent=VALUES(total_cent)", updated.id(), updated.userId(), updated.merchantId(), updated.productId(), updated.quantity(), updated.totalCent(), updated.status(), "DELIVERY", request.addressId(), java.sql.Timestamp.from(updated.createdAt()));
    }
    clearCart(request.userId());
    return new ArrayList<>(grouped.values());
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
    String code = String.format("%012d", orderId);
    Coupon coupon = new Coupon(code, orderId, order.merchantId(), "UNUSED");
    coupons.putIfAbsent(code, coupon);
    return coupons.get(code);
  }

  public synchronized Order verifyCoupon(long merchantId, String code) {
    Coupon coupon = coupons.get(code);
    if (coupon == null && jdbc != null) {
      var rows = jdbc.query("SELECT code,order_id,merchant_id,status FROM service_coupon WHERE code=?", (rs,n) -> new Coupon(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getString(4)), code);
      if (!rows.isEmpty()) coupon = rows.get(0);
    }
    if (coupon == null) throw new IllegalArgumentException("券码不存在");
    if (coupon.merchantId() != merchantId) throw new SecurityException("不能核销其他商家的券码");
    if (!"UNUSED".equals(coupon.status())) throw new IllegalStateException("券码不可重复核销");
    Order order = findOrder(coupon.orderId()).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    Order used = setStatus(order, merchantId, "USED");
    coupons.put(code, new Coupon(code, coupon.orderId(), coupon.merchantId(), "USED"));
    if (jdbc != null) jdbc.update("UPDATE service_coupon SET status='USED', redeemed_at=CURRENT_TIMESTAMP WHERE code=? AND status='UNUSED'", code);
    return used;
  }

  public synchronized Review addReview(ReviewRequest request) {
    if (request.score() < 1 || request.score() > 5 || request.tasteScore() < 1 || request.tasteScore() > 5 || request.serviceScore() < 1 || request.serviceScore() > 5 || request.content() == null || request.content().isBlank()) {
      throw new IllegalArgumentException("评价参数不合法");
    }
    if (reviews.values().stream().anyMatch(r -> r.orderId() == request.orderId())) throw new IllegalStateException("同一订单不可重复评价");
    Order order = findOrder(request.orderId()).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    if (order.userId() != request.userId() || !("RECEIVED".equals(order.status()) || "COMPLETED".equals(order.status()) || "USED".equals(order.status()))) throw new IllegalStateException("订单未完成不可评价");
    Review review = new Review(ids.incrementAndGet(), order.id(), order.merchantId(), request.userName(), request.score(), request.tasteScore(), request.serviceScore(), request.content().trim(), LocalDateTime.now());
    reviews.put(review.id(), review);
    if (jdbc != null) jdbc.update("INSERT INTO service_review(order_id,user_id,merchant_id,score,taste_score,service_score,content) VALUES (?,?,?,?,?,?,?)", order.id(), request.userId(), order.merchantId(), request.score(), request.tasteScore(), request.serviceScore(), request.content().trim());
    return review;
  }

  public synchronized List<Review> reviews(long merchantId) {
    if (jdbc != null) return jdbc.query("SELECT order_id,user_id,merchant_id,score,taste_score,service_score,content,created_at FROM service_review WHERE merchant_id=? ORDER BY created_at DESC", (rs,n) -> new Review(ids.incrementAndGet(), rs.getLong(1), rs.getLong(3), "用户" + rs.getLong(2), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getTimestamp(8).toLocalDateTime()), merchantId);
    return reviews.values().stream().filter(r -> r.merchantId() == merchantId).toList();
  }

  public synchronized List<Order> merchantOrders(long merchantId) {
    if (jdbc != null) return jdbc.query("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at FROM order_record WHERE merchant_id=? ORDER BY id DESC", this::map, merchantId);
    return orders.values().stream().filter(o -> o.merchantId() == merchantId).toList();
  }

  private Optional<Order> findOrder(long id) {
    Order cached = orders.get(id);
    if (cached != null || jdbc == null) return Optional.ofNullable(cached);
    var rows = jdbc.query("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at FROM order_record WHERE id=?", this::map, id);
    return rows.stream().findFirst();
  }

  private Order setStatus(Order order, long actor, String status) {
    Order updated = new Order(order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), status, order.createdAt());
    orders.put(order.id(), updated);
    if (jdbc != null) jdbc.update("UPDATE order_record SET status=?, version=version+1 WHERE id=? AND status=?", status, order.id(), order.status());
    appendEvent(order.id(), actor, status);
    return updated;
  }

  private void appendEvent(long orderId, long actor, String status) {
    if (jdbc != null) jdbc.update("INSERT INTO service_order_event(order_id,version,status,actor_id) SELECT ?, COALESCE(MAX(version),0)+1, ?, ? FROM service_order_event WHERE order_id=?", orderId, status, actor, orderId);
  }

  public record Payment(long userId,long orderId,String clientRequestId,long amountCent,String status) {}
  public record GroupOrderRequest(long userId, long dealId, long merchantId, long priceCent, int quantity) {}
  public record DeliveryLine(long productId, long merchantId, long priceCent, int quantity) {}
  public record DeliveryRequest(long userId, Long addressId, List<DeliveryLine> lines) {}
  public record Coupon(String code, long orderId, long merchantId, String status) {}
  public record ReviewRequest(long userId, long orderId, String userName, int score, int tasteScore, int serviceScore, String content) {}
  public record Review(long id, long orderId, long merchantId, String userName, int score, int tasteScore, int serviceScore, String content, LocalDateTime createdAt) {}

  private Order map(java.sql.ResultSet rs, int row) throws java.sql.SQLException { return new Order(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getInt(5),rs.getLong(6),rs.getString(7),rs.getTimestamp(8).toInstant()); }

  record CreateOrderRequest(long userId, long merchantId, long productId, int quantity, long totalCent) {}
}
