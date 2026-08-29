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

  private Order map(java.sql.ResultSet rs, int row) throws java.sql.SQLException { return new Order(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getInt(5),rs.getLong(6),rs.getString(7),rs.getTimestamp(8).toInstant()); }

  record CreateOrderRequest(long userId, long merchantId, long productId, int quantity, long totalCent) {}
}
