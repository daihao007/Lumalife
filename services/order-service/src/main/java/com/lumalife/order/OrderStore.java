package com.lumalife.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;

/** Order-owned transactional slice. The user/merchant ids are references only. */
@Service
public class OrderStore {
  public record Order(long id, long userId, long merchantId, long productId, int quantity,
                      long totalCent, String status, Instant createdAt) {}

  private final AtomicLong ids = new AtomicLong(4000);
  private final Map<Long, Order> orders = new LinkedHashMap<>();
  private final JdbcTemplate jdbc;
  private final Map<Long, Map<Long,Integer>> carts = new LinkedHashMap<>();

  public OrderStore(ObjectProvider<JdbcTemplate> provider) { this.jdbc = provider.getIfAvailable(); }
  public OrderStore() { this.jdbc = null; }

  public synchronized Order create(CreateOrderRequest request) {
    if (request.quantity() <= 0) throw new IllegalArgumentException("数量必须大于 0");
    Order order = new Order(ids.incrementAndGet(), request.userId(), request.merchantId(), request.productId(),
      request.quantity(), request.totalCent(), "PENDING_PAYMENT", Instant.now());
    orders.put(order.id(), order);
    if (jdbc != null) jdbc.update("INSERT INTO order_record(id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at) VALUES (?,?,?,?,?,?,?,?)", order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), order.status(), java.sql.Timestamp.from(order.createdAt()));
    return order;
  }

  public synchronized List<Order> byUser(long userId) {
    if (jdbc != null) return jdbc.query("SELECT id,user_id,merchant_id,product_id,quantity,total_cent,status,created_at FROM order_record WHERE user_id=? ORDER BY id", this::map, userId);
    return orders.values().stream().filter(item -> item.userId() == userId).toList();
  }

  public synchronized Order cancel(long userId, long id) {
    Order order = orders.get(id);
    if (order == null || order.userId() != userId) throw new IllegalArgumentException("订单不存在");
    if (!"PENDING_PAYMENT".equals(order.status())) throw new IllegalStateException("当前状态不可取消");
    Order cancelled = new Order(order.id(), order.userId(), order.merchantId(), order.productId(), order.quantity(), order.totalCent(), "CANCELLED", order.createdAt());
    orders.put(id, cancelled);
    if (jdbc != null) jdbc.update("UPDATE order_record SET status=? WHERE id=?", "CANCELLED", id);
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

  public synchronized Payment pay(long userId, long orderId, long amount, String requestId) {
    if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("clientRequestId 不能为空");
    if (jdbc != null) {
      List<Payment> existing = jdbc.query("SELECT user_id,order_id,client_request_id,amount_cent,status FROM service_payment WHERE user_id=? AND order_id=? AND client_request_id=?", (rs,n) -> new Payment(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5)), userId, orderId, requestId);
      if (!existing.isEmpty()) return existing.get(0);
      jdbc.update("INSERT INTO service_payment(user_id,order_id,client_request_id,amount_cent,status,paid_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)", userId, orderId, requestId, amount, "SUCCESS");
    }
    return new Payment(userId, orderId, requestId, amount, "SUCCESS");
  }

  public record Payment(long userId,long orderId,String clientRequestId,long amountCent,String status) {}

  private Order map(java.sql.ResultSet rs, int row) throws java.sql.SQLException { return new Order(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getInt(5),rs.getLong(6),rs.getString(7),rs.getTimestamp(8).toInstant()); }

  record CreateOrderRequest(long userId, long merchantId, long productId, int quantity, long totalCent) {}
}
